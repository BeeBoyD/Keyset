package net.beeboyd.keyset.core.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.beeboyd.keyset.core.KeysetCoreMetadata;

/** JSON codec and safe file persistence for {@link KeysetProfilesConfig}. */
public final class KeysetProfilesJson {
  private static final int LEGACY_SCHEMA_VERSION = 0;
  private static final Gson GSON =
      new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

  /** Reads a config document from a JSON string. */
  public KeysetProfilesConfig fromJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      return KeysetProfiles.createDefaultConfig();
    }

    return fromElement(new JsonParser().parse(json));
  }

  /** Reads a config document from a reader. */
  public KeysetProfilesConfig fromReader(Reader reader) {
    if (reader == null) {
      return KeysetProfiles.createDefaultConfig();
    }

    return fromElement(new JsonParser().parse(reader));
  }

  /** Serializes a config document into stable pretty-printed JSON. */
  public String toJson(KeysetProfilesConfig config) {
    KeysetProfilesConfig normalized = KeysetProfiles.normalize(config);
    return GSON.toJson(toElement(normalized));
  }

  /** Reads the config file, returning a starter document when the file does not exist yet. */
  public KeysetProfilesConfig read(Path path) throws IOException {
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }

    if (!Files.exists(path)) {
      return KeysetProfiles.createDefaultConfig();
    }

    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      return fromReader(reader);
    }
  }

  /** Writes the document with a temp-file swap to avoid partially written configs. */
  public void write(Path path, KeysetProfilesConfig config) throws IOException {
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }

    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    Path tempFile =
        Files.createTempFile(
            parent == null ? path.toAbsolutePath().getParent() : parent,
            path.getFileName().toString(),
            ".tmp");
    try {
      try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
        writer.write(toJson(config));
      }

      try {
        Files.move(
            tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  private KeysetProfilesConfig fromElement(JsonElement root) {
    if (root == null || root.isJsonNull()) {
      return KeysetProfiles.createDefaultConfig();
    }
    if (!root.isJsonObject()) {
      throw new JsonParseException("Expected profile config JSON object");
    }

    JsonObject rootObject = root.getAsJsonObject();
    JsonObject migratedRoot = migrateSchema(rootObject);
    return KeysetProfiles.normalize(
        new KeysetProfilesConfig(
            KeysetCoreMetadata.CONFIG_SCHEMA,
            readString(migratedRoot, "activeProfile"),
            readProfiles(migratedRoot)));
  }

  private JsonObject toElement(KeysetProfilesConfig config) {
    JsonObject root = new JsonObject();
    root.addProperty("schema", config.getSchemaVersion());
    root.addProperty("activeProfile", config.getActiveProfileId());

    JsonObject profiles = new JsonObject();
    for (Map.Entry<String, KeysetProfile> entry : config.getProfiles().entrySet()) {
      KeysetProfile profile = entry.getValue();
      JsonObject profileObject = new JsonObject();
      profileObject.addProperty("name", profile.getName());
      profileObject.addProperty("builtIn", profile.isBuiltIn());

      JsonObject bindings = new JsonObject();
      for (Map.Entry<String, KeysetBindingSnapshot> bindingEntry :
          profile.getBindings().entrySet()) {
        KeysetBindingSnapshot snapshot = bindingEntry.getValue();
        JsonObject bindingObject = new JsonObject();
        if (!snapshot.getKeyStroke().isUnbound()) {
          bindingObject.addProperty("key", snapshot.getKeyStroke().getKeyToken());
        }

        JsonArray modifiers = new JsonArray();
        for (KeysetModifier modifier : snapshot.getKeyStroke().getModifiers()) {
          modifiers.add(modifier.name());
        }
        bindingObject.add("modifiers", modifiers);
        if (snapshot.isSticky()) {
          bindingObject.addProperty("sticky", true);
        }
        bindings.add(bindingEntry.getKey(), bindingObject);
      }
      profileObject.add("bindings", bindings);
      profiles.add(entry.getKey(), profileObject);
    }

    root.add("profiles", profiles);
    return root;
  }

  private int readSchema(JsonObject rootObject) {
    JsonElement schemaElement = rootObject.get("schema");
    if (schemaElement == null || schemaElement.isJsonNull()) {
      return LEGACY_SCHEMA_VERSION;
    }
    try {
      return schemaElement.getAsInt();
    } catch (NumberFormatException | UnsupportedOperationException exception) {
      throw new JsonParseException("Invalid schema value", exception);
    }
  }

  private JsonObject migrateSchema(JsonObject rootObject) {
    int schema = readSchema(rootObject);
    if (schema > KeysetCoreMetadata.CONFIG_SCHEMA) {
      throw new JsonParseException("Unsupported profile schema " + schema);
    }

    switch (schema) {
      case LEGACY_SCHEMA_VERSION:
        return migrateLegacySchema(rootObject);
      case KeysetCoreMetadata.CONFIG_SCHEMA:
        return rootObject;
      default:
        throw new JsonParseException("Unsupported profile schema " + schema);
    }
  }

  private JsonObject migrateLegacySchema(JsonObject rootObject) {
    JsonObject migratedRoot = rootObject.deepCopy();
    migratedRoot.addProperty("schema", KeysetCoreMetadata.CONFIG_SCHEMA);
    return migratedRoot;
  }

  private Map<String, KeysetProfile> readProfiles(JsonObject rootObject) {
    Map<String, KeysetProfile> profiles = new LinkedHashMap<String, KeysetProfile>();
    JsonElement profilesElement = rootObject.get("profiles");
    if (profilesElement == null || profilesElement.isJsonNull()) {
      return profiles;
    }
    if (!profilesElement.isJsonObject()) {
      throw new JsonParseException("profiles must be a JSON object");
    }

    JsonObject profilesObject = profilesElement.getAsJsonObject();
    for (Map.Entry<String, JsonElement> entry : profilesObject.entrySet()) {
      String profileId = entry.getKey();
      JsonElement profileElement = entry.getValue();
      if (!profileElement.isJsonObject()) {
        throw new JsonParseException("Profile '" + profileId + "' must be a JSON object");
      }

      JsonObject profileObject = profileElement.getAsJsonObject();
      profiles.put(
          profileId,
          new KeysetProfile(
              profileId,
              fallbackProfileName(readString(profileObject, "name"), profileId),
              readBoolean(profileObject, "builtIn", false),
              readBindings(profileObject)));
    }

    return profiles;
  }

  private Map<String, KeysetBindingSnapshot> readBindings(JsonObject profileObject) {
    Map<String, KeysetBindingSnapshot> bindings =
        new LinkedHashMap<String, KeysetBindingSnapshot>();
    JsonElement bindingsElement = profileObject.get("bindings");
    if (bindingsElement == null || bindingsElement.isJsonNull()) {
      return bindings;
    }
    if (!bindingsElement.isJsonObject()) {
      throw new JsonParseException("bindings must be a JSON object");
    }

    JsonObject bindingsObject = bindingsElement.getAsJsonObject();
    for (Map.Entry<String, JsonElement> entry : bindingsObject.entrySet()) {
      if (!entry.getValue().isJsonObject()) {
        throw new JsonParseException("Binding '" + entry.getKey() + "' must be a JSON object");
      }

      JsonObject bindingObject = entry.getValue().getAsJsonObject();
      KeysetKeyStroke keyStroke =
          KeysetKeyStroke.of(readString(bindingObject, "key"), readModifiers(bindingObject));
      bindings.put(
          entry.getKey(),
          new KeysetBindingSnapshot(keyStroke, readBoolean(bindingObject, "sticky", false)));
    }

    return bindings;
  }

  private List<KeysetModifier> readModifiers(JsonObject bindingObject) {
    JsonElement modifiersElement = bindingObject.get("modifiers");
    if (modifiersElement == null || modifiersElement.isJsonNull()) {
      return new ArrayList<KeysetModifier>(0);
    }
    if (!modifiersElement.isJsonArray()) {
      throw new JsonParseException("modifiers must be a JSON array");
    }

    List<KeysetModifier> modifiers = new ArrayList<KeysetModifier>();
    for (JsonElement modifierElement : modifiersElement.getAsJsonArray()) {
      if (!modifierElement.isJsonPrimitive()) {
        continue;
      }

      try {
        modifiers.add(KeysetModifier.valueOf(modifierElement.getAsString().trim().toUpperCase()));
      } catch (IllegalArgumentException ignored) {
        // Ignore unknown modifiers so forward-compat data does not become fatal.
      }
    }
    return modifiers;
  }

  private String readString(JsonObject object, String memberName) {
    JsonElement element = object.get(memberName);
    if (element == null || element.isJsonNull()) {
      return null;
    }
    if (!element.isJsonPrimitive()) {
      throw new JsonParseException(memberName + " must be a JSON primitive");
    }
    return element.getAsString();
  }

  private boolean readBoolean(JsonObject object, String memberName, boolean defaultValue) {
    JsonElement element = object.get(memberName);
    if (element == null || element.isJsonNull()) {
      return defaultValue;
    }
    if (!element.isJsonPrimitive()) {
      throw new JsonParseException(memberName + " must be a JSON primitive");
    }
    return element.getAsBoolean();
  }

  private String fallbackProfileName(String value, String profileId) {
    if (value == null || value.trim().isEmpty()) {
      if (KeysetProfiles.DEFAULT_PROFILE_ID.equals(profileId)) {
        return "Default";
      }
      if (KeysetProfiles.PVP_PROFILE_ID.equals(profileId)) {
        return "PvP";
      }
      if (KeysetProfiles.BUILDING_PROFILE_ID.equals(profileId)) {
        return "Building";
      }
      if (KeysetProfiles.TECH_PROFILE_ID.equals(profileId)) {
        return "Tech";
      }

      String normalized = profileId.replace('-', ' ').trim();
      if (normalized.isEmpty()) {
        return "Profile";
      }

      StringBuilder displayName = new StringBuilder(normalized.length());
      boolean capitalize = true;
      for (int index = 0; index < normalized.length(); index++) {
        char character = normalized.charAt(index);
        if (character == ' ') {
          displayName.append(character);
          capitalize = true;
        } else if (capitalize) {
          displayName.append(Character.toUpperCase(character));
          capitalize = false;
        } else {
          displayName.append(character);
        }
      }
      return displayName.toString();
    }
    return value.trim();
  }
}
