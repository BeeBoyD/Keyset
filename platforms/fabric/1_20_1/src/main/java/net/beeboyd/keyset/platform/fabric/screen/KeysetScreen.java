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
  private static final int SCREEN_PADDING = 12;
  private static final int PANEL_GAP = 10;
  private static final int CARD_GAP = 8;
  private static final int ROW_GAP = 4;
  private static final int CARD_PADDING = 10;
  private static final int BUTTON_HEIGHT = 18;
  private static final int HEADER_HEIGHT = 56;

  private static final int SCREEN_BACKDROP = 0xE20B1015;
  private static final int BACKDROP_BAND = 0x191C2B3A;
  private static final int PANEL_SHADOW = 0x50000000;
  private static final int HEADER_FILL = 0xD11A232D;
  private static final int HEADER_BORDER = 0xFF4C677A;
  private static final int PANEL_FILL = 0xCC161E28;
  private static final int PANEL_BORDER = 0xFF465B6D;
  private static final int CHIP_FILL = 0xB2263340;
  private static final int CHIP_BORDER = 0xFF73889C;
  private static final int CHIP_ACTIVE_FILL = 0xB12C4428;
  private static final int CHIP_ACTIVE_BORDER = 0xFFA1D67E;
  private static final int CHIP_ACCENT_FILL = 0xB1273948;
  private static final int CHIP_ACCENT_BORDER = 0xFF86CDE8;
  private static final int STATUS_SUCCESS_COLOR = 0xFF9EE6A1;
  private static final int STATUS_ERROR_COLOR = 0xFFFFA58F;
  private static final int TITLE_COLOR = 0xFFF4F8FC;
  private static final int LABEL_COLOR = 0xFFF1D690;
  private static final int BODY_COLOR = 0xFFD8E3EE;
  private static final int MUTED_COLOR = 0xFFA3B6C8;

  private final Screen parent;
  private final KeysetFabricService service;

  private int headerX;
  private int headerY;
  private int headerWidth;
  private int headerHeight;
  private int compactTabsX;
  private int compactTabsY;
  private int compactTabsWidth;
  private int sidebarX;
  private int sidebarY;
  private int sidebarWidth;
  private int profileCardX;
  private int profileCardY;
  private int profileCardWidth;
  private int profileCardHeight;
  private int helpCardX;
  private int helpCardY;
  private int helpCardWidth;
  private int helpCardHeight;
  private int mainX;
  private int mainY;
  private int mainWidth;
  private int filterCardX;
  private int filterCardY;
  private int filterCardWidth;
  private int filterCardHeight;
  private int listCardX;
  private int listCardY;
  private int listCardWidth;
  private int listCardHeight;
  private int selectionCardX;
  private int selectionCardY;
  private int selectionCardWidth;
  private int selectionCardHeight;
  private int resolveCardX;
  private int resolveCardY;
  private int resolveCardWidth;
  private int resolveCardHeight;
  private int listTop;
  private int listBottom;
  private int footerY;
  private int doneButtonWidth;
  private boolean compactLayout;
  private boolean pagedLayout;
  private boolean microLayout;
  private boolean stackHeaderMetrics;
  private boolean stackActionCards;
  private boolean stackFilterControls;
  private boolean showNavigatorSummary;
  private boolean denseLayout;
  private CompactPage compactPage = CompactPage.PROFILES;

  private TextFieldWidget searchField;
  private TextFieldWidget profileNameField;
  private KeysetConflictListWidget conflictListWidget;
  private ButtonWidget profilesTabButton;
  private ButtonWidget navigatorTabButton;
  private ButtonWidget fixesTabButton;
  private ButtonWidget previousProfileButton;
  private ButtonWidget nextProfileButton;
  private ButtonWidget groupToggleButton;
  private ButtonWidget applyButton;
  private ButtonWidget captureButton;
  private ButtonWidget renameButton;
  private ButtonWidget createButton;
  private ButtonWidget duplicateButton;
  private ButtonWidget deleteButton;
  private ButtonWidget exportButton;
  private ButtonWidget importButton;
  private ButtonWidget previewResolveButton;
  private ButtonWidget applyPreviewButton;
  private ButtonWidget jumpButton;
  private ButtonWidget clearBindingButton;
  private ButtonWidget reassignButton;
  private ButtonWidget undoButton;
  private ButtonWidget doneButton;

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

    int profileInnerX = profileCardX + CARD_PADDING;
    int profileInnerWidth = profileCardWidth - (CARD_PADDING * 2);
    int profileButtonGap = pagedLayout ? 2 : ROW_GAP;
    int profileFieldY = pagedLayout ? profileCardY + 24 : profileCardY + 68;
    int profileRowY = profileFieldY + BUTTON_HEIGHT + (pagedLayout ? 4 : 8);
    int profileThreeColumnWidth = (profileInnerWidth - (profileButtonGap * 2)) / 3;
    int profileTwoColumnWidth = (profileInnerWidth - profileButtonGap) / 2;
    boolean useThreeColumnProfileGrid = pagedLayout && profileInnerWidth >= 210;

    int filterInnerX = filterCardX + CARD_PADDING;
    int filterInnerWidth = filterCardWidth - (CARD_PADDING * 2);
    int searchY = filterCardY + (pagedLayout ? 18 : 30);
    int groupToggleWidth =
        pagedLayout ? (stackFilterControls ? Math.min(120, filterInnerWidth) : 88) : 104;
    int searchWidth =
        stackFilterControls ? filterInnerWidth : filterInnerWidth - groupToggleWidth - ROW_GAP;
    int groupToggleX = stackFilterControls ? filterInnerX : filterInnerX + searchWidth + ROW_GAP;
    int groupToggleY =
        stackFilterControls ? searchY + BUTTON_HEIGHT + (pagedLayout ? 2 : ROW_GAP) : searchY;

    int selectionInnerX = selectionCardX + CARD_PADDING;
    int selectionInnerWidth = selectionCardWidth - (CARD_PADDING * 2);
    int detailActionsY =
        selectionCardY + selectionCardHeight - BUTTON_HEIGHT - (pagedLayout ? 8 : 10);
    int detailButtonWidth = (selectionInnerWidth - (ROW_GAP * 2)) / 3;

    int resolveInnerX = resolveCardX + CARD_PADDING;
    int resolveInnerWidth = resolveCardWidth - (CARD_PADDING * 2);
    int resolveActionsY = resolveCardY + resolveCardHeight - BUTTON_HEIGHT - (pagedLayout ? 8 : 10);
    int resolveButtonWidth = (resolveInnerWidth - (ROW_GAP * 2)) / 3;
    int compactTabButtonWidth = Math.max(60, (compactTabsWidth - (CARD_GAP * 2)) / 3);

    profilesTabButton =
        addDrawableChild(
            button(
                Text.translatable("keyset.tab.profiles"),
                compactTabsX,
                compactTabsY,
                compactTabButtonWidth,
                button -> openCompactPage(CompactPage.PROFILES),
                Text.translatable("keyset.section.profile_card")));
    navigatorTabButton =
        addDrawableChild(
            button(
                Text.translatable("keyset.tab.navigator"),
                compactTabsX + compactTabButtonWidth + CARD_GAP,
                compactTabsY,
                compactTabButtonWidth,
                button -> openCompactPage(CompactPage.NAVIGATOR),
                Text.translatable("keyset.section.navigator")));
    fixesTabButton =
        addDrawableChild(
            button(
                Text.translatable("keyset.tab.fixes"),
                compactTabsX + ((compactTabButtonWidth + CARD_GAP) * 2),
                compactTabsY,
                compactTabButtonWidth,
                button -> openCompactPage(CompactPage.FIXES),
                Text.translatable("keyset.section.resolve")));

    searchField =
        addDrawableChild(
            new TextFieldWidget(
                textRenderer,
                filterInnerX,
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
                .dimensions(groupToggleX, groupToggleY, groupToggleWidth, BUTTON_HEIGHT)
                .build());
    setTooltip(groupToggleButton, "keyset.tip.group");

    profileNameField =
        addDrawableChild(
            new TextFieldWidget(
                textRenderer,
                profileInnerX,
                profileFieldY,
                profileInnerWidth,
                BUTTON_HEIGHT,
                Text.translatable("keyset.profile.name")));
    profileNameField.setMaxLength(40);
    setTooltip(profileNameField, "keyset.tip.profile_name");

    previousProfileButton =
        addDrawableChild(
            button(
                "keyset.profile.prev",
                profileInnerX,
                profileRowY,
                useThreeColumnProfileGrid ? profileThreeColumnWidth : profileTwoColumnWidth,
                button -> selectRelative(-1),
                "keyset.tip.profile_prev"));
    nextProfileButton =
        addDrawableChild(
            button(
                "keyset.profile.next",
                profileInnerX
                    + (useThreeColumnProfileGrid ? profileThreeColumnWidth : profileTwoColumnWidth)
                    + profileButtonGap,
                profileRowY,
                useThreeColumnProfileGrid ? profileThreeColumnWidth : profileTwoColumnWidth,
                button -> selectRelative(1),
                "keyset.tip.profile_next"));

    applyButton =
        addDrawableChild(
            button(
                "keyset.profile.apply",
                useThreeColumnProfileGrid
                    ? profileInnerX + ((profileThreeColumnWidth + profileButtonGap) * 2)
                    : profileInnerX,
                useThreeColumnProfileGrid
                    ? profileRowY
                    : profileRowY + BUTTON_HEIGHT + profileButtonGap,
                useThreeColumnProfileGrid ? profileThreeColumnWidth : profileTwoColumnWidth,
                button -> applySelected(),
                "keyset.tip.profile_apply"));
    captureButton =
        addDrawableChild(
            button(
                "keyset.profile.capture",
                useThreeColumnProfileGrid
                    ? profileInnerX
                    : profileInnerX + profileTwoColumnWidth + profileButtonGap,
                useThreeColumnProfileGrid
                    ? profileRowY + BUTTON_HEIGHT + profileButtonGap
                    : profileRowY + BUTTON_HEIGHT + profileButtonGap,
                useThreeColumnProfileGrid ? profileThreeColumnWidth : profileTwoColumnWidth,
                button -> captureCurrent(),
                "keyset.tip.profile_capture"));

    renameButton =
        addDrawableChild(
            button(
                "keyset.profile.rename",
                useThreeColumnProfileGrid
                    ? profileInnerX + profileThreeColumnWidth + profileButtonGap
                    : profileInnerX,
                useThreeColumnProfileGrid
                    ? profileRowY + BUTTON_HEIGHT + profileButtonGap
                    : profileRowY + ((BUTTON_HEIGHT + profileButtonGap) * 2),
                useThreeColumnProfileGrid ? profileThreeColumnWidth : profileTwoColumnWidth,
                button -> renameSelected(),
                "keyset.tip.profile_rename"));
    createButton =
        addDrawableChild(
            button(
                "keyset.profile.new",
                useThreeColumnProfileGrid
                    ? profileInnerX + ((profileThreeColumnWidth + profileButtonGap) * 2)
                    : profileInnerX + profileTwoColumnWidth + profileButtonGap,
                useThreeColumnProfileGrid
                    ? profileRowY + BUTTON_HEIGHT + profileButtonGap
                    : profileRowY + ((BUTTON_HEIGHT + profileButtonGap) * 2),
                useThreeColumnProfileGrid ? profileThreeColumnWidth : profileTwoColumnWidth,
                button -> createProfile(),
                "keyset.tip.profile_new"));

    duplicateButton =
        addDrawableChild(
            button(
                "keyset.profile.duplicate",
                profileInnerX,
                useThreeColumnProfileGrid
                    ? profileRowY + ((BUTTON_HEIGHT + profileButtonGap) * 2)
                    : profileRowY + ((BUTTON_HEIGHT + profileButtonGap) * 3),
                useThreeColumnProfileGrid ? profileTwoColumnWidth : profileTwoColumnWidth,
                button -> duplicateSelected(),
                "keyset.tip.profile_duplicate"));
    deleteButton =
        addDrawableChild(
            button(
                "keyset.profile.delete",
                profileInnerX + profileTwoColumnWidth + profileButtonGap,
                useThreeColumnProfileGrid
                    ? profileRowY + ((BUTTON_HEIGHT + profileButtonGap) * 2)
                    : profileRowY + ((BUTTON_HEIGHT + profileButtonGap) * 3),
                profileTwoColumnWidth,
                button -> deleteSelected(),
                "keyset.tip.profile_delete"));

    exportButton =
        addDrawableChild(
            button(
                "keyset.export",
                profileInnerX,
                useThreeColumnProfileGrid
                    ? profileRowY + ((BUTTON_HEIGHT + profileButtonGap) * 3)
                    : profileRowY + ((BUTTON_HEIGHT + profileButtonGap) * 4),
                profileTwoColumnWidth,
                button -> exportSelected(),
                "keyset.tip.export"));
    importButton =
        addDrawableChild(
            button(
                "keyset.import",
                profileInnerX + profileTwoColumnWidth + profileButtonGap,
                useThreeColumnProfileGrid
                    ? profileRowY + ((BUTTON_HEIGHT + profileButtonGap) * 3)
                    : profileRowY + ((BUTTON_HEIGHT + profileButtonGap) * 4),
                profileTwoColumnWidth,
                button -> importProfiles(),
                "keyset.tip.import"));

    jumpButton =
        addDrawableChild(
            button(
                "keyset.binding.jump",
                selectionInnerX,
                detailActionsY,
                detailButtonWidth,
                button -> jumpToBinding(),
                "keyset.tip.binding_jump"));
    clearBindingButton =
        addDrawableChild(
            button(
                "keyset.binding.clear",
                selectionInnerX + detailButtonWidth + ROW_GAP,
                detailActionsY,
                detailButtonWidth,
                button -> clearSelectedBinding(),
                "keyset.tip.binding_clear"));
    reassignButton =
        addDrawableChild(
            button(
                "keyset.binding.reassign",
                selectionInnerX + (detailButtonWidth + ROW_GAP) * 2,
                detailActionsY,
                detailButtonWidth,
                button -> reassignSelectedBinding(),
                "keyset.tip.binding_reassign"));

    previewResolveButton =
        addDrawableChild(
            button(
                "keyset.resolve.preview",
                resolveInnerX,
                resolveActionsY,
                resolveButtonWidth,
                button -> previewResolve(),
                "keyset.tip.resolve_preview"));
    applyPreviewButton =
        addDrawableChild(
            button(
                "keyset.resolve.apply",
                resolveInnerX + resolveButtonWidth + ROW_GAP,
                resolveActionsY,
                resolveButtonWidth,
                button -> applyPreview(),
                "keyset.tip.resolve_apply"));
    undoButton =
        addDrawableChild(
            button(
                "keyset.resolve.undo",
                resolveInnerX + (resolveButtonWidth + ROW_GAP) * 2,
                resolveActionsY,
                resolveButtonWidth,
                button -> undoResolve(),
                "keyset.tip.resolve_undo"));

    doneButton =
        addDrawableChild(
            button(
                "gui.done",
                width - SCREEN_PADDING - doneButtonWidth,
                footerY,
                doneButtonWidth,
                button -> close(),
                "keyset.tip.done"));

    conflictListWidget =
        addDrawableChild(
            new KeysetConflictListWidget(
                client,
                listCardWidth - 16,
                listTop,
                listBottom,
                bindingDescriptor -> {
                  selectedBinding = bindingDescriptor;
                  refreshButtons();
                }));
    conflictListWidget.setLeftPos(listCardX + 8);

    try {
      reloadState(null, null);
    } catch (IOException exception) {
      setStatus(exception.getMessage(), true);
    }

    refreshButtons();
    focusCurrentPage();
  }

  @Override
  public void close() {
    client.setScreen(parent);
  }

  @Override
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    drawBackdrop(context);
    drawHeaderPanel(context);
    drawContentPanels(context);
    super.render(context, mouseX, mouseY, delta);
    drawForeground(context);
  }

  private void computeLayout() {
    boolean forcePagedLayout = false;
    while (true) {
      headerX = SCREEN_PADDING;
      headerY = 10;
      headerWidth = width - (SCREEN_PADDING * 2);
      pagedLayout = forcePagedLayout || width < 600 || height < 340;
      microLayout = pagedLayout && (width < 360 || height < 280);
      stackHeaderMetrics = !pagedLayout && headerWidth < 760;
      headerHeight =
          pagedLayout
              ? (microLayout ? 44 : HEADER_HEIGHT)
              : (stackHeaderMetrics ? 76 : HEADER_HEIGHT);
      footerY = height - SCREEN_PADDING - BUTTON_HEIGHT;
      doneButtonWidth = pagedLayout && microLayout ? 72 : 92;
      compactTabsX = SCREEN_PADDING;
      compactTabsY = headerY + headerHeight + 6;
      compactTabsWidth = headerWidth;

      int contentTop = headerY + headerHeight + PANEL_GAP;
      int contentBottom = footerY - PANEL_GAP;
      compactLayout = pagedLayout || height < 300 || width < 520;

      if (pagedLayout) {
        sidebarX = SCREEN_PADDING;
        sidebarY = compactTabsY + BUTTON_HEIGHT + 6;
        sidebarWidth = headerWidth;
        mainX = sidebarX;
        mainY = sidebarY;
        mainWidth = sidebarWidth;

        profileCardX = sidebarX;
        profileCardY = sidebarY;
        profileCardWidth = sidebarWidth;
        profileCardHeight = Math.max(104, contentBottom - sidebarY);

        helpCardX = profileCardX;
        helpCardY = profileCardY;
        helpCardWidth = profileCardWidth;
        helpCardHeight = profileCardHeight;

        filterCardX = mainX;
        filterCardY = mainY;
        filterCardWidth = mainWidth;
        stackFilterControls = filterCardWidth < 300;
        filterCardHeight = stackFilterControls ? 58 : 44;
        showNavigatorSummary = !microLayout && filterCardWidth >= 360;

        listCardX = mainX;
        listCardY = filterCardY + filterCardHeight + CARD_GAP;
        listCardWidth = mainWidth;
        listCardHeight = Math.max(72, contentBottom - listCardY);

        selectionCardX = mainX;
        selectionCardY = mainY;
        selectionCardWidth = mainWidth;
        selectionCardHeight = Math.max(54, (contentBottom - mainY - CARD_GAP) / 2);
        resolveCardX = mainX;
        resolveCardY = selectionCardY + selectionCardHeight + CARD_GAP;
        resolveCardWidth = mainWidth;
        resolveCardHeight = Math.max(42, contentBottom - resolveCardY);

        stackActionCards = true;
        denseLayout = true;
        listTop = listCardY + 8;
        listBottom = listCardY + listCardHeight - 8;
        return;
      }

      stackFilterControls = false;

      sidebarX = SCREEN_PADDING;
      sidebarY = contentTop;
      sidebarWidth = Math.max(196, Math.min(228, headerWidth / 3));
      if (headerWidth - sidebarWidth - PANEL_GAP < 292) {
        sidebarWidth = Math.max(184, headerWidth - PANEL_GAP - 292);
      }
      mainX = sidebarX + sidebarWidth + PANEL_GAP;
      mainY = contentTop;
      mainWidth = width - SCREEN_PADDING - mainX;

      profileCardX = sidebarX;
      profileCardY = sidebarY;
      profileCardWidth = sidebarWidth;
      profileCardHeight = compactLayout ? 230 : 240;

      helpCardX = sidebarX;
      helpCardY = profileCardY + profileCardHeight + CARD_GAP;
      helpCardWidth = sidebarWidth;
      helpCardHeight = Math.max(104, contentBottom - helpCardY);

      filterCardX = mainX;
      filterCardY = mainY;
      filterCardWidth = mainWidth;
      filterCardHeight = 58;
      showNavigatorSummary = filterCardWidth >= 520;

      stackActionCards = mainWidth < 820;
      denseLayout = stackActionCards || compactLayout;

      listCardX = mainX;
      listCardY = filterCardY + filterCardHeight + CARD_GAP;
      listCardWidth = mainWidth;

      if (stackActionCards) {
        int preferredCardHeight = compactLayout ? 112 : 126;
        int minCardHeight = compactLayout ? 72 : 84;
        int minListHeight = compactLayout ? 76 : 96;
        int availableMainHeight = contentBottom - listCardY;
        int totalGapHeight = CARD_GAP * 2;
        int availableListHeight = availableMainHeight - (preferredCardHeight * 2) - totalGapHeight;

        if (availableListHeight < minListHeight) {
          int shrinkBudget = (preferredCardHeight - minCardHeight) * 2;
          int shrinkNeeded = minListHeight - availableListHeight;
          if (shrinkNeeded > shrinkBudget) {
            forcePagedLayout = true;
            continue;
          }

          int firstShrink = Math.min(preferredCardHeight - minCardHeight, (shrinkNeeded + 1) / 2);
          int secondShrink =
              Math.min(preferredCardHeight - minCardHeight, shrinkNeeded - firstShrink);
          selectionCardHeight = preferredCardHeight - firstShrink;
          resolveCardHeight = preferredCardHeight - secondShrink;
        } else {
          selectionCardHeight = preferredCardHeight;
          resolveCardHeight = preferredCardHeight;
        }

        selectionCardWidth = mainWidth;
        resolveCardWidth = mainWidth;
        selectionCardX = mainX;
        resolveCardX = mainX;
        resolveCardY = contentBottom - resolveCardHeight;
        selectionCardY = resolveCardY - CARD_GAP - selectionCardHeight;
        listCardHeight = Math.max(48, selectionCardY - listCardY - CARD_GAP);
      } else {
        selectionCardHeight = compactLayout ? 92 : 104;
        resolveCardHeight = selectionCardHeight;
        selectionCardY = contentBottom - selectionCardHeight;
        resolveCardY = selectionCardY;
        resolveCardWidth = Math.max(236, Math.min(268, mainWidth / 3));
        selectionCardWidth = mainWidth - resolveCardWidth - CARD_GAP;
        if (selectionCardWidth < 260) {
          resolveCardWidth = Math.max(216, mainWidth / 2);
          selectionCardWidth = mainWidth - resolveCardWidth - CARD_GAP;
        }
        selectionCardX = mainX;
        resolveCardX = selectionCardX + selectionCardWidth + CARD_GAP;
        listCardHeight = Math.max(72, selectionCardY - listCardY - CARD_GAP);
      }

      listTop = listCardY + 8;
      listBottom = listCardY + listCardHeight - 8;
      return;
    }
  }

  private ButtonWidget button(
      String messageKey,
      int x,
      int y,
      int buttonWidth,
      ButtonWidget.PressAction action,
      String tooltipKey) {
    ButtonWidget widget =
        ButtonWidget.builder(Text.translatable(messageKey), action)
            .dimensions(x, y, buttonWidth, BUTTON_HEIGHT)
            .build();
    setTooltip(widget, tooltipKey);
    return widget;
  }

  private ButtonWidget button(
      Text message,
      int x,
      int y,
      int buttonWidth,
      ButtonWidget.PressAction action,
      Text tooltipText) {
    ButtonWidget widget =
        ButtonWidget.builder(message, action).dimensions(x, y, buttonWidth, BUTTON_HEIGHT).build();
    if (tooltipText != null) {
      setTooltip(widget, tooltipText);
    }
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
    context.fill(0, 0, width, headerY + headerHeight + 26, BACKDROP_BAND);
    for (int y = headerY + headerHeight + 6; y < height; y += 36) {
      context.fill(0, y, width, y + 1, 0x1226384A);
    }
  }

  private void drawHeaderPanel(DrawContext context) {
    drawPanel(context, headerX, headerY, headerWidth, headerHeight, HEADER_FILL, HEADER_BORDER);
    context.fill(headerX + 1, headerY + 1, headerX + headerWidth - 1, headerY + 14, 0x22304B61);
    int shimmer = (int) ((System.currentTimeMillis() / 45L) % 44L);
    for (int x = headerX - shimmer; x < headerX + headerWidth; x += 44) {
      context.fill(x, headerY + headerHeight - 7, x + 18, headerY + headerHeight - 5, 0x2250A4CA);
    }
  }

  private void drawContentPanels(DrawContext context) {
    if (pagedLayout) {
      drawPanel(
          context,
          compactTabsX,
          compactTabsY - 2,
          compactTabsWidth,
          BUTTON_HEIGHT + 4,
          PANEL_FILL,
          PANEL_BORDER);
      switch (compactPage) {
        case PROFILES:
          drawPanel(
              context,
              profileCardX,
              profileCardY,
              profileCardWidth,
              profileCardHeight,
              PANEL_FILL,
              PANEL_BORDER);
          return;
        case NAVIGATOR:
          drawPanel(
              context,
              filterCardX,
              filterCardY,
              filterCardWidth,
              filterCardHeight,
              PANEL_FILL,
              PANEL_BORDER);
          drawPanel(
              context,
              listCardX,
              listCardY,
              listCardWidth,
              listCardHeight,
              PANEL_FILL,
              PANEL_BORDER);
          return;
        case FIXES:
          drawPanel(
              context,
              selectionCardX,
              selectionCardY,
              selectionCardWidth,
              selectionCardHeight,
              PANEL_FILL,
              PANEL_BORDER);
          drawPanel(
              context,
              resolveCardX,
              resolveCardY,
              resolveCardWidth,
              resolveCardHeight,
              PANEL_FILL,
              PANEL_BORDER);
          return;
      }
    }

    drawPanel(
        context,
        profileCardX,
        profileCardY,
        profileCardWidth,
        profileCardHeight,
        PANEL_FILL,
        PANEL_BORDER);
    drawPanel(
        context, helpCardX, helpCardY, helpCardWidth, helpCardHeight, PANEL_FILL, PANEL_BORDER);
    drawPanel(
        context,
        filterCardX,
        filterCardY,
        filterCardWidth,
        filterCardHeight,
        PANEL_FILL,
        PANEL_BORDER);
    drawPanel(
        context, listCardX, listCardY, listCardWidth, listCardHeight, PANEL_FILL, PANEL_BORDER);
    drawPanel(
        context,
        selectionCardX,
        selectionCardY,
        selectionCardWidth,
        selectionCardHeight,
        PANEL_FILL,
        PANEL_BORDER);
    drawPanel(
        context,
        resolveCardX,
        resolveCardY,
        resolveCardWidth,
        resolveCardHeight,
        PANEL_FILL,
        PANEL_BORDER);
  }

  private void drawForeground(DrawContext context) {
    drawHeaderContent(context);
    if (pagedLayout) {
      switch (compactPage) {
        case PROFILES:
          drawProfileCard(context);
          break;
        case NAVIGATOR:
          drawFilterCard(context);
          drawEmptyStateIfNeeded(context);
          break;
        case FIXES:
          drawSelectionCard(context);
          drawResolveCard(context);
          break;
      }
    } else {
      drawProfileCard(context);
      drawHelpCard(context);
      drawFilterCard(context);
      drawSelectionCard(context);
      drawResolveCard(context);
      drawEmptyStateIfNeeded(context);
    }
    drawFooter(context);
  }

  private void drawHeaderContent(DrawContext context) {
    context.drawTextWithShadow(textRenderer, title, headerX + 12, headerY + 11, TITLE_COLOR);
    if (!microLayout) {
      drawTrimmedText(
          context,
          Text.translatable("keyset.subtitle"),
          headerX + 12,
          stackHeaderMetrics ? headerY + 50 : headerY + 28,
          headerWidth - 24,
          BODY_COLOR);
    }

    if (pagedLayout) {
      if (!microLayout) {
        drawRightChip(
            context,
            headerX + headerWidth - 12,
            headerY + 11,
            previewPlan != null
                ? Text.translatable(
                    "keyset.metric.changes", Integer.valueOf(previewPlan.getChanges().size()))
                : Text.translatable(
                    "keyset.metric.bindings", Integer.valueOf(visibleConflictBindings)),
            CHIP_ACCENT_FILL,
            CHIP_ACCENT_BORDER,
            TITLE_COLOR);
      }
      return;
    }

    int metricsY = stackHeaderMetrics ? headerY + 28 : headerY + 11;
    int chipRight = headerX + headerWidth - 12;
    chipRight =
        drawRightChip(
            context,
            chipRight,
            metricsY,
            Text.translatable(
                "keyset.metric.profiles",
                Integer.valueOf(config == null ? 0 : config.getProfiles().size())),
            CHIP_FILL,
            CHIP_BORDER,
            BODY_COLOR);
    chipRight -= 6;

    Text conflictChipText =
        previewPlan != null
            ? Text.translatable(
                "keyset.metric.changes", Integer.valueOf(previewPlan.getChanges().size()))
            : Text.translatable("keyset.metric.bindings", Integer.valueOf(visibleConflictBindings));
    chipRight =
        drawRightChip(
            context,
            chipRight,
            metricsY,
            conflictChipText,
            CHIP_ACCENT_FILL,
            CHIP_ACCENT_BORDER,
            TITLE_COLOR);
    chipRight -= 6;

    Text profileStateText =
        isSelectedProfileActive()
            ? Text.translatable("keyset.profile.state.active")
            : Text.translatable("keyset.profile.state.stored");
    drawRightChip(
        context,
        chipRight,
        metricsY,
        profileStateText,
        isSelectedProfileActive() ? CHIP_ACTIVE_FILL : CHIP_FILL,
        isSelectedProfileActive() ? CHIP_ACTIVE_BORDER : CHIP_BORDER,
        TITLE_COLOR);
  }

  private void drawProfileCard(DrawContext context) {
    int contentX = profileCardX + CARD_PADDING;
    int contentWidth = profileCardWidth - (CARD_PADDING * 2);

    context.drawTextWithShadow(
        textRenderer,
        Text.translatable("keyset.section.profile_card"),
        contentX,
        profileCardY + (pagedLayout ? 8 : 10),
        LABEL_COLOR);
    if (pagedLayout) {
      drawAdaptiveChip(
          context,
          profileCardX + profileCardWidth - CARD_PADDING,
          profileCardY + 6,
          Math.min(132, Math.max(72, contentWidth / 2)),
          isSelectedProfileActive()
              ? Text.translatable("keyset.profile.state.active")
              : Text.translatable("keyset.profile.state.stored"),
          isSelectedProfileActive() ? CHIP_ACTIVE_FILL : CHIP_FILL,
          isSelectedProfileActive() ? CHIP_ACTIVE_BORDER : CHIP_BORDER,
          TITLE_COLOR);
      return;
    }

    drawWrappedTextBlock(
        context,
        Text.translatable("keyset.profile.deck_body"),
        contentX,
        profileCardY + 24,
        contentWidth,
        14,
        MUTED_COLOR);
    drawChip(
        context,
        contentX,
        profileCardY + 46,
        contentWidth,
        isSelectedProfileActive()
            ? Text.translatable("keyset.profile.state.active")
            : Text.translatable("keyset.profile.state.stored"),
        isSelectedProfileActive());
  }

  private void drawHelpCard(DrawContext context) {
    if (pagedLayout) {
      return;
    }
    int contentX = helpCardX + CARD_PADDING;
    int contentWidth = helpCardWidth - (CARD_PADDING * 2);
    int stepY = helpCardY + 10;

    context.drawTextWithShadow(
        textRenderer, Text.translatable("keyset.section.help"), contentX, stepY, LABEL_COLOR);
    stepY += 18;
    stepY = drawHelpStep(context, contentX, contentWidth, stepY, "1", "keyset.help.step1");
    stepY = drawHelpStep(context, contentX, contentWidth, stepY, "2", "keyset.help.step2");
    stepY = drawHelpStep(context, contentX, contentWidth, stepY, "3", "keyset.help.step3");
    int remainingHeight = helpCardHeight - (stepY - helpCardY) - 12;
    if (remainingHeight >= textRenderer.fontHeight + 2) {
      drawWrappedTextBlock(
          context,
          Text.translatable("keyset.help.group_headers"),
          contentX,
          stepY + 2,
          contentWidth,
          remainingHeight,
          MUTED_COLOR);
    }
  }

  private int drawHelpStep(
      DrawContext context, int x, int maxWidth, int y, String stepNumber, String translationKey) {
    drawSmallChip(
        context,
        x,
        y,
        18,
        Text.literal(stepNumber),
        CHIP_ACCENT_FILL,
        CHIP_ACCENT_BORDER,
        TITLE_COLOR);
    int textX = x + 24;
    int textWidth = Math.max(24, maxWidth - 24);
    int lineHeight = textRenderer.fontHeight + 2;
    int lineY = y + 1;
    int lineCount = 0;
    for (OrderedText line : textRenderer.wrapLines(Text.translatable(translationKey), textWidth)) {
      if (lineCount >= 2) {
        break;
      }
      context.drawTextWithShadow(textRenderer, line, textX, lineY, BODY_COLOR);
      lineY += lineHeight;
      lineCount++;
    }
    return y + Math.max(24, (lineCount * lineHeight) + 4);
  }

  private void drawFilterCard(DrawContext context) {
    int contentX = filterCardX + CARD_PADDING;
    int contentWidth = filterCardWidth - (CARD_PADDING * 2);

    context.drawTextWithShadow(
        textRenderer,
        Text.translatable("keyset.section.navigator"),
        contentX,
        filterCardY + (pagedLayout ? 8 : 10),
        LABEL_COLOR);
    if (showNavigatorSummary) {
      Text summaryText = buildHeaderSummaryText();
      drawTrimmedText(
          context,
          summaryText,
          contentX + 118,
          filterCardY + (pagedLayout ? 8 : 10),
          Math.max(40, contentWidth - 320),
          MUTED_COLOR);
    }

    if (pagedLayout) {
      return;
    }

    int chipRight = filterCardX + filterCardWidth - CARD_PADDING;
    chipRight =
        drawRightChip(
            context,
            chipRight,
            filterCardY + 8,
            Text.translatable("keyset.metric.bindings", Integer.valueOf(visibleConflictBindings)),
            CHIP_FILL,
            CHIP_BORDER,
            BODY_COLOR);
    chipRight -= 6;
    drawRightChip(
        context,
        chipRight,
        filterCardY + 8,
        Text.translatable("keyset.metric.groups", Integer.valueOf(visibleConflictGroups)),
        CHIP_FILL,
        CHIP_BORDER,
        BODY_COLOR);
  }

  private void drawSelectionCard(DrawContext context) {
    int contentX = selectionCardX + CARD_PADDING;
    int contentWidth = selectionCardWidth - (CARD_PADDING * 2);
    int textWidth = contentWidth;
    int detailActionsY =
        selectionCardY + selectionCardHeight - BUTTON_HEIGHT - (pagedLayout ? 8 : 10);
    boolean compactCard = denseLayout || selectionCardWidth < 360;

    drawTrimmedText(
        context,
        Text.translatable("keyset.section.selection"),
        contentX,
        selectionCardY + (pagedLayout ? 8 : 10),
        compactCard ? contentWidth : Math.max(80, contentWidth - 136),
        LABEL_COLOR);
    if (!compactCard && (!pagedLayout || !microLayout)) {
      Text stateChip;
      if (previewPlan != null) {
        stateChip = Text.translatable("keyset.selection.preview_chip");
      } else if (selectedBinding == null) {
        stateChip = Text.translatable("keyset.selection.none_chip");
      } else if (isSelectedProfileActive()) {
        stateChip = Text.translatable("keyset.selection.active_chip");
      } else {
        stateChip = Text.translatable("keyset.selection.inactive_chip");
      }
      drawAdaptiveChip(
          context,
          selectionCardX + selectionCardWidth - CARD_PADDING,
          selectionCardY + 6,
          128,
          stateChip,
          previewPlan != null ? CHIP_ACCENT_FILL : CHIP_FILL,
          previewPlan != null ? CHIP_ACCENT_BORDER : CHIP_BORDER,
          TITLE_COLOR);
    }

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
      boolean activeSelection = isSelectedProfileActive();
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

    if (compactCard) {
      Text compactSummary;
      if (previewPlan != null) {
        compactSummary = Text.translatable("keyset.selection.preview_body");
      } else if (selectedBinding == null) {
        compactSummary = Text.translatable("keyset.selection.none_body");
      } else if (isSelectedProfileActive()) {
        compactSummary =
            Text.literal(
                selectedBinding.getDisplayName()
                    + " • "
                    + selectedBinding.getCategoryName()
                    + " • "
                    + selectedBinding.getKeyDisplayName());
      } else {
        compactSummary = bodyText;
      }
      drawTrimmedText(
          context, compactSummary, contentX, selectionCardY + 25, textWidth, TITLE_COLOR);
      return;
    }

    int titleY = selectionCardY + (compactCard ? 24 : pagedLayout ? 24 : 31);
    int bodyY =
        compactCard
            ? titleY + textRenderer.fontHeight + 6
            : selectionCardY + (pagedLayout ? 36 : 44);
    drawTrimmedText(context, titleText, contentX, titleY, textWidth, TITLE_COLOR);
    drawWrappedTextBlock(
        context,
        bodyText,
        contentX,
        bodyY,
        textWidth,
        Math.max(12, detailActionsY - bodyY - 6),
        BODY_COLOR);
  }

  private void drawResolveCard(DrawContext context) {
    int contentX = resolveCardX + CARD_PADDING;
    int contentWidth = resolveCardWidth - (CARD_PADDING * 2);
    int resolveActionsY = resolveCardY + resolveCardHeight - BUTTON_HEIGHT - (pagedLayout ? 8 : 10);
    boolean compactCard = denseLayout || resolveCardWidth < 300;
    drawTrimmedText(
        context,
        Text.translatable("keyset.section.resolve"),
        contentX,
        resolveCardY + (pagedLayout ? 8 : 10),
        contentWidth,
        LABEL_COLOR);

    Text bodyText;
    if (previewPlan != null) {
      bodyText = Text.translatable("keyset.resolve.card.preview_body");
    } else if (!isSelectedProfileActive()) {
      bodyText =
          Text.translatable(
              "keyset.resolve.card.inactive_body",
              config != null && selectedProfileId != null && config.hasProfile(selectedProfileId)
                  ? config.getProfile(selectedProfileId).getName()
                  : "");
    } else if (conflictReport.isEmpty()) {
      bodyText = Text.translatable("keyset.resolve.card.empty_body");
    } else {
      bodyText = Text.translatable("keyset.resolve.card.ready_body");
    }

    drawWrappedTextBlock(
        context,
        bodyText,
        contentX,
        resolveCardY + (pagedLayout ? 22 : 28),
        contentWidth,
        compactCard
            ? textRenderer.fontHeight + 2
            : Math.max(
                12,
                resolveActionsY
                    - (resolveCardY + (pagedLayout ? 22 : 28))
                    - (pagedLayout ? 6 : 26)),
        BODY_COLOR);

    int chipY = resolveActionsY - (pagedLayout ? 18 : 20);
    if (previewPlan != null) {
      if ((pagedLayout && microLayout) || compactCard) {
        return;
      }
      int chipRight = resolveCardX + resolveCardWidth - CARD_PADDING;
      chipRight =
          drawRightChip(
              context,
              chipRight,
              chipY,
              112,
              Text.translatable(
                  "keyset.metric.unresolved", Integer.valueOf(previewPlan.getUnresolvedBindings())),
              CHIP_FILL,
              CHIP_BORDER,
              BODY_COLOR);
      chipRight -= 6;
      drawRightChip(
          context,
          chipRight,
          chipY,
          112,
          Text.translatable(
              "keyset.metric.changes", Integer.valueOf(previewPlan.getChanges().size())),
          CHIP_ACTIVE_FILL,
          CHIP_ACTIVE_BORDER,
          TITLE_COLOR);
      return;
    }

    if ((pagedLayout && microLayout) || compactCard) {
      return;
    }

    drawAdaptiveChip(
        context,
        resolveCardX + resolveCardWidth - CARD_PADDING,
        chipY,
        112,
        Text.translatable("keyset.metric.groups", Integer.valueOf(visibleConflictGroups)),
        CHIP_FILL,
        CHIP_BORDER,
        BODY_COLOR);
  }

  private void drawFooter(DrawContext context) {
    String footerMessage = statusMessage.isEmpty() ? buildDefaultFooterMessage() : statusMessage;
    int availableWidth = width - (SCREEN_PADDING * 2) - doneButtonWidth - 8;
    context.drawTextWithShadow(
        textRenderer,
        Text.literal(ellipsize(footerMessage, Math.max(40, availableWidth))),
        SCREEN_PADDING,
        footerY + 5,
        statusMessage.isEmpty()
            ? MUTED_COLOR
            : errorStatus ? STATUS_ERROR_COLOR : STATUS_SUCCESS_COLOR);
  }

  private void drawEmptyStateIfNeeded(DrawContext context) {
    if (emptyStateTitle.getString().isEmpty() && emptyStateBody.getString().isEmpty()) {
      return;
    }

    int centerX = listCardX + (listCardWidth / 2);
    int y = listCardY + Math.max(28, (listCardHeight / 2) - 18);
    context.drawCenteredTextWithShadow(textRenderer, emptyStateTitle, centerX, y, TITLE_COLOR);
    drawWrappedTextBlock(
        context, emptyStateBody, listCardX + 24, y + 16, listCardWidth - 48, 28, MUTED_COLOR);
  }

  private Text buildHeaderSummaryText() {
    if (previewPlan != null) {
      return Text.translatable(
          "keyset.resolve.summary",
          Integer.valueOf(previewPlan.getChanges().size()),
          Integer.valueOf(previewPlan.getUnresolvedBindings()));
    }
    if (config == null || selectedProfileId == null || !config.hasProfile(selectedProfileId)) {
      return Text.translatable("keyset.summary.none");
    }
    if (visibleConflictBindings <= 0) {
      return Text.translatable(
          "keyset.summary.clean", config.getProfile(selectedProfileId).getName());
    }
    return Text.translatable(
        "keyset.summary.selected",
        config.getProfile(selectedProfileId).getName(),
        Integer.valueOf(visibleConflictBindings));
  }

  private void drawPanel(
      DrawContext context,
      int x,
      int y,
      int panelWidth,
      int panelHeight,
      int fillColor,
      int borderColor) {
    context.fill(x + 2, y + 2, x + panelWidth + 2, y + panelHeight + 2, PANEL_SHADOW);
    context.fill(x, y, x + panelWidth, y + panelHeight, fillColor);
    context.drawBorder(x, y, panelWidth, panelHeight, borderColor);
  }

  private int drawRightChip(
      DrawContext context,
      int rightX,
      int y,
      Text text,
      int fillColor,
      int borderColor,
      int textColor) {
    return drawRightChip(
        context, rightX, y, Integer.MAX_VALUE, text, fillColor, borderColor, textColor);
  }

  private int drawRightChip(
      DrawContext context,
      int rightX,
      int y,
      int maxWidth,
      Text text,
      int fillColor,
      int borderColor,
      int textColor) {
    int chipWidth = Math.min(maxWidth, Math.max(34, textRenderer.getWidth(text) + 14));
    drawSmallChip(
        context, rightX - chipWidth, y, chipWidth, text, fillColor, borderColor, textColor);
    return rightX - chipWidth;
  }

  private void drawAdaptiveChip(
      DrawContext context,
      int rightX,
      int y,
      Text text,
      int fillColor,
      int borderColor,
      int textColor) {
    drawAdaptiveChip(
        context, rightX, y, Integer.MAX_VALUE, text, fillColor, borderColor, textColor);
  }

  private void drawAdaptiveChip(
      DrawContext context,
      int rightX,
      int y,
      int maxWidth,
      Text text,
      int fillColor,
      int borderColor,
      int textColor) {
    int chipWidth = Math.min(maxWidth, Math.max(34, textRenderer.getWidth(text) + 14));
    drawSmallChip(
        context, rightX - chipWidth, y, chipWidth, text, fillColor, borderColor, textColor);
  }

  private void drawChip(
      DrawContext context, int x, int y, int chipWidth, Text text, boolean active) {
    drawSmallChip(
        context,
        x,
        y,
        chipWidth,
        Text.literal(ellipsize(text.getString(), Math.max(18, chipWidth - 10))),
        active ? CHIP_ACTIVE_FILL : CHIP_FILL,
        active ? CHIP_ACTIVE_BORDER : CHIP_BORDER,
        TITLE_COLOR);
  }

  private void drawSmallChip(
      DrawContext context,
      int x,
      int y,
      int chipWidth,
      Text text,
      int fillColor,
      int borderColor,
      int textColor) {
    context.fill(x, y, x + chipWidth, y + 14, fillColor);
    context.drawBorder(x, y, chipWidth, 14, borderColor);
    context.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal(ellipsize(text.getString(), Math.max(18, chipWidth - 8))),
        x + (chipWidth / 2),
        y + 3,
        textColor);
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
          if (pagedLayout) {
            compactPage = CompactPage.NAVIGATOR;
          }
          if (previewPlan.isEmpty()) {
            setStatus(Text.translatable("keyset.status.resolve_none").getString(), false);
          } else {
            setStatus(Text.translatable("keyset.status.resolve_preview").getString(), false);
          }
          refreshButtons();
          focusCurrentPage();
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
      } else {
        emptyStateTitle = Text.empty();
        emptyStateBody = Text.empty();
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
    boolean activeSelection = isSelectedProfileActive();

    if (config != null && selectedProfileId != null) {
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
    }

    groupToggleButton.setMessage(
        Text.translatable(
            groupMode == KeysetConflictGroupMode.BY_KEY
                ? "keyset.group.by_key"
                : "keyset.group.by_category"));
    refreshCompactPageButtons();
    refreshResponsiveLabels();
    refreshWidgetVisibility();
  }

  private void refreshCompactPageButtons() {
    profilesTabButton.visible = pagedLayout;
    navigatorTabButton.visible = pagedLayout;
    fixesTabButton.visible = pagedLayout;
    if (!pagedLayout) {
      return;
    }

    profilesTabButton.active = compactPage != CompactPage.PROFILES;
    navigatorTabButton.active = compactPage != CompactPage.NAVIGATOR;
    fixesTabButton.active = compactPage != CompactPage.FIXES;
  }

  private void refreshResponsiveLabels() {
    boolean compactButtons = pagedLayout || denseLayout;
    previousProfileButton.setMessage(
        Text.translatable(compactButtons ? "keyset.compact.profile.prev" : "keyset.profile.prev"));
    nextProfileButton.setMessage(
        Text.translatable(compactButtons ? "keyset.compact.profile.next" : "keyset.profile.next"));
    applyButton.setMessage(
        Text.translatable(
            compactButtons ? "keyset.compact.profile.apply" : "keyset.profile.apply"));
    captureButton.setMessage(
        Text.translatable(
            compactButtons ? "keyset.compact.profile.capture" : "keyset.profile.capture"));
    renameButton.setMessage(Text.translatable("keyset.profile.rename"));
    createButton.setMessage(
        Text.translatable(compactButtons ? "keyset.compact.profile.new" : "keyset.profile.new"));
    duplicateButton.setMessage(Text.translatable("keyset.profile.duplicate"));
    deleteButton.setMessage(Text.translatable("keyset.profile.delete"));
    exportButton.setMessage(
        Text.translatable(compactButtons ? "keyset.compact.export" : "keyset.export"));
    importButton.setMessage(
        Text.translatable(compactButtons ? "keyset.compact.import" : "keyset.import"));
    jumpButton.setMessage(
        Text.translatable(compactButtons ? "keyset.compact.binding.jump" : "keyset.binding.jump"));
    clearBindingButton.setMessage(
        Text.translatable(
            compactButtons ? "keyset.compact.binding.clear" : "keyset.binding.clear"));
    reassignButton.setMessage(
        Text.translatable(
            compactButtons ? "keyset.compact.binding.reassign" : "keyset.binding.reassign"));
  }

  private void refreshWidgetVisibility() {
    boolean showProfiles = !pagedLayout || compactPage == CompactPage.PROFILES;
    boolean showNavigator = !pagedLayout || compactPage == CompactPage.NAVIGATOR;
    boolean showFixes = !pagedLayout || compactPage == CompactPage.FIXES;

    profileNameField.visible = showProfiles;
    previousProfileButton.visible = showProfiles;
    nextProfileButton.visible = showProfiles;
    applyButton.visible = showProfiles;
    captureButton.visible = showProfiles;
    renameButton.visible = showProfiles;
    createButton.visible = showProfiles;
    duplicateButton.visible = showProfiles;
    deleteButton.visible = showProfiles;
    exportButton.visible = showProfiles;
    importButton.visible = showProfiles;

    searchField.visible = showNavigator;
    groupToggleButton.visible = showNavigator;
    conflictListWidget.setHidden(!showNavigator);

    jumpButton.visible = showFixes;
    clearBindingButton.visible = showFixes;
    reassignButton.visible = showFixes;
    previewResolveButton.visible = showFixes;
    applyPreviewButton.visible = showFixes;
    undoButton.visible = showFixes;
  }

  private void openCompactPage(CompactPage page) {
    if (!pagedLayout || compactPage == page) {
      return;
    }
    compactPage = page;
    refreshButtons();
    focusCurrentPage();
  }

  private void focusCurrentPage() {
    if (pagedLayout) {
      switch (compactPage) {
        case PROFILES:
          setInitialFocus(profileNameField);
          return;
        case NAVIGATOR:
          setInitialFocus(searchField);
          return;
        case FIXES:
          setFocused(null);
          return;
      }
    }
    setInitialFocus(searchField);
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

  private boolean isSelectedProfileActive() {
    return config != null
        && selectedProfileId != null
        && selectedProfileId.equals(config.getActiveProfileId());
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

  private String buildDefaultFooterMessage() {
    if (previewPlan != null) {
      return Text.translatable("keyset.footer.preview").getString();
    }
    if (config == null || selectedProfileId == null || !config.hasProfile(selectedProfileId)) {
      return Text.translatable("keyset.footer.default").getString();
    }
    if (!isSelectedProfileActive()) {
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

  private void requireActiveProfileSelection() {
    if (!isSelectedProfileActive()) {
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

  private enum CompactPage {
    PROFILES,
    NAVIGATOR,
    FIXES
  }

  private interface Action {
    void run() throws IOException;
  }
}
