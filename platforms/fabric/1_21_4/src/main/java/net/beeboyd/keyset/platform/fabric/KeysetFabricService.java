package net.beeboyd.keyset.platform.fabric;

import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.core.binding.KeysetBindingDescriptor;
import net.beeboyd.keyset.core.conflict.KeysetConflict;
import net.beeboyd.keyset.core.conflict.KeysetConflictReport;
import net.beeboyd.keyset.core.conflict.KeysetConflicts;
import net.beeboyd.keyset.core.profile.KeysetBindingSnapshot;
import net.beeboyd.keyset.core.profile.KeysetKeyStroke;
import net.beeboyd.keyset.core.profile.KeysetProfile;
import net.beeboyd.keyset.core.profile.KeysetProfiles;
import net.beeboyd.keyset.core.profile.KeysetProfilesConfig;
import net.beeboyd.keyset.core.profile.KeysetProfilesJson;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric-side bridge between live Minecraft keybindings and the shared Keyset core. */
public final class KeysetFabricService {
  private static final Logger LOGGER = LoggerFactory.getLogger(KeysetCoreMetadata.MOD_ID);

  private static final Comparator<KeyBindingDescriptorWithFlags> RESOLVE_ORDER =
      new Comparator<KeyBindingDescriptorWithFlags>() {
        @Override
        public int compare(
            KeyBindingDescriptorWithFlags left, KeyBindingDescriptorWithFlags right) {
          int protectionComparison = Integer.compare(left.protectionScore, right.protectionScore);
          if (protectionComparison != 0) {
            return protectionComparison;
          }

          int displayNameComparison =
              compareText(left.descriptor.getDisplayName(), right.descriptor.getDisplayName());
          if (displayNameComparison != 0) {
            return displayNameComparison;
          }

          return compareText(left.descriptor.getId(), right.descriptor.getId());
        }
      };

  private static final List<String> SAFE_KEY_POOL =
      Collections.unmodifiableList(
          java.util.Arrays.asList(
              "key.keyboard.g",
              "key.keyboard.h",
              "key.keyboard.j",
              "key.keyboard.k",
              "key.keyboard.l",
              "key.keyboard.z",
              "key.keyboard.x",
              "key.keyboard.c",
              "key.keyboard.v",
              "key.keyboard.b",
              "key.keyboard.n",
              "key.keyboard.m",
              "key.keyboard.r",
              "key.keyboard.t",
              "key.keyboard.y",
              "key.keyboard.u",
              "key.keyboard.i",
              "key.keyboard.o",
              "key.keyboard.p",
              "key.keyboard.semicolon",
              "key.keyboard.apostrophe",
              "key.keyboard.comma",
              "key.keyboard.period",
              "key.keyboard.slash",
              "key.keyboard.left.bracket",
              "key.keyboard.right.bracket",
              "key.keyboard.minus",
              "key.keyboard.equal",
              "key.keyboard.f6",
              "key.keyboard.f7",
              "key.keyboard.f8",
              "key.keyboard.f9",
              "key.keyboard.f10",
              "key.keyboard.kp.0",
              "key.keyboard.kp.1",
              "key.keyboard.kp.2",
              "key.keyboard.kp.3",
              "key.keyboard.kp.4",
              "key.keyboard.kp.5",
              "key.keyboard.kp.6",
              "key.keyboard.kp.7",
              "key.keyboard.kp.8",
              "key.keyboard.kp.9"));

  private static final int MAX_NOTICE_QUEUE = 5;
  private static final int MAX_UNDO_STACK = 20;

  private final KeysetProfilesJson codec = new KeysetProfilesJson();
  private KeysetProfilesConfig config;
  private boolean loaded;
  private final ArrayDeque<StatusNotice> pendingNotices = new ArrayDeque<StatusNotice>();
  private final ArrayDeque<UndoState> undoStack = new ArrayDeque<UndoState>();
  private final ArrayDeque<UndoState> redoStack = new ArrayDeque<UndoState>();
  private KeysetConflictReport cachedConflictReport;

  public void onClientStarted(MinecraftClient client) throws IOException {
    ensureLoaded(client);
  }

  public KeysetProfilesConfig getConfig(MinecraftClient client) throws IOException {
    ensureLoaded(client);
    return config;
  }

  public StatusNotice consumeStatusNotice() {
    return pendingNotices.pollFirst();
  }

  public void reportStatusNotice(String message, boolean error) {
    queueStatusNotice(message, error);
  }

  public void flushStatusNoticeToHud(MinecraftClient client) {
    if (client == null || client.player == null) {
      return;
    }

    StatusNotice notice = consumeStatusNotice();
    if (notice == null) {
      return;
    }

    client.player.sendMessage(
        Text.literal(notice.getMessage())
            .formatted(notice.isError() ? Formatting.RED : Formatting.GREEN),
        true);
  }

  public List<KeysetProfile> listProfiles(MinecraftClient client) throws IOException {
    ensureLoaded(client);
    return new ArrayList<KeysetProfile>(config.getProfiles().values());
  }

  public KeysetConflictReport buildConflictReport(MinecraftClient client) throws IOException {
    ensureLoaded(client);
    if (cachedConflictReport != null) {
      return cachedConflictReport;
    }
    cachedConflictReport = KeysetConflicts.analyze(describeBindings(client.options));
    return cachedConflictReport;
  }

  public KeysetConflictReport buildConflictReport(MinecraftClient client, String profileId)
      throws IOException {
    ensureLoaded(client);
    return KeysetConflicts.analyze(
        describeBindings(client.options, requireProfile(config, profileId)));
  }

  public String createProfileFromCurrent(MinecraftClient client, String requestedName)
      throws IOException {
    ensureLoaded(client);
    KeysetProfilesConfig previousConfig = config;
    config = KeysetProfiles.createProfile(config, requestedName);
    String createdProfileId = findAddedProfileId(previousConfig, config);
    config =
        replaceProfileBindings(
            config, createdProfileId, captureSnapshots(client.options, true), false);
    save(client);
    return createdProfileId;
  }

  public String duplicateProfile(MinecraftClient client, String profileId) throws IOException {
    ensureLoaded(client);
    KeysetProfilesConfig previousConfig = config;
    config = KeysetProfiles.duplicateProfile(config, profileId);
    String duplicateProfileId = findAddedProfileId(previousConfig, config);
    save(client);
    return duplicateProfileId;
  }

  public void renameProfile(MinecraftClient client, String profileId, String requestedName)
      throws IOException {
    ensureLoaded(client);
    config = KeysetProfiles.renameProfile(config, profileId, requestedName);
    save(client);
  }

  public String deleteProfile(MinecraftClient client, String profileId) throws IOException {
    ensureLoaded(client);
    String previousActiveProfileId = config.getActiveProfileId();
    config = KeysetProfiles.deleteProfile(config, profileId);
    if (!previousActiveProfileId.equals(config.getActiveProfileId())) {
      applyProfile(client.options, requireProfile(config, config.getActiveProfileId()));
    }
    cachedConflictReport = null;
    save(client);
    return config.getActiveProfileId();
  }

  public ActivationResult activateProfile(MinecraftClient client, String profileId)
      throws IOException {
    ensureLoaded(client);
    config = KeysetProfiles.setActiveProfile(config, profileId);
    KeysetProfile profile = requireProfile(config, profileId);
    applyProfile(client.options, profile);
    cachedConflictReport = KeysetConflicts.analyze(describeBindings(client.options));
    save(client);
    return ActivationResult.from(profile.getName(), cachedConflictReport);
  }

  public void captureCurrentToProfile(
      MinecraftClient client, String profileId, boolean stickySnapshots) throws IOException {
    ensureLoaded(client);
    config =
        replaceProfileBindings(
            config, profileId, captureSnapshots(client.options, stickySnapshots), null);
    cachedConflictReport = null;
    save(client);
  }

  public String exportProfileJson(MinecraftClient client, String profileId) throws IOException {
    ensureLoaded(client);
    KeysetProfile profile = requireProfile(config, profileId);
    Map<String, KeysetProfile> profiles = new LinkedHashMap<String, KeysetProfile>();
    profiles.put(profileId, profile);
    return codec.toJson(new KeysetProfilesConfig(config.getSchemaVersion(), profileId, profiles));
  }

  public void clearActiveBinding(MinecraftClient client, String bindingId) throws IOException {
    ensureLoaded(client);
    requireLiveBinding(client.options, bindingId);

    Map<String, KeysetKeyStroke> strokes = new LinkedHashMap<String, KeysetKeyStroke>(1);
    strokes.put(bindingId, KeysetKeyStroke.unbound());
    applyStrokes(client.options, strokes);
    config =
        syncProfileFromCurrent(
            client.options,
            config.getActiveProfileId(),
            Collections.singleton(bindingId),
            Collections.<String>emptySet());
    cachedConflictReport = null;
    save(client);
  }

  public void clearBindings(MinecraftClient client, java.util.List<String> bindingIds)
      throws IOException {
    ensureLoaded(client);
    if (bindingIds == null || bindingIds.isEmpty()) {
      return;
    }

    Map<String, KeysetKeyStroke> strokes =
        new LinkedHashMap<String, KeysetKeyStroke>(bindingIds.size());
    for (String bindingId : bindingIds) {
      boolean exists = false;
      for (KeyBinding binding : client.options.allKeys) {
        if (binding.getTranslationKey().equals(bindingId)) {
          exists = true;
          break;
        }
      }
      if (exists) {
        strokes.put(bindingId, KeysetKeyStroke.unbound());
      }
    }
    applyStrokes(client.options, strokes);
    config =
        KeysetProfiles.removeBindings(
            config, config.getActiveProfileId(), new ArrayList<String>(strokes.keySet()));
    cachedConflictReport = null;
    save(client);
  }

  public ImportResult importProfiles(MinecraftClient client, String json) throws IOException {
    ensureLoaded(client);
    if (json == null || json.trim().isEmpty()) {
      throw new IllegalArgumentException("Clipboard JSON did not contain any profiles.");
    }
    KeysetProfilesConfig importedConfig = codec.fromJson(json);
    int importedCount = 0;
    String lastImportedProfileId = null;
    for (KeysetProfile importedProfile : importedConfig.getProfiles().values()) {
      KeysetProfilesConfig previousConfig = config;
      config = KeysetProfiles.createProfile(config, importedProfile.getName());
      String importedProfileId = findAddedProfileId(previousConfig, config);
      config =
          replaceProfileBindings(config, importedProfileId, importedProfile.getBindings(), false);
      lastImportedProfileId = importedProfileId;
      importedCount++;
    }

    if (importedCount > 0) {
      cachedConflictReport = null;
      save(client);
    }

    return new ImportResult(importedCount, lastImportedProfileId);
  }

  public AutoResolvePlan previewAutoResolve(MinecraftClient client) throws IOException {
    ensureLoaded(client);

    List<KeysetBindingDescriptor> bindings = describeBindings(client.options);
    KeysetConflictReport conflictReport = KeysetConflicts.analyze(bindings);
    if (conflictReport.isEmpty()) {
      return AutoResolvePlan.empty();
    }

    KeysetProfile activeProfile = config.getProfile(config.getActiveProfileId());
    Set<String> stickyBindings = stickyBindings(activeProfile);
    Set<String> protectedBindings = protectedBindingIds(client.options);
    Set<String> usedKeys = currentlyUsedKeys(bindings);
    List<AutoResolveChange> changes = new ArrayList<AutoResolveChange>();
    int unresolvedBindings = 0;

    for (KeysetConflict conflict : conflictReport.getConflicts()) {
      List<KeyBindingDescriptorWithFlags> conflictBindings =
          resolveCandidates(conflict.getBindings(), stickyBindings, protectedBindings);
      KeyBindingDescriptorWithFlags keeper = conflictBindings.get(conflictBindings.size() - 1);

      for (KeyBindingDescriptorWithFlags candidate : conflictBindings) {
        if (candidate.descriptor.getId().equals(keeper.descriptor.getId())) {
          continue;
        }

        KeysetKeyStroke replacement = nextAvailableKey(usedKeys);
        if (replacement == null) {
          unresolvedBindings++;
          continue;
        }

        usedKeys.add(replacement.getKeyToken());
        changes.add(
            new AutoResolveChange(
                candidate.descriptor.getId(),
                candidate.descriptor.getDisplayName(),
                candidate.descriptor.getKeyStroke(),
                replacement,
                candidate.descriptor.getKeyDisplayName(),
                keyDisplayName(replacement)));
      }
    }

    return new AutoResolvePlan(changes, unresolvedBindings);
  }

  public UndoState applyAutoResolve(MinecraftClient client, AutoResolvePlan plan)
      throws IOException {
    ensureLoaded(client);
    if (plan == null || plan.getChanges().isEmpty()) {
      return null;
    }

    String activeProfileId = config.getActiveProfileId();
    KeysetProfile activeProfile = requireProfile(config, activeProfileId);
    UndoState undoState =
        new UndoState(
            activeProfileId,
            new LinkedHashMap<String, KeysetBindingSnapshot>(activeProfile.getBindings()));

    // Push current state to undo stack and clear redo stack.
    while (undoStack.size() >= MAX_UNDO_STACK) {
      undoStack.pollLast();
    }
    undoStack.addFirst(undoState);
    redoStack.clear();

    applyStrokes(client.options, plan.toStrokeMap());
    config =
        syncProfileFromCurrent(
            client.options,
            activeProfileId,
            Collections.<String>emptySet(),
            plan.changedBindingIds());
    cachedConflictReport = null;
    save(client);
    return undoState;
  }

  public boolean canUndo() {
    return !undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !redoStack.isEmpty();
  }

  public void undoAutoResolve(MinecraftClient client) throws IOException {
    ensureLoaded(client);
    UndoState undoState = undoStack.pollFirst();
    if (undoState == null) {
      return;
    }
    if (!config.hasProfile(undoState.profileId)) {
      throw new IllegalStateException("Cannot undo because the original profile no longer exists");
    }

    // Save current state to redo stack before applying the undo.
    KeysetProfile activeProfile = requireProfile(config, undoState.profileId);
    UndoState redoState =
        new UndoState(
            undoState.profileId,
            new LinkedHashMap<String, KeysetBindingSnapshot>(activeProfile.getBindings()));
    while (redoStack.size() >= MAX_UNDO_STACK) {
      redoStack.pollLast();
    }
    redoStack.addFirst(redoState);

    config = replaceProfileBindings(config, undoState.profileId, undoState.previousBindings, null);
    if (undoState.profileId.equals(config.getActiveProfileId())) {
      applyProfile(client.options, requireProfile(config, undoState.profileId));
    }
    cachedConflictReport = null;
    save(client);
  }

  /**
   * @deprecated Use {@link #undoAutoResolve(MinecraftClient)} instead.
   */
  @Deprecated
  public void undoAutoResolve(MinecraftClient client, UndoState undoState) throws IOException {
    undoAutoResolve(client);
  }

  public void redoAutoResolve(MinecraftClient client) throws IOException {
    ensureLoaded(client);
    UndoState redoState = redoStack.pollFirst();
    if (redoState == null) {
      return;
    }
    if (!config.hasProfile(redoState.profileId)) {
      throw new IllegalStateException("Cannot redo because the original profile no longer exists");
    }

    // Save current state back to undo stack before applying the redo.
    KeysetProfile activeProfile = requireProfile(config, redoState.profileId);
    UndoState newUndoState =
        new UndoState(
            redoState.profileId,
            new LinkedHashMap<String, KeysetBindingSnapshot>(activeProfile.getBindings()));
    while (undoStack.size() >= MAX_UNDO_STACK) {
      undoStack.pollLast();
    }
    undoStack.addFirst(newUndoState);

    config = replaceProfileBindings(config, redoState.profileId, redoState.previousBindings, null);
    if (redoState.profileId.equals(config.getActiveProfileId())) {
      applyProfile(client.options, requireProfile(config, redoState.profileId));
    }
    cachedConflictReport = null;
    save(client);
  }

  public boolean syncActiveProfileFromCurrentManual(MinecraftClient client) throws IOException {
    ensureLoaded(client);

    String activeProfileId = config.getActiveProfileId();
    KeysetProfile activeProfile = requireProfile(config, activeProfileId);
    Map<String, KeysetBindingSnapshot> currentSnapshots = captureSnapshots(client.options, false);
    Set<String> manuallyChangedBindings = new HashSet<String>();
    for (Map.Entry<String, KeysetBindingSnapshot> entry : currentSnapshots.entrySet()) {
      KeysetBindingSnapshot previousSnapshot = activeProfile.getBindings().get(entry.getKey());
      if (previousSnapshot == null
          || !previousSnapshot.getKeyStroke().equals(entry.getValue().getKeyStroke())) {
        manuallyChangedBindings.add(entry.getKey());
      }
    }

    if (manuallyChangedBindings.isEmpty()) {
      return false;
    }

    config =
        syncProfileFromCurrent(
            client.options,
            activeProfileId,
            manuallyChangedBindings,
            Collections.<String>emptySet());
    save(client);
    queueStatusNotice(Text.translatable("keyset.status.manual_synced").getString(), false);
    return true;
  }

  public ActivationResult cycleToNextProfile(MinecraftClient client) throws IOException {
    ensureLoaded(client);
    List<KeysetProfile> profiles = new ArrayList<KeysetProfile>(config.getProfiles().values());
    if (profiles.size() <= 1) {
      KeysetProfile activeProfile = requireProfile(config, config.getActiveProfileId());
      return ActivationResult.from(activeProfile.getName(), KeysetConflictReport.empty());
    }

    List<String> profileIds = new ArrayList<String>(config.getProfiles().keySet());
    int currentIndex = profileIds.indexOf(config.getActiveProfileId());
    if (currentIndex < 0) {
      currentIndex = 0;
    }
    int nextIndex = (currentIndex + 1) % profileIds.size();
    String nextProfileId = profileIds.get(nextIndex);
    return activateProfile(client, nextProfileId);
  }

  public ActivationResult cycleToPreviousProfile(MinecraftClient client) throws IOException {
    ensureLoaded(client);
    List<KeysetProfile> profiles = new ArrayList<KeysetProfile>(config.getProfiles().values());
    if (profiles.size() <= 1) {
      KeysetProfile activeProfile = requireProfile(config, config.getActiveProfileId());
      return ActivationResult.from(activeProfile.getName(), KeysetConflictReport.empty());
    }

    List<String> profileIds = new ArrayList<String>(config.getProfiles().keySet());
    int currentIndex = profileIds.indexOf(config.getActiveProfileId());
    if (currentIndex < 0) {
      currentIndex = 0;
    }
    int prevIndex = (currentIndex - 1 + profileIds.size()) % profileIds.size();
    String prevProfileId = profileIds.get(prevIndex);
    return activateProfile(client, prevProfileId);
  }

  public ActivationResult activateProfileByIndex(MinecraftClient client, int slotIndex)
      throws IOException {
    ensureLoaded(client);
    List<String> profileIds = new ArrayList<String>(config.getProfiles().keySet());
    if (slotIndex < 0 || slotIndex >= profileIds.size()) {
      return null;
    }
    return activateProfile(client, profileIds.get(slotIndex));
  }

  public void moveProfileUp(MinecraftClient client, String profileId) throws IOException {
    ensureLoaded(client);
    config = KeysetProfiles.moveProfileUp(config, profileId);
    save(client);
  }

  public void moveProfileDown(MinecraftClient client, String profileId) throws IOException {
    ensureLoaded(client);
    config = KeysetProfiles.moveProfileDown(config, profileId);
    save(client);
  }

  private void ensureLoaded(MinecraftClient client) throws IOException {
    if (loaded) {
      return;
    }

    Path path = configPath(client);
    boolean fileExists = Files.exists(path);
    try {
      config = KeysetProfiles.normalize(codec.read(path));
    } catch (JsonParseException | IllegalArgumentException exception) {
      LOGGER.warn("Keyset: failed to load config, recovering", exception);
      if (fileExists) {
        archiveConfigCopy(path, "broken");
      }
      config = recoverConfigAfterLoadFailure(client);
      loaded = true;
      applyProfile(client.options, requireProfile(config, config.getActiveProfileId()));
      return;
    }

    if (!fileExists) {
      config = seedStarterProfiles(client.options, config);
      save(client);
    }

    loaded = true;
    applyProfile(client.options, requireProfile(config, config.getActiveProfileId()));
  }

  private KeysetProfilesConfig seedStarterProfiles(
      GameOptions options, KeysetProfilesConfig currentConfig) {
    Map<String, KeysetBindingSnapshot> initialBindings = captureSnapshots(options, false);
    KeysetProfilesConfig seededConfig = currentConfig;
    for (String builtInProfileId :
        java.util.Arrays.asList(
            KeysetProfiles.DEFAULT_PROFILE_ID,
            KeysetProfiles.PVP_PROFILE_ID,
            KeysetProfiles.BUILDING_PROFILE_ID,
            KeysetProfiles.TECH_PROFILE_ID)) {
      KeysetProfile profile = seededConfig.getProfile(builtInProfileId);
      if (profile != null && profile.getBindings().isEmpty()) {
        seededConfig =
            replaceProfileBindings(seededConfig, builtInProfileId, initialBindings, true);
      }
    }
    return seededConfig;
  }

  private KeysetProfilesConfig syncProfileFromCurrent(
      GameOptions options,
      String profileId,
      Set<String> stickyBindings,
      Set<String> nonStickyBindings) {
    KeysetProfile previousProfile = requireProfile(config, profileId);
    Map<String, KeysetBindingSnapshot> currentSnapshots = captureSnapshots(options, false);
    Map<String, KeysetBindingSnapshot> mergedSnapshots =
        new LinkedHashMap<String, KeysetBindingSnapshot>(currentSnapshots.size());

    for (Map.Entry<String, KeysetBindingSnapshot> entry : currentSnapshots.entrySet()) {
      KeysetBindingSnapshot previousSnapshot = previousProfile.getBindings().get(entry.getKey());
      boolean sticky = previousSnapshot != null && previousSnapshot.isSticky();
      if (nonStickyBindings.contains(entry.getKey())) {
        sticky = false;
      }
      if (stickyBindings.contains(entry.getKey())) {
        sticky = true;
      }
      mergedSnapshots.put(
          entry.getKey(), new KeysetBindingSnapshot(entry.getValue().getKeyStroke(), sticky));
    }

    return replaceProfileBindings(config, profileId, mergedSnapshots, previousProfile.isBuiltIn());
  }

  private KeysetProfilesConfig replaceProfileBindings(
      KeysetProfilesConfig currentConfig,
      String profileId,
      Map<String, KeysetBindingSnapshot> bindings,
      Boolean builtInOverride) {
    KeysetProfile existingProfile = requireProfile(currentConfig, profileId);
    Map<String, KeysetProfile> profiles =
        new LinkedHashMap<String, KeysetProfile>(currentConfig.getProfiles());
    profiles.put(
        profileId,
        new KeysetProfile(
            profileId,
            existingProfile.getName(),
            builtInOverride == null ? existingProfile.isBuiltIn() : builtInOverride.booleanValue(),
            bindings));

    return KeysetProfiles.normalize(
        new KeysetProfilesConfig(
            currentConfig.getSchemaVersion(), currentConfig.getActiveProfileId(), profiles));
  }

  private KeysetProfilesConfig recoverConfigAfterLoadFailure(MinecraftClient client)
      throws IOException {
    // Try .bak, then .bak1, then .bak2.
    Path[] backupPaths = {
      backupConfigPath(client), backupConfigPath1(client), backupConfigPath2(client)
    };

    for (Path backupPath : backupPaths) {
      if (Files.exists(backupPath)) {
        try {
          KeysetProfilesConfig recoveredConfig = KeysetProfiles.normalize(codec.read(backupPath));
          recoveredConfig = seedStarterProfiles(client.options, recoveredConfig);
          config = recoveredConfig;
          save(client);
          LOGGER.info("Keyset: recovered from backup config");
          queueStatusNotice(
              Text.translatable("keyset.status.config_recovered_backup").getString(), false);
          return recoveredConfig;
        } catch (JsonParseException | IllegalArgumentException exception) {
          archiveConfigCopy(backupPath, "backup-broken");
        }
      }
    }

    KeysetProfilesConfig recoveredConfig =
        seedStarterProfiles(client.options, KeysetProfiles.createDefaultConfig());
    config = recoveredConfig;
    save(client);
    LOGGER.warn("Keyset: reset to default config after backup failure");
    queueStatusNotice(
        Text.translatable("keyset.status.config_recovered_default").getString(), true);
    return recoveredConfig;
  }

  private void save(MinecraftClient client) throws IOException {
    Path path = configPath(client);
    codec.write(path, config);

    // Write-back verification.
    try {
      codec.read(path);
    } catch (JsonParseException | IllegalArgumentException exception) {
      LOGGER.warn("Keyset: write-back verification failed — saved file may be corrupt", exception);
    }

    refreshBackup(path, client);
  }

  private Path configPath(MinecraftClient client) {
    return client
        .runDirectory
        .toPath()
        .resolve("config")
        .resolve(KeysetCoreMetadata.CONFIG_FILE_NAME);
  }

  private Path backupConfigPath(MinecraftClient client) {
    return client
        .runDirectory
        .toPath()
        .resolve("config")
        .resolve(KeysetCoreMetadata.CONFIG_FILE_NAME + ".bak");
  }

  private Path backupConfigPath1(MinecraftClient client) {
    return client
        .runDirectory
        .toPath()
        .resolve("config")
        .resolve(KeysetCoreMetadata.CONFIG_FILE_NAME + ".bak1");
  }

  private Path backupConfigPath2(MinecraftClient client) {
    return client
        .runDirectory
        .toPath()
        .resolve("config")
        .resolve(KeysetCoreMetadata.CONFIG_FILE_NAME + ".bak2");
  }

  private void refreshBackup(Path path, MinecraftClient client) {
    try {
      Path bak = backupConfigPath(client);
      Path bak1 = backupConfigPath1(client);
      Path bak2 = backupConfigPath2(client);

      Path parent = bak.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }

      // Rotate: bak2 = old bak1, bak1 = old bak, bak = new.
      if (Files.exists(bak1)) {
        Files.copy(bak1, bak2, StandardCopyOption.REPLACE_EXISTING);
      }
      if (Files.exists(bak)) {
        Files.copy(bak, bak1, StandardCopyOption.REPLACE_EXISTING);
      }
      Files.copy(path, bak, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      queueStatusNotice(Text.translatable("keyset.status.config_backup_warning").getString(), true);
    }
  }

  private void archiveConfigCopy(Path sourcePath, String label) {
    if (sourcePath == null || !Files.exists(sourcePath)) {
      return;
    }

    try {
      Path parent = sourcePath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      String archivedName =
          sourcePath.getFileName().toString() + "." + label + "." + System.currentTimeMillis();
      Files.copy(
          sourcePath, sourcePath.resolveSibling(archivedName), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      queueStatusNotice(
          Text.translatable("keyset.status.config_archive_warning").getString(), true);
    }
  }

  private void queueStatusNotice(String message, boolean error) {
    String normalized = message == null ? "" : message.trim();
    if (normalized.isEmpty()) {
      return;
    }

    // Non-errors are suppressed if there is already an error at the front of the queue.
    StatusNotice front = pendingNotices.peekFirst();
    if (front != null && front.isError() && !error) {
      return;
    }

    pendingNotices.addLast(new StatusNotice(normalized, error));
    while (pendingNotices.size() > MAX_NOTICE_QUEUE) {
      pendingNotices.pollLast();
    }
  }

  private static KeysetProfile requireProfile(KeysetProfilesConfig config, String profileId) {
    KeysetProfile profile = config.getProfile(profileId);
    if (profile == null) {
      throw new IllegalArgumentException("Unknown profile id: " + profileId);
    }
    return profile;
  }

  private static List<KeysetBindingDescriptor> describeBindings(GameOptions options) {
    return describeBindings(options, null);
  }

  private static List<KeysetBindingDescriptor> describeBindings(
      GameOptions options, KeysetProfile profileOverride) {
    List<KeysetBindingDescriptor> bindings =
        new ArrayList<KeysetBindingDescriptor>(options.allKeys.length);
    for (KeyBinding binding : options.allKeys) {
      KeysetBindingSnapshot snapshot =
          profileOverride == null
              ? null
              : profileOverride.getBindings().get(binding.getTranslationKey());
      KeysetKeyStroke keyStroke = snapshot == null ? toKeyStroke(binding) : snapshot.getKeyStroke();
      bindings.add(
          new KeysetBindingDescriptor(
              binding.getTranslationKey(),
              Text.translatable(binding.getTranslationKey()).getString(),
              binding.getCategory(),
              KeyBinding.getLocalizedName(binding.getCategory()).get().getString(),
              keyStroke,
              snapshot == null
                  ? binding.getBoundKeyLocalizedText().getString()
                  : keyDisplayName(keyStroke)));
    }
    return bindings;
  }

  private static Map<String, KeysetBindingSnapshot> captureSnapshots(
      GameOptions options, boolean sticky) {
    Map<String, KeysetBindingSnapshot> snapshots =
        new LinkedHashMap<String, KeysetBindingSnapshot>(options.allKeys.length);
    for (KeyBinding binding : options.allKeys) {
      snapshots.put(
          binding.getTranslationKey(), new KeysetBindingSnapshot(toKeyStroke(binding), sticky));
    }
    return snapshots;
  }

  private static KeysetKeyStroke toKeyStroke(KeyBinding binding) {
    return binding.isUnbound()
        ? KeysetKeyStroke.unbound()
        : KeysetKeyStroke.of(binding.getBoundKeyTranslationKey());
  }

  private static void applyProfile(GameOptions options, KeysetProfile profile) {
    Map<String, KeysetKeyStroke> strokes =
        new LinkedHashMap<String, KeysetKeyStroke>(profile.getBindings().size());
    for (Map.Entry<String, KeysetBindingSnapshot> entry : profile.getBindings().entrySet()) {
      strokes.put(entry.getKey(), entry.getValue().getKeyStroke());
    }
    applyStrokes(options, strokes);
  }

  private static void applyStrokes(GameOptions options, Map<String, KeysetKeyStroke> strokes) {
    if (strokes.isEmpty()) {
      return;
    }

    for (KeyBinding binding : options.allKeys) {
      KeysetKeyStroke keyStroke = strokes.get(binding.getTranslationKey());
      if (keyStroke == null) {
        continue;
      }
      binding.setBoundKey(toInputKey(keyStroke));
    }

    KeyBinding.updateKeysByCode();
    options.write();
  }

  private static InputUtil.Key toInputKey(KeysetKeyStroke keyStroke) {
    if (keyStroke == null || keyStroke.isUnbound()) {
      return InputUtil.UNKNOWN_KEY;
    }

    try {
      return InputUtil.fromTranslationKey(keyStroke.getKeyToken());
    } catch (IllegalArgumentException exception) {
      return InputUtil.UNKNOWN_KEY;
    }
  }

  private static String keyDisplayName(KeysetKeyStroke keyStroke) {
    return toInputKey(keyStroke).getLocalizedText().getString();
  }

  private static KeyBinding requireLiveBinding(GameOptions options, String bindingId) {
    for (KeyBinding binding : options.allKeys) {
      if (binding.getTranslationKey().equals(bindingId)) {
        return binding;
      }
    }
    throw new IllegalArgumentException("Unknown live keybind: " + bindingId);
  }

  private static Set<String> stickyBindings(KeysetProfile profile) {
    if (profile == null) {
      return Collections.emptySet();
    }

    Set<String> stickyBindings = new HashSet<String>();
    for (Map.Entry<String, KeysetBindingSnapshot> entry : profile.getBindings().entrySet()) {
      if (entry.getValue().isSticky()) {
        stickyBindings.add(entry.getKey());
      }
    }
    return stickyBindings;
  }

  private static Set<String> protectedBindingIds(GameOptions options) {
    Set<String> protectedBindings = new HashSet<String>();
    Collections.addAll(
        protectedBindings,
        options.forwardKey.getTranslationKey(),
        options.leftKey.getTranslationKey(),
        options.backKey.getTranslationKey(),
        options.rightKey.getTranslationKey(),
        options.jumpKey.getTranslationKey(),
        options.inventoryKey.getTranslationKey(),
        options.chatKey.getTranslationKey(),
        options.dropKey.getTranslationKey());
    return protectedBindings;
  }

  private static Set<String> currentlyUsedKeys(Collection<KeysetBindingDescriptor> bindings) {
    Set<String> usedKeys = new LinkedHashSet<String>();
    for (KeysetBindingDescriptor binding : bindings) {
      if (binding.isBound()) {
        usedKeys.add(binding.getKeyStroke().getKeyToken());
      }
    }
    return usedKeys;
  }

  private static List<KeyBindingDescriptorWithFlags> resolveCandidates(
      List<KeysetBindingDescriptor> bindings,
      Set<String> stickyBindings,
      Set<String> protectedBindings) {
    List<KeyBindingDescriptorWithFlags> candidates =
        new ArrayList<KeyBindingDescriptorWithFlags>(bindings.size());
    for (KeysetBindingDescriptor binding : bindings) {
      int protectionScore = 0;
      if (stickyBindings.contains(binding.getId())) {
        protectionScore += 50;
      }
      if (protectedBindings.contains(binding.getId())) {
        protectionScore += 100;
      }
      candidates.add(new KeyBindingDescriptorWithFlags(binding, protectionScore));
    }

    Collections.sort(candidates, RESOLVE_ORDER);
    return candidates;
  }

  private static KeysetKeyStroke nextAvailableKey(Set<String> usedKeys) {
    for (String keyToken : SAFE_KEY_POOL) {
      if (!usedKeys.contains(keyToken)) {
        return KeysetKeyStroke.of(keyToken);
      }
    }
    return null;
  }

  private static String findAddedProfileId(
      KeysetProfilesConfig previousConfig, KeysetProfilesConfig updatedConfig) {
    Set<String> previousProfileIds = previousConfig.getProfiles().keySet();
    for (String profileId : updatedConfig.getProfiles().keySet()) {
      if (!previousProfileIds.contains(profileId)) {
        return profileId;
      }
    }
    throw new IllegalStateException("Expected a new profile to be created");
  }

  private static int compareText(String left, String right) {
    int insensitive = left.compareToIgnoreCase(right);
    return insensitive != 0 ? insensitive : left.compareTo(right);
  }

  public static final class ImportResult {
    private final int importedCount;
    private final String lastImportedProfileId;

    private ImportResult(int importedCount, String lastImportedProfileId) {
      this.importedCount = importedCount;
      this.lastImportedProfileId = lastImportedProfileId;
    }

    public int getImportedCount() {
      return importedCount;
    }

    public String getLastImportedProfileId() {
      return lastImportedProfileId;
    }
  }

  public static final class AutoResolvePlan {
    private static final AutoResolvePlan EMPTY =
        new AutoResolvePlan(Collections.<AutoResolveChange>emptyList(), 0);

    private final List<AutoResolveChange> changes;
    private final int unresolvedBindings;

    private AutoResolvePlan(List<AutoResolveChange> changes, int unresolvedBindings) {
      this.changes = Collections.unmodifiableList(new ArrayList<AutoResolveChange>(changes));
      this.unresolvedBindings = unresolvedBindings;
    }

    public static AutoResolvePlan empty() {
      return EMPTY;
    }

    public List<AutoResolveChange> getChanges() {
      return changes;
    }

    public int getUnresolvedBindings() {
      return unresolvedBindings;
    }

    public boolean isEmpty() {
      return changes.isEmpty() && unresolvedBindings == 0;
    }

    private Map<String, KeysetKeyStroke> toStrokeMap() {
      Map<String, KeysetKeyStroke> strokes =
          new LinkedHashMap<String, KeysetKeyStroke>(changes.size());
      for (AutoResolveChange change : changes) {
        strokes.put(change.bindingId, change.newKeyStroke);
      }
      return strokes;
    }

    private Set<String> changedBindingIds() {
      Set<String> bindingIds = new HashSet<String>();
      for (AutoResolveChange change : changes) {
        bindingIds.add(change.bindingId);
      }
      return bindingIds;
    }
  }

  public static final class AutoResolveChange {
    private final String bindingId;
    private final String bindingName;
    private final KeysetKeyStroke oldKeyStroke;
    private final KeysetKeyStroke newKeyStroke;
    private final String oldKeyDisplayName;
    private final String newKeyDisplayName;

    private AutoResolveChange(
        String bindingId,
        String bindingName,
        KeysetKeyStroke oldKeyStroke,
        KeysetKeyStroke newKeyStroke,
        String oldKeyDisplayName,
        String newKeyDisplayName) {
      this.bindingId = bindingId;
      this.bindingName = bindingName;
      this.oldKeyStroke = oldKeyStroke;
      this.newKeyStroke = newKeyStroke;
      this.oldKeyDisplayName = oldKeyDisplayName;
      this.newKeyDisplayName = newKeyDisplayName;
    }

    public String getBindingName() {
      return bindingName;
    }

    public String getOldKeyDisplayName() {
      return oldKeyDisplayName;
    }

    public String getNewKeyDisplayName() {
      return newKeyDisplayName;
    }
  }

  public static final class UndoState {
    private final String profileId;
    private final Map<String, KeysetBindingSnapshot> previousBindings;

    private UndoState(String profileId, Map<String, KeysetBindingSnapshot> previousBindings) {
      this.profileId = profileId;
      this.previousBindings = previousBindings;
    }
  }

  private static final class KeyBindingDescriptorWithFlags {
    private final KeysetBindingDescriptor descriptor;
    private final int protectionScore;

    private KeyBindingDescriptorWithFlags(KeysetBindingDescriptor descriptor, int protectionScore) {
      this.descriptor = descriptor;
      this.protectionScore = protectionScore;
    }
  }

  public static final class StatusNotice {
    private final String message;
    private final boolean error;

    private StatusNotice(String message, boolean error) {
      this.message = message;
      this.error = error;
    }

    public String getMessage() {
      return message;
    }

    public boolean isError() {
      return error;
    }
  }

  public static final class ActivationResult {
    private final String profileName;
    private final int conflictCount;
    private final int affectedBindingCount;

    private ActivationResult(String profileName, int conflictCount, int affectedBindingCount) {
      this.profileName = profileName;
      this.conflictCount = conflictCount;
      this.affectedBindingCount = affectedBindingCount;
    }

    private static ActivationResult from(String profileName, KeysetConflictReport conflictReport) {
      KeysetConflictReport safeReport =
          conflictReport == null ? KeysetConflictReport.empty() : conflictReport;
      return new ActivationResult(
          profileName, safeReport.getConflictCount(), safeReport.getAffectedBindingCount());
    }

    public String getProfileName() {
      return profileName;
    }

    public int getConflictCount() {
      return conflictCount;
    }

    public int getAffectedBindingCount() {
      return affectedBindingCount;
    }

    public boolean hasConflicts() {
      return conflictCount > 0;
    }
  }
}
