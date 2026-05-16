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

  public record DownloadResult(String data) {}

  public static CompletableFuture<UploadResult> upload(String profileJson) {
    var body = new JsonObject();
    body.addProperty("data", profileJson);
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
              return new DownloadResult(j.get("data").getAsString());
            });
  }
}
