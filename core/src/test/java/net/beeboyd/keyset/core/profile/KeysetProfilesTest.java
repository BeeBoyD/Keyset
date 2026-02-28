package net.beeboyd.keyset.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
