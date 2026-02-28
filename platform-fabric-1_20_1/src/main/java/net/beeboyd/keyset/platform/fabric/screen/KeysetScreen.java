package net.beeboyd.keyset.platform.fabric.screen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.beeboyd.keyset.core.binding.KeysetBindingDescriptor;
import net.beeboyd.keyset.core.conflict.KeysetConflictGroup;
import net.beeboyd.keyset.core.conflict.KeysetConflictGroupMode;
import net.beeboyd.keyset.core.conflict.KeysetConflictQuery;
import net.beeboyd.keyset.core.conflict.KeysetConflictReport;
import net.beeboyd.keyset.core.profile.KeysetProfile;
import net.beeboyd.keyset.core.profile.KeysetProfiles;
import net.beeboyd.keyset.core.profile.KeysetProfilesConfig;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService.AutoResolvePlan;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService.ImportResult;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService.UndoState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Main Keyset screen for the representative Fabric target. */
public final class KeysetScreen extends Screen {
  private static final int PANEL_PADDING = 12;
  private static final int STATUS_SUCCESS_COLOR = 0x8FD98F;
  private static final int STATUS_ERROR_COLOR = 0xFF9A8A;

  private final Screen parent;
  private final KeysetFabricService service;

  private TextFieldWidget searchField;
  private TextFieldWidget profileNameField;
  private KeysetConflictListWidget conflictListWidget;
  private ButtonWidget groupToggleButton;
  private ButtonWidget deleteButton;
  private ButtonWidget applyButton;
  private ButtonWidget previewResolveButton;
  private ButtonWidget applyPreviewButton;
  private ButtonWidget jumpButton;
  private ButtonWidget clearBindingButton;
  private ButtonWidget reassignButton;
  private ButtonWidget undoButton;

  private KeysetProfilesConfig config;
  private KeysetConflictReport conflictReport = KeysetConflictReport.empty();
  private KeysetConflictGroupMode groupMode = KeysetConflictGroupMode.BY_KEY;
  private String selectedProfileId;
  private KeysetBindingDescriptor selectedBinding;
  private AutoResolvePlan previewPlan;
  private UndoState undoState;
  private String statusMessage = "";
  private boolean errorStatus;

  public KeysetScreen(Screen parent, KeysetFabricService service) {
    super(Text.translatable("keyset.title"));
    this.parent = parent;
    this.service = service;
  }

  @Override
  protected void init() {
    int left = PANEL_PADDING;
    int buttonGap = 4;
    int fullWidth = width - PANEL_PADDING * 2;
    int topButtonWidth = (fullWidth - buttonGap * 3) / 4;
    int actionButtonWidth = (fullWidth - buttonGap * 4) / 5;

    searchField =
        addDrawableChild(
            new TextFieldWidget(
                textRenderer, left, 28, fullWidth - 124, 20, Text.translatable("keyset.search")));
    searchField.setMaxLength(80);
    searchField.setPlaceholder(Text.translatable("keyset.search.placeholder"));
    searchField.setChangedListener(
        value -> {
          previewPlan = null;
          rebuildList(selectedBindingId());
        });

    groupToggleButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.group.by_key"), button -> toggleGroupMode())
                .dimensions(width - PANEL_PADDING - 120, 28, 120, 20)
                .build());

    profileNameField =
        addDrawableChild(
            new TextFieldWidget(
                textRenderer, left, 52, fullWidth, 20, Text.translatable("keyset.profile.name")));
    profileNameField.setMaxLength(40);

    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.profile.prev"), button -> selectRelative(-1))
            .dimensions(left, 76, topButtonWidth, 20)
            .build());
    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.profile.next"), button -> selectRelative(1))
            .dimensions(left + (topButtonWidth + buttonGap), 76, topButtonWidth, 20)
            .build());
    applyButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.profile.apply"), button -> applySelected())
                .dimensions(left + (topButtonWidth + buttonGap) * 2, 76, topButtonWidth, 20)
                .build());
    addDrawableChild(
        ButtonWidget.builder(
                Text.translatable("keyset.profile.capture"), button -> captureCurrent())
            .dimensions(left + (topButtonWidth + buttonGap) * 3, 76, topButtonWidth, 20)
            .build());

    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.profile.rename"), button -> renameSelected())
            .dimensions(left, 100, topButtonWidth, 20)
            .build());
    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.profile.new"), button -> createProfile())
            .dimensions(left + (topButtonWidth + buttonGap), 100, topButtonWidth, 20)
            .build());
    addDrawableChild(
        ButtonWidget.builder(
                Text.translatable("keyset.profile.duplicate"), button -> duplicateSelected())
            .dimensions(left + (topButtonWidth + buttonGap) * 2, 100, topButtonWidth, 20)
            .build());
    deleteButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.profile.delete"), button -> deleteSelected())
                .dimensions(left + (topButtonWidth + buttonGap) * 3, 100, topButtonWidth, 20)
                .build());

    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.export"), button -> exportSelected())
            .dimensions(left, 124, topButtonWidth, 20)
            .build());
    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.import"), button -> importProfiles())
            .dimensions(left + (topButtonWidth + buttonGap), 124, topButtonWidth, 20)
            .build());
    previewResolveButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.resolve.preview"), button -> previewResolve())
                .dimensions(left + (topButtonWidth + buttonGap) * 2, 124, topButtonWidth, 20)
                .build());
    applyPreviewButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.resolve.apply"), button -> applyPreview())
                .dimensions(left + (topButtonWidth + buttonGap) * 3, 124, topButtonWidth, 20)
                .build());

    jumpButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.binding.jump"), button -> jumpToBinding())
                .dimensions(left, 148, actionButtonWidth, 20)
                .build());
    clearBindingButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.binding.clear"), button -> clearSelectedBinding())
                .dimensions(left + (actionButtonWidth + buttonGap), 148, actionButtonWidth, 20)
                .build());
    reassignButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.binding.reassign"),
                    button -> reassignSelectedBinding())
                .dimensions(left + (actionButtonWidth + buttonGap) * 2, 148, actionButtonWidth, 20)
                .build());
    undoButton =
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("keyset.resolve.undo"), button -> undoResolve())
                .dimensions(left + (actionButtonWidth + buttonGap) * 3, 148, actionButtonWidth, 20)
                .build());
    addDrawableChild(
        ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
            .dimensions(left + (actionButtonWidth + buttonGap) * 4, 148, actionButtonWidth, 20)
            .build());

    conflictListWidget =
        addDrawableChild(
            new KeysetConflictListWidget(
                client,
                fullWidth,
                196,
                height - 20,
                bindingDescriptor -> {
                  selectedBinding = bindingDescriptor;
                  refreshButtons();
                }));
    conflictListWidget.setLeftPos(left);

    try {
      reloadState(null, null);
    } catch (IOException exception) {
      setStatus(exception.getMessage(), true);
    }
    setInitialFocus(searchField);
  }

  @Override
  public void tick() {
    super.tick();
    searchField.tick();
    profileNameField.tick();
  }

  @Override
  public void close() {
    client.setScreen(parent);
  }

  @Override
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    renderBackground(context);
    drawHeader(context);
    super.render(context, mouseX, mouseY, delta);
  }

  private void drawHeader(DrawContext context) {
    context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);

    if (config == null || !config.hasProfile(selectedProfileId)) {
      return;
    }

    KeysetProfile selectedProfile = config.getProfile(selectedProfileId);
    String activeProfileName = config.getProfile(config.getActiveProfileId()).getName();
    context.drawTextWithShadow(
        textRenderer,
        Text.translatable("keyset.summary.active", activeProfileName),
        PANEL_PADDING,
        176,
        0xD8D8D8);

    if (selectedBinding != null && previewPlan == null) {
      context.drawTextWithShadow(
          textRenderer,
          Text.translatable(
              "keyset.summary.binding",
              selectedBinding.getDisplayName(),
              selectedBinding.getCategoryName(),
              selectedBinding.getKeyDisplayName()),
          PANEL_PADDING,
          186,
          0xB8C7D9);
    } else {
      context.drawTextWithShadow(
          textRenderer,
          Text.translatable(
              "keyset.summary.selected",
              selectedProfile.getName(),
              Integer.valueOf(conflictReport.getConflictCount())),
          PANEL_PADDING,
          186,
          0xB8C7D9);
    }

    if (!statusMessage.isEmpty()) {
      context.drawTextWithShadow(
          textRenderer,
          Text.literal(statusMessage),
          width - PANEL_PADDING - textRenderer.getWidth(statusMessage),
          176,
          errorStatus ? STATUS_ERROR_COLOR : STATUS_SUCCESS_COLOR);
    }
  }

  private void toggleGroupMode() {
    groupMode =
        groupMode == KeysetConflictGroupMode.BY_KEY
            ? KeysetConflictGroupMode.BY_CATEGORY
            : KeysetConflictGroupMode.BY_KEY;
    previewPlan = null;
    rebuildList(selectedBindingId());
  }

  private void selectRelative(int direction) {
    if (config == null) {
      return;
    }

    List<KeysetProfile> profiles = new ArrayList<KeysetProfile>(config.getProfiles().values());
    if (profiles.isEmpty()) {
      return;
    }

    int currentIndex = 0;
    for (int index = 0; index < profiles.size(); index++) {
      if (profiles.get(index).getId().equals(selectedProfileId)) {
        currentIndex = index;
        break;
      }
    }

    int nextIndex = (currentIndex + direction + profiles.size()) % profiles.size();
    selectedProfileId = profiles.get(nextIndex).getId();
    profileNameField.setText(profiles.get(nextIndex).getName());
    previewPlan = null;
    refreshButtons();
  }

  private void createProfile() {
    runAction(
        () -> {
          String createdProfileId =
              service.createProfileFromCurrent(
                  client, nonBlankOrFallback(profileNameField.getText(), "Profile"));
          previewPlan = null;
          reloadState(createdProfileId, null);
          setStatus(Text.translatable("keyset.status.profile_created").getString(), false);
        });
  }

  private void duplicateSelected() {
    runAction(
        () -> {
          String duplicateProfileId = service.duplicateProfile(client, selectedProfileId);
          previewPlan = null;
          reloadState(duplicateProfileId, null);
          setStatus(Text.translatable("keyset.status.profile_duplicated").getString(), false);
        });
  }

  private void renameSelected() {
    runAction(
        () -> {
          service.renameProfile(client, selectedProfileId, profileNameField.getText());
          previewPlan = null;
          reloadState(selectedProfileId, selectedBindingId());
          setStatus(Text.translatable("keyset.status.profile_renamed").getString(), false);
        });
  }

  private void deleteSelected() {
    runAction(
        () -> {
          String fallbackProfileId = service.deleteProfile(client, selectedProfileId);
          previewPlan = null;
          reloadState(fallbackProfileId, null);
          setStatus(Text.translatable("keyset.status.profile_deleted").getString(), false);
        });
  }

  private void applySelected() {
    runAction(
        () -> {
          service.activateProfile(client, selectedProfileId);
          previewPlan = null;
          reloadState(selectedProfileId, selectedBindingId());
          setStatus(Text.translatable("keyset.status.profile_applied").getString(), false);
        });
  }

  private void captureCurrent() {
    runAction(
        () -> {
          service.captureCurrentToProfile(client, selectedProfileId, true);
          previewPlan = null;
          reloadState(selectedProfileId, selectedBindingId());
          setStatus(Text.translatable("keyset.status.profile_captured").getString(), false);
        });
  }

  private void exportSelected() {
    runAction(
        () -> {
          client.keyboard.setClipboard(service.exportProfileJson(client, selectedProfileId));
          setStatus(Text.translatable("keyset.status.exported").getString(), false);
        });
  }

  private void importProfiles() {
    runAction(
        () -> {
          ImportResult result = service.importProfiles(client, client.keyboard.getClipboard());
          if (result.getImportedCount() == 0) {
            throw new IllegalArgumentException(
                Text.translatable("keyset.error.import_empty").getString());
          }
          previewPlan = null;
          reloadState(result.getLastImportedProfileId(), null);
          setStatus(
              Text.translatable(
                      "keyset.status.imported", Integer.valueOf(result.getImportedCount()))
                  .getString(),
              false);
        });
  }

  private void previewResolve() {
    runAction(
        () -> {
          previewPlan = service.previewAutoResolve(client);
          rebuildList(null);
          if (previewPlan.isEmpty()) {
            setStatus(Text.translatable("keyset.status.resolve_none").getString(), false);
          } else {
            setStatus(Text.translatable("keyset.status.resolve_preview").getString(), false);
          }
        });
  }

  private void applyPreview() {
    runAction(
        () -> {
          if (previewPlan == null || previewPlan.getChanges().isEmpty()) {
            throw new IllegalStateException(
                Text.translatable("keyset.error.no_preview").getString());
          }
          undoState = service.applyAutoResolve(client, previewPlan);
          previewPlan = null;
          reloadState(config.getActiveProfileId(), null);
          setStatus(Text.translatable("keyset.status.resolve_applied").getString(), false);
        });
  }

  private void jumpToBinding() {
    try {
      client.setScreen(
          new KeysetKeybindsScreen(
              this, client.options, service, requireSelectedBindingId(), false));
    } catch (IllegalStateException exception) {
      setStatus(exception.getMessage(), true);
    }
  }

  private void clearSelectedBinding() {
    runAction(
        () -> {
          String bindingId = requireSelectedBindingId();
          service.clearActiveBinding(client, bindingId);
          previewPlan = null;
          reloadState(config.getActiveProfileId(), bindingId);
          setStatus(Text.translatable("keyset.status.binding_cleared").getString(), false);
        });
  }

  private void reassignSelectedBinding() {
    try {
      client.setScreen(
          new KeysetKeybindsScreen(
              this, client.options, service, requireSelectedBindingId(), true));
    } catch (IllegalStateException exception) {
      setStatus(exception.getMessage(), true);
    }
  }

  private void undoResolve() {
    runAction(
        () -> {
          if (undoState == null) {
            throw new IllegalStateException(Text.translatable("keyset.error.no_undo").getString());
          }
          service.undoAutoResolve(client, undoState);
          undoState = null;
          previewPlan = null;
          reloadState(config.getActiveProfileId(), null);
          setStatus(Text.translatable("keyset.status.resolve_undone").getString(), false);
        });
  }

  private void reloadState(String preferredSelectedProfileId, String preferredBindingId)
      throws IOException {
    config = service.getConfig(client);
    conflictReport = service.buildConflictReport(client);
    selectedProfileId =
        preferredSelectedProfileId != null && config.hasProfile(preferredSelectedProfileId)
            ? preferredSelectedProfileId
            : config.getActiveProfileId();
    profileNameField.setText(config.getProfile(selectedProfileId).getName());
    rebuildList(preferredBindingId);
  }

  private void rebuildList(String preferredBindingId) {
    if (conflictListWidget == null) {
      return;
    }

    if (previewPlan != null) {
      selectedBinding = null;
      conflictListWidget.showPreview(previewPlan);
      refreshButtons();
      return;
    }

    List<KeysetConflictGroup> groups =
        conflictReport.query(new KeysetConflictQuery(groupMode, searchField.getText()));
    if (groups.isEmpty()) {
      selectedBinding = null;
      conflictListWidget.clearContents();
      refreshButtons();
      return;
    }

    conflictListWidget.showConflicts(groups, groupMode, preferredBindingId);
    refreshButtons();
  }

  private void refreshButtons() {
    if (config == null || selectedProfileId == null) {
      return;
    }

    deleteButton.active = !KeysetProfiles.DEFAULT_PROFILE_ID.equals(selectedProfileId);
    applyButton.active = !selectedProfileId.equals(config.getActiveProfileId());
    previewResolveButton.active = !conflictReport.isEmpty();
    applyPreviewButton.active = previewPlan != null && !previewPlan.getChanges().isEmpty();
    undoButton.active = undoState != null;

    boolean bindingActionsActive =
        selectedBinding != null
            && previewPlan == null
            && selectedProfileId.equals(config.getActiveProfileId());
    jumpButton.active = bindingActionsActive;
    clearBindingButton.active = bindingActionsActive;
    reassignButton.active = bindingActionsActive;

    groupToggleButton.setMessage(
        Text.translatable(
            groupMode == KeysetConflictGroupMode.BY_KEY
                ? "keyset.group.by_key"
                : "keyset.group.by_category"));
  }

  private void setStatus(String message, boolean error) {
    statusMessage = message == null ? "" : message;
    errorStatus = error;
  }

  private String selectedBindingId() {
    return selectedBinding == null ? null : selectedBinding.getId();
  }

  private String requireSelectedBindingId() {
    if (selectedBinding == null) {
      throw new IllegalStateException(
          Text.translatable("keyset.error.no_binding_selected").getString());
    }
    if (config == null || !selectedProfileId.equals(config.getActiveProfileId())) {
      throw new IllegalStateException(
          Text.translatable("keyset.error.actions_require_active").getString());
    }
    return selectedBinding.getId();
  }

  private void runAction(Action action) {
    try {
      action.run();
    } catch (IllegalArgumentException | IllegalStateException | IOException exception) {
      setStatus(exception.getMessage(), true);
    }
  }

  private static String nonBlankOrFallback(String value, String fallback) {
    String normalized = value == null ? "" : value.trim();
    return normalized.isEmpty() ? fallback : normalized;
  }

  private interface Action {
    void run() throws IOException;
  }
}
