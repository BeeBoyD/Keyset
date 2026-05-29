package net.beeboyd.keyset.core.profile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.beeboyd.keyset.core.KeysetCoreMetadata;

/** Pure profile operations shared across every loader and version shim. */
public final class KeysetProfiles {
  public static final String DEFAULT_PROFILE_ID = "default";
  public static final String PVP_PROFILE_ID = "pvp";
  public static final String BUILDING_PROFILE_ID = "building";
  public static final String TECH_PROFILE_ID = "tech";

  private static final String DUPLICATE_SUFFIX = " Copy";

  private KeysetProfiles() {}

  /** Creates the starter config document used for first-run installs. */
  public static KeysetProfilesConfig createDefaultConfig() {
    Map<String, KeysetProfile> profiles = new LinkedHashMap<String, KeysetProfile>();
    profiles.put(DEFAULT_PROFILE_ID, builtInProfile(DEFAULT_PROFILE_ID, "Default"));
    profiles.put(PVP_PROFILE_ID, builtInProfile(PVP_PROFILE_ID, "PvP"));
    profiles.put(BUILDING_PROFILE_ID, builtInProfile(BUILDING_PROFILE_ID, "Building"));
    profiles.put(TECH_PROFILE_ID, builtInProfile(TECH_PROFILE_ID, "Tech"));
    return new KeysetProfilesConfig(KeysetCoreMetadata.CONFIG_SCHEMA, DEFAULT_PROFILE_ID, profiles);
  }

  /** Normalizes a possibly incomplete document into a safe runtime representation. */
  public static KeysetProfilesConfig normalize(KeysetProfilesConfig config) {
    if (config == null) {
      return createDefaultConfig();
    }

    if (config.getSchemaVersion() > KeysetCoreMetadata.CONFIG_SCHEMA) {
      throw new IllegalArgumentException("Unsupported profile schema " + config.getSchemaVersion());
    }

    Map<String, KeysetProfile> normalizedProfiles = normalizeProfiles(config.getProfiles());
    if (normalizedProfiles.isEmpty()) {
      normalizedProfiles.putAll(createDefaultConfig().getProfiles());
    }
    if (!normalizedProfiles.containsKey(DEFAULT_PROFILE_ID)) {
      normalizedProfiles.put(DEFAULT_PROFILE_ID, builtInProfile(DEFAULT_PROFILE_ID, "Default"));
    }

    String activeProfileId = normalizeNullableText(config.getActiveProfileId());
    if (activeProfileId == null || !normalizedProfiles.containsKey(activeProfileId)) {
      activeProfileId = DEFAULT_PROFILE_ID;
    }

    return new KeysetProfilesConfig(
        KeysetCoreMetadata.CONFIG_SCHEMA, activeProfileId, normalizedProfiles);
  }

  public static KeysetProfilesConfig createProfile(
      KeysetProfilesConfig config, String requestedName) {
    String baseName = requireProfileName(requestedName);
    KeysetProfilesConfig normalized = normalize(config);
    String profileName = uniqueProfileName(normalized, baseName, null);
    String profileId = uniqueProfileId(slugify(profileName), normalized.getProfiles());

    Map<String, KeysetProfile> updatedProfiles = mutableProfiles(normalized);
    updatedProfiles.put(profileId, new KeysetProfile(profileId, profileName, false, null));
    return new KeysetProfilesConfig(
        KeysetCoreMetadata.CONFIG_SCHEMA, normalized.getActiveProfileId(), updatedProfiles);
  }

  public static KeysetProfilesConfig renameProfile(
      KeysetProfilesConfig config, String profileId, String requestedName) {
    String normalizedProfileId = requireProfileId(profileId);
    String baseName = requireProfileName(requestedName);
    KeysetProfilesConfig normalized = normalize(config);
    KeysetProfile existing = requireProfile(normalized, normalizedProfileId);
    String updatedName = uniqueProfileName(normalized, baseName, normalizedProfileId);

    Map<String, KeysetProfile> updatedProfiles = mutableProfiles(normalized);
    updatedProfiles.put(normalizedProfileId, existing.withName(updatedName));
    return new KeysetProfilesConfig(
        KeysetCoreMetadata.CONFIG_SCHEMA, normalized.getActiveProfileId(), updatedProfiles);
  }

  public static KeysetProfilesConfig duplicateProfile(
      KeysetProfilesConfig config, String profileId) {
    String normalizedProfileId = requireProfileId(profileId);
    KeysetProfilesConfig normalized = normalize(config);
    KeysetProfile source = requireProfile(normalized, normalizedProfileId);
    String duplicateName = uniqueProfileName(normalized, source.getName() + DUPLICATE_SUFFIX, null);
    String duplicateId = uniqueProfileId(slugify(duplicateName), normalized.getProfiles());

    Map<String, KeysetProfile> updatedProfiles = mutableProfiles(normalized);
    updatedProfiles.put(
        duplicateId, new KeysetProfile(duplicateId, duplicateName, false, source.getBindings()));
    return new KeysetProfilesConfig(
        KeysetCoreMetadata.CONFIG_SCHEMA, normalized.getActiveProfileId(), updatedProfiles);
  }

  public static KeysetProfilesConfig deleteProfile(KeysetProfilesConfig config, String profileId) {
    String normalizedProfileId = requireProfileId(profileId);
    if (DEFAULT_PROFILE_ID.equals(normalizedProfileId)) {
      throw new IllegalArgumentException("The Default profile cannot be deleted");
    }

    KeysetProfilesConfig normalized = normalize(config);
    requireProfile(normalized, normalizedProfileId);

    Map<String, KeysetProfile> updatedProfiles = mutableProfiles(normalized);
    updatedProfiles.remove(normalizedProfileId);

    String activeProfileId = normalized.getActiveProfileId();
    if (normalizedProfileId.equals(activeProfileId)) {
      activeProfileId = DEFAULT_PROFILE_ID;
    }

    return new KeysetProfilesConfig(
        KeysetCoreMetadata.CONFIG_SCHEMA, activeProfileId, updatedProfiles);
  }

  public static KeysetProfilesConfig setActiveProfile(
      KeysetProfilesConfig config, String profileId) {
    String normalizedProfileId = requireProfileId(profileId);
    KeysetProfilesConfig normalized = normalize(config);
    requireProfile(normalized, normalizedProfileId);
    return new KeysetProfilesConfig(
        KeysetCoreMetadata.CONFIG_SCHEMA, normalizedProfileId, normalized.getProfiles());
  }

  public static KeysetProfilesConfig putBinding(
      KeysetProfilesConfig config,
      String profileId,
      String bindingId,
      KeysetBindingSnapshot snapshot) {
    String normalizedProfileId = requireProfileId(profileId);
    String normalizedBindingId = requireBindingId(bindingId);
    if (snapshot == null) {
      throw new IllegalArgumentException("snapshot must not be null");
    }

    KeysetProfilesConfig normalized = normalize(config);
    KeysetProfile profile = requireProfile(normalized, normalizedProfileId);
    Map<String, KeysetProfile> updatedProfiles = mutableProfiles(normalized);
    updatedProfiles.put(normalizedProfileId, profile.withBinding(normalizedBindingId, snapshot));
    return new KeysetProfilesConfig(
        KeysetCoreMetadata.CONFIG_SCHEMA, normalized.getActiveProfileId(), updatedProfiles);
  }

  public static KeysetProfilesConfig removeBinding(
      KeysetProfilesConfig config, String profileId, String bindingId) {
    String normalizedProfileId = requireProfileId(profileId);
    String normalizedBindingId = requireBindingId(bindingId);

    KeysetProfilesConfig normalized = normalize(config);
    KeysetProfile profile = requireProfile(normalized, normalizedProfileId);
    Map<String, KeysetProfile> updatedProfiles = mutableProfiles(normalized);
    updatedProfiles.put(normalizedProfileId, profile.withoutBinding(normalizedBindingId));
    return new KeysetProfilesConfig(
        KeysetCoreMetadata.CONFIG_SCHEMA, normalized.getActiveProfileId(), updatedProfiles);
  }

  /**
   * Moves the given profile one position earlier in the profile list. If the profile is already
   * first, returns the config unchanged. The Default profile cannot be moved.
   */
  public static KeysetProfilesConfig moveProfileUp(KeysetProfilesConfig config, String profileId) {
    String normalizedProfileId = requireProfileId(profileId);
    if (DEFAULT_PROFILE_ID.equals(normalizedProfileId)) {
      throw new IllegalArgumentException("The Default profile cannot be moved");
    }

    KeysetProfilesConfig normalized = normalize(config);
    requireProfile(normalized, normalizedProfileId);

    List<Map.Entry<String, KeysetProfile>> entries =
        new ArrayList<Map.Entry<String, KeysetProfile>>(normalized.getProfiles().entrySet());
    int index = indexOfProfileId(entries, normalizedProfileId);

    if (index <= 0) {
      return normalized;
    }

    Collections.swap(entries, index, index - 1);
    return new KeysetProfilesConfig(
        KeysetCoreMetadata.CONFIG_SCHEMA,
        normalized.getActiveProfileId(),
        rebuildProfiles(entries));
  }

  /**
   * Moves the given profile one position later in the profile list. If the profile is already last,
   * returns the config unchanged. The Default profile cannot be moved.
   */
  public static KeysetProfilesConfig moveProfileDown(
      KeysetProfilesConfig config, String profileId) {
    String normalizedProfileId = requireProfileId(profileId);
    if (DEFAULT_PROFILE_ID.equals(normalizedProfileId)) {
      throw new IllegalArgumentException("The Default profile cannot be moved");
    }

    KeysetProfilesConfig normalized = normalize(config);
    requireProfile(normalized, normalizedProfileId);

    List<Map.Entry<String, KeysetProfile>> entries =
        new ArrayList<Map.Entry<String, KeysetProfile>>(normalized.getProfiles().entrySet());
    int index = indexOfProfileId(entries, normalizedProfileId);

    if (index < 0 || index >= entries.size() - 1) {
      return normalized;
    }

    Collections.swap(entries, index, index + 1);
    return new KeysetProfilesConfig(
        KeysetCoreMetadata.CONFIG_SCHEMA,
        normalized.getActiveProfileId(),
        rebuildProfiles(entries));
  }

  /** Removes all given binding ids from the specified profile in one operation. */
  public static KeysetProfilesConfig removeBindings(
      KeysetProfilesConfig config, String profileId, Collection<String> bindingIds) {
    String normalizedProfileId = requireProfileId(profileId);
    KeysetProfilesConfig normalized = normalize(config);
    requireProfile(normalized, normalizedProfileId);

    KeysetProfilesConfig result = normalized;
    for (String bindingId : bindingIds) {
      result = removeBinding(result, normalizedProfileId, bindingId);
    }
    return result;
  }

  private static int indexOfProfileId(
      List<Map.Entry<String, KeysetProfile>> entries, String profileId) {
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).getKey().equals(profileId)) {
        return i;
      }
    }
    return -1;
  }

  private static Map<String, KeysetProfile> rebuildProfiles(
      List<Map.Entry<String, KeysetProfile>> entries) {
    LinkedHashMap<String, KeysetProfile> result =
        new LinkedHashMap<String, KeysetProfile>(entries.size());
    for (Map.Entry<String, KeysetProfile> entry : entries) {
      result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }

  private static KeysetProfile builtInProfile(String id, String name) {
    return new KeysetProfile(id, name, true, Collections.<String, KeysetBindingSnapshot>emptyMap());
  }

  private static Map<String, KeysetProfile> normalizeProfiles(Map<String, KeysetProfile> profiles) {
    Map<String, KeysetProfile> normalized = new LinkedHashMap<String, KeysetProfile>();
    if (profiles == null || profiles.isEmpty()) {
      return normalized;
    }

    for (Map.Entry<String, KeysetProfile> entry : profiles.entrySet()) {
      KeysetProfile profile = entry.getValue();
      if (profile == null) {
        continue;
      }

      String requestedId = normalizeNullableText(entry.getKey());
      if (requestedId == null) {
        requestedId = profile.getId();
      }

      String normalizedId = uniqueProfileId(requestedId, normalized);
      String normalizedName = normalizeNullableText(profile.getName());
      if (normalizedName == null) {
        normalizedName = fallbackProfileName(normalizedId);
      }
      normalizedName = uniqueProfileName(normalized, normalizedName, null);

      normalized.put(
          normalizedId,
          new KeysetProfile(
              normalizedId, normalizedName, profile.isBuiltIn(), profile.getBindings()));
    }

    return normalized;
  }

  private static KeysetProfile requireProfile(KeysetProfilesConfig config, String profileId) {
    KeysetProfile profile = config.getProfile(profileId);
    if (profile == null) {
      throw new IllegalArgumentException("Unknown profile id: " + profileId);
    }
    return profile;
  }

  private static String requireProfileName(String requestedName) {
    String normalized = normalizeNullableText(requestedName);
    if (normalized == null) {
      throw new IllegalArgumentException("profile name must not be blank");
    }
    return normalized;
  }

  private static String requireProfileId(String profileId) {
    String normalized = normalizeNullableText(profileId);
    if (normalized == null) {
      throw new IllegalArgumentException("profileId must not be blank");
    }
    return normalized;
  }

  private static String requireBindingId(String bindingId) {
    String normalized = normalizeNullableText(bindingId);
    if (normalized == null) {
      throw new IllegalArgumentException("bindingId must not be blank");
    }
    return normalized;
  }

  private static String uniqueProfileName(
      KeysetProfilesConfig config, String baseName, String ignoredProfileId) {
    return uniqueProfileName(config.getProfiles(), baseName, ignoredProfileId);
  }

  private static String uniqueProfileName(
      Map<String, KeysetProfile> profiles, String baseName, String ignoredProfileId) {
    String candidate = baseName;
    int suffix = 2;
    while (containsProfileName(profiles, candidate, ignoredProfileId)) {
      candidate = baseName + " (" + suffix + ")";
      suffix++;
    }
    return candidate;
  }

  private static boolean containsProfileName(
      KeysetProfilesConfig config, String candidate, String ignoredProfileId) {
    return containsProfileName(config.getProfiles(), candidate, ignoredProfileId);
  }

  private static boolean containsProfileName(
      Map<String, KeysetProfile> profiles, String candidate, String ignoredProfileId) {
    String needle = candidate.toLowerCase(Locale.ROOT);
    for (KeysetProfile profile : profiles.values()) {
      if (profile.getId().equals(ignoredProfileId)) {
        continue;
      }
      if (profile.getName().toLowerCase(Locale.ROOT).equals(needle)) {
        return true;
      }
    }
    return false;
  }

  private static String uniqueProfileId(String requestedId, Map<String, KeysetProfile> profiles) {
    String baseId = slugify(requestedId);
    if (!profiles.containsKey(baseId)) {
      return baseId;
    }

    int suffix = 2;
    String candidate = baseId + "-" + suffix;
    while (profiles.containsKey(candidate)) {
      suffix++;
      candidate = baseId + "-" + suffix;
    }
    return candidate;
  }

  private static String slugify(String value) {
    String normalized = normalizeNullableText(value);
    if (normalized == null) {
      return "profile";
    }

    String decomposed = Normalizer.normalize(normalized, Normalizer.Form.NFKD);
    StringBuilder slug = new StringBuilder();
    boolean lastWasSeparator = false;
    String lowerCase = decomposed.toLowerCase(Locale.ROOT);
    for (int index = 0; index < lowerCase.length(); index++) {
      char character = lowerCase.charAt(index);
      int characterType = Character.getType(character);
      if (characterType == Character.NON_SPACING_MARK
          || characterType == Character.COMBINING_SPACING_MARK
          || characterType == Character.ENCLOSING_MARK) {
        continue;
      }
      if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
        slug.append(character);
        lastWasSeparator = false;
      } else if (!lastWasSeparator) {
        slug.append('-');
        lastWasSeparator = true;
      }
    }

    int length = slug.length();
    while (length > 0 && slug.charAt(length - 1) == '-') {
      slug.deleteCharAt(length - 1);
      length--;
    }

    while (slug.length() > 0 && slug.charAt(0) == '-') {
      slug.deleteCharAt(0);
    }

    return slug.length() == 0 ? "profile" : slug.toString();
  }

  private static String fallbackProfileName(String profileId) {
    if (DEFAULT_PROFILE_ID.equals(profileId)) {
      return "Default";
    }
    if (PVP_PROFILE_ID.equals(profileId)) {
      return "PvP";
    }
    if (BUILDING_PROFILE_ID.equals(profileId)) {
      return "Building";
    }
    if (TECH_PROFILE_ID.equals(profileId)) {
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

  private static String normalizeNullableText(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private static Map<String, KeysetProfile> mutableProfiles(KeysetProfilesConfig config) {
    return new LinkedHashMap<String, KeysetProfile>(config.getProfiles());
  }
}
