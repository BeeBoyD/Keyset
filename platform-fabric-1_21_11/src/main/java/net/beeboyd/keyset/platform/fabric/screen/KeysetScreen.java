package net.beeboyd.keyset.platform.fabric.screen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.beeboyd.keyset.core.binding.KeysetBindingDescriptor;
import net.beeboyd.keyset.core.conflict.KeysetConflict;
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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/** Main Keyset screen for the representative Fabric target. */
public final class KeysetScreen extends Screen {
  private static final int PANEL_PADDING = 12;
  private static final int PANEL_GAP = 8;
  private static final int ROW_GAP = 4;
  private static final int BUTTON_HEIGHT = 18;
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
  private static final int SCREEN_BACKDROP = 0xB010141A;

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
  private int panelBottom;
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
  private Text emptyStateTitle = Text.empty();
  private Text emptyStateBody = Text.empty();
  private int visibleConflictGroups;
  private int visibleConflictBindings;

  public KeysetScreen(Screen parent, KeysetFabricService service) {
    super(Text.translatable("keyset.title"));
    this.parent = parent;
    this.service = service;
  }

  @Override
  protected void init() {
    computeLayout();

    int halfSidebarButtonWidth = (sidebarInnerWidth - ROW_GAP) / 2;
    int searchWidth = mainInnerWidth - 84;
    int searchY = mainY + 24;
    int sidebarFieldY = sidebarY + 42;
    int sidebarRow1Y = sidebarY + 64;
    int sidebarRow2Y = sidebarRow1Y + BUTTON_HEIGHT + ROW_GAP;
    int sidebarRow3Y = sidebarRow2Y + BUTTON_HEIGHT + ROW_GAP;
    int sidebarRow4Y = sidebarRow3Y + BUTTON_HEIGHT + ROW_GAP;
    int sidebarRow5Y = sidebarRow4Y + BUTTON_HEIGHT + ROW_GAP;
    int footerButtonWidth = (width - (PANEL_PADDING * 2) - (ROW_GAP * 3)) / 4;
    int detailActionsY = detailActionY();

    searchField =
        addDrawableChild(
            new TextFieldWidget(
                textRenderer,
                mainInnerX,
                searchY,
                searchWidth,
                BUTTON_HEIGHT,
                Text.translatable("keyset.search")));
    searchField.setMaxLength(80);
    searchField.setPlaceholder(Text.translatable("keyset.search.placeholder"));
    searchField.setChangedListener(
        value -> {
          previewPlan = null;
          rebuildList(selectedBindingId());
        });
    setTooltip(searchField, "keyset.tip.search");

    groupToggleButton =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.group.by_key"), button -> toggleGroupMode())
                .dimensions(mainInnerX + searchWidth + ROW_GAP, searchY, 80, BUTTON_HEIGHT)
                .build());
    setTooltip(groupToggleButton, "keyset.tip.group");

    profileNameField =
        addDrawableChild(
            new TextFieldWidget(
                textRenderer,
                sidebarInnerX,
                sidebarFieldY,
                sidebarInnerWidth,
                BUTTON_HEIGHT,
                Text.translatable("keyset.profile.name")));
    profileNameField.setMaxLength(40);
    setTooltip(profileNameField, "keyset.tip.profile_name");

    addDrawableChild(
        button(
            "keyset.profile.prev",
            sidebarInnerX,
            sidebarRow1Y,
            halfSidebarButtonWidth,
            button -> selectRelative(-1),
            "keyset.tip.profile_prev"));
    addDrawableChild(
        button(
            "keyset.profile.next",
            sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
            sidebarRow1Y,
            halfSidebarButtonWidth,
            button -> selectRelative(1),
            "keyset.tip.profile_next"));

    applyButton =
        addDrawableChild(
            button(
                "keyset.profile.apply",
                sidebarInnerX,
                sidebarRow2Y,
                halfSidebarButtonWidth,
                button -> applySelected(),
                "keyset.tip.profile_apply"));
    captureButton =
        addDrawableChild(
            button(
                "keyset.profile.capture",
                sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
                sidebarRow2Y,
                halfSidebarButtonWidth,
                button -> captureCurrent(),
                "keyset.tip.profile_capture"));

    addDrawableChild(
        button(
            "keyset.profile.rename",
            sidebarInnerX,
            sidebarRow3Y,
            halfSidebarButtonWidth,
            button -> renameSelected(),
            "keyset.tip.profile_rename"));
    addDrawableChild(
        button(
            "keyset.profile.new",
            sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
            sidebarRow3Y,
            halfSidebarButtonWidth,
            button -> createProfile(),
            "keyset.tip.profile_new"));

    addDrawableChild(
        button(
            "keyset.profile.duplicate",
            sidebarInnerX,
            sidebarRow4Y,
            halfSidebarButtonWidth,
            button -> duplicateSelected(),
            "keyset.tip.profile_duplicate"));
    deleteButton =
        addDrawableChild(
            button(
                "keyset.profile.delete",
                sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
                sidebarRow4Y,
                halfSidebarButtonWidth,
                button -> deleteSelected(),
                "keyset.tip.profile_delete"));

    addDrawableChild(
        button(
            "keyset.export",
            sidebarInnerX,
            sidebarRow5Y,
            halfSidebarButtonWidth,
            button -> exportSelected(),
            "keyset.tip.export"));
    addDrawableChild(
        button(
            "keyset.import",
            sidebarInnerX + halfSidebarButtonWidth + ROW_GAP,
            sidebarRow5Y,
            halfSidebarButtonWidth,
            button -> importProfiles(),
            "keyset.tip.import"));

    previewResolveButton =
        addDrawableChild(
            button(
                "keyset.resolve.preview",
                PANEL_PADDING,
                footerY,
                footerButtonWidth,
                button -> previewResolve(),
                "keyset.tip.resolve_preview"));
    applyPreviewButton =
        addDrawableChild(
            button(
                "keyset.resolve.apply",
                PANEL_PADDING + footerButtonWidth + ROW_GAP,
                footerY,
                footerButtonWidth,
                button -> applyPreview(),
                "keyset.tip.resolve_apply"));
    undoButton =
        addDrawableChild(
            button(
                "keyset.resolve.undo",
                PANEL_PADDING + (footerButtonWidth + ROW_GAP) * 2,
                footerY,
                footerButtonWidth,
                button -> undoResolve(),
                "keyset.tip.resolve_undo"));

    int detailButtonWidth = (mainInnerWidth - ROW_GAP * 2) / 3;
    jumpButton =
        addDrawableChild(
            button(
                "keyset.binding.jump",
                mainInnerX,
                detailActionsY,
                detailButtonWidth,
                button -> jumpToBinding(),
                "keyset.tip.binding_jump"));
    clearBindingButton =
        addDrawableChild(
            button(
                "keyset.binding.clear",
                mainInnerX + detailButtonWidth + ROW_GAP,
                detailActionsY,
                detailButtonWidth,
                button -> clearSelectedBinding(),
                "keyset.tip.binding_clear"));
    reassignButton =
        addDrawableChild(
            button(
                "keyset.binding.reassign",
                mainInnerX + (detailButtonWidth + ROW_GAP) * 2,
                detailActionsY,
                detailButtonWidth,
                button -> reassignSelectedBinding(),
                "keyset.tip.binding_reassign"));

    addDrawableChild(
        button(
            "gui.done",
            PANEL_PADDING + (footerButtonWidth + ROW_GAP) * 3,
            footerY,
            footerButtonWidth,
            button -> close(),
            "keyset.tip.done"));

    conflictListWidget =
        addDrawableChild(
            new KeysetConflictListWidget(
                client,
                mainInnerWidth,
                listTop,
                listBottom,
                bindingDescriptor -> {
                  selectedBinding = bindingDescriptor;
                  refreshButtons();
                }));
    conflictListWidget.setX(mainInnerX);

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
  }

  @Override
  public void close() {
    client.setScreen(parent);
  }

  @Override
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    drawBackdrop(context);
    drawShell(context);
    super.render(context, mouseX, mouseY, delta);
    drawForeground(context);
  }

  private void computeLayout() {
    int availableWidth = width - PANEL_PADDING * 2;
    int proposedSidebarWidth = Math.max(176, Math.min(208, availableWidth / 3));
    int minimumMainWidth = 230;
    if (availableWidth - proposedSidebarWidth - PANEL_GAP < minimumMainWidth) {
      proposedSidebarWidth = Math.max(168, availableWidth - minimumMainWidth - PANEL_GAP);
    }

    compactLayout = height < 260;
    sidebarX = PANEL_PADDING;
    sidebarY = compactLayout ? 28 : 30;
    sidebarWidth = proposedSidebarWidth;
    sidebarInnerX = sidebarX + 10;
    sidebarInnerWidth = sidebarWidth - 20;

    mainX = sidebarX + sidebarWidth + PANEL_GAP;
    mainY = sidebarY;
    mainWidth = width - PANEL_PADDING - mainX;
    mainInnerX = mainX + 10;
    mainInnerWidth = mainWidth - 20;

    footerY = height - PANEL_PADDING - BUTTON_HEIGHT;
    panelBottom = footerY - 6;
    detailHeight = compactLayout ? 64 : 72;
    detailY = panelBottom - detailHeight - 8;
    listTop = mainY + 52;
    listBottom = detailY - 8;
  }

  private int detailActionY() {
    return detailY + detailHeight - BUTTON_HEIGHT - 6;
  }

  private ButtonWidget button(
      String messageKey,
      int x,
      int y,
      int width,
      ButtonWidget.PressAction action,
      String tooltipKey) {
    ButtonWidget widget =
        ButtonWidget.builder(Text.translatable(messageKey), action)
            .dimensions(x, y, width, BUTTON_HEIGHT)
            .build();
    setTooltip(widget, tooltipKey);
    return widget;
  }

  private void setTooltip(ClickableWidget widget, String translationKey) {
    setTooltip(widget, Text.translatable(translationKey));
  }

  private void setTooltip(ClickableWidget widget, Text tooltipText) {
    widget.setTooltip(Tooltip.of(tooltipText));
  }

  private void drawBackdrop(DrawContext context) {
    context.fill(0, 0, width, height, SCREEN_BACKDROP);
  }

  private void drawShell(DrawContext context) {
    drawFrame(context, sidebarX, sidebarY, sidebarWidth, panelBottom - sidebarY);
    drawFrame(context, mainX, mainY, mainWidth, panelBottom - mainY);
    drawFrame(context, mainInnerX, listTop - 4, mainInnerWidth, listBottom - listTop + 8);
    drawFrame(context, mainInnerX, detailY, mainInnerWidth, detailHeight);
  }

  private void drawForeground(DrawContext context) {
    context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
    drawSidebar(context);
    drawMainPane(context);
    drawFooter(context);
  }

  private void drawSidebar(DrawContext context) {
    drawSectionTitle(context, "keyset.section.profile", sidebarInnerX, sidebarY + 10);
    drawChip(
        context,
        sidebarInnerX,
        sidebarY + 22,
        sidebarInnerWidth,
        config != null
                && selectedProfileId != null
                && selectedProfileId.equals(config.getActiveProfileId())
            ? Text.translatable("keyset.profile.state.active")
            : Text.translatable("keyset.profile.state.stored"),
        config != null
            && selectedProfileId != null
            && selectedProfileId.equals(config.getActiveProfileId()));
  }

  private void drawMainPane(DrawContext context) {
    drawSectionTitle(context, "keyset.section.conflicts", mainInnerX, mainY + 10);
    drawMainSummary(context);
    drawSelectionPanel(context);
    if (conflictReport.isEmpty() && previewPlan == null) {
      drawEmptyState(context, listTop + 24);
      return;
    }
    if (previewPlan != null && previewPlan.getChanges().isEmpty()) {
      drawEmptyState(context, listTop + 24);
      return;
    }
    if (conflictListWidget.children().isEmpty()) {
      drawEmptyState(context, listTop + 24);
    }
  }

  private void drawMainSummary(DrawContext context) {
    if (compactLayout) {
      return;
    }

    Text summaryText = buildHeaderSummaryText();
    if (summaryText == null || summaryText.getString().isEmpty()) {
      return;
    }

    int summaryMaxWidth = mainX + mainWidth - 10 - (mainInnerX + 96);
    if (summaryMaxWidth < 40) {
      return;
    }

    String summary = ellipsize(summaryText.getString(), summaryMaxWidth);
    int summaryX = mainX + mainWidth - 10 - textRenderer.getWidth(summary);
    context.drawTextWithShadow(
        textRenderer, Text.literal(summary), summaryX, mainY + 10, MUTED_COLOR);
  }

  private Text buildHeaderSummaryText() {
    if (previewPlan != null) {
      return Text.translatable(
          "keyset.resolve.summary",
          Integer.valueOf(previewPlan.getChanges().size()),
          Integer.valueOf(previewPlan.getUnresolvedBindings()));
    }
    if (config == null
        || selectedProfileId == null
        || !config.hasProfile(selectedProfileId)
        || visibleConflictBindings <= 0) {
      return Text.empty();
    }
    return Text.translatable(
        "keyset.summary.selected",
        config.getProfile(selectedProfileId).getName(),
        Integer.valueOf(visibleConflictBindings));
  }

  private void drawSelectionPanel(DrawContext context) {
    Text titleText;
    Text bodyText;

    if (previewPlan != null) {
      titleText = Text.translatable("keyset.selection.preview_title");
      bodyText = Text.translatable("keyset.selection.preview_body");
    } else if (selectedBinding == null) {
      titleText = Text.translatable("keyset.selection.none_title");
      bodyText = Text.translatable("keyset.selection.none_body");
    } else {
      titleText = Text.literal(selectedBinding.getDisplayName());
      boolean activeSelection =
          selectedProfileId != null
              && config != null
              && selectedProfileId.equals(config.getActiveProfileId());
      bodyText =
          activeSelection
              ? Text.translatable(
                  "keyset.selection.binding_body_active",
                  selectedBinding.getCategoryName(),
                  selectedBinding.getKeyDisplayName())
              : Text.translatable(
                  "keyset.selection.binding_body_inactive",
                  config == null || selectedProfileId == null
                      ? ""
                      : config.getProfile(selectedProfileId).getName());
    }

    int textX = mainInnerX + 8;
    int textWidth = mainInnerWidth - 16;
    int bodyY = detailY + 20;
    drawTrimmedText(context, titleText, textX, detailY + 6, textWidth, 0xF2F5F8);
    drawTrimmedText(context, bodyText, textX, bodyY, textWidth, BODY_COLOR);
  }

  private void drawFooter(DrawContext context) {
    if (statusMessage.isEmpty()) {
      return;
    }

    context.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal(ellipsize(statusMessage, width - (PANEL_PADDING * 2))),
        width / 2,
        22,
        errorStatus ? STATUS_ERROR_COLOR : STATUS_SUCCESS_COLOR);
  }

  private String buildDefaultFooterMessage() {
    if (previewPlan != null) {
      return Text.translatable("keyset.footer.preview").getString();
    }
    if (config == null || selectedProfileId == null || !config.hasProfile(selectedProfileId)) {
      return Text.translatable("keyset.footer.default").getString();
    }
    if (!selectedProfileId.equals(config.getActiveProfileId())) {
      return Text.translatable(
              "keyset.footer.inactive", config.getProfile(selectedProfileId).getName())
          .getString();
    }
    if (selectedBinding == null) {
      return Text.translatable("keyset.footer.pick_conflict").getString();
    }
    return Text.translatable("keyset.footer.binding_ready", selectedBinding.getDisplayName())
        .getString();
  }

  private void drawEmptyState(DrawContext context, int y) {
    context.drawCenteredTextWithShadow(
        textRenderer, emptyStateTitle, mainInnerX + (mainInnerWidth / 2), y, 0xF2F5F8);
    context.drawWrappedTextWithShadow(
        textRenderer, emptyStateBody, mainInnerX + 20, y + 14, mainInnerWidth - 40, MUTED_COLOR);
  }

  private void drawSectionTitle(DrawContext context, String key, int x, int y) {
    context.drawTextWithShadow(textRenderer, Text.translatable(key), x, y, SECTION_TITLE_COLOR);
  }

  private void drawFrame(DrawContext context, int x, int y, int width, int height) {
    context.fill(x, y, x + width, y + height, PANEL_FILL);
    context.drawStrokedRectangle(x, y, width, height, PANEL_BORDER);
  }

  private void drawChip(DrawContext context, int x, int y, int width, Text text, boolean active) {
    context.fill(x, y, x + width, y + 14, active ? CHIP_ACTIVE_FILL : CHIP_FILL);
    context.drawStrokedRectangle(x, y, width, 14, active ? CHIP_ACTIVE_BORDER : CHIP_BORDER);
    context.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal(ellipsize(text.getString(), Math.max(24, width - 8))),
        x + (width / 2),
        y + 3,
        0xF2F5F8);
  }

  private void drawTrimmedText(
      DrawContext context, Text text, int x, int y, int maxWidth, int color) {
    context.drawTextWithShadow(
        textRenderer, Text.literal(ellipsize(text.getString(), maxWidth)), x, y, color);
  }

  private void drawWrappedTextBlock(
      DrawContext context, Text text, int x, int y, int maxWidth, int maxHeight, int color) {
    int lineHeight = textRenderer.fontHeight + 2;
    int maxLines = Math.max(1, maxHeight / lineHeight);
    int lineY = y;
    int lineCount = 0;
    for (OrderedText line : textRenderer.wrapLines(text, maxWidth)) {
      if (lineCount >= maxLines) {
        break;
      }
      context.drawTextWithShadow(textRenderer, line, x, lineY, color);
      lineY += lineHeight;
      lineCount++;
    }
  }

  private String ellipsize(String value, int maxWidth) {
    if (value == null || textRenderer.getWidth(value) <= maxWidth) {
      return value == null ? "" : value;
    }

    String ellipsis = "...";
    String candidate = value;
    while (!candidate.isEmpty() && textRenderer.getWidth(candidate + ellipsis) > maxWidth) {
      candidate = candidate.substring(0, candidate.length() - 1);
    }
    return candidate + ellipsis;
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
          requireActiveProfileSelection();
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
      visibleConflictGroups = previewPlan.getChanges().isEmpty() ? 0 : 1;
      visibleConflictBindings = previewPlan.getChanges().size();
      selectedBinding = null;
      conflictListWidget.showPreview(previewPlan);
      if (previewPlan.getChanges().isEmpty()) {
        emptyStateTitle = Text.translatable("keyset.empty.preview_title");
        emptyStateBody = Text.translatable("keyset.empty.preview_body");
      }
      refreshButtons();
      return;
    }

    List<KeysetConflictGroup> groups =
        conflictReport.query(new KeysetConflictQuery(groupMode, searchField.getText()));
    if (groups.isEmpty()) {
      visibleConflictGroups = 0;
      visibleConflictBindings = 0;
      selectedBinding = null;
      conflictListWidget.clearContents();
      emptyStateTitle = Text.translatable("keyset.empty.conflicts_title");
      emptyStateBody = Text.translatable("keyset.empty.conflicts_body");
      refreshButtons();
      return;
    }

    visibleConflictGroups = groups.size();
    visibleConflictBindings = countVisibleBindings(groups);
    emptyStateTitle = Text.empty();
    emptyStateBody = Text.empty();
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
    updateDynamicTooltips(activeSelection, bindingActionsActive);

    groupToggleButton.setMessage(
        Text.translatable(
            groupMode == KeysetConflictGroupMode.BY_KEY
                ? "keyset.group.by_key"
                : "keyset.group.by_category"));
  }

  private void updateDynamicTooltips(boolean activeSelection, boolean bindingActionsActive) {
    setTooltip(
        applyButton,
        Text.translatable(
            activeSelection ? "keyset.tip.profile_apply_active" : "keyset.tip.profile_apply"));
    setTooltip(
        previewResolveButton,
        Text.translatable(
            !activeSelection
                ? "keyset.tip.actions_require_active"
                : conflictReport.isEmpty()
                    ? "keyset.tip.resolve_preview_empty"
                    : "keyset.tip.resolve_preview"));
    setTooltip(
        applyPreviewButton,
        Text.translatable(
            previewPlan != null && !previewPlan.getChanges().isEmpty()
                ? "keyset.tip.resolve_apply"
                : "keyset.tip.resolve_apply_pending"));
    setTooltip(
        undoButton,
        Text.translatable(
            undoState != null ? "keyset.tip.resolve_undo" : "keyset.tip.resolve_undo_unavailable"));

    String inactiveBindingTooltipKey = inactiveBindingTooltipKey(activeSelection);
    setTooltip(
        jumpButton,
        Text.translatable(
            bindingActionsActive ? "keyset.tip.binding_jump" : inactiveBindingTooltipKey));
    setTooltip(
        clearBindingButton,
        Text.translatable(
            bindingActionsActive ? "keyset.tip.binding_clear" : inactiveBindingTooltipKey));
    setTooltip(
        reassignButton,
        Text.translatable(
            bindingActionsActive ? "keyset.tip.binding_reassign" : inactiveBindingTooltipKey));
  }

  private String inactiveBindingTooltipKey(boolean activeSelection) {
    if (previewPlan != null) {
      return "keyset.tip.binding_preview_mode";
    }
    if (selectedBinding == null) {
      return "keyset.tip.binding_requires_selection";
    }
    if (!activeSelection) {
      return "keyset.tip.actions_require_active";
    }
    return "keyset.tip.binding_requires_selection";
  }

  private static int countVisibleBindings(List<KeysetConflictGroup> groups) {
    int bindingCount = 0;
    for (KeysetConflictGroup group : groups) {
      for (KeysetConflict conflict : group.getConflicts()) {
        bindingCount += conflict.getBindings().size();
      }
    }
    return bindingCount;
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
          Text.translatable("keyset.error.actions_require_active").getString());
    }
  }

  private String requireSelectedBindingId() {
    if (selectedBinding == null) {
      throw new IllegalStateException(
          Text.translatable("keyset.error.no_binding_selected").getString());
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
}
