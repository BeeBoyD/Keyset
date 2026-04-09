package net.beeboyd.keyset.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeysetProfilesJsonTest {
  private final KeysetProfilesJson codec = new KeysetProfilesJson();

  @TempDir Path tempDir;

  @Test
  void jsonRoundTripPreservesProfilesAndBindings() {
    KeysetProfilesConfig config =
        KeysetProfiles.putBinding(
            KeysetProfiles.createProfile(KeysetProfiles.createDefaultConfig(), "Work"),
            "work",
            "key.jump",
            new KeysetBindingSnapshot(
                KeysetKeyStroke.of(
                    "key.keyboard.g",
                    Arrays.asList(KeysetModifier.CTRL, KeysetModifier.SHIFT, KeysetModifier.CTRL)),
                true));

    String json = codec.toJson(config);
    KeysetProfilesConfig decoded = codec.fromJson(json);

    assertEquals(config, decoded);
    assertTrue(json.contains("\"activeProfile\": \"default\""));
    assertTrue(json.contains("\"SHIFT\""));
    assertTrue(json.contains("\"CTRL\""));
  }

  @Test
  void missingSchemaDefaultsToCurrentSchemaDuringDecode() {
    String json =
        "{\n"
            + "  \"activeProfile\": \"default\",\n"
            + "  \"profiles\": {\n"
            + "    \"default\": {\n"
            + "      \"name\": \"Default\",\n"
            + "      \"bindings\": {\n"
            + "        \"mod.key\": {\n"
            + "          \"key\": \"key.keyboard.g\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

    KeysetProfilesConfig decoded = codec.fromJson(json);

    assertEquals(KeysetCoreMetadata.CONFIG_SCHEMA, decoded.getSchemaVersion());
    assertEquals("default", decoded.getActiveProfileId());
    assertTrue(decoded.getProfile("default").getBindings().containsKey("mod.key"));
  }

  @Test
  void legacySchemaZeroIsMigratedDuringDecode() {
    String json =
        "{\n"
            + "  \"schema\": 0,\n"
            + "  \"activeProfile\": \"raiding\",\n"
            + "  \"profiles\": {\n"
            + "    \"raiding\": {\n"
            + "      \"name\": \"Raiding\",\n"
            + "      \"bindings\": {\n"
            + "        \"mod.key\": {\n"
            + "          \"key\": \"key.keyboard.r\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

    KeysetProfilesConfig decoded = codec.fromJson(json);

    assertEquals(KeysetCoreMetadata.CONFIG_SCHEMA, decoded.getSchemaVersion());
    assertEquals("raiding", decoded.getActiveProfileId());
    assertTrue(decoded.getProfile("raiding").getBindings().containsKey("mod.key"));
  }

  @Test
  void readMissingFileReturnsStarterConfig() throws IOException {
    Path configPath = tempDir.resolve("config").resolve("keybindprofiles.json");

    KeysetProfilesConfig config = codec.read(configPath);

    assertEquals(KeysetProfiles.DEFAULT_PROFILE_ID, config.getActiveProfileId());
    assertTrue(config.hasProfile(KeysetProfiles.PVP_PROFILE_ID));
  }

  @Test
  void writeAndReadRoundTripUsesStableFilePath() throws IOException {
    KeysetProfilesConfig config =
        KeysetProfiles.setActiveProfile(
            KeysetProfiles.createProfile(KeysetProfiles.createDefaultConfig(), "Adventure"),
            "adventure");
    Path configPath = tempDir.resolve("config").resolve("keybindprofiles.json");

    codec.write(configPath, config);
    KeysetProfilesConfig decoded = codec.read(configPath);

    assertTrue(Files.exists(configPath));
    assertEquals(config, decoded);
  }

  @Test
  void missingDefaultProfileInJsonIsRestoredDuringDecode() {
    String json =
        "{\n"
            + "  \"schema\": 1,\n"
            + "  \"activeProfile\": \"raiding\",\n"
            + "  \"profiles\": {\n"
            + "    \"raiding\": {\n"
            + "      \"name\": \"Raiding\",\n"
            + "      \"bindings\": {\n"
            + "        \"mod.key\": {\n"
            + "          \"key\": \"key.keyboard.r\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

    KeysetProfilesConfig decoded = codec.fromJson(json);

    assertTrue(decoded.hasProfile(KeysetProfiles.DEFAULT_PROFILE_ID));
    assertEquals("raiding", decoded.getActiveProfileId());
    assertFalse(decoded.getProfile("raiding").getBindings().isEmpty());
  }
}
