package net.beeboyd.keyset.platform.fabric.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.beeboyd.keyset.core.autoswitch.AutoSwitchRule;
import net.beeboyd.keyset.core.profile.KeysetBindingSnapshot;
import net.beeboyd.keyset.core.profile.KeysetProfile;
import net.beeboyd.keyset.core.profile.KeysetProfilesConfig;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

public final class KeysetScreen extends Screen {
  private static final int TUTORIAL_OVERLAY_Z = 400;
  private static final int TUTORIAL_PANEL_WIDTH = 340;
  private static final int TUTORIAL_PANEL_HEIGHT = 176;
  private static final int INTRO_ANIM_BUILD_MS = 900;
  private static final int INTRO_ANIM_HOLD_MS = 2800;
  private static final int INTRO_ANIM_FADE_MS = 700;
  private static final int INTRO_ANIM_REST_MS = 1500;
  private static final int INTRO_ANIM_CYCLE_MS =
      INTRO_ANIM_BUILD_MS + INTRO_ANIM_HOLD_MS + INTRO_ANIM_FADE_MS + INTRO_ANIM_REST_MS;
  private static final int[][] INTRO_KEYCAP_SEEDS = {
    {8, 10, 28}, {21, 76, 22}, {34, 24, 30}, {47, 88, 24}, {61, 36, 26}, {74, 12, 22},
    {86, 72, 30}, {11, 58, 24}, {29, 43, 20}, {52, 18, 22}, {67, 84, 28}, {81, 31, 24},
    {18, 28, 18}, {42, 66, 26}, {91, 16, 20}, {6, 86, 24}, {96, 52, 22}, {56, 68, 30},
    {31, 8, 18}, {72, 56, 24}, {14, 38, 30}, {39, 94, 22}, {89, 91, 26}, {58, 7, 20},
    {26, 53, 28}, {79, 42, 18}, {4, 49, 20}, {64, 94, 24}, {97, 27, 28}, {45, 14, 22},
    {13, 18, 24}, {23, 90, 18}, {36, 81, 30}, {49, 5, 20}, {54, 48, 18}, {68, 23, 30},
    {77, 8, 26}, {94, 77, 18}, {7, 69, 30}, {17, 95, 22}, {33, 35, 24}, {43, 72, 18},
    {59, 90, 20}, {71, 69, 28}, {84, 5, 18}, {93, 40, 30}, {2, 24, 22}, {25, 12, 26},
    {66, 4, 22}, {83, 58, 24}, {53, 78, 28}, {38, 2, 18}
  };

  enum Tab {
    BINDINGS,
    CONFLICTS,
    AUTO_SWITCH,
    SHARE
  }

  private record SidebarBtn(String labelKey, Runnable action, int x, int y, int w, int h) {}

  private static final class BindingRow {
    final String id;
    final String displayName;
    final String category;
    final String categoryName;
    final String keyLabel;
    final boolean conflict;

    BindingRow(
        String id,
        String displayName,
        String category,
        String categoryName,
        String keyLabel,
        boolean conflict) {
      this.id = id;
      this.displayName = displayName;
      this.category = category;
      this.categoryName = categoryName;
      this.keyLabel = keyLabel;
      this.conflict = conflict;
    }
  }

  private record ConflictTarget(
      boolean isGroupHeader,
      String groupKey,
      int x,
      int y,
      int w,
      int h,
      String bindingId,
      String actionName,
      String keyLabel,
      String categoryName,
      List<String> otherActions) {}

  private record AutoSwitchTarget(boolean isAddBtn, int deleteIndex, int x, int y, int w, int h) {}

  private record ConflictGroup(String boundKey, List<KeyMapping> bindings) {}

  private record IntroAnim(float reveal, float alpha) {}

  private record ToastEntry(String msg, boolean error, long createdMs, float slideProgress) {}

  enum TutorialStep {
    INTRO("keyset.tutorial.intro"),
    WELCOME("keyset.tutorial.welcome"),
    CREATE("keyset.tutorial.create"),
    RENAME("keyset.tutorial.rename"),
    ACTIVATE("keyset.tutorial.activate"),
    CONFLICTS("keyset.tutorial.conflicts"),
    FIX_CONFLICT("keyset.tutorial.fix"),
    SAVE_LIVE("keyset.tutorial.savelive"),
    AUTO_SWITCH("keyset.tutorial.autoswitch"),
    SHARE("keyset.tutorial.share"),
    DONE("keyset.tutorial.done");

    private final String keyPrefix;

    TutorialStep(String keyPrefix) {
      this.keyPrefix = keyPrefix;
    }

    String titleKey() {
      return keyPrefix + ".title";
    }

    String bodyKey() {
      return keyPrefix + ".body";
    }

    String hintKey() {
      return keyPrefix + ".hint";
    }
  }

  private final Screen parent;
  private final KeysetFabricService service;
  private Tab currentTab = Tab.BINDINGS;

  // Profile sidebar state
  private float sidebarScrollTarget;
  private float sidebarScrollSmooth;
  private String selectedProfileId;
  private final List<ToastEntry> toastQueue = new ArrayList<>();
  private static final int MAX_TOASTS = 5;
  private static final int SHARE_DROPDOWN_MAX_VISIBLE = 8;

  // Custom sidebar buttons (no vanilla widgets — fully custom-rendered)
  private final List<SidebarBtn> sidebarButtons = new ArrayList<>();

  // Bindings tab
  private KeysetTextFieldWidget bindingsSearch;
  private boolean bindingsGroupByCategory = true;
  private float bindingsScrollTarget;
  private float bindingsScrollSmooth;

  // Conflicts tab
  private KeysetTextFieldWidget conflictsSearch;
  private float conflictsScrollTarget;
  private float conflictsScrollSmooth;
  private final Set<String> expandedConflictGroups = new HashSet<>();
  private final List<ConflictTarget> conflictTargets = new ArrayList<>();

  // Auto-switch tab
  private final List<AutoSwitchTarget> autoSwitchTargets = new ArrayList<>();
  private float autoSwitchScrollTarget;
  private float autoSwitchScrollSmooth;

  // Share tab
  private record ShareTarget(String id, int x, int y, int w, int h) {}

  private final List<ShareTarget> shareTargets = new ArrayList<>();
  private boolean shareUploading = false;
  private boolean shareDownloading = false;
  private String shareResultCode = "";
  private long shareExpiresAt = 0;
  private KeysetTextFieldWidget shareCodeField;
  private List<ShareHistoryStore.Entry> shareHistory = new ArrayList<>();
  private String shareTargetProfileId = null;
  private boolean shareDropdownOpen = false;
  private int shareDropdownRowX;
  private int shareDropdownSelY;
  private int shareDropdownElemW;
  private float shareDropdownScrollTarget;
  private float shareDropdownScrollSmooth;
  private float shareHistoryScrollTarget;
  private float shareHistoryScrollSmooth;
  private int shareHistoryScrollX, shareHistoryScrollY, shareHistoryScrollW, shareHistoryScrollH;
  private boolean importCodeInvalid = false;
  private boolean codesExpanded = false;

  // Per-row hover animation state (profile id → alpha 0..1)
  private final java.util.Map<String, Float> rowHoverAlphas = new java.util.HashMap<>();

  // Layout (computed in init)
  private int sidebarW;
  private int topbarY;
  private int sidebarX;
  private int sidebarY;
  private int mainX;
  private int mainY;
  private int mainW;
  private int mainH;
  private int tabBarY;
  private int contentY;

  // Animation
  private long openedAtMs;
  private long lastFrameMs;
  private float frameDt = 16f;
  private float screenAlpha;
  private float tabUnderlineX;
  private float tabUnderlineTarget;

  // Conflict auto-refresh
  private String lastKeybindHash = "";
  private int refreshTick = 0;
  private final List<ConflictGroup> conflictGroups = new ArrayList<>();

  // Interactive tutorial
  private boolean tutorialActive;
  private TutorialStep tutorialStep = TutorialStep.WELCOME;
  private boolean tutNextEnabled;
  private int tutHlX = -1, tutHlY = -1, tutHlW = 0, tutHlH = 0;
  private int profileCountAtStepStart;
  private String profileNameAtStepStart = "";
  private boolean didActivate;
  private boolean didSaveLive;
  private boolean didOpenConflictDialog;
  private boolean didUseShareTab;
  private boolean lastStepComplete = false;

  public KeysetScreen(Screen parent, KeysetFabricService service) {
    super(Component.translatable("keyset.title"));
    this.parent = parent;
    this.service = service;
  }

  @Override
  public void tick() {
    super.tick();
    if (++refreshTick < 10) return;
    refreshTick = 0;
    String hash = computeKeybindHash();
    if (!hash.equals(lastKeybindHash)) {
      lastKeybindHash = hash;
      refreshConflicts();
    }
    if (tutorialActive) {
      boolean nowComplete = isStepComplete();
      if (nowComplete && !lastStepComplete && tutorialStep != TutorialStep.INTRO) {
        playStepCompleteSound();
      }
      lastStepComplete = nowComplete;
      tutNextEnabled = nowComplete;
    }
  }

  @Override
  protected void init() {
    if (openedAtMs == 0) openedAtMs = System.currentTimeMillis();
    lastFrameMs = 0;
    computeLayout();

    if (selectedProfileId == null) {
      try {
        selectedProfileId = service.getConfig(minecraft).getActiveProfileId();
      } catch (IOException e) {
        // keep null
      }
    }

    ShareHistoryStore.LoadResult historyLoad = ShareHistoryStore.loadResult(shareHistoryPath());
    shareHistory = historyLoad.entries();
    if (historyLoad.recoveredBrokenFile()) {
      setStatus("Share history was reset; broken file was archived.", true);
    }
    buildSidebarButtons();
    rebuildTabWidgets();
    refreshConflicts();

    // ? help button — always reopens tutorial from WELCOME
    int helpBtnX = width - KeysetTheme.PAD - 20;
    int helpBtnY = topbarY + (KeysetTheme.TOPBAR_H - 16) / 2;
    addRenderableWidget(
        KeysetButtonWidget.create(
            helpBtnX,
            helpBtnY,
            16,
            16,
            Component.literal("?"),
            b -> {
              tutorialActive = true;
              tutorialStep = TutorialStep.INTRO;
              clearWidgets();
              init();
            }));

    // First-launch tutorial trigger
    if (!tutorialActive) {
      try {
        if (!service.isTutorialComplete(minecraft)) {
          tutorialActive = true;
          tutorialStep = TutorialStep.INTRO;
        }
      } catch (Exception ignored) {
      }
    }

    tutNextEnabled = tutorialActive && isStepComplete();
  }

  private void buildSidebarButtons() {
    sidebarButtons.clear();
    int bx = sidebarX + KeysetTheme.GAP_SM;
    int bw = sidebarW - KeysetTheme.GAP_SM * 2;
    int by = sidebarY + mainH - 84;
    int g = KeysetTheme.GAP_SM;
    int hw = (bw - g) / 2;
    int qw = (bw - g * 3) / 4;

    sidebarButtons.add(
        new SidebarBtn("keyset.profile.apply", this::activateSelected, bx, by, hw, 18));
    sidebarButtons.add(
        new SidebarBtn("keyset.profile.capture", this::saveLiveSelected, bx + hw + g, by, hw, 18));
    sidebarButtons.add(new SidebarBtn("keyset.profile.new", this::newProfile, bx, by + 22, qw, 18));
    sidebarButtons.add(
        new SidebarBtn(
            "keyset.profile.rename", this::renameSelected, bx + qw + g, by + 22, qw, 18));
    sidebarButtons.add(
        new SidebarBtn(
            "keyset.profile.duplicate", this::cloneSelected, bx + (qw + g) * 2, by + 22, qw, 18));
    sidebarButtons.add(
        new SidebarBtn(
            "keyset.profile.delete", this::confirmDelete, bx + (qw + g) * 3, by + 22, qw, 18));
    sidebarButtons.add(new SidebarBtn("↑", this::moveUp, bx, by + 44, qw, 18));
    sidebarButtons.add(new SidebarBtn("↓", this::moveDown, bx + qw + g, by + 44, qw, 18));
    sidebarButtons.add(
        new SidebarBtn("keyset.export", this::copyToClipboard, bx + (qw + g) * 2, by + 44, qw, 18));
    sidebarButtons.add(
        new SidebarBtn(
            "keyset.import", this::pasteFromClipboard, bx + (qw + g) * 3, by + 44, qw, 18));
  }

  private void rebuildTabWidgets() {
    if (bindingsSearch != null) {
      removeWidget(bindingsSearch);
      bindingsSearch = null;
    }
    if (conflictsSearch != null) {
      removeWidget(conflictsSearch);
      conflictsSearch = null;
    }
    if (shareCodeField != null) {
      removeWidget(shareCodeField);
      shareCodeField = null;
    }
    int sx = mainX + KeysetTheme.GAP;
    int sy = contentY + KeysetTheme.GAP;
    int toggleW = 72;
    int searchW = mainW - KeysetTheme.GAP * 2 - toggleW - KeysetTheme.GAP_SM;
    if (currentTab == Tab.BINDINGS) {
      bindingsSearch = new KeysetTextFieldWidget(font, sx, sy, searchW, 18, Component.empty());
      bindingsSearch.setHint(Component.translatable("keyset.search.placeholder"));
      bindingsSearch.setMaxLength(64);
      addRenderableWidget(bindingsSearch);
    } else if (currentTab == Tab.CONFLICTS) {
      conflictsSearch = new KeysetTextFieldWidget(font, sx, sy, searchW, 18, Component.empty());
      conflictsSearch.setHint(Component.translatable("keyset.search.placeholder"));
      conflictsSearch.setMaxLength(64);
      addRenderableWidget(conflictsSearch);
    } else if (currentTab == Tab.SHARE) {
      int pad = KeysetTheme.PAD;
      int gap = KeysetTheme.GAP;
      int gapSm = KeysetTheme.GAP_SM;
      int sectionGap = gap * 2;
      int tabContentX = mainX + pad;
      int tabContentY = contentY + pad;
      int tabContentW = mainW - pad * 2;
      int tabContentH = mainY + mainH - tabContentY - pad;
      int elemW = (int) (tabContentW * 0.55f);
      int btnW = 90;
      int rowH = 22;
      int labelH = 9;
      // Compute same shareH as renderShareTab (no code result at init time)
      int shareH = labelH + gapSm + rowH;
      if (!shareResultCode.isEmpty()) shareH += gap + 36 + gapSm + labelH + gapSm + 20;
      int importH = labelH + gapSm + rowH;
      int codesH = labelH;
      if (codesExpanded && !shareHistory.isEmpty()) codesH += gapSm + shareHistory.size() * rowH;
      int totalH =
          shareH + sectionGap + 1 + sectionGap + importH + sectionGap + 1 + sectionGap + codesH;
      int curY = tabContentY + Math.max(0, (tabContentH - totalH) / 2);
      // Skip SHARE NEW section
      curY += shareH + sectionGap + 1 + sectionGap;
      // Skip IMPORT label
      curY += labelH + gapSm;
      int impTotalW = elemW + gap + btnW;
      int impX = tabContentX + (tabContentW - impTotalW) / 2;
      int importRowY = curY;
      shareCodeField =
          new KeysetTextFieldWidget(font, impX, importRowY, elemW, rowH, Component.empty());
      shareCodeField.setHint(Component.literal("XXXX-XXXX"));
      shareCodeField.setMaxLength(9);
      shareCodeField.setResponder(text -> importCodeInvalid = false);
      addRenderableWidget(shareCodeField);
    }
  }

  private void computeLayout() {
    int safeW = Math.max(width, 480);
    int safeH = Math.max(height, 320);
    int p = KeysetTheme.PAD;
    sidebarW = Math.min(KeysetTheme.SIDEBAR_W, safeW / 4);
    topbarY = p;
    sidebarX = p;
    sidebarY = p + KeysetTheme.TOPBAR_H + KeysetTheme.GAP;
    mainX = sidebarX + sidebarW + KeysetTheme.GAP;
    mainY = sidebarY;
    mainW = safeW - mainX - p;
    mainH = safeH - sidebarY - KeysetTheme.FOOTER_H - KeysetTheme.GAP - p;
    tabBarY = mainY;
    contentY = tabBarY + KeysetTheme.TAB_H + KeysetTheme.GAP;
    tabUnderlineTarget = mainX + currentTab.ordinal() * (mainW / 4f);
    if (tabUnderlineX == 0) tabUnderlineX = tabUnderlineTarget;
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    // Intentionally empty — backdrop rendered in render() for animation support.
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    long now = System.currentTimeMillis();
    frameDt = lastFrameMs > 0 ? Math.min(now - lastFrameMs, 50f) : 16f;
    lastFrameMs = now;
    screenAlpha = KeysetTheme.easeOutQuart(Math.min(1f, (now - openedAtMs) / KeysetTheme.OPEN_MS));
    tabUnderlineX =
        KeysetTheme.expLerp(tabUnderlineX, tabUnderlineTarget, Math.max(delta * 50f, 1f), 25f);

    if (width < 400 || height < 280) {
      ctx.fill(0, 0, width, height, 0xFF0A0C0E);
      ctx.centeredText(
          font,
          Component.literal("Please increase the window size"),
          width / 2,
          height / 2 - 9,
          KeysetTheme.ACCENT);
      ctx.centeredText(
          font,
          Component.literal("or decrease the GUI scale in Options"),
          width / 2,
          height / 2 + 4,
          KeysetTheme.TEXT_MUTED);
      return;
    }

    boolean isIntro = tutorialActive && tutorialStep == TutorialStep.INTRO;
    boolean inDark =
        !isIntro
            && tutorialActive
            && tutorialStep != TutorialStep.DONE
            && tutHlX >= 0
            && !(mouseX >= (int) tutHlX
                && mouseX < (int) (tutHlX + tutHlW)
                && mouseY >= (int) tutHlY
                && mouseY < (int) (tutHlY + tutHlH));
    int hmx = (isIntro || inDark) ? -1 : mouseX;
    int hmy = (isIntro || inDark) ? -1 : mouseY;

    ctx.fill(0, 0, width, height, KeysetTheme.scaleAlpha(KeysetTheme.BG_BACKDROP, screenAlpha));
    renderTopbar(ctx);
    renderSidebar(ctx, hmx, hmy);
    renderMain(ctx, hmx, hmy);

    super.extractRenderState(ctx, hmx, hmy, delta);
    if (currentTab == Tab.SHARE && shareDropdownOpen) {
      ctx.nextStratum();
      renderShareDropdownOverlay(ctx, hmx, hmy);
    }
    // Search field clear (✕) button overlay
    KeysetTextFieldWidget activeSearch =
        currentTab == Tab.BINDINGS
            ? bindingsSearch
            : currentTab == Tab.CONFLICTS ? conflictsSearch : null;
    if (activeSearch != null && !activeSearch.getValue().isEmpty()) {
      int cx2 = activeSearch.getRight() - 10;
      int cy2 = activeSearch.getY() + (activeSearch.getHeight() - 9) / 2;
      boolean clearHov = hmx >= cx2 - 2 && hmx < cx2 + 9 && hmy >= cy2 - 1 && hmy < cy2 + 10;
      ctx.fill(
          activeSearch.getRight() - 14,
          activeSearch.getY() + 1,
          activeSearch.getRight() - 1,
          activeSearch.getBottom() - 1,
          KeysetTheme.scaleAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
      ctx.text(
          font,
          Component.literal("✕"),
          cx2,
          cy2,
          KeysetTheme.withAlpha(
              clearHov ? KeysetTheme.TEXT_BODY : KeysetTheme.TEXT_MUTED, screenAlpha),
          true);
    }
    if (importCodeInvalid && currentTab == Tab.SHARE && shareCodeField != null) {
      ctx.text(
          font,
          Component.literal("⚠"),
          shareCodeField.getX() - 14,
          shareCodeField.getY() + (shareCodeField.getHeight() - 9) / 2,
          KeysetTheme.withAlpha(KeysetTheme.ERROR, screenAlpha),
          true);
    }
    ctx.enableScissor(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + mainH);
    renderSidebarButtons(ctx, hmx, hmy);
    ctx.disableScissor();
    renderDoneButton(ctx, isIntro ? -1 : mouseX, isIntro ? -1 : mouseY);
    renderFooter(ctx);
    if (tutorialActive && tutorialStep != TutorialStep.DONE) {
      renderTutorialOverlay(ctx, mouseX, mouseY);
    }
  }

  private void renderTutorialOverlay(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
    ctx.nextStratum();
    ctx.pose().pushMatrix();
    try {
      ctx.pose().translate(0, 0);
      if (tutorialStep == TutorialStep.INTRO) {
        renderTutorialIntroPage(ctx, mouseX, mouseY);
      } else {
        renderTutorialDarkening(ctx);
        renderTutorialArrow(ctx);
        renderTutorialPanel(ctx, mouseX, mouseY);
      }
    } finally {
      ctx.pose().popMatrix();
    }
    ctx.nextStratum();
  }

  private void renderTopbar(GuiGraphicsExtractor ctx) {
    ctx.fill(
        KeysetTheme.PAD,
        topbarY,
        width - KeysetTheme.PAD,
        topbarY + KeysetTheme.TOPBAR_H,
        KeysetTheme.scaleAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.outline(
        KeysetTheme.PAD,
        topbarY,
        width - KeysetTheme.PAD * 2,
        KeysetTheme.TOPBAR_H,
        KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
    renderScaledCenteredText(
        ctx,
        Component.literal("Keyset").withStyle(style -> style.withBold(true)),
        width / 2,
        topbarY + KeysetTheme.TOPBAR_H / 2,
        1.6f,
        KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));
  }

  private void renderScaledCenteredText(
      GuiGraphicsExtractor ctx, Component text, int centerX, int centerY, float scale, int color) {
    ctx.pose().pushMatrix();
    try {
      ctx.pose().translate(centerX, centerY - (int) (9 * scale / 2f));
      ctx.pose().scale(scale, scale);
      ctx.centeredText(font, text, 0, 0, color);
    } finally {
      ctx.pose().popMatrix();
    }
  }

  private void renderSidebar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
    int sx = sidebarX;
    int sy = sidebarY;
    int sw = sidebarW;
    int sh = mainH;
    ctx.fill(sx, sy, sx + sw, sy + sh, KeysetTheme.scaleAlpha(KeysetTheme.BG_SIDEBAR, screenAlpha));
    ctx.outline(sx, sy, sw, sh, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
    ctx.text(
        font,
        Component.literal("PROFILES"),
        sx + KeysetTheme.CARD_PAD,
        sy + KeysetTheme.CARD_PAD,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha),
        true);
    renderProfileRows(ctx, mouseX, mouseY);
  }

  private void renderProfileRows(GuiGraphicsExtractor ctx, int mx, int my) {
    KeysetProfilesConfig cfg;
    try {
      cfg = service.getConfig(minecraft);
    } catch (IOException e) {
      return;
    }
    if (cfg == null) return;

    var profiles = new ArrayList<KeysetProfile>(cfg.getProfiles().values());
    int rowH = KeysetTheme.ROW_H;
    int listTop = sidebarY + 20;
    int listBot = sidebarY + mainH - 92;
    int maxScroll = Math.max(0, profiles.size() * rowH - (listBot - listTop));
    sidebarScrollTarget = Math.max(0, Math.min(sidebarScrollTarget, maxScroll));
    sidebarScrollSmooth =
        KeysetTheme.expLerp(sidebarScrollSmooth, sidebarScrollTarget, frameDt, 22f);

    ctx.enableScissor(sidebarX, listTop, sidebarX + sidebarW, listBot);

    for (int i = 0; i < profiles.size(); i++) {
      KeysetProfile profile = profiles.get(i);
      int rowY = listTop + i * rowH - (int) sidebarScrollSmooth;
      if (rowY + rowH < listTop || rowY > listBot) continue;

      boolean active = profile.getId().equals(cfg.getActiveProfileId());
      boolean selected = profile.getId().equals(selectedProfileId);
      boolean hovered =
          !isOverTutorialPanel(mx, my)
              && mx >= sidebarX
              && mx < sidebarX + sidebarW
              && my >= rowY
              && my < rowY + rowH;

      if (selected) {
        ctx.fill(
            sidebarX,
            rowY,
            sidebarX + sidebarW,
            rowY + rowH,
            KeysetTheme.scaleAlpha(KeysetTheme.BG_TAB_ACTIVE, screenAlpha));
      } else {
        String rowKey = "sidebar-" + profile.getId();
        float ha = rowHoverAlphas.getOrDefault(rowKey, 0f);
        ha = KeysetTheme.expLerp(ha, hovered ? 1f : 0f, frameDt, 20f);
        rowHoverAlphas.put(rowKey, ha);
        if (ha > 0.01f) {
          ctx.fill(
              sidebarX,
              rowY,
              sidebarX + sidebarW,
              rowY + rowH,
              KeysetTheme.withAlpha(KeysetTheme.BG_HOVER, screenAlpha * ha));
        }
      }

      if (active) {
        float pulse = (float) (Math.sin(System.currentTimeMillis() / 600.0) * 0.2 + 0.8);
        ctx.fill(
            sidebarX,
            rowY,
            sidebarX + 3,
            rowY + rowH,
            KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha * pulse));
      }

      int nameCol =
          active
              ? KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha)
              : selected
                  ? KeysetTheme.withAlpha(KeysetTheme.TEXT_TITLE, screenAlpha)
                  : KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha);
      int indent = active ? sidebarX + 9 : sidebarX + 7;
      String name = profile.getName();
      int maxW = sidebarW - indent + sidebarX - (active ? 32 : 8);
      int ellipsisW = font.width("…");
      while (font.width(name) > maxW - ellipsisW && name.length() > 1) {
        name = name.substring(0, name.length() - 1);
      }
      if (!name.equals(profile.getName())) name += "…";
      ctx.text(font, Component.literal(name), indent, rowY + (rowH - 9) / 2, nameCol, true);

      if (active) {
        int cw = font.width("LIVE") + 8;
        int cx2 = sidebarX + sidebarW - cw - 4;
        int cy2 = rowY + (rowH - 12) / 2;

        // MOD badge — show when active profile differs from live MC bindings
        boolean isModified = minecraft != null && profileDiffersFromLive(profile, minecraft);
        if (isModified) {
          int mw = font.width("MOD") + 8;
          int mx2 = cx2 - mw - 3;
          ctx.fill(
              mx2,
              cy2,
              mx2 + mw,
              cy2 + 12,
              KeysetTheme.withAlpha(KeysetTheme.CHIP_ERR_BG, screenAlpha));
          ctx.outline(
              mx2, cy2, mw, 12, KeysetTheme.withAlpha(KeysetTheme.CHIP_ERR_BR, screenAlpha));
          ctx.centeredText(
              font,
              Component.literal("MOD"),
              mx2 + mw / 2,
              cy2 + 2,
              KeysetTheme.withAlpha(KeysetTheme.ERROR, screenAlpha));
        }

        float livePulse = (float) (Math.sin(System.currentTimeMillis() / 1200.0) * 0.15 + 0.85);
        ctx.fill(
            cx2,
            cy2,
            cx2 + cw,
            cy2 + 12,
            KeysetTheme.withAlpha(KeysetTheme.CHIP_OK_BG, screenAlpha * livePulse));
        ctx.outline(cx2, cy2, cw, 12, KeysetTheme.withAlpha(KeysetTheme.CHIP_OK_BR, screenAlpha));
        ctx.centeredText(
            font,
            Component.literal("LIVE"),
            cx2 + cw / 2,
            cy2 + 2,
            KeysetTheme.withAlpha(KeysetTheme.SUCCESS, screenAlpha));
      }
    }
    ctx.disableScissor();
    rowHoverAlphas
        .keySet()
        .retainAll(
            profiles.stream()
                .map(profile -> "sidebar-" + profile.getId())
                .collect(Collectors.toSet()));

    if (maxScroll > 0) {
      int trackH = listBot - listTop;
      int thumbH = Math.max(16, (int) ((float) trackH / (profiles.size() * rowH) * trackH));
      int thumbY = listTop + (int) ((sidebarScrollSmooth / maxScroll) * (trackH - thumbH));
      ctx.fill(
          sidebarX + sidebarW - 2,
          thumbY,
          sidebarX + sidebarW,
          thumbY + thumbH,
          KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
    }
  }

  private void renderSidebarButtons(GuiGraphicsExtractor ctx, int mx, int my) {
    for (SidebarBtn btn : sidebarButtons) {
      boolean hovered =
          !isOverTutorialPanel(mx, my)
              && mx >= btn.x()
              && mx < btn.x() + btn.w()
              && my >= btn.y()
              && my < btn.y() + btn.h();
      int bg =
          hovered
              ? KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha * 0.85f)
              : KeysetTheme.withAlpha(KeysetTheme.BG_SURFACE, screenAlpha);
      int border =
          KeysetTheme.withAlpha(hovered ? KeysetTheme.ACCENT : KeysetTheme.BORDER, screenAlpha);
      int textCol =
          KeysetTheme.withAlpha(hovered ? 0xFF1A0800 : KeysetTheme.TEXT_BODY, screenAlpha);
      ctx.fill(btn.x(), btn.y(), btn.x() + btn.w(), btn.y() + btn.h(), bg);
      ctx.outline(btn.x(), btn.y(), btn.w(), btn.h(), border);
      Component btnLabel = Component.translatable(btn.labelKey());
      drawSidebarButtonLabel(ctx, btnLabel, btn.x(), btn.y(), btn.w(), btn.h(), textCol, hovered);
    }
  }

  private void drawSidebarButtonLabel(
      GuiGraphicsExtractor ctx,
      Component label,
      int x,
      int y,
      int w,
      int h,
      int color,
      boolean noShadow) {
    int maxTextW = Math.max(0, w - 6);
    String labelText = label.getString();
    if (font.width(labelText) > maxTextW) {
      String ellipsis = "…";
      int trimmedW = Math.max(0, maxTextW - font.width(ellipsis));
      labelText = trimmedW > 0 ? font.plainSubstrByWidth(labelText, trimmedW) + ellipsis : "";
    }
    int tx = x + (w - font.width(labelText)) / 2;
    int ty = y + (h - 9) / 2;
    ctx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
    try {
      if (noShadow) {
        ctx.text(font, Component.literal(labelText), tx, ty, color, false); // no shadow on amber bg
      } else {
        ctx.centeredText(font, Component.literal(labelText), x + w / 2, ty, color);
      }
    } finally {
      ctx.disableScissor();
    }
  }

  private void renderMain(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
    ctx.fill(
        mainX,
        mainY,
        mainX + mainW,
        mainY + mainH,
        KeysetTheme.scaleAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.outline(mainX, mainY, mainW, mainH, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));

    String[] names = {"Bindings", "Conflicts", "Auto-Switch", "Share"};
    Tab[] tabs = Tab.values();
    int tabW = mainW / 4;
    for (int i = 0; i < 4; i++) {
      int tx = mainX + i * tabW;
      boolean active = currentTab == tabs[i];
      boolean hovered =
          !active
              && !isOverTutorialPanel((int) mouseX, (int) mouseY)
              && mouseX >= tx
              && mouseX < tx + tabW
              && mouseY >= tabBarY
              && mouseY < tabBarY + KeysetTheme.TAB_H;
      if (hovered) {
        ctx.fill(
            tx,
            tabBarY,
            tx + tabW,
            tabBarY + KeysetTheme.TAB_H,
            KeysetTheme.withAlpha(KeysetTheme.BG_HOVER, screenAlpha));
      }
      int color =
          active
              ? KeysetTheme.TEXT_TITLE
              : hovered ? KeysetTheme.TEXT_BODY : KeysetTheme.TEXT_MUTED;
      ctx.centeredText(
          font,
          Component.literal(names[i]),
          tx + tabW / 2,
          tabBarY + 10,
          KeysetTheme.withAlpha(color, screenAlpha));
      // Conflict count badge on Conflicts tab
      if (tabs[i] == Tab.CONFLICTS && !conflictGroups.isEmpty()) {
        String badge = String.valueOf(conflictGroups.size());
        int bw = font.width(badge) + 8;
        int bx = tx + tabW / 2 + font.width(names[i]) / 2 + 6;
        int by = tabBarY + 7;
        ctx.fill(bx, by, bx + bw, by + 10, KeysetTheme.WARNING);
        ctx.text(
            font,
            Component.literal(badge),
            bx + (bw - font.width(badge)) / 2,
            by + 1,
            0xFF0A0C0E,
            false);
      }
    }

    int GAP = 4;
    int ulX = (int) tabUnderlineX;
    ctx.fill(
        ulX + GAP,
        tabBarY + KeysetTheme.TAB_H - 2,
        ulX + tabW - GAP,
        tabBarY + KeysetTheme.TAB_H,
        KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));
    ctx.fill(
        mainX,
        tabBarY + KeysetTheme.TAB_H,
        mainX + mainW,
        tabBarY + KeysetTheme.TAB_H + 1,
        KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));

    if (currentTab == Tab.BINDINGS) {
      renderBindingsTab(ctx, mouseX, mouseY);
    } else if (currentTab == Tab.CONFLICTS) {
      renderConflictsTab(ctx, mouseX, mouseY);
    } else if (currentTab == Tab.AUTO_SWITCH) {
      renderAutoSwitchTab(ctx, mouseX, mouseY);
    } else if (currentTab == Tab.SHARE) {
      renderShareTab(ctx, mouseX, mouseY);
    }
  }

  // ── Bindings tab ─────────────────────────────────────────────────────────────

  private void renderBindingsTab(GuiGraphicsExtractor ctx, int mx, int my) {
    int toggleW = 72;
    int toggleX = mainX + mainW - KeysetTheme.GAP - toggleW;
    int toggleY = contentY + KeysetTheme.GAP;
    boolean toggleHovered =
        !isOverTutorialPanel(mx, my)
            && mx >= toggleX
            && mx < toggleX + toggleW
            && my >= toggleY
            && my < toggleY + 18;
    String toggleLabel = bindingsGroupByCategory ? "Group: Cat" : "Group: Key";
    ctx.fill(
        toggleX,
        toggleY,
        toggleX + toggleW,
        toggleY + 18,
        KeysetTheme.withAlpha(
            toggleHovered ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.outline(
        toggleX,
        toggleY,
        toggleW,
        18,
        KeysetTheme.withAlpha(
            toggleHovered ? KeysetTheme.ACCENT : KeysetTheme.BORDER, screenAlpha));
    ctx.centeredText(
        font,
        Component.literal(toggleLabel),
        toggleX + toggleW / 2,
        toggleY + 5,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));

    List<BindingRow> rows = buildBindingRows(minecraft);

    int listTop = contentY + KeysetTheme.GAP + 18 + KeysetTheme.GAP_SM;
    int listBot = mainY + mainH - KeysetTheme.GAP_SM;

    if (rows.isEmpty()) {
      String emptyMsg =
          selectedProfileId == null
              ? "Select a profile to view bindings."
              : "No bindings match this search.";
      ctx.centeredText(
          font,
          Component.literal(emptyMsg),
          mainX + mainW / 2,
          listTop + (listBot - listTop) / 2 - 4,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_DISABLED, screenAlpha));
      return;
    }

    Map<String, List<BindingRow>> grouped = null;
    int contentHeight;
    if (bindingsGroupByCategory) {
      grouped = new LinkedHashMap<>();
      for (BindingRow row : rows) {
        grouped.computeIfAbsent(row.category, k -> new ArrayList<>()).add(row);
      }
      contentHeight = 0;
      for (List<BindingRow> group : grouped.values()) {
        contentHeight += 28 + group.size() * KeysetTheme.ROW_H;
      }
    } else {
      contentHeight = rows.size() * KeysetTheme.ROW_H;
    }

    int maxScroll = Math.max(0, contentHeight - (listBot - listTop));
    bindingsScrollTarget = Mth.clamp(bindingsScrollTarget, 0, maxScroll);
    bindingsScrollSmooth =
        KeysetTheme.expLerp(bindingsScrollSmooth, bindingsScrollTarget, frameDt, 22f);

    ctx.enableScissor(mainX + 1, listTop, mainX + mainW - 1, listBot);

    int curY = listTop - (int) bindingsScrollSmooth;
    int rowW = mainW - KeysetTheme.GAP_SM * 2;
    int rowX = mainX + KeysetTheme.GAP_SM;

    if (grouped != null) {
      for (Map.Entry<String, List<BindingRow>> entry : grouped.entrySet()) {
        if (curY + 28 > listTop && curY < listBot) {
          renderCategoryHeader(
              ctx, entry.getValue().get(0).categoryName, entry.getValue().size(), rowX, curY, rowW);
        }
        curY += 28;
        for (BindingRow row : entry.getValue()) {
          if (curY + KeysetTheme.ROW_H > listTop && curY < listBot) {
            renderBindingRow(ctx, row, rowX, curY, rowW, mx, my);
          }
          curY += KeysetTheme.ROW_H;
        }
      }
    } else {
      for (BindingRow row : rows) {
        if (curY + KeysetTheme.ROW_H > listTop && curY < listBot) {
          renderBindingRow(ctx, row, rowX, curY, rowW, mx, my);
        }
        curY += KeysetTheme.ROW_H;
      }
    }

    ctx.disableScissor();

    if (maxScroll > 0 && contentHeight > 0) {
      int trackH = listBot - listTop;
      int thumbH = Math.max(16, (int) ((float) trackH / contentHeight * trackH));
      int thumbY = listTop + (int) ((bindingsScrollSmooth / maxScroll) * (trackH - thumbH));
      ctx.fill(
          mainX + mainW - 2,
          thumbY,
          mainX + mainW,
          thumbY + thumbH,
          KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
    }
  }

  private void renderCategoryHeader(
      GuiGraphicsExtractor ctx, String name, int count, int x, int y, int w) {
    ctx.fill(x, y, x + w, y + 28, KeysetTheme.scaleAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.text(
        font,
        Component.literal(name.toUpperCase()),
        x + 8,
        y + 10,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha),
        true);
    String countLabel = count + " binds";
    ctx.text(
        font,
        Component.literal(countLabel),
        x + w - font.width(countLabel) - 8,
        y + 10,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_DISABLED, screenAlpha),
        true);
    ctx.fill(
        x + 4, y + 27, x + w - 4, y + 28, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
  }

  private void renderBindingRow(
      GuiGraphicsExtractor ctx, BindingRow row, int x, int y, int w, int mx, int my) {
    boolean hovered =
        !isOverTutorialPanel(mx, my)
            && mx >= x
            && mx < x + w
            && my >= y
            && my < y + KeysetTheme.ROW_H;
    String rowKey = "bind-" + row.id;
    float ha = rowHoverAlphas.getOrDefault(rowKey, 0f);
    ha = KeysetTheme.expLerp(ha, hovered ? 1f : 0f, frameDt, 20f);
    rowHoverAlphas.put(rowKey, ha);
    if (ha > 0.01f) {
      ctx.fill(
          x,
          y,
          x + w,
          y + KeysetTheme.ROW_H,
          KeysetTheme.withAlpha(KeysetTheme.BG_HOVER, screenAlpha * ha));
    }

    String displayName = row.displayName;
    String keyLabel = row.keyLabel;
    int chipMaxW = Math.max(32, Math.min(w / 2, w - 32));
    if (font.width(keyLabel) + 10 > chipMaxW) {
      keyLabel = font.plainSubstrByWidth(keyLabel, Math.max(0, chipMaxW - 16)) + "…";
    }
    int chipW = Math.min(chipMaxW, font.width(keyLabel) + 10);
    int maxNameW = Math.max(20, w - chipW - 22);
    while (font.width(displayName) > maxNameW && displayName.length() > 1) {
      displayName = displayName.substring(0, displayName.length() - 1);
    }
    if (!displayName.equals(row.displayName)) displayName += "…";

    ctx.text(
        font,
        Component.literal(displayName),
        x + 8,
        y + (KeysetTheme.ROW_H - 9) / 2,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha),
        true);

    int chipX = x + w - chipW - 6;
    int chipY = y + (KeysetTheme.ROW_H - 14) / 2;
    int chipBg = row.conflict ? KeysetTheme.CHIP_ERR_BG : KeysetTheme.CHIP_BG;
    int chipBr = row.conflict ? KeysetTheme.CHIP_ERR_BR : KeysetTheme.BORDER;
    int chipText = row.conflict ? KeysetTheme.ERROR : KeysetTheme.TEXT_TITLE;
    ctx.fill(chipX, chipY, chipX + chipW, chipY + 14, KeysetTheme.withAlpha(chipBg, screenAlpha));
    ctx.outline(chipX, chipY, chipW, 14, KeysetTheme.withAlpha(chipBr, screenAlpha));
    ctx.centeredText(
        font,
        Component.literal(keyLabel),
        chipX + chipW / 2,
        chipY + 3,
        KeysetTheme.withAlpha(chipText, screenAlpha));
  }

  private List<BindingRow> buildBindingRows(Minecraft mc) {
    if (selectedProfileId == null
        || mc == null
        || mc.options == null
        || mc.options.keyMappings == null) return Collections.emptyList();
    KeysetProfilesConfig cfg;
    try {
      cfg = service.getConfig(mc);
    } catch (IOException e) {
      return Collections.emptyList();
    }
    KeysetProfile profile = cfg.getProfile(selectedProfileId);
    if (profile == null) return Collections.emptyList();

    Map<String, KeysetBindingSnapshot> profileBindings = profile.getBindings();

    Map<String, Integer> keyCount = new HashMap<>();
    for (KeysetBindingSnapshot snap : profileBindings.values()) {
      if (!snap.getKeyStroke().isUnbound()) {
        String tok = snap.getKeyStroke().getKeyToken();
        keyCount.merge(tok, 1, Integer::sum);
      }
    }
    Set<String> conflictTokens = new HashSet<>();
    for (Map.Entry<String, Integer> e : keyCount.entrySet()) {
      if (e.getValue() > 1) conflictTokens.add(e.getKey());
    }

    String filter = bindingsSearch != null ? bindingsSearch.getValue().trim().toLowerCase() : "";

    List<BindingRow> rows = new ArrayList<>();
    for (KeyMapping kb : mc.options.keyMappings) {
      String id = kb.getName();
      KeysetBindingSnapshot snap = profileBindings.get(id);
      if (snap == null) continue;

      String displayName = Component.translatable(id).getString();
      String category = kb.getCategory().id().toString();
      String categoryName = kb.getCategory().label().getString();

      String keyLabel = keyDisplayName(snap.getKeyStroke().getKeyToken());

      boolean conflict =
          !snap.getKeyStroke().isUnbound()
              && conflictTokens.contains(snap.getKeyStroke().getKeyToken());

      if (!filter.isEmpty()
          && !displayName.toLowerCase().contains(filter)
          && !categoryName.toLowerCase().contains(filter)
          && !keyLabel.toLowerCase().contains(filter)
          && !id.toLowerCase().contains(filter)) {
        continue;
      }

      rows.add(new BindingRow(id, displayName, category, categoryName, keyLabel, conflict));
    }
    return rows;
  }

  // ── Conflicts tab ─────────────────────────────────────────────────────────────

  private void renderConflictsTab(GuiGraphicsExtractor ctx, int mx, int my) {
    int listTop = contentY + KeysetTheme.GAP + 18 + KeysetTheme.GAP_SM;
    int listBot = mainY + mainH - KeysetTheme.GAP_SM;

    String filter = conflictsSearch != null ? conflictsSearch.getValue().trim().toLowerCase() : "";
    List<ConflictGroup> visible = new ArrayList<>();
    for (ConflictGroup g : conflictGroups) {
      if (filter.isEmpty() || matchesConflictFilter(g, filter)) visible.add(g);
    }

    if (visible.isEmpty()) {
      int msgColor = conflictGroups.isEmpty() ? KeysetTheme.SUCCESS : KeysetTheme.TEXT_DISABLED;
      String msg =
          conflictGroups.isEmpty()
              ? Component.translatable("keyset.conflicts.none").getString()
              : "No conflicts match this search.";
      ctx.centeredText(
          font,
          Component.literal(msg),
          mainX + mainW / 2,
          listTop + (listBot - listTop) / 2 - 4,
          KeysetTheme.withAlpha(msgColor, screenAlpha));
      return;
    }

    int contentHeight = 0;
    for (ConflictGroup g : visible) {
      contentHeight += 28;
      if (expandedConflictGroups.contains(g.boundKey())) {
        contentHeight += g.bindings().size() * KeysetTheme.ROW_H;
      }
    }

    int maxScroll = Math.max(0, contentHeight - (listBot - listTop));
    conflictsScrollTarget = Mth.clamp(conflictsScrollTarget, 0, maxScroll);
    conflictsScrollSmooth =
        KeysetTheme.expLerp(conflictsScrollSmooth, conflictsScrollTarget, frameDt, 22f);

    ctx.enableScissor(mainX + 1, listTop, mainX + mainW - 1, listBot);
    conflictTargets.clear();

    int curY = listTop - (int) conflictsScrollSmooth;
    int rowW = mainW - KeysetTheme.GAP_SM * 2;
    int rowX = mainX + KeysetTheme.GAP_SM;

    for (ConflictGroup g : visible) {
      boolean expanded = expandedConflictGroups.contains(g.boundKey());
      String keyLabel = keyDisplayName(g.boundKey());

      if (curY + 28 > listTop && curY < listBot) {
        int targetY = Math.max(curY, listTop);
        int targetH = Math.min(curY + 28, listBot) - targetY;
        if (targetH > 0) {
          conflictTargets.add(
              new ConflictTarget(
                  true, g.boundKey(), rowX, targetY, rowW, targetH, null, null, null, null, null));
        }
        renderConflictGroup(ctx, keyLabel, g.bindings().size(), expanded, rowX, curY, rowW, mx, my);
      }
      curY += 28;

      if (expanded) {
        for (KeyMapping kb : g.bindings()) {
          String actionName = Component.translatable(kb.getName()).getString();
          String categoryName = kb.getCategory().label().getString();
          List<String> others = new ArrayList<>();
          for (KeyMapping other : g.bindings()) {
            if (!other.getName().equals(kb.getName())) {
              others.add(Component.translatable(other.getName()).getString());
            }
          }
          if (curY + KeysetTheme.ROW_H > listTop && curY < listBot) {
            int targetY = Math.max(curY, listTop);
            int targetH = Math.min(curY + KeysetTheme.ROW_H, listBot) - targetY;
            if (targetH > 0) {
              conflictTargets.add(
                  new ConflictTarget(
                      false,
                      g.boundKey(),
                      rowX,
                      targetY,
                      rowW,
                      targetH,
                      kb.getName(),
                      actionName,
                      keyLabel,
                      categoryName,
                      others));
            }
            renderConflictBinding(ctx, actionName, categoryName, rowX, curY, rowW, mx, my);
          }
          curY += KeysetTheme.ROW_H;
        }
      }
    }

    ctx.disableScissor();

    if (maxScroll > 0 && contentHeight > 0) {
      int trackH = listBot - listTop;
      int thumbH = Math.max(16, (int) ((float) trackH / contentHeight * trackH));
      int thumbY = listTop + (int) ((conflictsScrollSmooth / maxScroll) * (trackH - thumbH));
      ctx.fill(
          mainX + mainW - 2,
          thumbY,
          mainX + mainW,
          thumbY + thumbH,
          KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
    }
  }

  private void renderConflictGroup(
      GuiGraphicsExtractor ctx,
      String keyLabel,
      int bindCount,
      boolean expanded,
      int x,
      int y,
      int w,
      int mx,
      int my) {
    boolean hov = !isOverTutorialPanel(mx, my) && mx >= x && mx < x + w && my >= y && my < y + 28;
    ctx.fill(x, y, x + w, y + 28, KeysetTheme.scaleAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
    if (hov) {
      ctx.fill(x, y, x + w, y + 28, KeysetTheme.withAlpha(KeysetTheme.BG_HOVER, screenAlpha));
    }

    ctx.text(
        font,
        Component.literal(expanded ? "▼" : "▶"),
        x + 6,
        y + 10,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha),
        true);

    int chipW = font.width(keyLabel) + 10;
    ctx.fill(
        x + 18,
        y + 7,
        x + 18 + chipW,
        y + 21,
        KeysetTheme.withAlpha(KeysetTheme.CHIP_ERR_BG, screenAlpha));
    ctx.outline(
        x + 18, y + 7, chipW, 14, KeysetTheme.withAlpha(KeysetTheme.CHIP_ERR_BR, screenAlpha));
    ctx.centeredText(
        font,
        Component.literal(keyLabel),
        x + 18 + chipW / 2,
        y + 9,
        KeysetTheme.withAlpha(KeysetTheme.ERROR, screenAlpha));

    String cnt = bindCount + " binds";
    ctx.text(
        font,
        Component.literal(cnt),
        x + w - font.width(cnt) - 8,
        y + 10,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha),
        true);
    ctx.fill(
        x + 4, y + 27, x + w - 4, y + 28, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
  }

  private void renderConflictBinding(
      GuiGraphicsExtractor ctx,
      String actionName,
      String category,
      int x,
      int y,
      int w,
      int mx,
      int my) {
    int indent = 16;
    int gap = KeysetTheme.GAP_SM;
    int categoryMaxW = Math.max(40, Math.min(w / 3, font.width(category)));
    if (font.width(category) > categoryMaxW) {
      category =
          font.plainSubstrByWidth(category, Math.max(0, categoryMaxW - font.width("…"))) + "…";
    }
    int actionMaxW = Math.max(20, w - indent - categoryMaxW - gap - 8);
    if (font.width(actionName) > actionMaxW) {
      actionName =
          font.plainSubstrByWidth(actionName, Math.max(0, actionMaxW - font.width("…"))) + "…";
    }
    boolean hov =
        !isOverTutorialPanel(mx, my)
            && mx >= x + indent
            && mx < x + w
            && my >= y
            && my < y + KeysetTheme.ROW_H;
    if (hov) {
      ctx.fill(
          x,
          y,
          x + w,
          y + KeysetTheme.ROW_H,
          KeysetTheme.withAlpha(KeysetTheme.BG_HOVER, screenAlpha));
    }

    ctx.text(
        font,
        Component.literal(actionName),
        x + indent,
        y + (KeysetTheme.ROW_H - 9) / 2,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha),
        true);

    ctx.text(
        font,
        Component.literal(category),
        x + w - font.width(category) - 8,
        y + (KeysetTheme.ROW_H - 9) / 2,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha),
        true);
  }

  private boolean matchesConflictFilter(ConflictGroup g, String filter) {
    if (keyDisplayName(g.boundKey()).toLowerCase().contains(filter)) return true;
    for (KeyMapping kb : g.bindings()) {
      if (Component.translatable(kb.getName()).getString().toLowerCase().contains(filter))
        return true;
      if (kb.getCategory().label().getString().toLowerCase().contains(filter)) return true;
    }
    return false;
  }

  private boolean profileDiffersFromLive(KeysetProfile profile, Minecraft mc) {
    if (profile == null || mc == null || mc.options == null || mc.options.keyMappings == null) {
      return false;
    }
    Map<String, KeysetBindingSnapshot> bindings = profile.getBindings();
    for (KeyMapping kb : mc.options.keyMappings) {
      KeysetBindingSnapshot snap = bindings.get(kb.getName());
      if (snap == null) continue;
      boolean snapUnbound = snap.getKeyStroke().isUnbound();
      boolean liveUnbound = kb.isUnbound();
      if (snapUnbound != liveUnbound) return true;
      if (!snapUnbound && !snap.getKeyStroke().getKeyToken().equals(kb.saveString())) return true;
    }
    return false;
  }

  // ── Auto-switch tab ───────────────────────────────────────────────────────────

  private void renderAutoSwitchTab(GuiGraphicsExtractor ctx, int mx, int my) {
    autoSwitchTargets.clear();

    List<AutoSwitchRule> rules;
    try {
      rules = service.getAutoSwitchRules(minecraft);
    } catch (IOException e) {
      return;
    }

    // "Add Rule" button
    int btnW = 72;
    int btnH = 18;
    int btnX = mainX + mainW - KeysetTheme.GAP - btnW;
    int btnY = contentY + KeysetTheme.GAP;
    boolean addHov =
        !isOverTutorialPanel(mx, my)
            && mx >= btnX
            && mx < btnX + btnW
            && my >= btnY
            && my < btnY + btnH;
    ctx.fill(
        btnX,
        btnY,
        btnX + btnW,
        btnY + btnH,
        KeysetTheme.withAlpha(
            addHov ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.outline(
        btnX,
        btnY,
        btnW,
        btnH,
        KeysetTheme.withAlpha(addHov ? KeysetTheme.ACCENT : KeysetTheme.BORDER, screenAlpha));
    ctx.centeredText(
        font,
        Component.literal("+ Add Rule"),
        btnX + btnW / 2,
        btnY + 5,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));
    autoSwitchTargets.add(new AutoSwitchTarget(true, -1, btnX, btnY, btnW, btnH));

    int listTop = contentY + KeysetTheme.GAP + btnH + KeysetTheme.GAP_SM;
    int listBot = mainY + mainH - KeysetTheme.GAP_SM;
    int rowX = mainX + KeysetTheme.GAP_SM;
    int rowW = mainW - KeysetTheme.GAP_SM * 2;
    int rowH = 28;

    if (rules.isEmpty()) {
      ctx.centeredText(
          font,
          Component.literal("No rules. Add a rule to auto-switch profiles when joining servers."),
          mainX + mainW / 2,
          listTop + (listBot - listTop) / 2 - 4,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_DISABLED, screenAlpha));
      return;
    }

    KeysetProfilesConfig cfg = null;
    try {
      cfg = service.getConfig(minecraft);
    } catch (IOException ignored) {
    }

    int maxScroll = Math.max(0, rules.size() * rowH - (listBot - listTop));
    autoSwitchScrollTarget = Mth.clamp(autoSwitchScrollTarget, 0, maxScroll);
    autoSwitchScrollSmooth =
        KeysetTheme.expLerp(autoSwitchScrollSmooth, autoSwitchScrollTarget, frameDt, 22f);

    ctx.enableScissor(mainX + 1, listTop, mainX + mainW - 1, listBot);
    int curY = listTop - (int) autoSwitchScrollSmooth;
    for (int i = 0; i < rules.size(); i++) {
      AutoSwitchRule rule = rules.get(i);
      if (curY + rowH <= listTop) {
        curY += rowH;
        continue;
      }
      if (curY >= listBot) break;

      boolean rowHov =
          !isOverTutorialPanel(mx, my)
              && mx >= rowX
              && mx < rowX + rowW
              && my >= curY
              && my < curY + rowH;
      if (rowHov) {
        ctx.fill(
            rowX,
            curY,
            rowX + rowW,
            curY + rowH,
            KeysetTheme.withAlpha(KeysetTheme.BG_HOVER, screenAlpha));
      }

      // Delete button [✕]
      int delW = 16;
      int delX = rowX + rowW - delW - 4;
      int delY = curY + (rowH - 14) / 2;

      // Pattern chip
      String pattern = rule.getPattern();
      int patMaxW = Math.max(32, Math.min(font.width(pattern) + 10, rowW / 2));
      if (font.width(pattern) > patMaxW - 10) {
        pattern = font.plainSubstrByWidth(pattern, Math.max(0, patMaxW - 16)) + "…";
      }
      int patW = Math.min(patMaxW, font.width(pattern) + 10);
      int patY = curY + (rowH - 14) / 2;
      ctx.fill(
          rowX + 6,
          patY,
          rowX + 6 + patW,
          patY + 14,
          KeysetTheme.withAlpha(KeysetTheme.CHIP_BG, screenAlpha));
      ctx.outline(rowX + 6, patY, patW, 14, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
      ctx.centeredText(
          font,
          Component.literal(pattern),
          rowX + 6 + patW / 2,
          patY + 3,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));

      // Arrow + profile name
      String profileName =
          cfg != null && cfg.getProfile(rule.getProfileId()) != null
              ? cfg.getProfile(rule.getProfileId()).getName()
              : "⚠ [Deleted]";
      boolean invalidRule = cfg == null || cfg.getProfile(rule.getProfileId()) == null;
      String arrow = "→ " + profileName;
      int arrowX = rowX + 6 + patW + 8;
      int arrowMaxW = Math.max(0, delX - arrowX - 6);
      if (font.width(arrow) > arrowMaxW) {
        arrow = font.plainSubstrByWidth(arrow, Math.max(0, arrowMaxW - 6)) + "…";
      }
      ctx.text(
          font,
          Component.literal(arrow),
          arrowX,
          curY + (rowH - 9) / 2,
          KeysetTheme.withAlpha(
              invalidRule ? KeysetTheme.TEXT_DISABLED : KeysetTheme.TEXT_MUTED, screenAlpha),
          true);

      // Delete button [✕]
      boolean delHov =
          !isOverTutorialPanel(mx, my)
              && mx >= delX
              && mx < delX + delW
              && my >= delY
              && my < delY + 14;
      ctx.fill(
          delX,
          delY,
          delX + delW,
          delY + 14,
          KeysetTheme.withAlpha(
              delHov ? KeysetTheme.CHIP_ERR_BG : KeysetTheme.BG_SURFACE, screenAlpha));
      ctx.outline(
          delX,
          delY,
          delW,
          14,
          KeysetTheme.withAlpha(
              delHov ? KeysetTheme.CHIP_ERR_BR : KeysetTheme.BORDER, screenAlpha));
      ctx.centeredText(
          font,
          Component.literal("✕"),
          delX + delW / 2,
          delY + 3,
          KeysetTheme.withAlpha(delHov ? KeysetTheme.ERROR : KeysetTheme.TEXT_MUTED, screenAlpha));
      int targetY = Math.max(delY, listTop);
      int targetH = Math.min(delY + 14, listBot) - targetY;
      if (targetH > 0) {
        autoSwitchTargets.add(new AutoSwitchTarget(false, i, delX, targetY, delW, targetH));
      }

      // Row divider
      ctx.fill(
          rowX + 4,
          curY + rowH - 1,
          rowX + rowW - 4,
          curY + rowH,
          KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
      curY += rowH;
    }
    ctx.disableScissor();
  }

  // ── Input ────────────────────────────────────────────────────────────────────

  @Override
  public boolean mouseScrolled(
      double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
    if (tutorialActive && tutorialStep == TutorialStep.INTRO) return true;
    if (tutorialActive && tutorialStep != TutorialStep.DONE && tutHlX >= 0) {
      boolean inLit =
          mouseX >= tutHlX
              && mouseX < tutHlX + tutHlW
              && mouseY >= tutHlY
              && mouseY < tutHlY + tutHlH;
      if (!inLit) return true;
    }
    if (mouseX >= sidebarX && mouseX < sidebarX + sidebarW) {
      sidebarScrollTarget -= (float) (verticalAmount * KeysetTheme.ROW_H);
      return true;
    }
    if (mouseX >= mainX && mouseX < mainX + mainW) {
      if (currentTab == Tab.SHARE) {
        if (shareDropdownOpen && mouseInShareDropdown(mouseX, mouseY)) {
          shareDropdownScrollTarget -= (float) verticalAmount;
          return true;
        }
        if (codesExpanded && !shareHistory.isEmpty() && mouseInShareHistory(mouseX, mouseY)) {
          shareHistoryScrollTarget -= (float) (verticalAmount * KeysetTheme.ROW_H);
          return true;
        }
      }
      int listTop = contentY + KeysetTheme.GAP + 18 + KeysetTheme.GAP_SM;
      int listBot = mainY + mainH - KeysetTheme.GAP_SM;
      if (mouseY >= listTop && mouseY < listBot) {
        if (currentTab == Tab.BINDINGS) {
          bindingsScrollTarget -= (float) (verticalAmount * KeysetTheme.ROW_H);
        } else if (currentTab == Tab.CONFLICTS) {
          conflictsScrollTarget -= (float) (verticalAmount * KeysetTheme.ROW_H);
        }
        return true;
      }
    }
    return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    double mouseX = event.x();
    double mouseY = event.y();
    int button = event.button();
    int mx = (int) mouseX;
    int my = (int) mouseY;
    if (width < 400 || height < 280) return super.mouseClicked(event, doubleClick);

    // Search clear (✕) button
    if (button == 0) {
      KeysetTextFieldWidget activeSearch =
          currentTab == Tab.BINDINGS
              ? bindingsSearch
              : currentTab == Tab.CONFLICTS ? conflictsSearch : null;
      if (activeSearch != null && !activeSearch.getValue().isEmpty()) {
        int cx2 = activeSearch.getRight() - 10;
        int cy2 = activeSearch.getY() + (activeSearch.getHeight() - 9) / 2;
        if (mx >= cx2 - 2 && mx < cx2 + 9 && my >= cy2 - 1 && my < cy2 + 10) {
          activeSearch.setValue("");
          return true;
        }
      }
    }

    if (tutorialActive && tutorialStep != TutorialStep.DONE && button == 0) {
      if (tutorialStep == TutorialStep.INTRO) {
        int buttonY = height / 2 + 56;
        int startX = width / 2 - 128;
        int skipX = width / 2 + 8;
        if (mx >= startX && mx < startX + 120 && my >= buttonY && my < buttonY + 22) {
          advanceTutorial();
          return true;
        }
        if (mx >= skipX && mx < skipX + 120 && my >= buttonY && my < buttonY + 22) {
          closeTutorial();
          return true;
        }
        return true;
      }
      int tpw = tutorialPanelWidth();
      int tph = tutorialPanelHeight();
      int tpx = mainX + mainW - tpw - 10;
      int tpy = mainY + mainH - tph - 10;
      if (mx >= tpx && mx < tpx + tpw && my >= tpy && my < tpy + tph) {
        if (mx >= tpx + 5 && mx < tpx + 15 && my >= tpy + 4 && my < tpy + 14) {
          closeTutorial();
          return true;
        }
        int nbx = tpx + tpw - 76, nby = tpy + tph - 22;
        if (mx >= nbx && mx < nbx + 70 && my >= nby && my < nby + 16 && tutNextEnabled) {
          advanceTutorial();
          return true;
        }
        int sbx = tpx + 6, sby = tpy + tph - 22, sbw = 66;
        if (mx >= sbx && mx < sbx + sbw && my >= sby && my < sby + 16) {
          skipTutorialStep();
          return true;
        }
        return true;
      }
      if (tutHlX >= 0) {
        boolean inLit =
            mouseX >= tutHlX
                && mouseX < tutHlX + tutHlW
                && mouseY >= tutHlY
                && mouseY < tutHlY + tutHlH;
        if (!inLit) return true;
      }
    }

    // Done button
    int dbw = 60, dbh = 18;
    int dbx = width - dbw - KeysetTheme.PAD;
    int dby = height - dbh - KeysetTheme.PAD;
    if (button == 0 && mouseX >= dbx && mouseX < dbx + dbw && mouseY >= dby && mouseY < dby + dbh) {
      onClose();
      return true;
    }

    // Sidebar profile list click
    if (button == 0 && mouseX >= sidebarX && mouseX < sidebarX + sidebarW && minecraft != null) {
      int listTop = sidebarY + 20;
      int listBot = sidebarY + mainH - 92;
      if (mouseY >= listTop && mouseY < listBot) {
        try {
          var profiles =
              new ArrayList<KeysetProfile>(service.getConfig(minecraft).getProfiles().values());
          int idx = (int) ((mouseY - listTop + sidebarScrollSmooth) / KeysetTheme.ROW_H);
          if (idx >= 0 && idx < profiles.size()) {
            selectedProfileId = profiles.get(idx).getId();
            profileStateChanged();
            return true;
          }
        } catch (IOException e) {
          setStatus(e.getMessage(), true);
          return true;
        }
      }
    }

    // Sidebar custom buttons
    if (button == 0) {
      for (SidebarBtn btn : sidebarButtons) {
        if (mx >= btn.x() && mx < btn.x() + btn.w() && my >= btn.y() && my < btn.y() + btn.h()) {
          btn.action().run();
          return true;
        }
      }
    }

    // Tab bar
    if (button == 0
        && mouseY >= tabBarY
        && mouseY < tabBarY + KeysetTheme.TAB_H
        && mouseX >= mainX
        && mouseX < mainX + mainW) {
      int idx = Mth.clamp((int) ((mouseX - mainX) / (mainW / 4)), 0, 3);
      currentTab = Tab.values()[idx];
      tabUnderlineTarget = mainX + idx * (mainW / 4f);
      bindingsScrollTarget = 0;
      bindingsScrollSmooth = 0;
      conflictsScrollTarget = 0;
      conflictsScrollSmooth = 0;
      shareDropdownOpen = false;
      if (currentTab == Tab.SHARE && shareTargetProfileId == null) {
        shareTargetProfileId = selectedProfileId;
      }
      if (currentTab == Tab.SHARE) {
        didUseShareTab = true;
      }
      rebuildTabWidgets();
      return true;
    }

    // Bindings tab: group toggle
    if (button == 0 && currentTab == Tab.BINDINGS) {
      int toggleW = 72;
      int toggleX = mainX + mainW - KeysetTheme.GAP - toggleW;
      int toggleY = contentY + KeysetTheme.GAP;
      if (mx >= toggleX && mx < toggleX + toggleW && my >= toggleY && my < toggleY + 18) {
        bindingsGroupByCategory = !bindingsGroupByCategory;
        bindingsScrollTarget = 0;
        bindingsScrollSmooth = 0;
        return true;
      }
    }

    // Auto-switch tab: hit targets
    if (button == 0 && currentTab == Tab.AUTO_SWITCH) {
      for (AutoSwitchTarget t : autoSwitchTargets) {
        if (mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + t.h()) {
          if (t.isAddBtn()) {
            minecraft.setScreen(new AddRuleDialog(this, service));
          } else {
            try {
              service.deleteAutoSwitchRule(minecraft, t.deleteIndex());
            } catch (IOException e) {
              setStatus(e.getMessage(), true);
            }
          }
          return true;
        }
      }
    }

    // Conflicts tab: hit targets
    if (button == 0 && currentTab == Tab.CONFLICTS) {
      for (ConflictTarget t : conflictTargets) {
        if (mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + t.h()) {
          if (t.isGroupHeader()) {
            if (expandedConflictGroups.contains(t.groupKey())) {
              expandedConflictGroups.remove(t.groupKey());
            } else {
              expandedConflictGroups.add(t.groupKey());
            }
          } else {
            openConflictDialog(t);
          }
          return true;
        }
      }
    }

    // Share tab: hit targets
    if (button == 0 && currentTab == Tab.SHARE) {
      if (shareDropdownOpen) {
        for (ShareTarget t : shareTargets) {
          if (mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + t.h()) {
            if (t.id().startsWith("profile-")) {
              String newId = t.id().substring(8);
              if (!newId.equals(shareTargetProfileId)) {
                shareTargetProfileId = newId;
                shareResultCode = "";
                shareExpiresAt = 0L;
                shareUploading = false;
              }
            }
            shareDropdownOpen = false;
            return true;
          }
        }
        shareDropdownOpen = false;
        return true;
      }
      for (ShareTarget t : shareTargets) {
        if (mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + t.h()) {
          String tid = t.id();
          if (tid.equals("gen")) doShareUpload();
          else if (tid.equals("copy")) doShareCopy();
          else if (tid.equals("import")) doShareImport();
          else if (tid.equals("selector")) shareDropdownOpen = !shareDropdownOpen;
          else if (tid.startsWith("profile-")) {
            String newId = tid.substring(8);
            if (!newId.equals(shareTargetProfileId)) {
              shareTargetProfileId = newId;
              shareResultCode = "";
              shareExpiresAt = 0L;
              shareUploading = false;
            }
            shareDropdownOpen = false;
          } else if (tid.equals("codes-header")) {
            codesExpanded = !codesExpanded;
            clearWidgets();
            init();
          } else if (tid.startsWith("del-")) {
            int idx = Integer.parseInt(tid.substring(4));
            if (idx >= 0 && idx < shareHistory.size()) {
              shareHistory.remove(idx);
              if (!ShareHistoryStore.save(shareHistoryPath(), shareHistory)) {
                setStatus("Could not save share history.", true);
              }
              if (codesExpanded) {
                clearWidgets();
                init();
              }
            }
          }
          return true;
        }
      }
    }

    return super.mouseClicked(event, doubleClick);
  }

  // ── Share tab ─────────────────────────────────────────────────────────────────

  private void renderShareTab(GuiGraphicsExtractor ctx, int mx, int my) {
    shareTargets.clear();
    shareHistoryScrollX = 0;
    shareHistoryScrollY = 0;
    shareHistoryScrollW = 0;
    shareHistoryScrollH = 0;

    int pad = KeysetTheme.PAD;
    int gap = KeysetTheme.GAP;
    int gapSm = KeysetTheme.GAP_SM;
    int sectionGap = gap * 2;
    int sep = 1;

    int tabContentX = mainX + pad;
    int tabContentY = contentY + pad;
    int tabContentW = mainW - pad * 2;
    int tabContentH = mainY + mainH - tabContentY - pad;

    int elemW = (int) (tabContentW * 0.55f);
    int btnW = 90;
    int rowH = 22;
    int labelH = 9;
    int codeBoxH = 36;
    int copyBtnH = 20;

    // ── Content height ────────────────────────────────────────────────────────
    int shareH = labelH + gapSm + rowH;
    if (!shareResultCode.isEmpty()) {
      shareH += gap + codeBoxH + gapSm + labelH + gapSm + copyBtnH;
    }
    int importH = labelH + gapSm + rowH;
    int codesH = labelH;
    if (codesExpanded && !shareHistory.isEmpty()) codesH += gapSm + shareHistory.size() * rowH;
    int sepBlock = sectionGap + sep + sectionGap;
    int totalH = shareH + sepBlock + importH + sepBlock + codesH;

    // ── Vertical center ───────────────────────────────────────────────────────
    int curY = tabContentY + Math.max(0, (tabContentH - totalH) / 2);
    int cx = tabContentX + tabContentW / 2;

    // ── SHARE NEW ─────────────────────────────────────────────────────────────
    ctx.centeredText(
        font,
        Component.translatable("keyset.share.section.share"),
        cx,
        curY,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));
    curY += labelH + gapSm;

    if (shareTargetProfileId == null) shareTargetProfileId = selectedProfileId;
    String profileDisplayName = "(select profile)";
    boolean profileHasBindings = false;
    if (shareTargetProfileId != null && minecraft != null) {
      try {
        KeysetProfile prof = service.getConfig(minecraft).getProfile(shareTargetProfileId);
        if (prof != null) {
          profileDisplayName = prof.getName();
          profileHasBindings = prof.getBindings() != null && !prof.getBindings().isEmpty();
        }
      } catch (IOException ignored) {
      }
    }

    int rowTotalW = elemW + gap + btnW;
    int rowX = tabContentX + (tabContentW - rowTotalW) / 2;
    int selY = curY;
    boolean selHov =
        !isOverTutorialPanel(mx, my)
            && mx >= rowX
            && mx < rowX + elemW
            && my >= selY
            && my < selY + rowH;
    ctx.fill(
        rowX,
        selY,
        rowX + elemW,
        selY + rowH,
        KeysetTheme.withAlpha(selHov ? KeysetTheme.BG_HOVER : KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.outline(
        rowX,
        selY,
        elemW,
        rowH,
        KeysetTheme.withAlpha(
            shareDropdownOpen ? KeysetTheme.ACCENT : KeysetTheme.BORDER, screenAlpha));
    ctx.text(
        font,
        Component.literal("▾ " + profileDisplayName),
        rowX + 4,
        selY + 7,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha),
        true);
    shareTargets.add(new ShareTarget("selector", rowX, selY, elemW, rowH));
    shareDropdownRowX = rowX;
    shareDropdownSelY = selY;
    shareDropdownElemW = elemW;

    int shareBtnX = rowX + elemW + gap;
    boolean shareDisabled = shareUploading || shareTargetProfileId == null || !profileHasBindings;
    boolean shareBtnHov =
        !shareDisabled
            && !isOverTutorialPanel(mx, my)
            && mx >= shareBtnX
            && mx < shareBtnX + btnW
            && my >= selY
            && my < selY + rowH;
    ctx.fill(
        shareBtnX,
        selY,
        shareBtnX + btnW,
        selY + rowH,
        KeysetTheme.withAlpha(
            shareBtnHov ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE,
            shareDisabled ? screenAlpha * 0.5f : screenAlpha));
    ctx.outline(
        shareBtnX,
        selY,
        btnW,
        rowH,
        KeysetTheme.withAlpha(
            shareBtnHov ? KeysetTheme.ACCENT : KeysetTheme.BORDER,
            shareDisabled ? screenAlpha * 0.5f : screenAlpha));
    if (shareUploading) {
      String[] frames = {"|", "/", "─", "\\"};
      int frame = (int) ((System.currentTimeMillis() / 150) % frames.length);
      ctx.centeredText(
          font,
          Component.literal(frames[frame]),
          shareBtnX + btnW / 2,
          selY + 6,
          KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));
    } else {
      ctx.centeredText(
          font,
          Component.translatable("keyset.share.generate"),
          shareBtnX + btnW / 2,
          selY + 6,
          KeysetTheme.withAlpha(
              shareDisabled ? KeysetTheme.TEXT_DISABLED : KeysetTheme.TEXT_BODY, screenAlpha));
    }
    if (!shareDisabled) shareTargets.add(new ShareTarget("gen", shareBtnX, selY, btnW, rowH));

    // Profile dropdown overlay — drawn after super.extractRenderState() in render() to stay on top
    if (shareDropdownOpen && minecraft != null) {
      java.util.List<KeysetProfile> profiles;
      try {
        profiles = new ArrayList<>(service.getConfig(minecraft).getProfiles().values());
      } catch (IOException e) {
        profiles = Collections.emptyList();
      }
      int dropItemH = 18;
      int dropY = shareDropdownSelY + 22;
      int maxVisible =
          Math.max(
              1,
              Math.min(
                  SHARE_DROPDOWN_MAX_VISIBLE,
                  (mainY + mainH - KeysetTheme.PAD - dropY - 4) / dropItemH));
      int visibleCount = Math.min(profiles.size(), maxVisible);
      int maxScrollIndex = Math.max(0, profiles.size() - visibleCount);
      shareDropdownScrollTarget = Mth.clamp(shareDropdownScrollTarget, 0, maxScrollIndex);
      shareDropdownScrollSmooth =
          KeysetTheme.expLerp(shareDropdownScrollSmooth, shareDropdownScrollTarget, frameDt, 22f);
      int firstProfile = Math.min(maxScrollIndex, Math.max(0, (int) shareDropdownScrollSmooth));
      for (int i = 0; i < visibleCount; i++) {
        KeysetProfile p = profiles.get(firstProfile + i);
        int itemY = dropY + 2 + i * dropItemH;
        shareTargets.add(new ShareTarget("profile-" + p.getId(), rowX, itemY, elemW, dropItemH));
      }
    }

    curY += rowH;

    // Code result box
    if (!shareResultCode.isEmpty()) {
      curY += gap;
      String displayCode =
          shareResultCode.length() >= 8
              ? shareResultCode.substring(0, 4) + "-" + shareResultCode.substring(4)
              : shareResultCode;
      int codeBoxW = Math.min(tabContentW - 80, 400);
      int codeBoxX = tabContentX + (tabContentW - codeBoxW) / 2;
      ctx.fill(
          codeBoxX,
          curY,
          codeBoxX + codeBoxW,
          curY + codeBoxH,
          KeysetTheme.withAlpha(KeysetTheme.CHIP_BG, screenAlpha));
      ctx.outline(
          codeBoxX,
          curY,
          codeBoxW,
          codeBoxH,
          KeysetTheme.withAlpha(KeysetTheme.ACCENT_DIM, screenAlpha));
      ctx.centeredText(
          font,
          Component.literal(displayCode),
          cx,
          curY + (codeBoxH - 9) / 2,
          KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));
      curY += codeBoxH + gapSm;
      long daysLeft = Math.max(0, (shareExpiresAt - System.currentTimeMillis()) / 86_400_000L);
      ctx.centeredText(
          font,
          Component.literal("Expires in " + daysLeft + " days"),
          cx,
          curY,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));
      curY += labelH + gapSm;
      int copyW = 100;
      int copyX = tabContentX + (tabContentW - copyW) / 2;
      boolean copyHov =
          !isOverTutorialPanel(mx, my)
              && mx >= copyX
              && mx < copyX + copyW
              && my >= curY
              && my < curY + copyBtnH;
      ctx.fill(
          copyX,
          curY,
          copyX + copyW,
          curY + copyBtnH,
          KeysetTheme.withAlpha(
              copyHov ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE, screenAlpha));
      ctx.outline(
          copyX,
          curY,
          copyW,
          copyBtnH,
          KeysetTheme.withAlpha(copyHov ? KeysetTheme.ACCENT : KeysetTheme.BORDER, screenAlpha));
      ctx.centeredText(
          font,
          Component.literal("Copy"),
          copyX + copyW / 2,
          curY + 6,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));
      shareTargets.add(new ShareTarget("copy", copyX, curY, copyW, copyBtnH));
      curY += copyBtnH;
    }

    // ── Separator ─────────────────────────────────────────────────────────────
    curY += sectionGap;
    ctx.fill(
        tabContentX + 40,
        curY,
        tabContentX + tabContentW - 40,
        curY + sep,
        KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
    curY += sep + sectionGap;

    // ── IMPORT ────────────────────────────────────────────────────────────────
    ctx.centeredText(
        font,
        Component.translatable("keyset.share.section.import"),
        cx,
        curY,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));
    curY += labelH + gapSm;

    int impTotalW = elemW + gap + btnW;
    int impX = tabContentX + (tabContentW - impTotalW) / 2;
    int importRowY = curY;
    int importBtnX = impX + elemW + gap;
    boolean importDisabled = shareDownloading;
    boolean importHov =
        !importDisabled
            && !isOverTutorialPanel(mx, my)
            && mx >= importBtnX
            && mx < importBtnX + btnW
            && my >= importRowY
            && my < importRowY + rowH;
    ctx.fill(
        importBtnX,
        importRowY,
        importBtnX + btnW,
        importRowY + rowH,
        KeysetTheme.withAlpha(
            importHov ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE,
            importDisabled ? screenAlpha * 0.5f : screenAlpha));
    ctx.outline(
        importBtnX,
        importRowY,
        btnW,
        rowH,
        KeysetTheme.withAlpha(
            importHov ? KeysetTheme.ACCENT : KeysetTheme.BORDER,
            importDisabled ? screenAlpha * 0.5f : screenAlpha));
    if (shareDownloading) {
      String[] frames = {"|", "/", "─", "\\"};
      int frame = (int) ((System.currentTimeMillis() / 150) % frames.length);
      ctx.centeredText(
          font,
          Component.literal(frames[frame]),
          importBtnX + btnW / 2,
          importRowY + (rowH - 9) / 2,
          KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));
    } else {
      ctx.centeredText(
          font,
          Component.translatable("keyset.share.import"),
          importBtnX + btnW / 2,
          importRowY + (rowH - 9) / 2,
          KeysetTheme.withAlpha(
              importDisabled ? KeysetTheme.TEXT_DISABLED : KeysetTheme.TEXT_BODY, screenAlpha));
    }
    if (!importDisabled)
      shareTargets.add(new ShareTarget("import", importBtnX, importRowY, btnW, rowH));
    curY += rowH;

    // ── Separator ─────────────────────────────────────────────────────────────
    curY += sectionGap;
    ctx.fill(
        tabContentX + 40,
        curY,
        tabContentX + tabContentW - 40,
        curY + sep,
        KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
    curY += sep + sectionGap;

    // ── YOUR CODES ────────────────────────────────────────────────────────────
    String codesLabel = "YOUR CODES " + (codesExpanded ? "▾" : "▸");
    boolean codesHeaderHov =
        !isOverTutorialPanel(mx, my)
            && mx >= tabContentX
            && mx < tabContentX + tabContentW
            && my >= curY
            && my < curY + labelH;
    ctx.centeredText(
        font,
        Component.literal(codesLabel),
        cx,
        curY,
        KeysetTheme.withAlpha(
            codesHeaderHov ? KeysetTheme.TEXT_BODY : KeysetTheme.TEXT_MUTED, screenAlpha));
    shareTargets.add(new ShareTarget("codes-header", tabContentX, curY, tabContentW, labelH));
    curY += labelH;

    if (codesExpanded) {
      if (shareHistory.isEmpty()) {
        ctx.centeredText(
            font,
            Component.literal("No shared profiles yet."),
            cx,
            curY + gapSm + 3,
            KeysetTheme.withAlpha(KeysetTheme.TEXT_DISABLED, screenAlpha));
      } else {
        curY += gapSm;
        int histBottom = tabContentY + tabContentH;
        shareHistoryScrollX = tabContentX;
        shareHistoryScrollY = curY;
        shareHistoryScrollW = tabContentW;
        shareHistoryScrollH = Math.max(0, histBottom - curY);
        int histViewportH = Math.max(0, histBottom - curY);
        int maxScroll = Math.max(0, shareHistory.size() * rowH - histViewportH);
        shareHistoryScrollTarget = Mth.clamp(shareHistoryScrollTarget, 0, maxScroll);
        shareHistoryScrollSmooth =
            KeysetTheme.expLerp(shareHistoryScrollSmooth, shareHistoryScrollTarget, frameDt, 22f);
        ctx.enableScissor(tabContentX, curY, tabContentX + tabContentW, histBottom);
        int delBtnW = 12, delBtnH = 12;
        int codeColW = font.width("ABCD-EFGH") + 10;
        int expiresColW = font.width("000d") + 6;
        for (int i = 0; i < shareHistory.size(); i++) {
          ShareHistoryStore.Entry entry = shareHistory.get(i);
          int rowY = curY + i * rowH - (int) shareHistoryScrollSmooth;
          boolean rowHov =
              !isOverTutorialPanel(mx, my)
                  && mx >= tabContentX
                  && mx < tabContentX + tabContentW - delBtnW - gapSm
                  && my >= rowY
                  && my < rowY + rowH;
          if (rowHov) {
            ctx.fill(
                tabContentX,
                rowY,
                tabContentX + tabContentW,
                rowY + rowH,
                KeysetTheme.withAlpha(KeysetTheme.BG_HOVER, screenAlpha));
          }
          long daysLeft =
              Math.max(0, (entry.expiresAt() - System.currentTimeMillis()) / 86_400_000L);
          int maxNameW = tabContentW - codeColW - expiresColW - delBtnW - gapSm * 3;
          String nameStr = entry.profileName();
          if (font.width(nameStr) > maxNameW)
            nameStr = font.plainSubstrByWidth(nameStr, maxNameW - 6) + "…";
          ctx.text(
              font,
              Component.literal(nameStr),
              tabContentX,
              rowY + 6,
              KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha),
              true);
          String code = entry.code();
          String dispCode =
              code.length() >= 8 ? code.substring(0, 4) + "-" + code.substring(4) : code;
          int chipX = tabContentX + maxNameW + gapSm;
          ctx.fill(
              chipX,
              rowY + 4,
              chipX + codeColW,
              rowY + rowH - 4,
              KeysetTheme.withAlpha(KeysetTheme.CHIP_BG, screenAlpha));
          ctx.outline(
              chipX,
              rowY + 4,
              codeColW,
              rowH - 8,
              KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
          ctx.centeredText(
              font,
              Component.literal(dispCode),
              chipX + codeColW / 2,
              rowY + 7,
              KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));
          int exColX = chipX + codeColW + gapSm;
          int dayColor =
              daysLeft > 30
                  ? KeysetTheme.SUCCESS
                  : daysLeft > 10 ? KeysetTheme.WARNING : KeysetTheme.ERROR;
          ctx.text(
              font,
              Component.literal(daysLeft > 0 ? daysLeft + "d" : "exp"),
              exColX,
              rowY + 6,
              KeysetTheme.withAlpha(dayColor, screenAlpha),
              true);
          int delX = tabContentX + tabContentW - delBtnW;
          int delY = rowY + (rowH - delBtnH) / 2;
          boolean delHov =
              !isOverTutorialPanel(mx, my)
                  && mx >= delX
                  && mx < delX + delBtnW
                  && my >= delY
                  && my < delY + delBtnH;
          ctx.fill(
              delX,
              delY,
              delX + delBtnW,
              delY + delBtnH,
              KeysetTheme.withAlpha(
                  delHov ? KeysetTheme.CHIP_ERR_BG : KeysetTheme.BG_SURFACE, screenAlpha));
          ctx.outline(
              delX,
              delY,
              delBtnW,
              delBtnH,
              KeysetTheme.withAlpha(
                  delHov ? KeysetTheme.CHIP_ERR_BR : KeysetTheme.BORDER, screenAlpha));
          ctx.centeredText(
              font,
              Component.literal("✕"),
              delX + delBtnW / 2,
              delY + 1,
              KeysetTheme.withAlpha(KeysetTheme.ERROR, screenAlpha));
          if (rowY + rowH > curY && rowY < histBottom) {
            int targetY = Math.max(delY, curY);
            int targetH = Math.min(delY + delBtnH, histBottom) - targetY;
            if (targetH > 0) {
              shareTargets.add(new ShareTarget("del-" + i, delX, targetY, delBtnW, targetH));
            }
          }
        }
        ctx.disableScissor();
      }
    }
  }

  private void renderShareDropdownOverlay(GuiGraphicsExtractor ctx, int mx, int my) {
    if (!shareDropdownOpen || minecraft == null) return;
    java.util.List<KeysetProfile> profiles;
    try {
      profiles = new ArrayList<>(service.getConfig(minecraft).getProfiles().values());
    } catch (IOException e) {
      profiles = Collections.emptyList();
    }
    int rowX = shareDropdownRowX;
    int selY = shareDropdownSelY;
    int elemW = shareDropdownElemW;
    int dropItemH = 18;
    int dropY = selY + 22;
    int maxVisible =
        Math.max(
            1,
            Math.min(
                SHARE_DROPDOWN_MAX_VISIBLE,
                (mainY + mainH - KeysetTheme.PAD - dropY - 4) / dropItemH));
    int visibleCount = Math.min(profiles.size(), maxVisible);
    int maxScrollIndex = Math.max(0, profiles.size() - visibleCount);
    shareDropdownScrollTarget = Mth.clamp(shareDropdownScrollTarget, 0, maxScrollIndex);
    shareDropdownScrollSmooth =
        KeysetTheme.expLerp(shareDropdownScrollSmooth, shareDropdownScrollTarget, frameDt, 22f);
    int firstProfile = Math.min(maxScrollIndex, Math.max(0, (int) shareDropdownScrollSmooth));
    int dropH = visibleCount * dropItemH + 4;
    ctx.fill(
        mainX + KeysetTheme.PAD,
        dropY,
        mainX + mainW - KeysetTheme.PAD,
        mainY + mainH - KeysetTheme.PAD,
        KeysetTheme.withAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.fill(
        rowX,
        dropY,
        rowX + elemW,
        dropY + dropH,
        KeysetTheme.withAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.outline(rowX, dropY, elemW, dropH, KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));
    ctx.enableScissor(rowX, dropY, rowX + elemW, dropY + dropH);
    for (int i = 0; i < visibleCount; i++) {
      KeysetProfile p = profiles.get(firstProfile + i);
      int itemY = dropY + 2 + i * dropItemH;
      boolean itemHov =
          !isOverTutorialPanel(mx, my)
              && mx >= rowX
              && mx < rowX + elemW
              && my >= itemY
              && my < itemY + dropItemH;
      if (itemHov) {
        ctx.fill(
            rowX + 1,
            itemY,
            rowX + elemW - 1,
            itemY + dropItemH,
            KeysetTheme.withAlpha(KeysetTheme.BG_HOVER, screenAlpha));
      }
      boolean isSel = p.getId().equals(shareTargetProfileId);
      boolean pCanShare = p.getBindings() != null && !p.getBindings().isEmpty();
      int itemColor =
          isSel
              ? KeysetTheme.ACCENT
              : (pCanShare ? KeysetTheme.TEXT_BODY : KeysetTheme.TEXT_DISABLED);
      ctx.text(
          font,
          Component.literal((isSel ? "✓ " : "  ") + p.getName() + (pCanShare ? "" : " (empty)")),
          rowX + 6,
          itemY + 5,
          KeysetTheme.withAlpha(itemColor, screenAlpha),
          true);
    }
    ctx.disableScissor();
  }

  private Path shareHistoryPath() {
    if (minecraft == null) return Path.of("config", "keyset-share-history.json");
    return minecraft.gameDirectory.toPath().resolve("config/keyset-share-history.json");
  }

  private boolean mouseInShareDropdown(double mouseX, double mouseY) {
    int dropItemH = 18;
    int dropY = shareDropdownSelY + 22;
    int maxVisible =
        Math.max(
            1,
            Math.min(
                SHARE_DROPDOWN_MAX_VISIBLE,
                (mainY + mainH - KeysetTheme.PAD - dropY - 4) / dropItemH));
    int dropH = maxVisible * dropItemH + 4;
    return mouseX >= shareDropdownRowX
        && mouseX < shareDropdownRowX + shareDropdownElemW
        && mouseY >= dropY
        && mouseY < dropY + Math.max(dropH, dropItemH + 4);
  }

  private boolean mouseInShareHistory(double mouseX, double mouseY) {
    return shareHistoryScrollW > 0
        && shareHistoryScrollH > 0
        && mouseX >= shareHistoryScrollX
        && mouseX < shareHistoryScrollX + shareHistoryScrollW
        && mouseY >= shareHistoryScrollY
        && mouseY < shareHistoryScrollY + shareHistoryScrollH;
  }

  private void doShareUpload() {
    if (shareUploading || shareTargetProfileId == null || minecraft == null) return;
    String json;
    String profileName;
    try {
      KeysetProfile prof = service.getConfig(minecraft).getProfile(shareTargetProfileId);
      if (prof == null || prof.getBindings() == null || prof.getBindings().isEmpty()) {
        setStatus(Component.translatable("keyset.share.error.empty_profile").getString(), true);
        return;
      }
      json = service.exportShareProfileJson(minecraft, shareTargetProfileId);
      profileName = prof.getName();
    } catch (IOException | IllegalArgumentException e) {
      setStatus("Share: " + e.getMessage(), true);
      return;
    }
    String username = minecraft.getUser().getName();
    String capturedProfileId = shareTargetProfileId;
    String capturedProfileName = profileName;
    shareUploading = true;
    didUseShareTab = true;
    shareResultCode = "";
    ShareApiClient.upload(json, username, profileName)
        .thenAcceptAsync(
            result -> {
              if (minecraft.screen != this) return;
              shareUploading = false;
              shareResultCode = result.code();
              shareExpiresAt = result.expiresAt();
              shareHistory.add(
                  new ShareHistoryStore.Entry(
                      result.code(),
                      capturedProfileName,
                      capturedProfileId,
                      System.currentTimeMillis(),
                      result.expiresAt()));
              if (!ShareHistoryStore.save(shareHistoryPath(), shareHistory)) {
                setStatus("Could not save share history.", true);
              }
              clearWidgets();
              init();
            },
            Minecraft.getInstance()::execute)
        .exceptionally(
            ex -> {
              Minecraft.getInstance()
                  .execute(
                      () -> {
                        if (minecraft.screen != this) return;
                        shareUploading = false;
                        setStatus("Upload failed: " + shareErrorMessage(ex), true);
                      });
              return null;
            });
  }

  private void doShareCopy() {
    if (minecraft == null || shareResultCode.isEmpty()) return;
    minecraft.keyboardHandler.setClipboard(shareResultCode);
    setStatus("Code copied to clipboard.", false);
  }

  private void doShareImport() {
    if (shareDownloading || minecraft == null || shareCodeField == null) return;
    String rawCode = shareCodeField.getValue().replaceAll("[\\s\\-]", "").toUpperCase();
    if (rawCode.length() != 8 || !rawCode.matches("[A-Z0-9]+")) {
      importCodeInvalid = true;
      return;
    }
    shareDownloading = true;
    didUseShareTab = true;
    ShareApiClient.download(rawCode)
        .thenAcceptAsync(
            result -> {
              if (minecraft.screen != this) return;
              shareDownloading = false;
              Set<String> liveKeys = liveBindingIds();
              List<String> missing =
                  ShareApiClient.parseBindingKeys(result.data()).stream()
                      .filter(k -> !liveKeys.contains(k))
                      .collect(Collectors.toList());
              KeysetScreen self = this;
              minecraft.setScreen(
                  new ImportConfirmDialog(
                      self,
                      result.meta().username(),
                      result.meta().profileName().isEmpty()
                          ? "Unknown Profile"
                          : result.meta().profileName(),
                      missing,
                      () -> {
                        try {
                          KeysetFabricService.ImportResult ir =
                              service.importShareProfileJson(minecraft, result.data());
                          selectedProfileId = ir.getLastImportedProfileId();
                          ShareHistoryStore.LoadResult historyLoad =
                              ShareHistoryStore.loadResult(shareHistoryPath());
                          shareHistory = historyLoad.entries();
                          if (historyLoad.recoveredBrokenFile()) {
                            setStatus("Share history was reset; broken file was archived.", true);
                          }
                          setStatus(
                              Component.translatable(
                                      "keyset.status.imported", ir.getImportedCount())
                                  .getString(),
                              false);
                        } catch (IOException | IllegalArgumentException e) {
                          setStatus("Import failed: " + e.getMessage(), true);
                        }
                      },
                      () -> {}));
            },
            Minecraft.getInstance()::execute)
        .exceptionally(
            ex -> {
              Minecraft.getInstance()
                  .execute(
                      () -> {
                        if (minecraft.screen != this) return;
                        shareDownloading = false;
                        setStatus("Download failed: " + shareErrorMessage(ex), true);
                      });
              return null;
            });
  }

  private static String simplifyError(Throwable ex) {
    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
    String msg = t.getMessage();
    return msg != null ? msg : t.getClass().getSimpleName();
  }

  private static String shareErrorMessage(Throwable ex) {
    String msg = simplifyError(ex);
    if (msg.startsWith("keyset.")) return Component.translatable(msg).getString();
    return Component.translatable("keyset.share.error.unknown").getString();
  }

  private void profileStateChanged() {
    lastKeybindHash = computeKeybindHash();
    refreshConflicts();
  }

  private Set<String> liveBindingIds() {
    if (minecraft == null || minecraft.options == null || minecraft.options.keyMappings == null) {
      return Collections.emptySet();
    }
    return Arrays.stream(minecraft.options.keyMappings)
        .map(KeyMapping::getName)
        .collect(Collectors.toSet());
  }

  private String keyDisplayName(String keyToken) {
    if (keyToken == null || keyToken.equals("key.keyboard.unknown")) {
      return Component.translatable("key.keyboard.unknown").getString();
    }
    try {
      return InputConstants.getKey(keyToken).getDisplayName().getString();
    } catch (IllegalArgumentException exception) {
      return keyToken;
    }
  }

  private void openConflictDialog(ConflictTarget t) {
    didOpenConflictDialog = true;
    String activeId;
    try {
      activeId = service.getConfig(minecraft).getActiveProfileId();
    } catch (IOException e) {
      setStatus(e.getMessage(), true);
      return;
    }
    boolean isActive =
        t.bindingId() != null && activeId != null && activeId.equals(selectedProfileId);
    Runnable onClear =
        !isActive
            ? () -> setStatus("Apply this profile first to edit bindings.", true)
            : () -> {
              try {
                service.clearActiveBinding(minecraft, t.bindingId());
                setStatus(
                    Component.translatable("keyset.status.binding_cleared").getString(), false);
              } catch (IOException | IllegalArgumentException e) {
                setStatus(e.getMessage(), true);
              }
            };
    Runnable onJump =
        t.bindingId() == null
            ? () -> {}
            : () ->
                minecraft.setScreen(
                    new KeysetKeybindsScreen(
                        this, minecraft.options, service, t.bindingId(), false));
    minecraft.setScreen(
        new ConflictDialog(this, t.keyLabel(), t.actionName(), t.otherActions(), onClear, onJump));
  }

  // ── Render helpers ───────────────────────────────────────────────────────────

  private void renderDoneButton(GuiGraphicsExtractor ctx, int mx, int my) {
    int bw = 60, bh = 18;
    int bx = width - bw - KeysetTheme.PAD;
    int by = height - bh - KeysetTheme.PAD;
    boolean hovered =
        !isOverTutorialPanel(mx, my) && mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    ctx.fill(
        bx,
        by,
        bx + bw,
        by + bh,
        hovered
            ? KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha * 0.9f)
            : KeysetTheme.withAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.outline(
        bx,
        by,
        bw,
        bh,
        KeysetTheme.withAlpha(hovered ? KeysetTheme.ACCENT : KeysetTheme.BORDER, screenAlpha));
    Component doneText = Component.translatable("keyset.action.done");
    int doneCol = KeysetTheme.withAlpha(hovered ? 0xFF1A0800 : KeysetTheme.TEXT_BODY, screenAlpha);
    if (hovered) {
      ctx.text(
          font,
          doneText,
          bx + bw / 2 - font.width(doneText) / 2,
          by + 5,
          doneCol,
          false); // no shadow on amber bg
    } else {
      ctx.centeredText(font, doneText, bx + bw / 2, by + 5, doneCol);
    }
  }

  private void renderFooter(GuiGraphicsExtractor ctx) {
    long now = System.currentTimeMillis();
    toastQueue.removeIf(e -> now - e.createdMs() > 10_400L);
    if (toastQueue.isEmpty()) return;

    int toastW =
        Math.min(Math.max(260, width / 2), Math.max(120, width - KeysetTheme.PAD * 2 - 80));
    int toastH = 18;
    int toastGap = 4;
    int baseX = KeysetTheme.PAD;
    int baseY = height - KeysetTheme.FOOTER_H - KeysetTheme.PAD;

    for (int i = toastQueue.size() - 1; i >= 0; i--) {
      ToastEntry e = toastQueue.get(i);
      long age = now - e.createdMs();

      float target;
      if (age < 200L) {
        float t = (float) age / 200f;
        target = 1f - (1f - t) * (1f - t); // ease-out quad slide in
      } else if (age > 10_000L) {
        target = 1f - Math.min(1f, (float) (age - 10_000L) / 300f);
      } else {
        target = 1f;
      }

      float smooth = KeysetTheme.expLerp(e.slideProgress(), target, frameDt, 30f);
      toastQueue.set(i, new ToastEntry(e.msg(), e.error(), e.createdMs(), smooth));

      int rowOffset = (toastQueue.size() - 1 - i) * (toastH + toastGap);
      int slideOffset = (int) ((1f - smooth) * (toastH + 20));
      int ty = baseY - rowOffset + slideOffset;
      if (ty > height) continue;

      int bg = e.error() ? 0xFF2A1010 : 0xFF0E2018;
      int border = e.error() ? KeysetTheme.ERROR : KeysetTheme.SUCCESS;

      ctx.fill(baseX, ty, baseX + toastW, ty + toastH, KeysetTheme.withAlpha(bg, smooth));
      ctx.outline(baseX, ty, toastW, toastH, KeysetTheme.withAlpha(border, smooth));
      ctx.fill(baseX, ty, baseX + 2, ty + toastH, KeysetTheme.withAlpha(border, smooth));

      String msgStr = e.msg();
      if (font.width(msgStr) > toastW - 14) {
        msgStr = font.plainSubstrByWidth(msgStr, toastW - 20) + "…";
      }
      ctx.text(
          font,
          Component.literal(msgStr),
          baseX + 7,
          ty + (toastH - 9) / 2,
          KeysetTheme.withAlpha(e.error() ? KeysetTheme.ERROR : KeysetTheme.SUCCESS, smooth),
          true);
    }
  }

  // ── Profile actions ───────────────────────────────────────────────────────────

  private void activateSelected() {
    if (selectedProfileId == null) return;
    try {
      service.activateProfile(minecraft, selectedProfileId);
      didActivate = true;
      profileStateChanged();
      setStatus(Component.translatable("keyset.status.profile_applied").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void saveLiveSelected() {
    if (selectedProfileId == null) return;
    try {
      service.captureCurrentToProfile(minecraft, selectedProfileId, true);
      didSaveLive = true;
      profileStateChanged();
      setStatus(Component.translatable("keyset.status.profile_captured").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void newProfile() {
    try {
      String id = service.createProfileFromCurrent(minecraft, "New Profile");
      selectedProfileId = id;
      profileStateChanged();
      setStatus(Component.translatable("keyset.status.profile_created").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void renameSelected() {
    if (selectedProfileId == null) return;
    KeysetProfilesConfig cfg;
    try {
      cfg = service.getConfig(minecraft);
    } catch (IOException e) {
      setStatus(e.getMessage(), true);
      return;
    }
    KeysetProfile profile = cfg.getProfile(selectedProfileId);
    if (profile == null) return;
    minecraft.setScreen(
        new RenameProfileDialog(this, service, selectedProfileId, profile.getName()));
  }

  private void cloneSelected() {
    if (selectedProfileId == null) return;
    try {
      String id = service.duplicateProfile(minecraft, selectedProfileId);
      selectedProfileId = id;
      profileStateChanged();
      setStatus(Component.translatable("keyset.status.profile_duplicated").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void confirmDelete() {
    if (selectedProfileId == null) return;
    KeysetProfilesConfig cfg;
    try {
      cfg = service.getConfig(minecraft);
    } catch (IOException e) {
      setStatus(e.getMessage(), true);
      return;
    }
    KeysetProfile profile = cfg.getProfile(selectedProfileId);
    String name = profile != null ? profile.getName() : selectedProfileId;
    minecraft.setScreen(
        new KeysetConfirmDialog(
            this,
            Component.translatable("keyset.confirm.delete_profile"),
            Component.translatable("keyset.confirm.delete_body", name),
            () -> {
              try {
                service.deleteProfile(minecraft, selectedProfileId);
                selectedProfileId = service.getConfig(minecraft).getActiveProfileId();
                profileStateChanged();
                setStatus(
                    Component.translatable("keyset.status.profile_deleted").getString(), false);
              } catch (IOException | IllegalArgumentException e) {
                setStatus(e.getMessage(), true);
              }
            }));
  }

  private void moveUp() {
    if (selectedProfileId == null) return;
    try {
      service.moveProfileUp(minecraft, selectedProfileId);
      profileStateChanged();
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void moveDown() {
    if (selectedProfileId == null) return;
    try {
      service.moveProfileDown(minecraft, selectedProfileId);
      profileStateChanged();
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void copyToClipboard() {
    if (selectedProfileId == null) return;
    try {
      minecraft.keyboardHandler.setClipboard(
          service.exportProfileJson(minecraft, selectedProfileId));
      setStatus(Component.translatable("keyset.status.exported").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void pasteFromClipboard() {
    String json = minecraft.keyboardHandler.getClipboard();
    List<String> missing =
        ShareApiClient.parseBindingKeys(json).stream()
            .filter(k -> !liveBindingIds().contains(k))
            .collect(Collectors.toList());
    if (!missing.isEmpty()) {
      KeysetScreen self = this;
      minecraft.setScreen(
          new ImportConfirmDialog(
              self,
              "Clipboard",
              "Clipboard Profile",
              missing,
              () -> importClipboardJson(json),
              () -> {}));
      return;
    }
    importClipboardJson(json);
  }

  private void importClipboardJson(String json) {
    try {
      KeysetFabricService.ImportResult result = service.importProfiles(minecraft, json);
      if (result.getImportedCount() > 0) {
        selectedProfileId = result.getLastImportedProfileId();
        profileStateChanged();
        setStatus(
            Component.translatable("keyset.status.imported", result.getImportedCount()).getString(),
            false);
      }
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  // ── Conflict auto-refresh ────────────────────────────────────────────────────

  private String computeKeybindHash() {
    if (minecraft == null || minecraft.options == null || minecraft.options.keyMappings == null)
      return "";
    StringBuilder sb = new StringBuilder();
    for (KeyMapping kb : minecraft.options.keyMappings) {
      sb.append(kb.getName()).append('=').append(kb.saveString()).append(';');
    }
    return sb.toString();
  }

  private void refreshConflicts() {
    conflictGroups.clear();
    conflictsScrollTarget = 0;
    conflictsScrollSmooth = 0;
    if (minecraft == null || minecraft.options == null || minecraft.options.keyMappings == null)
      return;

    try {
      KeysetProfilesConfig cfg = service.getConfig(minecraft);
      boolean selectedIsActive =
          selectedProfileId == null || selectedProfileId.equals(cfg.getActiveProfileId());
      if (!selectedIsActive) {
        KeysetProfile profile = cfg.getProfile(selectedProfileId);
        if (profile != null) {
          buildConflictsFromProfile(profile);
          return;
        }
      }
    } catch (IOException ignored) {
      // Fall back to live bindings below.
    }

    buildConflictsFromLive();
  }

  private void buildConflictsFromLive() {
    Map<String, List<KeyMapping>> byKey = new LinkedHashMap<>();
    for (KeyMapping kb : minecraft.options.keyMappings) {
      String boundKey = kb.saveString();
      if (boundKey.equals("key.keyboard.unknown")) continue;
      byKey.computeIfAbsent(boundKey, k -> new ArrayList<>()).add(kb);
    }
    addConflictGroups(byKey);
  }

  private void buildConflictsFromProfile(KeysetProfile profile) {
    Map<String, List<KeyMapping>> byKey = new LinkedHashMap<>();
    for (KeyMapping kb : minecraft.options.keyMappings) {
      KeysetBindingSnapshot snapshot = profile.getBindings().get(kb.getName());
      if (snapshot == null || snapshot.getKeyStroke().isUnbound()) continue;
      String boundKey = snapshot.getKeyStroke().getKeyToken();
      if (boundKey == null || boundKey.equals("key.keyboard.unknown")) continue;
      byKey.computeIfAbsent(boundKey, k -> new ArrayList<>()).add(kb);
    }
    addConflictGroups(byKey);
  }

  private void addConflictGroups(Map<String, List<KeyMapping>> byKey) {
    for (Map.Entry<String, List<KeyMapping>> entry : byKey.entrySet()) {
      if (entry.getValue().size() > 1) {
        conflictGroups.add(new ConflictGroup(entry.getKey(), entry.getValue()));
      }
    }
  }

  // ── Tutorial ─────────────────────────────────────────────────────────────────

  private int getProfileCount() {
    try {
      return service.getConfig(minecraft).getProfiles().size();
    } catch (IOException e) {
      return 0;
    }
  }

  private String getSelectedProfileName() {
    if (selectedProfileId == null) return "";
    try {
      KeysetProfile p = service.getConfig(minecraft).getProfile(selectedProfileId);
      return p != null ? p.getName() : "";
    } catch (IOException e) {
      return "";
    }
  }

  private boolean isStepComplete() {
    switch (tutorialStep) {
      case INTRO:
        return true;
      case WELCOME:
        return true;
      case CREATE:
        return getProfileCount() > profileCountAtStepStart;
      case RENAME:
        return !getSelectedProfileName().equals(profileNameAtStepStart);
      case ACTIVATE:
        return didActivate;
      case CONFLICTS:
        return currentTab == Tab.CONFLICTS || conflictGroups.isEmpty();
      case FIX_CONFLICT:
        return didOpenConflictDialog || conflictGroups.isEmpty();
      case SAVE_LIVE:
        return didSaveLive;
      case AUTO_SWITCH:
        return currentTab == Tab.AUTO_SWITCH;
      case SHARE:
        return currentTab == Tab.SHARE || didUseShareTab;
      default:
        return true;
    }
  }

  private void advanceTutorial() {
    TutorialStep[] steps = TutorialStep.values();
    tutorialStep = steps[tutorialStep.ordinal() + 1];
    lastStepComplete = false;
    if (tutorialStep == TutorialStep.INTRO) {
      // No setup needed.
    } else if (tutorialStep == TutorialStep.CREATE) {
      profileCountAtStepStart = getProfileCount();
    } else if (tutorialStep == TutorialStep.RENAME) {
      profileNameAtStepStart = getSelectedProfileName();
    } else if (tutorialStep == TutorialStep.ACTIVATE) {
      didActivate = false;
    } else if (tutorialStep == TutorialStep.FIX_CONFLICT) {
      didOpenConflictDialog = false;
    } else if (tutorialStep == TutorialStep.SAVE_LIVE) {
      didSaveLive = false;
    } else if (tutorialStep == TutorialStep.SHARE) {
      didUseShareTab = false;
    } else if (tutorialStep == TutorialStep.DONE) {
      playTutorialCompleteSound();
      tutorialActive = false;
      service.setTutorialComplete(minecraft, true);
    }
    clearWidgets();
    init();
  }

  private void skipTutorialStep() {
    advanceTutorial();
  }

  private void playStepCompleteSound() {
    if (minecraft == null) return;
    minecraft
        .getSoundManager()
        .play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f, 0.6f));
  }

  private void playTutorialCompleteSound() {
    if (minecraft == null) return;
    minecraft
        .getSoundManager()
        .play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 1.0f));
  }

  private void closeTutorial() {
    tutorialActive = false;
    service.setTutorialComplete(minecraft, true);
    clearWidgets();
    init();
  }

  private void renderTutorialPanel(GuiGraphicsExtractor ctx, int mx, int my) {
    int pw = tutorialPanelWidth();
    int ph = tutorialPanelHeight();
    int px = mainX + mainW - pw - 10;
    int py = mainY + mainH - ph - 10;

    ctx.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x60000000);
    ctx.fill(px, py, px + pw, py + ph, 0xFF1E2125); // fully opaque — nothing bleeds through
    ctx.outline(px, py, pw, ph, KeysetTheme.ACCENT);
    ctx.fill(px, py, px + pw, py + 18, KeysetTheme.BG_SIDEBAR);
    ctx.fill(px, py + 18, px + pw, py + 19, KeysetTheme.ACCENT_DIM);
    renderTutorialCloseButton(ctx, mx, my, px, py);

    renderTutorialStepContent(ctx, px, py, pw, ph);

    // Custom Next/Done button
    int nbx = px + pw - 76, nby = py + ph - 22;
    boolean nextHov = mx >= nbx && mx < nbx + 70 && my >= nby && my < nby + 16;
    int nextBg =
        !tutNextEnabled
            ? KeysetTheme.withAlpha(KeysetTheme.BG_SURFACE, 0.5f)
            : nextHov ? KeysetTheme.ACCENT : KeysetTheme.BG_TAB_ACTIVE;
    int nextBr =
        tutNextEnabled ? (nextHov ? KeysetTheme.ACCENT : KeysetTheme.BORDER) : KeysetTheme.BORDER;
    int nextTxt =
        tutNextEnabled ? (nextHov ? 0xFF1A0800 : KeysetTheme.TEXT_BODY) : KeysetTheme.TEXT_DISABLED;
    ctx.fill(nbx, nby, nbx + 70, nby + 16, nextBg);
    ctx.outline(nbx, nby, 70, 16, nextBr);
    Component nextLabel =
        Component.translatable(
            tutorialStep == TutorialStep.SHARE ? "keyset.tutorial.finish" : "keyset.tutorial.next");
    if (nextHov && tutNextEnabled) {
      ctx.text(font, nextLabel, nbx + 35 - font.width(nextLabel) / 2, nby + 4, nextTxt, false);
    } else {
      ctx.centeredText(font, nextLabel, nbx + 35, nby + 4, nextTxt);
    }

    // Custom Skip Step button
    int sbx = px + 6, sby = py + ph - 22, sbw = 66;
    boolean skipHov = mx >= sbx && mx < sbx + sbw && my >= sby && my < sby + 16;
    ctx.fill(
        sbx,
        sby,
        sbx + sbw,
        sby + 16,
        skipHov ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE);
    ctx.outline(
        sbx,
        sby,
        sbw,
        16,
        skipHov ? KeysetTheme.BORDER : KeysetTheme.withAlpha(KeysetTheme.BORDER, 0.5f));
    ctx.centeredText(
        font,
        Component.translatable("keyset.tutorial.skip_step"),
        sbx + sbw / 2,
        sby + 4,
        skipHov ? KeysetTheme.TEXT_BODY : KeysetTheme.TEXT_MUTED);
  }

  private void renderTutorialCloseButton(GuiGraphicsExtractor ctx, int mx, int my, int px, int py) {
    boolean closeHov = mx >= px + 5 && mx < px + 15 && my >= py + 4 && my < py + 14;
    ctx.centeredText(
        font,
        Component.literal("x"),
        px + 10,
        py + 5,
        closeHov ? KeysetTheme.ERROR : KeysetTheme.TEXT_MUTED);
  }

  private void renderTutorialIntroPage(GuiGraphicsExtractor ctx, int mx, int my) {
    ctx.fill(0, 0, width, height, 0xF20B0D10);

    long now = System.currentTimeMillis();
    int centerX = width / 2;
    int centerY = height / 2;
    int titleY = centerY - 84;
    renderIntroAmbientDetails(ctx, now);
    renderIntroKeycapBackground(ctx, now);
    float titlePulse = (float) (Math.sin(now / 520.0) * 0.04 + 1.0);
    int titleColor =
        KeysetTheme.withAlpha(KeysetTheme.ACCENT, (float) (0.88 + Math.sin(now / 700.0) * 0.12));
    renderScaledCenteredText(
        ctx,
        Component.literal("Keyset").withStyle(style -> style.withBold(true)),
        centerX,
        titleY + 38,
        2.4f * titlePulse,
        titleColor);
    ctx.centeredText(
        font,
        Component.literal("Profiles for every way you play"),
        centerX,
        centerY - 24,
        KeysetTheme.TEXT_TITLE);

    var lines =
        font.split(Component.translatable("keyset.tutorial.intro.body"), Math.min(360, width - 48));
    for (int i = 0; i < Math.min(lines.size(), 3); i++) {
      ctx.centeredText(font, lines.get(i), centerX, centerY - 4 + i * 12, KeysetTheme.TEXT_BODY);
    }

    renderIntroCredit(ctx, centerX, centerY + 36);

    int buttonY = centerY + 56;
    renderIntroButton(
        ctx,
        Component.translatable("keyset.tutorial.start_tutorial"),
        centerX - 128,
        buttonY,
        120,
        mx,
        my,
        true);
    renderIntroButton(
        ctx,
        Component.translatable("keyset.tutorial.skip_tutorial"),
        centerX + 8,
        buttonY,
        120,
        mx,
        my,
        false);
  }

  private void renderIntroKeycapBackground(GuiGraphicsExtractor ctx, long now) {
    int keycapCount = introKeycapCount();
    for (int i = 0; i < keycapCount; i++) {
      int[] key = INTRO_KEYCAP_SEEDS[i];
      IntroAnim anim = introAnimation(now, i * 28);
      if (anim.alpha() <= 0f) {
        continue;
      }

      int size = key[2];
      int x = Mth.clamp((key[0] * width) / 100 - size / 2, 12, width - size - 12);
      int y = Mth.clamp((key[1] * height) / 100 - size / 2, 12, height - size - 12);
      int border = KeysetTheme.withAlpha(0xFF555B64, anim.alpha() * 0.17f);
      drawAnimatedRectOutline(ctx, x, y, size, size, 1, border, anim.reveal());
    }
  }

  private int introKeycapCount() {
    float widthProgress = (width - 400f) / (1500f - 400f);
    float areaProgress = ((width * height) - (400f * 280f)) / ((1500f * 900f) - (400f * 280f));
    float progress = Mth.clamp(Math.max(widthProgress, areaProgress), 0f, 1f);
    int count = Math.round(5f + progress * 45f);
    return Mth.clamp(count, 5, INTRO_KEYCAP_SEEDS.length);
  }

  private void renderIntroAmbientDetails(GuiGraphicsExtractor ctx, long now) {
    IntroAnim profileA = introAnimation(now, 80);
    IntroAnim profileB = introAnimation(now, 180);
    IntroAnim route = introAnimation(now, 260);
    IntroAnim chip = introAnimation(now, 360);
    drawIntroProfileCard(ctx, 30, height / 2 - 112, 104, "PvP", "WASD / Mouse", profileA);
    drawIntroProfileCard(
        ctx, width - 134, height / 2 + 58, 104, "Build", "Shift / Blocks", profileB);

    int routeY = Mth.clamp(height / 2 + 116, 96, height - 88);
    int routeX = Mth.clamp(width / 2 - 238, 18, width - 476);
    drawIntroRoute(ctx, routeX, routeY, route);

    int chipY = Mth.clamp(height / 2 - 152, 26, height - 48);
    int chipX = Mth.clamp(width / 2 + 178, 22, width - 156);
    drawIntroServerChip(ctx, chipX, chipY, chip);
  }

  private void drawIntroProfileCard(
      GuiGraphicsExtractor ctx, int x, int y, int w, String title, String detail, IntroAnim anim) {
    if (anim.alpha() <= 0f || x < 0 || y < 0 || x + w > width || y + 42 > height) {
      return;
    }
    int fill = KeysetTheme.withAlpha(KeysetTheme.BG_SURFACE, 0.18f * anim.alpha());
    int border = KeysetTheme.withAlpha(0xFF39404A, 0.16f * anim.alpha());
    int accent = KeysetTheme.withAlpha(KeysetTheme.ACCENT, 0.18f * anim.alpha());
    float textAlpha = introTextAlpha(anim);
    ctx.fill(x, y, x + w, y + 42, fill);
    drawAnimatedRectOutline(ctx, x, y, w, 42, 1, border, anim.reveal());
    ctx.fill(x + 1, y + 1, x + 3, y + 41, accent);
    if (textAlpha > 0f) {
      ctx.text(
          font,
          Component.literal(title),
          x + 9,
          y + 8,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, 0.42f * textAlpha),
          true);
      ctx.text(
          font,
          Component.literal(detail),
          x + 9,
          y + 23,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_DISABLED, 0.5f * textAlpha),
          true);
    }
  }

  private void drawIntroRoute(GuiGraphicsExtractor ctx, int x, int y, IntroAnim anim) {
    if (anim.alpha() <= 0f) {
      return;
    }
    int accent = KeysetTheme.withAlpha(KeysetTheme.ACCENT, 0.18f * anim.alpha());
    int border = KeysetTheme.withAlpha(0xFF39404A, 0.16f * anim.alpha());
    float textAlpha = introTextAlpha(anim);
    int node = 16;
    for (int i = 0; i < 3; i++) {
      int nx = x + i * 106;
      float reveal = Mth.clamp(anim.reveal() * 5f - i * 1.2f, 0f, 1f);
      drawAnimatedRectOutline(ctx, nx, y, node, node, 1, border, reveal);
      ctx.fill(
          nx + 4,
          y + 4,
          nx + 12,
          y + 12,
          KeysetTheme.withAlpha(KeysetTheme.CHIP_BG, 0.36f * anim.alpha()));
      if (i < 2) {
        drawAnimatedLine(
            ctx,
            nx + node + 6,
            y + 8,
            nx + 100,
            y + 8,
            2,
            accent,
            Mth.clamp(anim.reveal() * 5f - i * 1.2f - 0.8f, 0f, 1f));
      }
    }
    if (textAlpha > 0f) {
      ctx.text(
          font,
          Component.literal("server -> profile -> keys"),
          x + 2,
          y + 23,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, 0.42f * textAlpha),
          true);
    }
  }

  private void drawIntroServerChip(GuiGraphicsExtractor ctx, int x, int y, IntroAnim anim) {
    if (anim.alpha() <= 0f) {
      return;
    }
    int fill = KeysetTheme.withAlpha(KeysetTheme.BG_SURFACE, 0.18f * anim.alpha());
    int border = KeysetTheme.withAlpha(0xFF39404A, 0.16f * anim.alpha());
    int accent = KeysetTheme.withAlpha(KeysetTheme.ACCENT, 0.18f * anim.alpha());
    float textAlpha = introTextAlpha(anim);
    int w = 132;
    ctx.fill(x, y, x + w, y + 22, fill);
    drawAnimatedRectOutline(ctx, x, y, w, 22, 1, border, anim.reveal());
    ctx.fill(x + 6, y + 8, x + 10, y + 12, accent);
    if (textAlpha > 0f) {
      ctx.text(
          font,
          Component.literal("auto-switch ready"),
          x + 16,
          y + 7,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, 0.42f * textAlpha),
          true);
    }
  }

  private IntroAnim introAnimation(long now, int delayMs) {
    int elapsed = (int) ((now + INTRO_ANIM_CYCLE_MS - delayMs) % INTRO_ANIM_CYCLE_MS);
    if (elapsed < INTRO_ANIM_BUILD_MS) {
      float reveal = KeysetTheme.easeOutQuart(elapsed / (float) INTRO_ANIM_BUILD_MS);
      return new IntroAnim(reveal, reveal);
    }
    if (elapsed < INTRO_ANIM_BUILD_MS + INTRO_ANIM_HOLD_MS) {
      return new IntroAnim(1f, 1f);
    }
    if (elapsed < INTRO_ANIM_BUILD_MS + INTRO_ANIM_HOLD_MS + INTRO_ANIM_FADE_MS) {
      float fadeElapsed = elapsed - INTRO_ANIM_BUILD_MS - INTRO_ANIM_HOLD_MS;
      float alpha = 1f - KeysetTheme.easeOutQuart(fadeElapsed / (float) INTRO_ANIM_FADE_MS);
      return new IntroAnim(1f, alpha);
    }
    return new IntroAnim(0f, 0f);
  }

  private float introTextAlpha(IntroAnim anim) {
    if (anim.reveal() < 0.98f || anim.alpha() < 0.08f) {
      return 0f;
    }
    return anim.alpha();
  }

  private void renderIntroCredit(GuiGraphicsExtractor ctx, int centerX, int y) {
    Component made = Component.literal("Made with ");
    Component heart = Component.literal("\u2764");
    Component by = Component.literal(" by ");
    Component author = Component.literal("BeeBoyD");
    int totalWidth = font.width(made) + font.width(heart) + font.width(by) + font.width(author);
    int x = centerX - totalWidth / 2;
    ctx.text(font, made, x, y, KeysetTheme.TEXT_MUTED, true);
    x += font.width(made);
    ctx.text(font, heart, x, y, 0xFFFF5C5C, true);
    x += font.width(heart);
    ctx.text(font, by, x, y, KeysetTheme.TEXT_MUTED, true);
    x += font.width(by);
    ctx.text(font, author, x, y, KeysetTheme.ACCENT, true);
  }

  private void drawAnimatedRectOutline(
      GuiGraphicsExtractor ctx,
      int x,
      int y,
      int w,
      int h,
      int thickness,
      int color,
      float reveal) {
    drawAnimatedPath(ctx, thickness, color, reveal, x, y, x + w, y, x + w, y + h, x, y + h, x, y);
  }

  private void drawAnimatedPath(
      GuiGraphicsExtractor ctx, int thickness, int color, float reveal, int... points) {
    int segments = points.length / 2 - 1;
    if (segments <= 0) {
      return;
    }
    for (int i = 0; i < segments; i++) {
      float segmentReveal = Mth.clamp(reveal * segments - i, 0f, 1f);
      if (segmentReveal <= 0f) {
        continue;
      }
      int offset = i * 2;
      drawAnimatedLine(
          ctx,
          points[offset],
          points[offset + 1],
          points[offset + 2],
          points[offset + 3],
          thickness,
          color,
          segmentReveal);
    }
  }

  private void drawAnimatedLine(
      GuiGraphicsExtractor ctx,
      int x1,
      int y1,
      int x2,
      int y2,
      int thickness,
      int color,
      float reveal) {
    int dx = x2 - x1;
    int dy = y2 - y1;
    int steps = Math.max(Math.abs(dx), Math.abs(dy));
    int drawnSteps = Math.max(1, (int) (steps * reveal));
    for (int i = 0; i <= drawnSteps; i++) {
      float t = steps == 0 ? 1f : (float) i / steps;
      int x = x1 + Math.round(dx * t);
      int y = y1 + Math.round(dy * t);
      ctx.fill(x, y, x + thickness, y + thickness, color);
    }
  }

  private void renderIntroButton(
      GuiGraphicsExtractor ctx,
      Component label,
      int x,
      int y,
      int w,
      int mx,
      int my,
      boolean primary) {
    boolean hovered = mx >= x && mx < x + w && my >= y && my < y + 22;
    int bg =
        hovered ? KeysetTheme.ACCENT : primary ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE;
    int textColor = hovered ? 0xFF1A0800 : primary ? KeysetTheme.TEXT_TITLE : KeysetTheme.TEXT_BODY;
    ctx.fill(x, y, x + w, y + 22, bg);
    ctx.outline(x, y, w, 22, hovered || primary ? KeysetTheme.ACCENT : KeysetTheme.BORDER);
    if (hovered) {
      ctx.text(font, label, x + w / 2 - font.width(label) / 2, y + 7, textColor, false);
    } else {
      ctx.centeredText(font, label, x + w / 2, y + 7, textColor);
    }
  }

  private void renderTutorialStepContent(GuiGraphicsExtractor ctx, int px, int py, int pw, int ph) {
    // Step dots (excluding INTRO, WELCOME, and DONE)
    TutorialStep[] steps = TutorialStep.values();
    int totalSteps = steps.length - 3;
    int activeDotIdx = tutorialStep.ordinal() - 2;
    for (int i = 0; i < totalSteps; i++) {
      int col =
          i < activeDotIdx
              ? KeysetTheme.SUCCESS
              : i == activeDotIdx ? KeysetTheme.ACCENT : KeysetTheme.TEXT_DISABLED;
      int dx = px + pw - (totalSteps - i) * 10 - 5;
      ctx.fill(dx, py + 5, dx + 6, py + 11, col);
    }

    ctx.text(font, Component.literal("TUTORIAL"), px + 18, py + 5, KeysetTheme.TEXT_MUTED, true);
    ctx.text(
        font,
        Component.translatable(tutorialStep.titleKey()),
        px + 6,
        py + 24,
        KeysetTheme.TEXT_TITLE,
        true);

    var lines = font.split(Component.translatable(tutorialStep.bodyKey()), pw - 12);
    int bodyLineLimit =
        tutorialStep == TutorialStep.WELCOME
            ? Math.max(3, (ph - 60) / 11)
            : Math.max(3, (ph - 86) / 11);
    for (int i = 0; i < Math.min(lines.size(), bodyLineLimit); i++) {
      ctx.text(font, lines.get(i), px + 6, py + 36 + i * 11, KeysetTheme.TEXT_BODY, true);
    }

    boolean complete = isStepComplete();
    if (complete && tutorialStep != TutorialStep.WELCOME) {
      ctx.text(font, Component.literal("✓ Done!"), px + 6, py + ph - 38, KeysetTheme.SUCCESS, true);
    } else if (tutorialStep != TutorialStep.WELCOME) {
      ctx.text(
          font,
          Component.translatable(tutorialStep.hintKey()),
          px + 6,
          py + ph - 38,
          KeysetTheme.TEXT_MUTED,
          true);
    }
  }

  private void renderTutorialArrow(GuiGraphicsExtractor ctx) {
    if (tutorialStep == TutorialStep.INTRO
        || tutorialStep == TutorialStep.WELCOME
        || tutorialStep == TutorialStep.DONE) return;
    float pulse = (float) (Math.sin(System.currentTimeMillis() / 400.0) * 0.3 + 0.7);
    int arrowCol = KeysetTheme.withAlpha(KeysetTheme.ACCENT, pulse);
    Component rightArrow = Component.literal("▶");
    Component downArrow = Component.literal("▼");

    if (tutorialStep == TutorialStep.CREATE
        || tutorialStep == TutorialStep.RENAME
        || tutorialStep == TutorialStep.ACTIVATE
        || tutorialStep == TutorialStep.SAVE_LIVE) {
      String labelKey =
          tutorialStep == TutorialStep.CREATE
              ? "keyset.profile.new"
              : tutorialStep == TutorialStep.RENAME
                  ? "keyset.profile.rename"
                  : tutorialStep == TutorialStep.ACTIVATE
                      ? "keyset.profile.apply"
                      : "keyset.profile.capture";
      sidebarButtons.stream()
          .filter(b -> b.labelKey().equals(labelKey))
          .findFirst()
          .ifPresent(
              btn ->
                  ctx.text(
                      font, rightArrow, btn.x() - 14, btn.y() + (btn.h() - 9) / 2, arrowCol, true));
    } else if (tutorialStep == TutorialStep.CONFLICTS
        || tutorialStep == TutorialStep.FIX_CONFLICT) {
      int tabW = mainW / 4;
      int tx = mainX + Tab.CONFLICTS.ordinal() * tabW;
      ctx.text(font, downArrow, tx + tabW / 2 - 3, tabBarY - 12, arrowCol, true);
    } else if (tutorialStep == TutorialStep.AUTO_SWITCH) {
      int tabW = mainW / 4;
      int tx = mainX + Tab.AUTO_SWITCH.ordinal() * tabW;
      ctx.text(font, downArrow, tx + tabW / 2 - 3, tabBarY - 12, arrowCol, true);
    } else if (tutorialStep == TutorialStep.SHARE) {
      int tabW = mainW / 4;
      int tx = mainX + Tab.SHARE.ordinal() * tabW;
      ctx.text(font, downArrow, tx + tabW / 2 - 3, tabBarY - 12, arrowCol, true);
    }
  }

  private void renderTutorialDarkening(GuiGraphicsExtractor ctx) {
    tutHlX = -1;
    tutHlY = -1;
    tutHlW = 0;
    tutHlH = 0;
    if (!tutorialActive || tutorialStep == null) return;
    int dim = 0x99000000;
    int fx, fy, fw, fh;
    if (tutorialStep == TutorialStep.CREATE
        || tutorialStep == TutorialStep.RENAME
        || tutorialStep == TutorialStep.ACTIVATE
        || tutorialStep == TutorialStep.SAVE_LIVE) {
      fx = sidebarX;
      fy = sidebarY;
      fw = sidebarW;
      fh = mainH;
    } else if (tutorialStep == TutorialStep.CONFLICTS
        || tutorialStep == TutorialStep.FIX_CONFLICT) {
      fx = mainX;
      fy = tabBarY;
      fw = mainW;
      fh = mainH;
    } else if (tutorialStep == TutorialStep.AUTO_SWITCH || tutorialStep == TutorialStep.SHARE) {
      fx = mainX;
      fy = tabBarY;
      fw = mainW;
      fh = KeysetTheme.TAB_H + 4;
    } else {
      ctx.fill(0, 0, width, height, 0x55000000);
      return;
    }
    tutHlX = fx;
    tutHlY = fy;
    tutHlW = fw;
    tutHlH = fh;
    if (fy > 0) ctx.fill(0, 0, width, fy, dim);
    if (fy + fh < height) ctx.fill(0, fy + fh, width, height, dim);
    if (fx > 0) ctx.fill(0, fy, fx, fy + fh, dim);
    if (fx + fw < width) ctx.fill(fx + fw, fy, width, fy + fh, dim);
  }

  private boolean isOverTutorialPanel(int mx, int my) {
    if (!tutorialActive || tutorialStep == TutorialStep.DONE) return false;
    int pw = tutorialPanelWidth();
    int ph = tutorialPanelHeight();
    int px = mainX + mainW - pw - 10;
    int py = mainY + mainH - ph - 10;
    return mx >= px && mx < px + pw && my >= py && my < py + ph;
  }

  private int tutorialPanelWidth() {
    return Math.min(TUTORIAL_PANEL_WIDTH, Math.max(260, mainW - 20));
  }

  private int tutorialPanelHeight() {
    return Math.min(TUTORIAL_PANEL_HEIGHT, Math.max(130, mainH - 20));
  }

  private void setStatus(String msg, boolean error) {
    if (msg == null || msg.isEmpty()) return;
    if (!toastQueue.isEmpty()) {
      ToastEntry last = toastQueue.get(toastQueue.size() - 1);
      if (last.msg().equals(msg) && last.error() == error) {
        toastQueue.set(
            toastQueue.size() - 1, new ToastEntry(msg, error, System.currentTimeMillis(), 0f));
        return;
      }
    }
    while (toastQueue.size() >= MAX_TOASTS) toastQueue.remove(0);
    toastQueue.add(new ToastEntry(msg, error, System.currentTimeMillis(), 0f));
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void onClose() {
    this.minecraft.setScreen(parent);
  }
}
