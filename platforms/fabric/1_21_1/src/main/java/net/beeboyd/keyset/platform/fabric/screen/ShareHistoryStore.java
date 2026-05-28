package net.beeboyd.keyset.platform.fabric.screen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Persists the list of profiles this client has shared, for display in the Share tab. */
public final class ShareHistoryStore {
  private ShareHistoryStore() {}

  private static final Gson GSON = new Gson();
  private static final int MAX_HISTORY = 20;

  public record Entry(
      String code, String profileName, String profileId, long sharedAt, long expiresAt) {}

  public record LoadResult(List<Entry> entries, boolean recoveredBrokenFile) {}

  public static List<Entry> load(Path historyFile) {
    return loadResult(historyFile).entries();
  }

  public static LoadResult loadResult(Path historyFile) {
    List<Entry> result = new ArrayList<>();
    boolean recoveredBrokenFile = false;
    if (!Files.exists(historyFile)) return new LoadResult(result, false);
    try {
      String json = Files.readString(historyFile, StandardCharsets.UTF_8);
      JsonElement root = JsonParser.parseString(json);
      if (root == null || root.isJsonNull() || !root.isJsonArray()) {
        archiveBrokenHistory(historyFile);
        return new LoadResult(result, true);
      }
      long now = System.currentTimeMillis();
      for (var elem : root.getAsJsonArray()) {
        Entry entry = readEntry(elem);
        if (entry == null || entry.expiresAt() <= now) continue;
        result.add(entry);
        if (result.size() >= MAX_HISTORY) break;
      }
    } catch (Exception ignored) {
      archiveBrokenHistory(historyFile);
      recoveredBrokenFile = true;
    }
    return new LoadResult(result, recoveredBrokenFile);
  }

  private static void archiveBrokenHistory(Path historyFile) {
    try {
      if (!Files.exists(historyFile)) return;
      Path archived =
          historyFile.resolveSibling(
              historyFile.getFileName().toString() + ".broken." + System.currentTimeMillis());
      try {
        Files.move(
            historyFile,
            archived,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(historyFile, archived, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException ignored) {
    }
  }

  private static Entry readEntry(JsonElement elem) {
    try {
      if (!elem.isJsonObject()) return null;
      JsonObject obj = elem.getAsJsonObject();
      return new Entry(
          readString(obj, "code"),
          readString(obj, "profileName"),
          readString(obj, "profileId"),
          readLong(obj, "sharedAt"),
          readLong(obj, "expiresAt"));
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private static String readString(JsonObject obj, String field) {
    JsonElement value = obj.get(field);
    if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return "";
    return value.getAsString();
  }

  private static long readLong(JsonObject obj, String field) {
    JsonElement value = obj.get(field);
    if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return 0L;
    return value.getAsLong();
  }

  public static boolean save(Path historyFile, List<Entry> entries) {
    JsonArray arr = new JsonArray();
    long now = System.currentTimeMillis();
    List<Entry> capped = new ArrayList<>(entries);
    capped.removeIf(e -> e.expiresAt() <= now);
    while (capped.size() > MAX_HISTORY) capped.remove(0);
    for (Entry e : capped) {
      JsonObject obj = new JsonObject();
      obj.addProperty("code", e.code());
      obj.addProperty("profileName", e.profileName());
      obj.addProperty("profileId", e.profileId());
      obj.addProperty("sharedAt", e.sharedAt());
      obj.addProperty("expiresAt", e.expiresAt());
      arr.add(obj);
    }
    Path tempFile = null;
    try {
      Path parent = historyFile.getParent();
      if (parent != null) Files.createDirectories(parent);
      tempFile =
          Files.createTempFile(
              parent != null ? parent : historyFile.toAbsolutePath().getParent(),
              historyFile.getFileName().toString(),
              ".tmp");
      Files.writeString(tempFile, GSON.toJson(arr), StandardCharsets.UTF_8);
      try {
        Files.move(
            tempFile,
            historyFile,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(tempFile, historyFile, StandardCopyOption.REPLACE_EXISTING);
      }
      tempFile = null;
      return true;
    } catch (IOException ignored) {
      return false;
    } finally {
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
      }
    }
  }
}
