package net.beeboyd.keyset.platform.fabric.screen;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ShareApiClient {
  private ShareApiClient() {}

  private static final String BASE = "https://share.beeboyd.com/api/keyset/shares";
  private static final int MAX_RESPONSE_BYTES = 256 * 1024;
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  private static final Gson GSON = new Gson();

  public record UploadResult(String code, long expiresAt) {}

  public record ShareMeta(String username, String profileName) {
    public static ShareMeta empty() {
      return new ShareMeta("", "");
    }
  }

  public record DownloadResult(String data, ShareMeta meta, long expiresAt) {}

  public static CompletableFuture<UploadResult> upload(
      String profileJson, String username, String profileName) {
    var body = new JsonObject();
    body.addProperty("data", profileJson);
    var meta = new JsonObject();
    meta.addProperty("username", username);
    meta.addProperty("profileName", profileName);
    body.add("meta", meta);
    var req =
        HttpRequest.newBuilder()
            .uri(URI.create(BASE))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
    return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream())
        .thenApply(
            r -> {
              if (r.statusCode() != 201) {
                closeQuietly(r.body());
                throw new RuntimeException(errorKey(r.statusCode()));
              }
              JsonObject j = parseObject(readLimitedBody(r.body()));
              return new UploadResult(requireCode(j), requireLong(j, "expires_at"));
            });
  }

  /** Returns the binding translation-key IDs stored in a single-profile share JSON. */
  public static java.util.List<String> parseBindingKeys(String singleProfileJson) {
    try {
      var obj = GSON.fromJson(singleProfileJson, JsonObject.class);
      if (!obj.has("bindings") || !obj.get("bindings").isJsonObject()) {
        return java.util.List.of();
      }
      return new java.util.ArrayList<>(obj.getAsJsonObject("bindings").keySet());
    } catch (Exception e) {
      return java.util.List.of();
    }
  }

  public static CompletableFuture<DownloadResult> download(String code) {
    var req =
        HttpRequest.newBuilder()
            .uri(URI.create(BASE + "/" + normalizeCode(code)))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();
    return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream())
        .thenApply(
            r -> {
              if (r.statusCode() != 200) {
                closeQuietly(r.body());
                throw new RuntimeException(errorKey(r.statusCode()));
              }
              JsonObject j = parseObject(readLimitedBody(r.body()));
              ShareMeta meta = ShareMeta.empty();
              if (j.has("meta") && j.get("meta").isJsonObject()) {
                var mj = j.getAsJsonObject("meta");
                meta =
                    new ShareMeta(
                        optionalString(mj, "username"), optionalString(mj, "profileName"));
              }
              return new DownloadResult(
                  requireString(j, "data"), meta, requireLong(j, "expires_at"));
            });
  }

  private static String errorKey(int statusCode) {
    if (statusCode == 404) return "keyset.share.error.not_found";
    if (statusCode == 429) return "keyset.share.error.rate_limited";
    if (statusCode >= 500) return "keyset.share.error.server_error";
    return "keyset.share.error.unknown";
  }

  private static String readLimitedBody(InputStream body) {
    try (InputStream in = body) {
      byte[] bytes = in.readNBytes(MAX_RESPONSE_BYTES + 1);
      if (bytes.length > MAX_RESPONSE_BYTES) {
        throw new RuntimeException("keyset.share.error.malformed_response");
      }
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static void closeQuietly(InputStream body) {
    try {
      body.close();
    } catch (IOException ignored) {
    }
  }

  private static JsonObject parseObject(String body) {
    try {
      JsonElement element = GSON.fromJson(body, JsonElement.class);
      if (element == null || element.isJsonNull() || !element.isJsonObject()) {
        throw new IllegalArgumentException("malformed_response");
      }
      return element.getAsJsonObject();
    } catch (RuntimeException exception) {
      throw new RuntimeException("keyset.share.error.malformed_response", exception);
    }
  }

  private static String requireCode(JsonObject object) {
    String normalized = normalizeCode(requireString(object, "code"));
    if (!normalized.matches("[A-Z0-9]{8}")) {
      throw new RuntimeException("keyset.share.error.malformed_response");
    }
    return normalized;
  }

  private static String normalizeCode(String code) {
    String normalized = code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z0-9]*")) {
      throw new RuntimeException("keyset.share.error.malformed_response");
    }
    return normalized;
  }

  private static String optionalString(JsonObject object, String field) {
    if (!object.has(field) || object.get(field).isJsonNull()) return "";
    return requireString(object, field);
  }

  private static String requireString(JsonObject object, String field) {
    if (!object.has(field) || object.get(field).isJsonNull()) {
      throw new RuntimeException("keyset.share.error.malformed_response");
    }
    JsonElement value = object.get(field);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new RuntimeException("keyset.share.error.malformed_response");
    }
    return value.getAsString();
  }

  private static long requireLong(JsonObject object, String field) {
    if (!object.has(field) || object.get(field).isJsonNull()) {
      throw new RuntimeException("keyset.share.error.malformed_response");
    }
    JsonElement value = object.get(field);
    if (!value.isJsonPrimitive()) {
      throw new RuntimeException("keyset.share.error.malformed_response");
    }
    JsonPrimitive primitive = value.getAsJsonPrimitive();
    if (!primitive.isNumber()) {
      throw new RuntimeException("keyset.share.error.malformed_response");
    }
    try {
      return primitive.getAsLong();
    } catch (RuntimeException exception) {
      throw new RuntimeException("keyset.share.error.malformed_response", exception);
    }
  }
}
