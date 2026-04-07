package net.beeboyd.keyset.core.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable top-level config document stored in {@code config/keybindprofiles.json}. */
public final class KeysetProfilesConfig {
  private final int schemaVersion;
  private final String activeProfileId;
  private final Map<String, KeysetProfile> profiles;

  public KeysetProfilesConfig(
      int schemaVersion, String activeProfileId, Map<String, KeysetProfile> profiles) {
    if (schemaVersion <= 0) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }

    this.schemaVersion = schemaVersion;
    this.activeProfileId = normalizeNullableText(activeProfileId);
    this.profiles = Collections.unmodifiableMap(copyProfiles(profiles));
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public String getActiveProfileId() {
    return activeProfileId;
  }

  public Map<String, KeysetProfile> getProfiles() {
    return profiles;
  }

  public KeysetProfile getProfile(String profileId) {
    return profiles.get(profileId);
  }

  public boolean hasProfile(String profileId) {
    return profiles.containsKey(profileId);
  }

  private static Map<String, KeysetProfile> copyProfiles(Map<String, KeysetProfile> profiles) {
    if (profiles == null || profiles.isEmpty()) {
      return Collections.emptyMap();
    }

    LinkedHashMap<String, KeysetProfile> result = new LinkedHashMap<String, KeysetProfile>();
    for (Map.Entry<String, KeysetProfile> entry : profiles.entrySet()) {
      KeysetProfile profile = entry.getValue();
      if (profile == null) {
        continue;
      }

      String key = normalizeNullableText(entry.getKey());
      if (key == null) {
        key = profile.getId();
      }

      if (!key.equals(profile.getId())) {
        throw new IllegalArgumentException(
            "profile map key '" + key + "' does not match profile id '" + profile.getId() + "'");
      }

      result.put(key, profile);
    }

    return result;
  }

  private static String normalizeNullableText(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KeysetProfilesConfig)) {
      return false;
    }
    KeysetProfilesConfig that = (KeysetProfilesConfig) other;
    return schemaVersion == that.schemaVersion
        && Objects.equals(activeProfileId, that.activeProfileId)
        && profiles.equals(that.profiles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(schemaVersion, activeProfileId, profiles);
  }
}
