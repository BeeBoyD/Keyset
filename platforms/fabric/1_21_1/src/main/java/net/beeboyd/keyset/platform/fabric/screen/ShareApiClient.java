package net.beeboyd.keyset.platform.fabric.screen;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class ShareApiClient {
  private ShareApiClient() {}

  private static final String BASE = "https://share.beeboyd.com/api/keyset/shares";
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
    return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
        .thenApply(
            r -> {
              if (r.statusCode() != 201) throw new RuntimeException("HTTP " + r.statusCode());
              var j = GSON.fromJson(r.body(), JsonObject.class);
              return new UploadResult(j.get("code").getAsString(), j.get("expires_at").getAsLong());
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
            .uri(URI.create(BASE + "/" + code.toUpperCase()))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();
    return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
        .thenApply(
            r -> {
              if (r.statusCode() == 404) throw new RuntimeException("not_found");
              if (r.statusCode() != 200) throw new RuntimeException("HTTP " + r.statusCode());
              var j = GSON.fromJson(r.body(), JsonObject.class);
              ShareMeta meta = ShareMeta.empty();
              if (j.has("meta") && j.get("meta").isJsonObject()) {
                var mj = j.getAsJsonObject("meta");
                meta =
                    new ShareMeta(
                        mj.has("username") ? mj.get("username").getAsString() : "",
                        mj.has("profileName") ? mj.get("profileName").getAsString() : "");
              }
              return new DownloadResult(
                  j.get("data").getAsString(), meta, j.get("expires_at").getAsLong());
            });
  }
}
