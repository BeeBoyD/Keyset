package net.beeboyd.keyset.platform.fabric.screen;

import static net.beeboyd.keyset.platform.fabric.KeysetTextCompat.empty;
import static net.beeboyd.keyset.platform.fabric.KeysetTextCompat.literal;
import static net.beeboyd.keyset.platform.fabric.KeysetTextCompat.translatable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.beeboyd.keyset.core.binding.KeysetBindingDescriptor;
import net.beeboyd.keyset.core.conflict.KeysetConflictGroup;
import net.beeboyd.keyset.core.conflict.KeysetConflictGroupMode;
import net.beeboyd.keyset.core.conflict.KeysetConflictQuery;
import net.beeboyd.keyset.core.conflict.KeysetConflictReport;
import net.beeboyd.keyset.core.profile.KeysetProfiles;
import net.beeboyd.keyset.core.profile.KeysetProfilesConfig;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService.AutoResolvePlan;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService.ImportResult;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService.UndoState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/** Main Keyset screen for the representative Fabric target. */
public final class KeysetScreen extends Screen {
  private static final int PANEL_PADDING = 12;
  private static final int PANEL_GAP = 8;
  private static final int ROW_GAP = 4;
  private static final int BUTTON_HEIGHT = 20;
  private static final int STATUS_SUCCESS_COLOR = 0x8FD98F;
  private static final int STATUS_ERROR_COLOR = 0xFF9A8A;
  private static final int PANEL_FILL = 0xA0141820;
  private static final int PANEL_BORDER = 0xCC455364;
  private static final int CARD_FILL = 0x99222933;
  private static final int CARD_BORDER = 0xCC59677A;
  private static final int CHIP_FILL = 0xAA24303C;
  private static final int CHIP_BORDER = 0xFF7C8EA3;
  private static final int CHIP_ACTIVE_FILL = 0xAA314622;
  private static final int CHIP_ACTIVE_BORDER = 0xFF90C06E;
  private static final int SECTION_TITLE_COLOR = 0xE8D7A0;
  private static final int BODY_COLOR = 0xD8DEE7;
  private static final int MUTED_COLOR = 0xA9B8C9;

  private final Screen parent;
  private final KeysetFabricService service;

  private int sidebarX;
  private int sidebarY;
  private int sidebarWidth;
  private int sidebarInnerX;
  private int sidebarInnerWidth;
  private int mainX;
  private int mainY;
  private int mainWidth;
  private int mainInnerX;
  private int mainInnerWidth;
  private int detailY;
  private int detailHeight;
  private int listTop;
  private int listBottom;
  private int footerY;
  private boolean compactLayout;

  private TextFieldWidget searchField;
  private TextFieldWidget profileNameField;
  private KeysetConflictListWidget conflictListWidget;
  private ButtonWidget groupToggleButton;
  private ButtonWidget applyButton;
  private ButtonWidget captureButton;
  private ButtonWidget deleteButton;
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
  private Text emptyStateTitle = empty();
  private Text emptyStateBody = empty();
  private final List<WidgetTooltip> widgetTooltips = new ArrayList<WidgetTooltip>();

  public KeysetScreen(Screen parent, KeysetFabricService service) {
    super(translatable("keyset.title"));
    this.parent = parent;
    this.service = service;
  }

  @Override
  protected void init() {
    widgetTooltips.clear();
    computeLayout();

    int halfSidebarButtonWidth = (sidebarInnerWidth - ROW_GAP) / 2;
    int searchWidth = mainInnerWidth - 112;
    int sidebarFieldY = sidebarY + 20;
    int sidebarRow1Y = sidebarY + 44;
    int sidebarRow2Y = sidebarY + 66;
    int sidebarRow3Y = sidebarY + 88;
    int sidebarRow4Y = sidebarY + 110;
    int sidebarRow5Y = sidebarY + 132;
    int sidebarRow6Y = sidebarY + 154;

    searchField =
        addButton(
            new TextFieldWidget(
                textRenderer,
                mainInnerX,
                mainY + 24,
                searchWidth,
                BUTTON_HEIGHT,
                translatable("keyset.search")));
    searchField.setMaxLength(80);
    searchField.setSuggestion(translatable("keyset.search.placeholder").getString());
    searchField.setChangedListener(
        value -> {
          previewPlan = null;
          rebuildList(selectedBindingId());
        });
    setTooltip(searchField, "keyset.tip.search");

    groupToggleButton =
        addButton(
            new ButtonWidget(
                mainInnerX + searchWidth + ROW_GAP,
                mainY + 24,
                108,
                BUTTON_HEIGHT,
                translatable("keyset.group.by_key"),
                button -> toggleGroupMode()));
    setTooltip(groupToggleButton, "keyset.tip.group");

    profileNameField =
        addButton(
            new TextFieldWidget(
                textRenderer,
                sidebarInnerX,
                sidebarFieldY,
                sidebarInnerWidth,
                BUTTON_HEIGHT,
                translatable("keyset.profile.name")));
    profileNameField.setMaxLength(40);
    setTooltip(profileNameField, "keyset.tip.profile_name");

    addButton(
        button(
            "keyset.profile.prev",
            sidebarInnerX,
            sidebarRow1Y,
            halfSidebarButtonWidth,
            button -> selectRelative(-1),
            "keyset.tip.profile_prev"));
    addButton(
        button(
            "keyset.profile.next",
            sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
            sidebarRow1Y,
            halfSidebarButtonWidth,
            button -> selectRelative(1),
            "keyset.tip.profile_next"));

    applyButton =
        addButton(
            button(
                "keyset.profile.apply",
                sidebarInnerX,
                sidebarRow2Y,
                halfSidebarButtonWidth,
                button -> applySelected(),
                "keyset.tip.profile_apply"));
    captureButton =
        addButton(
            button(
                "keyset.profile.capture",
                sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
                sidebarRow2Y,
                halfSidebarButtonWidth,
                button -> captureCurrent(),
                "keyset.tip.profile_capture"));

    addButton(
        button(
            "keyset.profile.rename",
            sidebarInnerX,
            sidebarRow3Y,
            halfSidebarButtonWidth,
            button -> renameSelected(),
            "keyset.tip.profile_rename"));
    addButton(
        button(
            "keyset.profile.new",
            sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
            sidebarRow3Y,
            halfSidebarButtonWidth,
            button -> createProfile(),
            "keyset.tip.profile_new"));

    addButton(
        button(
            "keyset.profile.duplicate",
            sidebarInnerX,
            sidebarRow4Y,
            halfSidebarButtonWidth,
            button -> duplicateSelected(),
            "keyset.tip.profile_duplicate"));
    deleteButton =
        addButton(
            button(
                "keyset.profile.delete",
                sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
                sidebarRow4Y,
                halfSidebarButtonWidth,
                button -> deleteSelected(),
                "keyset.tip.profile_delete"));

    addButton(
        button(
            "keyset.export",
            sidebarInnerX,
            sidebarRow5Y,
            halfSidebarButtonWidth,
            button -> exportSelected(),
            "keyset.tip.export"));
    addButton(
        button(
            "keyset.import",
            sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
            sidebarRow5Y,
            halfSidebarButtonWidth,
            button -> importProfiles(),
            "keyset.tip.import"));

    previewResolveButton =
        addButton(
            button(
                "keyset.resolve.preview",
                sidebarInnerX,
                sidebarRow6Y,
                halfSidebarButtonWidth,
                button -> previewResolve(),
                "keyset.tip.resolve_preview"));
    applyPreviewButton =
        addButton(
            button(
                "keyset.resolve.apply",
                sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
                sidebarRow6Y,
                halfSidebarButtonWidth,
                button -> applyPreview(),
                "keyset.tip.resolve_apply"));
    undoButton =
        addButton(
            button(
                "keyset.resolve.undo",
                width - PANEL_PADDING - 200,
                footerY,
                108,
                button -> undoResolve(),
                "keyset.tip.resolve_undo"));

    int detailButtonWidth = (mainInnerWidth - ROW_GAP * 2) / 3;
    jumpButton =
        addButton(
            button(
                "keyset.binding.jump",
                mainInnerX,
                detailY + detailHeight - 26,
                detailButtonWidth,
                button -> jumpToBinding(),
                "keyset.tip.binding_jump"));
    clearBindingButton =
        addButton(
            button(
                "keyset.binding.clear",
                mainInnerX + detailButtonWidth + ROW_GAP,
                detailY + detailHeight - 26,
                detailButtonWidth,
                button -> clearSelectedBinding(),
                "keyset.tip.binding_clear"));
    reassignButton =
        addButton(
            button(
                "keyset.binding.reassign",
                mainInnerX + (detailButtonWidth + ROW_GAP) * 2,
                detailY + detailHeight - 26,
                detailButtonWidth,
                button -> reassignSelectedBinding(),
                "keyset.tip.binding_reassign"));

    addButton(
        button(
            "gui.done",
            width - PANEL_PADDING - 88,
            footerY,
            88,
            button -> onClose(),
            "keyset.tip.done"));

    conflictListWidget =
        new KeysetConflictListWidget(
            client,
            mainInnerWidth,
            listTop,
            listBottom,
            bindingDescriptor -> {
              selectedBinding = bindingDescriptor;
              refreshButtons();
            });
    addChild(conflictListWidget);
    conflictListWidget.setLeftPos(mainInnerX);

    try {
      reloadState(null, null);
    } catch (IOException exception) {
      setStatus(exception.getMessage(), true);
    }

    setFocused(searchField);
    searchField.setTextFieldFocused(true);
  }

  @Override
  public void tick() {
    super.tick();
    searchField.tick();
    profileNameField.tick();
  }

  @Override
  public void onClose() {
    client.openScreen(parent);
  }

  @Override
  public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
    renderBackground(matrices);
    drawShell(matrices);
    conflictListWidget.render(matrices, mouseX, mouseY, delta);
    super.render(matrices, mouseX, mouseY, delta);
    renderHoveredTooltip(matrices, mouseX, mouseY);
  }

  private void computeLayout() {
    int availableWidth = width - PANEL_PADDING * 2;
    int proposedSidebarWidth = Math.max(182, Math.min(206, availableWidth / 3));
    int minimumMainWidth = 276;
    if (availableWidth - proposedSidebarWidth - PANEL_GAP < minimumMainWidth) {
      proposedSidebarWidth = Math.max(170, availableWidth - minimumMainWidth - PANEL_GAP);
    }

    compactLayout = height < 300;
    sidebarX = PANEL_PADDING;
    sidebarY = compactLayout ? 34 : 42;
    sidebarWidth = proposedSidebarWidth;
    sidebarInnerX = sidebarX + 10;
    sidebarInnerWidth = sidebarWidth - 20;

    mainX = sidebarX + sidebarWidth + PANEL_GAP;
    mainY = sidebarY;
    mainWidth = width - PANEL_PADDING - mainX;
    mainInnerX = mainX + 10;
    mainInnerWidth = mainWidth - 20;

    footerY = height - PANEL_PADDING - BUTTON_HEIGHT;
    listBottom = footerY - 8;
    detailY = mainY + (compactLayout ? 50 : 56);
    int preferredDetailHeight = compactLayout ? 60 : 96;
    int minDetailHeight = compactLayout ? 54 : 78;
    int minListHeight = compactLayout ? 52 : 88;
    int maxDetailHeight = Math.max(minDetailHeight, listBottom - detailY - minListHeight - 10);
    detailHeight = Math.min(preferredDetailHeight, maxDetailHeight);
    listTop = detailY + detailHeight + 10;
  }

  private ButtonWidget button(
      String messageKey,
      int x,
      int y,
      int width,
      ButtonWidget.PressAction action,
      String tooltipKey) {
    ButtonWidget widget =
        new ButtonWidget(x, y, width, BUTTON_HEIGHT, translatable(messageKey), action);
    setTooltip(widget, tooltipKey);
    return widget;
  }

  private void setTooltip(ClickableWidget widget, String translationKey) {
    widgetTooltips.add(new WidgetTooltip(widget, translatable(translationKey)));
  }

  private void drawShell(MatrixStack matrices) {
    drawFrame(matrices, sidebarX, sidebarY, sidebarWidth, footerY - sidebarY - 8);
    drawFrame(matrices, mainX, mainY, mainWidth, listBottom - mainY + 8);
    drawFrame(matrices, mainInnerX, detailY, mainInnerWidth, detailHeight);

    drawCenteredText(matrices, title, width / 2, 10, 0xFFFFFF);
    drawCenteredText(matrices, translatable("keyset.subtitle"), width / 2, 22, MUTED_COLOR);

    drawSidebar(matrices);
    drawMainPane(matrices);
    drawFooter(matrices);
  }

  private void drawSidebar(MatrixStack matrices) {
    drawSectionTitle(matrices, "keyset.section.profile", sidebarInnerX, sidebarY + 10);
    drawChip(
        matrices,
        sidebarInnerX + 92,
        sidebarY + 8,
        sidebarInnerWidth - 92,
        config != null
                && selectedProfileId != null
                && selectedProfileId.equals(config.getActiveProfileId())
            ? translatable("keyset.profile.state.active")
            : translatable("keyset.profile.state.stored"),
        config != null
            && selectedProfileId != null
            && selectedProfileId.equals(config.getActiveProfileId()));
    drawSectionTitle(matrices, "keyset.section.transfer", sidebarInnerX, sidebarY + 122);
    drawSectionTitle(matrices, "keyset.section.resolve", sidebarInnerX, sidebarY + 144);
    if (!compactLayout) {
      drawSectionTitle(matrices, "keyset.section.help", sidebarInnerX, sidebarY + 180);
      drawWrappedText(
          matrices,
          translatable("keyset.help.steps"),
          sidebarInnerX,
          sidebarY + 194,
          sidebarInnerWidth,
          BODY_COLOR);
      drawWrappedText(
          matrices,
          translatable("keyset.help.active_profile"),
          sidebarInnerX,
          sidebarY + 230,
          sidebarInnerWidth,
          MUTED_COLOR);
    }
  }

  private void renderHoveredTooltip(MatrixStack matrices, int mouseX, int mouseY) {
    for (WidgetTooltip widgetTooltip : widgetTooltips) {
      if (widgetTooltip.widget.visible && widgetTooltip.widget.isMouseOver(mouseX, mouseY)) {
        renderTooltip(matrices, widgetTooltip.text, mouseX, mouseY);
        return;
      }
    }
  }

  private void drawMainPane(MatrixStack matrices) {
    drawSectionTitle(matrices, "keyset.section.conflicts", mainInnerX, mainY + 10);
    drawSelectionPanel(matrices);
    if (conflictReport.isEmpty() && previewPlan == null) {
      drawEmptyState(matrices, listTop + 24);
      return;
    }
    if (previewPlan != null && previewPlan.getChanges().isEmpty()) {
      drawEmptyState(matrices, listTop + 24);
      return;
    }
    if (conflictListWidget.children().isEmpty()) {
      drawEmptyState(matrices, listTop + 24);
    }
  }

  private void drawSelectionPanel(MatrixStack matrices) {
    Text titleText;
    Text bodyText;
    Text chipText;
    boolean chipActive;

    if (previewPlan != null) {
      titleText = translatable("keyset.selection.preview_title");
      bodyText = translatable("keyset.selection.preview_body");
      chipText = translatable("keyset.selection.preview_chip");
      chipActive = false;
    } else if (selectedBinding == null) {
      titleText = translatable("keyset.selection.none_title");
      bodyText = translatable("keyset.selection.none_body");
      chipText = translatable("keyset.selection.none_chip");
      chipActive = false;
    } else {
      titleText = literal(selectedBinding.getDisplayName());
      bodyText =
          translatable(
              selectedProfileId != null
                      && config != null
                      && selectedProfileId.equals(config.getActiveProfileId())
                  ? "keyset.selection.binding_body_active"
                  : "keyset.selection.binding_body_inactive",
              selectedBinding.getCategoryName(),
              selectedBinding.getKeyDisplayName(),
              config == null ? "" : config.getProfile(config.getActiveProfileId()).getName());
      chipText =
          translatable(
              selectedProfileId != null
                      && config != null
                      && selectedProfileId.equals(config.getActiveProfileId())
                  ? "keyset.selection.active_chip"
                  : "keyset.selection.inactive_chip");
      chipActive =
          selectedProfileId != null
              && config != null
              && selectedProfileId.equals(config.getActiveProfileId());
    }

    drawTextWithShadow(matrices, textRenderer, titleText, mainInnerX + 10, detailY + 10, 0xF2F5F8);
    drawChip(matrices, mainInnerX + 10, detailY + 24, 126, chipText, chipActive);
    drawWrappedText(
        matrices, bodyText, mainInnerX + 10, detailY + 42, mainInnerWidth - 20, BODY_COLOR);
  }

  private void drawFooter(MatrixStack matrices) {
    String footerMessage =
        statusMessage.isEmpty()
            ? translatable(previewPlan == null ? "keyset.footer.default" : "keyset.footer.preview")
                .getString()
            : statusMessage;
    int color =
        statusMessage.isEmpty()
            ? MUTED_COLOR
            : errorStatus ? STATUS_ERROR_COLOR : STATUS_SUCCESS_COLOR;

    drawTextWithShadow(
        matrices, textRenderer, literal(footerMessage), PANEL_PADDING, footerY + 6, color);
  }

  private void drawEmptyState(MatrixStack matrices, int y) {
    drawCenteredText(matrices, emptyStateTitle, mainX + (mainWidth / 2), y, 0xF2F5F8);
    drawWrappedText(
        matrices, emptyStateBody, mainInnerX + 26, y + 14, mainInnerWidth - 52, MUTED_COLOR);
  }

  private void drawSectionTitle(MatrixStack matrices, String key, int x, int y) {
    drawTextWithShadow(matrices, textRenderer, translatable(key), x, y, SECTION_TITLE_COLOR);
  }

  private void drawFrame(MatrixStack matrices, int x, int y, int width, int height) {
    fill(matrices, x, y, x + width, y + height, PANEL_FILL);
    drawBorderBox(matrices, x, y, width, height, PANEL_BORDER);
  }

  private void drawChip(MatrixStack matrices, int x, int y, int width, Text text, boolean active) {
    fill(matrices, x, y, x + width, y + 14, active ? CHIP_ACTIVE_FILL : CHIP_FILL);
    drawBorderBox(matrices, x, y, width, 14, active ? CHIP_ACTIVE_BORDER : CHIP_BORDER);
    drawCenteredText(matrices, text, x + (width / 2), y + 3, 0xF2F5F8);
  }

  private void drawWrappedText(
      MatrixStack matrices, Text text, int x, int y, int maxWidth, int color) {
    int lineY = y;
    for (OrderedText line : textRenderer.wrapLines(text, maxWidth)) {
      textRenderer.drawWithShadow(matrices, line, x, lineY, color);
      lineY += textRenderer.fontHeight + 2;
    }
  }

  private void drawCenteredText(MatrixStack matrices, Text text, int centerX, int y, int color) {
    int textX = centerX - (textRenderer.getWidth(text) / 2);
    textRenderer.drawWithShadow(matrices, text, textX, y, color);
  }

  private void drawBorderBox(MatrixStack matrices, int x, int y, int width, int height, int color) {
    fill(matrices, x, y, x + width, y + 1, color);
    fill(matrices, x, y + height - 1, x + width, y + height, color);
    fill(matrices, x, y, x + 1, y + height, color);
    fill(matrices, x + width - 1, y, x + width, y + height, color);
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

    List<String> profileIds = new ArrayList<String>(config.getProfiles().keySet());
    if (profileIds.isEmpty()) {
      return;
    }

    int currentIndex = profileIds.indexOf(selectedProfileId);
    if (currentIndex < 0) {
      currentIndex = 0;
    }

    int nextIndex = (currentIndex + direction + profileIds.size()) % profileIds.size();
    runAction(
        () -> {
          selectedProfileId = profileIds.get(nextIndex);
          profileNameField.setText(config.getProfile(selectedProfileId).getName());
          previewPlan = null;
          reloadConflictReport();
          rebuildList(null);
          refreshButtons();
        });
  }

  private void createProfile() {
    runAction(
        () -> {
          String createdProfileId =
              service.createProfileFromCurrent(
                  client, nonBlankOrFallback(profileNameField.getText(), "Profile"));
          previewPlan = null;
          reloadState(createdProfileId, null);
          setStatus(translatable("keyset.status.profile_created").getString(), false);
        });
  }

  private void duplicateSelected() {
    runAction(
        () -> {
          String duplicateProfileId = service.duplicateProfile(client, selectedProfileId);
          previewPlan = null;
          reloadState(duplicateProfileId, null);
          setStatus(translatable("keyset.status.profile_duplicated").getString(), false);
        });
  }

  private void renameSelected() {
    runAction(
        () -> {
          service.renameProfile(client, selectedProfileId, profileNameField.getText());
          previewPlan = null;
          reloadState(selectedProfileId, selectedBindingId());
          setStatus(translatable("keyset.status.profile_renamed").getString(), false);
        });
  }

  private void deleteSelected() {
    runAction(
        () -> {
          String fallbackProfileId = service.deleteProfile(client, selectedProfileId);
          previewPlan = null;
          reloadState(fallbackProfileId, null);
          setStatus(translatable("keyset.status.profile_deleted").getString(), false);
        });
  }

  private void applySelected() {
    runAction(
        () -> {
          service.activateProfile(client, selectedProfileId);
          previewPlan = null;
          reloadState(selectedProfileId, selectedBindingId());
          setStatus(translatable("keyset.status.profile_applied").getString(), false);
        });
  }

  private void captureCurrent() {
    runAction(
        () -> {
          service.captureCurrentToProfile(client, selectedProfileId, true);
          previewPlan = null;
          reloadState(selectedProfileId, selectedBindingId());
          setStatus(translatable("keyset.status.profile_captured").getString(), false);
        });
  }

  private void exportSelected() {
    runAction(
        () -> {
          client.keyboard.setClipboard(service.exportProfileJson(client, selectedProfileId));
          setStatus(translatable("keyset.status.exported").getString(), false);
        });
  }

  private void importProfiles() {
    runAction(
        () -> {
          ImportResult result = service.importProfiles(client, client.keyboard.getClipboard());
          if (result.getImportedCount() == 0) {
            throw new IllegalArgumentException(
                translatable("keyset.error.import_empty").getString());
          }
          previewPlan = null;
          reloadState(result.getLastImportedProfileId(), null);
          setStatus(
              translatable("keyset.status.imported", Integer.valueOf(result.getImportedCount()))
                  .getString(),
              false);
        });
  }

  private void previewResolve() {
    runAction(
        () -> {
          requireActiveProfileSelection();
          previewPlan = service.previewAutoResolve(client);
          rebuildList(null);
          if (previewPlan.isEmpty()) {
            setStatus(translatable("keyset.status.resolve_none").getString(), false);
          } else {
            setStatus(translatable("keyset.status.resolve_preview").getString(), false);
          }
        });
  }

  private void applyPreview() {
    runAction(
        () -> {
          if (previewPlan == null || previewPlan.getChanges().isEmpty()) {
            throw new IllegalStateException(translatable("keyset.error.no_preview").getString());
          }
          undoState = service.applyAutoResolve(client, previewPlan);
          previewPlan = null;
          reloadState(config.getActiveProfileId(), null);
          setStatus(translatable("keyset.status.resolve_applied").getString(), false);
        });
  }

  private void jumpToBinding() {
    try {
      client.openScreen(
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
          setStatus(translatable("keyset.status.binding_cleared").getString(), false);
        });
  }

  private void reassignSelectedBinding() {
    try {
      client.openScreen(
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
            throw new IllegalStateException(translatable("keyset.error.no_undo").getString());
          }
          service.undoAutoResolve(client, undoState);
          undoState = null;
          previewPlan = null;
          reloadState(config.getActiveProfileId(), null);
          setStatus(translatable("keyset.status.resolve_undone").getString(), false);
        });
  }

  private void reloadState(String preferredSelectedProfileId, String preferredBindingId)
      throws IOException {
    config = service.getConfig(client);
    selectedProfileId =
        preferredSelectedProfileId != null && config.hasProfile(preferredSelectedProfileId)
            ? preferredSelectedProfileId
            : config.getActiveProfileId();
    profileNameField.setText(config.getProfile(selectedProfileId).getName());
    reloadConflictReport();
    rebuildList(preferredBindingId);
  }

  private void reloadConflictReport() throws IOException {
    if (selectedProfileId != null && config != null && config.hasProfile(selectedProfileId)) {
      conflictReport = service.buildConflictReport(client, selectedProfileId);
      return;
    }
    conflictReport = service.buildConflictReport(client);
  }

  private void rebuildList(String preferredBindingId) {
    if (conflictListWidget == null) {
      return;
    }

    if (previewPlan != null) {
      selectedBinding = null;
      conflictListWidget.showPreview(previewPlan);
      if (previewPlan.getChanges().isEmpty()) {
        emptyStateTitle = translatable("keyset.empty.preview_title");
        emptyStateBody = translatable("keyset.empty.preview_body");
      }
      refreshButtons();
      return;
    }

    List<KeysetConflictGroup> groups =
        conflictReport.query(new KeysetConflictQuery(groupMode, searchField.getText()));
    if (groups.isEmpty()) {
      selectedBinding = null;
      conflictListWidget.clearContents();
      emptyStateTitle = translatable("keyset.empty.conflicts_title");
      emptyStateBody = translatable("keyset.empty.conflicts_body");
      refreshButtons();
      return;
    }

    emptyStateTitle = empty();
    emptyStateBody = empty();
    conflictListWidget.showConflicts(groups, groupMode, preferredBindingId);
    refreshButtons();
  }

  private void refreshButtons() {
    if (config == null || selectedProfileId == null) {
      return;
    }

    boolean activeSelection = selectedProfileId.equals(config.getActiveProfileId());
    deleteButton.active = !KeysetProfiles.DEFAULT_PROFILE_ID.equals(selectedProfileId);
    applyButton.active = !activeSelection;
    captureButton.active = true;
    previewResolveButton.active = activeSelection && !conflictReport.isEmpty();
    applyPreviewButton.active = previewPlan != null && !previewPlan.getChanges().isEmpty();
    undoButton.active = undoState != null;

    boolean bindingActionsActive =
        selectedBinding != null && previewPlan == null && activeSelection;
    jumpButton.active = bindingActionsActive;
    clearBindingButton.active = bindingActionsActive;
    reassignButton.active = bindingActionsActive;

    groupToggleButton.setMessage(
        translatable(
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

  private void requireActiveProfileSelection() {
    if (config == null || !selectedProfileId.equals(config.getActiveProfileId())) {
      throw new IllegalStateException(
          translatable("keyset.error.actions_require_active").getString());
    }
  }

  private String requireSelectedBindingId() {
    if (selectedBinding == null) {
      throw new IllegalStateException(translatable("keyset.error.no_binding_selected").getString());
    }
    requireActiveProfileSelection();
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

  private static final class WidgetTooltip {
    private final ClickableWidget widget;
    private final Text text;

    private WidgetTooltip(ClickableWidget widget, Text text) {
      this.widget = widget;
      this.text = text;
    }
  }
}
