package net.beeboyd.keyset.platform.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.beeboyd.keyset.core.autoswitch.AutoSwitchRule;

/** Reads and writes auto-switch rules to {@code config/keyset-autoswitch.json}. */
final class KeysetAutoSwitchStore {
  private static final Gson GSON =
      new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

  private final Path path;

  KeysetAutoSwitchStore(Path path) {
    this.path = path;
  }

  List<AutoSwitchRule> load() throws IOException {
    if (!Files.exists(path)) return new ArrayList<AutoSwitchRule>();
    try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      JsonElement root = new JsonParser().parse(r);
      if (!root.isJsonArray()) return new ArrayList<AutoSwitchRule>();
      List<AutoSwitchRule> rules = new ArrayList<AutoSwitchRule>();
      for (JsonElement el : root.getAsJsonArray()) {
        if (!el.isJsonObject()) continue;
        JsonObject obj = el.getAsJsonObject();
        String pattern = obj.has("pattern") ? obj.get("pattern").getAsString() : null;
        String profileId = obj.has("profileId") ? obj.get("profileId").getAsString() : null;
        if (pattern != null && !pattern.isEmpty() && profileId != null && !profileId.isEmpty()) {
          try {
            rules.add(new AutoSwitchRule(pattern, profileId));
          } catch (IllegalArgumentException ignored) {
            // skip malformed entry
          }
        }
      }
      return rules;
    }
  }

  void save(List<AutoSwitchRule> rules) throws IOException {
    Path parent = path.getParent();
    if (parent != null) Files.createDirectories(parent);

    JsonArray arr = new JsonArray();
    for (AutoSwitchRule rule : rules) {
      JsonObject obj = new JsonObject();
      obj.addProperty("pattern", rule.getPattern());
      obj.addProperty("profileId", rule.getProfileId());
      arr.add(obj);
    }

    Path tmp =
        Files.createTempFile(
            parent != null ? parent : path.toAbsolutePath().getParent(),
            path.getFileName().toString(),
            ".tmp");
    try {
      try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
        w.write(GSON.toJson(arr));
      }
      try {
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(tmp);
    }
  }
}
