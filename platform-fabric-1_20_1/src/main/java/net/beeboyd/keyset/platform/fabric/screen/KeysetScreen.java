package net.beeboyd.keyset.platform.fabric.screen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.beeboyd.keyset.core.conflict.KeysetConflict;
import net.beeboyd.keyset.core.conflict.KeysetConflictGroup;
import net.beeboyd.keyset.core.conflict.KeysetConflictGroupMode;
import net.beeboyd.keyset.core.conflict.KeysetConflictQuery;
import net.beeboyd.keyset.core.conflict.KeysetConflictReport;
import net.beeboyd.keyset.core.profile.KeysetProfile;
import net.beeboyd.keyset.core.profile.KeysetProfiles;
import net.beeboyd.keyset.core.profile.KeysetProfilesConfig;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService.AutoResolveChange;
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
  private static final int PANEL_ROW_HEIGHT = 12;
  private static final int STATUS_SUCCESS_COLOR = 0x8FD98F;
  private static final int STATUS_ERROR_COLOR = 0xFF9A8A;

  private final Screen parent;
  private final KeysetFabricService service;

  private TextFieldWidget searchField;
  private TextFieldWidget profileNameField;
  private ButtonWidget groupToggleButton;
  private ButtonWidget deleteButton;
  private ButtonWidget applyButton;
  private ButtonWidget previewResolveButton;
  private ButtonWidget applyPreviewButton;
  private ButtonWidget undoButton;

  private KeysetProfilesConfig config;
  private KeysetConflictReport conflictReport = KeysetConflictReport.empty();
  private KeysetConflictGroupMode groupMode = KeysetConflictGroupMode.BY_KEY;
  private String selectedProfileId;
  private List<String> listLines = Collections.emptyList();
  private int scrollOffset;
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
    int buttonWidth = (fullWidth - buttonGap * 3) / 4;

    searchField =
        addDrawableChild(
            new TextFieldWidget(
                textRenderer, left, 28, fullWidth - 124, 20, Text.translatable("keyset.search")));
    searchField.setMaxLength(80);
    searchField.setPlaceholder(Text.translatable("keyset.search.placeholder"));
    searchField.setChangedListener(
        value -> {
          previewPlan = null;
          rebuildList();
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
            .dimensions(left, 76, buttonWidth, 20)
            .build());
    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.profile.next"), button -> selectRelative(1))
            .dimensions(left + (buttonWidth + buttonGap), 76, buttonWidth, 20)
            .build());
    applyButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.profile.apply"), button -> applySelected())
                .dimensions(left + (buttonWidth + buttonGap) * 2, 76, buttonWidth, 20)
                .build());
    addDrawableChild(
        ButtonWidget.builder(
                Text.translatable("keyset.profile.capture"), button -> captureCurrent())
            .dimensions(left + (buttonWidth + buttonGap) * 3, 76, buttonWidth, 20)
            .build());

    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.profile.rename"), button -> renameSelected())
            .dimensions(left, 100, buttonWidth, 20)
            .build());
    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.profile.new"), button -> createProfile())
            .dimensions(left + (buttonWidth + buttonGap), 100, buttonWidth, 20)
            .build());
    addDrawableChild(
        ButtonWidget.builder(
                Text.translatable("keyset.profile.duplicate"), button -> duplicateSelected())
            .dimensions(left + (buttonWidth + buttonGap) * 2, 100, buttonWidth, 20)
            .build());
    deleteButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.profile.delete"), button -> deleteSelected())
                .dimensions(left + (buttonWidth + buttonGap) * 3, 100, buttonWidth, 20)
                .build());

    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.export"), button -> exportSelected())
            .dimensions(left, 124, buttonWidth, 20)
            .build());
    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.import"), button -> importProfiles())
            .dimensions(left + (buttonWidth + buttonGap), 124, buttonWidth, 20)
            .build());
    previewResolveButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.resolve.preview"), button -> previewResolve())
                .dimensions(left + (buttonWidth + buttonGap) * 2, 124, buttonWidth, 20)
                .build());
    applyPreviewButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.resolve.apply"), button -> applyPreview())
                .dimensions(left + (buttonWidth + buttonGap) * 3, 124, buttonWidth, 20)
                .build());

    undoButton =
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("keyset.resolve.undo"), button -> undoResolve())
                .dimensions(left, 148, buttonWidth, 20)
                .build());
    addDrawableChild(
        ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
            .dimensions(width - PANEL_PADDING - buttonWidth, 148, buttonWidth, 20)
            .build());

    try {
      reloadState(null);
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
  public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
    int panelTop = 196;
    int panelBottom = height - 20;
    if (mouseX >= PANEL_PADDING
        && mouseX <= width - PANEL_PADDING
        && mouseY >= panelTop
        && mouseY <= panelBottom) {
      int visibleRows = visibleRows(panelTop, panelBottom);
      int maxOffset = Math.max(0, listLines.size() - visibleRows);
      if (amount < 0 && scrollOffset < maxOffset) {
        scrollOffset++;
      } else if (amount > 0 && scrollOffset > 0) {
        scrollOffset--;
      }
      return true;
    }

    return super.mouseScrolled(mouseX, mouseY, amount);
  }

  @Override
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    renderBackground(context);
    drawHeader(context);
    drawConflictPanel(context);
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
    context.drawTextWithShadow(
        textRenderer,
        Text.translatable(
            "keyset.summary.selected",
            selectedProfile.getName(),
            Integer.valueOf(conflictReport.getConflictCount())),
        PANEL_PADDING,
        186,
        0xB8C7D9);

    if (!statusMessage.isEmpty()) {
      context.drawTextWithShadow(
          textRenderer,
          Text.literal(statusMessage),
          width - PANEL_PADDING - textRenderer.getWidth(statusMessage),
          176,
          errorStatus ? STATUS_ERROR_COLOR : STATUS_SUCCESS_COLOR);
    }
  }

  private void drawConflictPanel(DrawContext context) {
    int panelLeft = PANEL_PADDING;
    int panelTop = 196;
    int panelRight = width - PANEL_PADDING;
    int panelBottom = height - 20;
    context.fill(panelLeft, panelTop, panelRight, panelBottom, 0x8A111418);
    context.drawBorder(
        panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFF4A5562);

    int visibleRows = visibleRows(panelTop, panelBottom);
    int maxOffset = Math.max(0, listLines.size() - visibleRows);
    if (scrollOffset > maxOffset) {
      scrollOffset = maxOffset;
    }

    if (listLines.isEmpty()) {
      String emptyMessage =
          previewPlan != null
              ? Text.translatable("keyset.resolve.no_preview").getString()
              : Text.translatable("keyset.conflicts.none").getString();
      context.drawCenteredTextWithShadow(
          textRenderer, emptyMessage, width / 2, panelTop + 14, 0xAAB6C4);
      return;
    }

    int y = panelTop + 8;
    for (int index = scrollOffset;
        index < listLines.size() && index < scrollOffset + visibleRows;
        index++) {
      String line = textRenderer.trimToWidth(listLines.get(index), panelRight - panelLeft - 12);
      context.drawTextWithShadow(textRenderer, line, panelLeft + 6, y, 0xE0E6ED);
      y += PANEL_ROW_HEIGHT;
    }
  }

  private int visibleRows(int panelTop, int panelBottom) {
    return Math.max(1, (panelBottom - panelTop - 12) / PANEL_ROW_HEIGHT);
  }

  private void toggleGroupMode() {
    groupMode =
        groupMode == KeysetConflictGroupMode.BY_KEY
            ? KeysetConflictGroupMode.BY_CATEGORY
            : KeysetConflictGroupMode.BY_KEY;
    groupToggleButton.setMessage(
        Text.translatable(
            groupMode == KeysetConflictGroupMode.BY_KEY
                ? "keyset.group.by_key"
                : "keyset.group.by_category"));
    previewPlan = null;
    rebuildList();
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
          reloadState(createdProfileId);
          setStatus(Text.translatable("keyset.status.profile_created").getString(), false);
        });
  }

  private void duplicateSelected() {
    runAction(
        () -> {
          String duplicateProfileId = service.duplicateProfile(client, selectedProfileId);
          reloadState(duplicateProfileId);
          setStatus(Text.translatable("keyset.status.profile_duplicated").getString(), false);
        });
  }

  private void renameSelected() {
    runAction(
        () -> {
          service.renameProfile(client, selectedProfileId, profileNameField.getText());
          reloadState(selectedProfileId);
          setStatus(Text.translatable("keyset.status.profile_renamed").getString(), false);
        });
  }

  private void deleteSelected() {
    runAction(
        () -> {
          String fallbackProfileId = service.deleteProfile(client, selectedProfileId);
          reloadState(fallbackProfileId);
          setStatus(Text.translatable("keyset.status.profile_deleted").getString(), false);
        });
  }

  private void applySelected() {
    runAction(
        () -> {
          service.activateProfile(client, selectedProfileId);
          reloadState(selectedProfileId);
          setStatus(Text.translatable("keyset.status.profile_applied").getString(), false);
        });
  }

  private void captureCurrent() {
    runAction(
        () -> {
          service.captureCurrentToProfile(client, selectedProfileId, true);
          reloadState(selectedProfileId);
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
          reloadState(result.getLastImportedProfileId());
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
          rebuildList();
          refreshButtons();
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
          reloadState(config.getActiveProfileId());
          setStatus(Text.translatable("keyset.status.resolve_applied").getString(), false);
        });
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
          reloadState(config.getActiveProfileId());
          setStatus(Text.translatable("keyset.status.resolve_undone").getString(), false);
        });
  }

  private void reloadState(String preferredSelectedProfileId) throws IOException {
    config = service.getConfig(client);
    conflictReport = service.buildConflictReport(client);
    selectedProfileId =
        preferredSelectedProfileId != null && config.hasProfile(preferredSelectedProfileId)
            ? preferredSelectedProfileId
            : config.getActiveProfileId();
    profileNameField.setText(config.getProfile(selectedProfileId).getName());
    rebuildList();
    refreshButtons();
  }

  private void rebuildList() {
    List<String> lines = new ArrayList<String>();
    if (previewPlan != null) {
      lines.add(
          Text.translatable(
                  "keyset.resolve.summary",
                  Integer.valueOf(previewPlan.getChanges().size()),
                  Integer.valueOf(previewPlan.getUnresolvedBindings()))
              .getString());
      for (AutoResolveChange change : previewPlan.getChanges()) {
        lines.add(
            Text.translatable(
                    "keyset.resolve.change",
                    change.getBindingName(),
                    change.getOldKeyDisplayName(),
                    change.getNewKeyDisplayName())
                .getString());
      }
      if (previewPlan.getChanges().isEmpty()) {
        lines.add(Text.translatable("keyset.resolve.no_preview").getString());
      }
    } else {
      List<KeysetConflictGroup> groups =
          conflictReport.query(new KeysetConflictQuery(groupMode, searchField.getText()));
      if (groups.isEmpty()) {
        listLines = Collections.emptyList();
        scrollOffset = 0;
        refreshButtons();
        return;
      }

      for (KeysetConflictGroup group : groups) {
        lines.add(
            Text.translatable(
                    groupMode == KeysetConflictGroupMode.BY_KEY
                        ? "keyset.group.key"
                        : "keyset.group.category",
                    group.getTitle(),
                    Integer.valueOf(group.getConflictCount()))
                .getString());
        for (KeysetConflict conflict : group.getConflicts()) {
          if (groupMode == KeysetConflictGroupMode.BY_CATEGORY) {
            lines.add(
                Text.translatable(
                        "keyset.conflict.key_row",
                        conflict.getKeyDisplayName(),
                        Integer.valueOf(conflict.getBindings().size()))
                    .getString());
          }
          for (net.beeboyd.keyset.core.binding.KeysetBindingDescriptor binding :
              conflict.getBindings()) {
            lines.add(
                Text.translatable(
                        "keyset.conflict.binding_row",
                        binding.getDisplayName(),
                        binding.getCategoryName(),
                        binding.getKeyDisplayName())
                    .getString());
          }
        }
      }
    }

    listLines = Collections.unmodifiableList(lines);
    scrollOffset = 0;
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
    groupToggleButton.setMessage(
        Text.translatable(
            groupMode == KeysetConflictGroupMode.BY_KEY
                ? "keyset.group.by_key"
                : "keyset.group.by_category"));
  }

  private void setStatus(String message, boolean error) {
    statusMessage = message;
    errorStatus = error;
  }

  private void runAction(Action action) {
    try {
      action.run();
    } catch (IllegalArgumentException | IllegalStateException | IOException exception) {
      previewPlan = null;
      rebuildList();
      setStatus(exception.getMessage(), true);
    }
  }

  private String nonBlankOrFallback(String value, String fallback) {
    String normalized = value == null ? "" : value.trim();
    return normalized.isEmpty() ? fallback : normalized;
  }

  private interface Action {
    void run() throws IOException;
  }
}
