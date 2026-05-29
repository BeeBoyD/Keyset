package net.beeboyd.keyset.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import org.junit.jupiter.api.Test;

class KeysetProfilesTest {
  @Test
  void defaultConfigSeedsStarterProfiles() {
    KeysetProfilesConfig config = KeysetProfiles.createDefaultConfig();

    assertEquals(KeysetProfiles.DEFAULT_PROFILE_ID, config.getActiveProfileId());
    assertEquals(4, config.getProfiles().size());
    assertTrue(config.getProfile(KeysetProfiles.DEFAULT_PROFILE_ID).isBuiltIn());
    assertEquals("PvP", config.getProfile(KeysetProfiles.PVP_PROFILE_ID).getName());
  }

  @Test
  void createRenameAndDuplicateKeepNamesUnique() {
    KeysetProfilesConfig config = KeysetProfiles.createDefaultConfig();

    KeysetProfilesConfig created = KeysetProfiles.createProfile(config, "PvP");
    KeysetProfile createdProfile = created.getProfile("pvp-2");
    assertEquals("PvP (2)", createdProfile.getName());

    KeysetProfilesConfig renamed = KeysetProfiles.renameProfile(created, "pvp-2", "Default");
    assertEquals("Default (2)", renamed.getProfile("pvp-2").getName());

    KeysetProfilesConfig duplicated = KeysetProfiles.duplicateProfile(renamed, "pvp-2");
    assertEquals("Default (2) Copy", duplicated.getProfile("default-2-copy").getName());
    assertFalse(duplicated.getProfile("default-2-copy").isBuiltIn());
  }

  @Test
  void deletingActiveProfileFallsBackToDefault() {
    KeysetProfilesConfig config =
        KeysetProfiles.createProfile(KeysetProfiles.createDefaultConfig(), "Raiding");
    KeysetProfilesConfig activated = KeysetProfiles.setActiveProfile(config, "raiding");

    KeysetProfilesConfig deleted = KeysetProfiles.deleteProfile(activated, "raiding");

    assertEquals(KeysetProfiles.DEFAULT_PROFILE_ID, deleted.getActiveProfileId());
    assertFalse(deleted.hasProfile("raiding"));
  }

  @Test
  void defaultProfileCannotBeDeleted() {
    assertThrows(
        IllegalArgumentException.class,
        () -> KeysetProfiles.deleteProfile(KeysetProfiles.createDefaultConfig(), "default"));
  }

  @Test
  void normalizeRebuildsMissingDefaultProfile() {
    KeysetProfilesConfig broken = new KeysetProfilesConfig(1, "missing", null);

    KeysetProfilesConfig normalized = KeysetProfiles.normalize(broken);

    assertEquals(KeysetProfiles.DEFAULT_PROFILE_ID, normalized.getActiveProfileId());
    assertTrue(normalized.hasProfile(KeysetProfiles.DEFAULT_PROFILE_ID));
    assertEquals("Default", normalized.getProfile(KeysetProfiles.DEFAULT_PROFILE_ID).getName());
  }

  @Test
  void normalizeUpgradesLegacySchemaZero() {
    KeysetProfilesConfig legacy = new KeysetProfilesConfig(0, "default", createProfiles());

    KeysetProfilesConfig normalized = KeysetProfiles.normalize(legacy);

    assertEquals(KeysetCoreMetadata.CONFIG_SCHEMA, normalized.getSchemaVersion());
    assertEquals(KeysetProfiles.DEFAULT_PROFILE_ID, normalized.getActiveProfileId());
    assertTrue(normalized.hasProfile(KeysetProfiles.PVP_PROFILE_ID));
  }

  @Test
  void normalizeAssignsUniqueNamesToCaseInsensitiveDuplicates() {
    Map<String, KeysetProfile> profiles = new LinkedHashMap<String, KeysetProfile>();
    profiles.put("one", new KeysetProfile("one", "Test", false, null));
    profiles.put("two", new KeysetProfile("two", "test", false, null));

    KeysetProfilesConfig normalized =
        KeysetProfiles.normalize(new KeysetProfilesConfig(0, "one", profiles));

    assertEquals("Test", normalized.getProfile("one").getName());
    assertEquals("test (2)", normalized.getProfile("two").getName());
  }

  @Test
  void slugifyNormalizesUnicodeCharacters() {
    KeysetProfilesConfig created =
        KeysetProfiles.createProfile(KeysetProfiles.createDefaultConfig(), "Über");

    assertTrue(created.hasProfile("uber"));
    assertEquals("Über", created.getProfile("uber").getName());
  }

  @Test
  void putBindingStoresUnknownBindingIdsAndStickyAssignments() {
    KeysetProfilesConfig updated =
        KeysetProfiles.putBinding(
            KeysetProfiles.createDefaultConfig(),
            KeysetProfiles.DEFAULT_PROFILE_ID,
            "mod.weird.binding",
            KeysetBindingSnapshot.sticky(
                KeysetKeyStroke.of(
                    "key.keyboard.semicolon",
                    java.util.Arrays.asList(
                        KeysetModifier.ALT, KeysetModifier.SHIFT, KeysetModifier.SHIFT))));

    KeysetBindingSnapshot snapshot =
        updated
            .getProfile(KeysetProfiles.DEFAULT_PROFILE_ID)
            .getBindings()
            .get("mod.weird.binding");

    assertTrue(snapshot.isSticky());
    assertEquals("key.keyboard.semicolon", snapshot.getKeyStroke().getKeyToken());
    assertEquals(
        java.util.Arrays.asList(KeysetModifier.SHIFT, KeysetModifier.ALT),
        snapshot.getKeyStroke().getModifiers());
  }

  private static Map<String, KeysetProfile> createProfiles() {
    return KeysetProfiles.createDefaultConfig().getProfiles();
  }
}
