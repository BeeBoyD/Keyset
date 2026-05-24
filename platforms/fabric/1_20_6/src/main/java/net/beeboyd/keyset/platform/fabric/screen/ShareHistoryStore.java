package net.beeboyd.keyset.platform.fabric.screen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Persists the list of profiles this client has shared, for display in the Share tab. */
public final class ShareHistoryStore {
  private ShareHistoryStore() {}

  private static final Gson GSON = new Gson();

  public record Entry(
      String code, String profileName, String profileId, long sharedAt, long expiresAt) {}

  public static List<Entry> load(Path historyFile) {
    List<Entry> result = new ArrayList<>();
    if (!Files.exists(historyFile)) return result;
    try {
      String json = Files.readString(historyFile, StandardCharsets.UTF_8);
      JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
      long now = System.currentTimeMillis();
      for (var elem : arr) {
        if (!elem.isJsonObject()) continue;
        JsonObject obj = elem.getAsJsonObject();
        long expiresAt = obj.get("expiresAt").getAsLong();
        if (expiresAt <= now) continue;
        result.add(
            new Entry(
                obj.get("code").getAsString(),
                obj.get("profileName").getAsString(),
                obj.get("profileId").getAsString(),
                obj.get("sharedAt").getAsLong(),
                expiresAt));
      }
    } catch (Exception ignored) {
    }
    return result;
  }

  public static void save(Path historyFile, List<Entry> entries) {
    JsonArray arr = new JsonArray();
    for (Entry e : entries) {
      JsonObject obj = new JsonObject();
      obj.addProperty("code", e.code());
      obj.addProperty("profileName", e.profileName());
      obj.addProperty("profileId", e.profileId());
      obj.addProperty("sharedAt", e.sharedAt());
      obj.addProperty("expiresAt", e.expiresAt());
      arr.add(obj);
    }
    try {
      Files.createDirectories(historyFile.getParent());
      Files.writeString(historyFile, GSON.toJson(arr), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
    }
  }
}
