package net.beeboyd.keyset.platform.fabric.screen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.beeboyd.keyset.core.autoswitch.AutoSwitchRule;
import net.beeboyd.keyset.core.profile.KeysetBindingSnapshot;
import net.beeboyd.keyset.core.profile.KeysetProfile;
import net.beeboyd.keyset.core.profile.KeysetProfilesConfig;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

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

  private record ConflictGroup(String boundKey, List<KeyBinding> bindings) {}

  private record IntroAnim(float reveal, float alpha) {}

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
  private String statusMsg = "";
  private boolean statusError;

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

  // Share tab
  private record ShareTarget(String id, int x, int y, int w, int h) {}

  private final List<ShareTarget> shareTargets = new ArrayList<>();
  private boolean shareUploading = false;
  private boolean shareDownloading = false;
  private String shareResultCode = "";
  private long shareExpiresAt = 0;
  private KeysetTextFieldWidget shareCodeField;

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
  private int profileCountAtStepStart;
  private String profileNameAtStepStart = "";
  private boolean didActivate;
  private boolean didSaveLive;
  private boolean didOpenConflictDialog;

  public KeysetScreen(Screen parent, KeysetFabricService service) {
    super(Text.translatable("keyset.title"));
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
      tutNextEnabled = isStepComplete();
    }
  }

  @Override
  protected void init() {
    openedAtMs = System.currentTimeMillis();
    lastFrameMs = 0;
    computeLayout();

    if (selectedProfileId == null) {
      try {
        selectedProfileId = service.getConfig(client).getActiveProfileId();
      } catch (IOException e) {
        // keep null
      }
    }

    buildSidebarButtons();
    rebuildTabWidgets();
    refreshConflicts();

    // ? help button — always reopens tutorial from WELCOME
    int helpBtnX = width - KeysetTheme.PAD - 20;
    int helpBtnY = topbarY + (KeysetTheme.TOPBAR_H - 16) / 2;
    addDrawableChild(
        KeysetButtonWidget.create(
            helpBtnX,
            helpBtnY,
            16,
            16,
            Text.literal("?"),
            b -> {
              tutorialActive = true;
              tutorialStep = TutorialStep.INTRO;
              clearChildren();
              init();
            }));

    // First-launch tutorial trigger
    if (!tutorialActive) {
      try {
        if (!service.isTutorialComplete(client)) {
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
      remove(bindingsSearch);
      bindingsSearch = null;
    }
    if (conflictsSearch != null) {
      remove(conflictsSearch);
      conflictsSearch = null;
    }
    if (shareCodeField != null) {
      remove(shareCodeField);
      shareCodeField = null;
    }
    int sx = mainX + KeysetTheme.GAP;
    int sy = contentY + KeysetTheme.GAP;
    int toggleW = 72;
    int searchW = mainW - KeysetTheme.GAP * 2 - toggleW - KeysetTheme.GAP_SM;
    if (currentTab == Tab.BINDINGS) {
      bindingsSearch = new KeysetTextFieldWidget(textRenderer, sx, sy, searchW, 18, Text.empty());
      bindingsSearch.setPlaceholder(Text.translatable("keyset.search.placeholder"));
      bindingsSearch.setMaxLength(64);
      addDrawableChild(bindingsSearch);
    } else if (currentTab == Tab.CONFLICTS) {
      conflictsSearch = new KeysetTextFieldWidget(textRenderer, sx, sy, searchW, 18, Text.empty());
      conflictsSearch.setPlaceholder(Text.translatable("keyset.search.placeholder"));
      conflictsSearch.setMaxLength(64);
      addDrawableChild(conflictsSearch);
    } else if (currentTab == Tab.SHARE) {
      int halfW = (mainW - KeysetTheme.GAP * 3) / 2;
      int rightX = mainX + KeysetTheme.GAP * 2 + halfW;
      int fieldY = contentY + KeysetTheme.GAP + 16 + KeysetTheme.GAP_SM;
      int importBtnW = 56;
      int fieldW = halfW - importBtnW - KeysetTheme.GAP_SM;
      shareCodeField =
          new KeysetTextFieldWidget(textRenderer, rightX, fieldY, fieldW, 18, Text.empty());
      shareCodeField.setPlaceholder(Text.literal("Enter code…"));
      shareCodeField.setMaxLength(8);
      addDrawableChild(shareCodeField);
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
  public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    // Intentionally empty — backdrop rendered in render() for animation support.
  }

  @Override
  public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
    long now = System.currentTimeMillis();
    frameDt = lastFrameMs > 0 ? Math.min(now - lastFrameMs, 50f) : 16f;
    lastFrameMs = now;
    screenAlpha = KeysetTheme.easeOutQuart(Math.min(1f, (now - openedAtMs) / KeysetTheme.OPEN_MS));
    tabUnderlineX =
        KeysetTheme.expLerp(tabUnderlineX, tabUnderlineTarget, Math.max(delta * 50f, 1f), 25f);

    if (width < 400 || height < 280) {
      ctx.fill(0, 0, width, height, 0xFF0A0C0E);
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal("Please increase the window size"),
          width / 2,
          height / 2 - 5,
          KeysetTheme.ACCENT);
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal("Minimum: 400 × 280"),
          width / 2,
          height / 2 + 8,
          KeysetTheme.TEXT_MUTED);
      return;
    }

    ctx.fill(0, 0, width, height, KeysetTheme.scaleAlpha(KeysetTheme.BG_BACKDROP, screenAlpha));
    renderTopbar(ctx);
    renderSidebar(ctx, mouseX, mouseY);
    renderMain(ctx, mouseX, mouseY);

    super.render(ctx, mouseX, mouseY, delta);
    ctx.enableScissor(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + mainH);
    renderSidebarButtons(ctx, mouseX, mouseY);
    ctx.disableScissor();
    renderDoneButton(ctx, mouseX, mouseY);
    renderFooter(ctx);
    if (tutorialActive && tutorialStep != TutorialStep.DONE) {
      renderTutorialOverlay(ctx, mouseX, mouseY);
    }
  }

  private void renderTutorialOverlay(DrawContext ctx, int mouseX, int mouseY) {
    ctx.draw();
    ctx.getMatrices().push();
    try {
      ctx.getMatrices().translate(0, 0, TUTORIAL_OVERLAY_Z);
      if (tutorialStep == TutorialStep.INTRO) {
        renderTutorialIntroPage(ctx, mouseX, mouseY);
      } else {
        renderTutorialDarkening(ctx);
        renderTutorialArrow(ctx);
        renderTutorialPanel(ctx, mouseX, mouseY);
      }
    } finally {
      ctx.getMatrices().pop();
    }
    ctx.draw();
  }

  private void renderTopbar(DrawContext ctx) {
    ctx.fill(
        KeysetTheme.PAD,
        topbarY,
        width - KeysetTheme.PAD,
        topbarY + KeysetTheme.TOPBAR_H,
        KeysetTheme.scaleAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.drawBorder(
        KeysetTheme.PAD,
        topbarY,
        width - KeysetTheme.PAD * 2,
        KeysetTheme.TOPBAR_H,
        KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
    renderScaledCenteredText(
        ctx,
        Text.literal("Keyset").styled(style -> style.withBold(true)),
        width / 2,
        topbarY + KeysetTheme.TOPBAR_H / 2,
        1.6f,
        KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));
  }

  private void renderScaledCenteredText(
      DrawContext ctx, Text text, int centerX, int centerY, float scale, int color) {
    ctx.getMatrices().push();
    try {
      ctx.getMatrices().translate(centerX, centerY - (int) (9 * scale / 2f), 0);
      ctx.getMatrices().scale(scale, scale, 1f);
      ctx.drawCenteredTextWithShadow(textRenderer, text, 0, 0, color);
    } finally {
      ctx.getMatrices().pop();
    }
  }

  private void renderSidebar(DrawContext ctx, int mouseX, int mouseY) {
    int sx = sidebarX;
    int sy = sidebarY;
    int sw = sidebarW;
    int sh = mainH;
    ctx.fill(sx, sy, sx + sw, sy + sh, KeysetTheme.scaleAlpha(KeysetTheme.BG_SIDEBAR, screenAlpha));
    ctx.drawBorder(sx, sy, sw, sh, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal("PROFILES"),
        sx + KeysetTheme.CARD_PAD,
        sy + KeysetTheme.CARD_PAD,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));
    renderProfileRows(ctx, mouseX, mouseY);
  }

  private void renderProfileRows(DrawContext ctx, int mx, int my) {
    KeysetProfilesConfig cfg;
    try {
      cfg = service.getConfig(client);
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
      } else if (hovered) {
        ctx.fill(
            sidebarX,
            rowY,
            sidebarX + sidebarW,
            rowY + rowH,
            KeysetTheme.withAlpha(KeysetTheme.BG_HOVER, screenAlpha));
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
      while (textRenderer.getWidth(name) > maxW && name.length() > 1) {
        name = name.substring(0, name.length() - 1);
      }
      if (!name.equals(profile.getName())) name += "…";
      ctx.drawTextWithShadow(
          textRenderer, Text.literal(name), indent, rowY + (rowH - 9) / 2, nameCol);

      if (active) {
        int cw = textRenderer.getWidth("LIVE") + 8;
        int cx2 = sidebarX + sidebarW - cw - 4;
        int cy2 = rowY + (rowH - 12) / 2;

        // MOD badge — show when active profile differs from live MC bindings
        boolean isModified = client != null && profileDiffersFromLive(profile, client);
        if (isModified) {
          int mw = textRenderer.getWidth("MOD") + 8;
          int mx2 = cx2 - mw - 3;
          ctx.fill(
              mx2,
              cy2,
              mx2 + mw,
              cy2 + 12,
              KeysetTheme.withAlpha(KeysetTheme.CHIP_ERR_BG, screenAlpha));
          ctx.drawBorder(
              mx2, cy2, mw, 12, KeysetTheme.withAlpha(KeysetTheme.CHIP_ERR_BR, screenAlpha));
          ctx.drawCenteredTextWithShadow(
              textRenderer,
              Text.literal("MOD"),
              mx2 + mw / 2,
              cy2 + 2,
              KeysetTheme.withAlpha(KeysetTheme.ERROR, screenAlpha));
        }

        ctx.fill(
            cx2,
            cy2,
            cx2 + cw,
            cy2 + 12,
            KeysetTheme.withAlpha(KeysetTheme.CHIP_OK_BG, screenAlpha));
        ctx.drawBorder(
            cx2, cy2, cw, 12, KeysetTheme.withAlpha(KeysetTheme.CHIP_OK_BR, screenAlpha));
        ctx.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal("LIVE"),
            cx2 + cw / 2,
            cy2 + 2,
            KeysetTheme.withAlpha(KeysetTheme.SUCCESS, screenAlpha));
      }
    }
    ctx.disableScissor();

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

  private void renderSidebarButtons(DrawContext ctx, int mx, int my) {
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
      ctx.drawBorder(btn.x(), btn.y(), btn.w(), btn.h(), border);
      Text btnLabel = Text.translatable(btn.labelKey());
      if (hovered) {
        ctx.drawText(
            textRenderer,
            btnLabel,
            btn.x() + btn.w() / 2 - textRenderer.getWidth(btnLabel) / 2,
            btn.y() + (btn.h() - 9) / 2,
            textCol,
            false); // no shadow on amber bg
      } else {
        ctx.drawCenteredTextWithShadow(
            textRenderer, btnLabel, btn.x() + btn.w() / 2, btn.y() + (btn.h() - 9) / 2, textCol);
      }
    }
  }

  private void renderMain(DrawContext ctx, int mouseX, int mouseY) {
    ctx.fill(
        mainX,
        mainY,
        mainX + mainW,
        mainY + mainH,
        KeysetTheme.scaleAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.drawBorder(
        mainX, mainY, mainW, mainH, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));

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
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal(names[i]),
          tx + tabW / 2,
          tabBarY + 10,
          KeysetTheme.withAlpha(color, screenAlpha));
    }

    int ulX = (int) tabUnderlineX;
    ctx.fill(
        ulX,
        tabBarY + KeysetTheme.TAB_H - 2,
        ulX + tabW,
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

  private void renderBindingsTab(DrawContext ctx, int mx, int my) {
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
    ctx.drawBorder(
        toggleX,
        toggleY,
        toggleW,
        18,
        KeysetTheme.withAlpha(
            toggleHovered ? KeysetTheme.ACCENT : KeysetTheme.BORDER, screenAlpha));
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal(toggleLabel),
        toggleX + toggleW / 2,
        toggleY + 5,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));

    List<BindingRow> rows = buildBindingRows(client);

    int listTop = contentY + KeysetTheme.GAP + 18 + KeysetTheme.GAP_SM;
    int listBot = mainY + mainH - KeysetTheme.GAP_SM;

    if (rows.isEmpty()) {
      String emptyMsg =
          selectedProfileId == null
              ? "Select a profile to view bindings."
              : "No bindings match this search.";
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal(emptyMsg),
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
    bindingsScrollTarget = MathHelper.clamp(bindingsScrollTarget, 0, maxScroll);
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

  private void renderCategoryHeader(DrawContext ctx, String name, int count, int x, int y, int w) {
    ctx.fill(x, y, x + w, y + 28, KeysetTheme.scaleAlpha(KeysetTheme.BG_SURFACE, screenAlpha));
    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal(name.toUpperCase()),
        x + 8,
        y + 10,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));
    String countLabel = count + " binds";
    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal(countLabel),
        x + w - textRenderer.getWidth(countLabel) - 8,
        y + 10,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_DISABLED, screenAlpha));
    ctx.fill(
        x + 4, y + 27, x + w - 4, y + 28, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
  }

  private void renderBindingRow(
      DrawContext ctx, BindingRow row, int x, int y, int w, int mx, int my) {
    boolean hovered =
        !isOverTutorialPanel(mx, my)
            && mx >= x
            && mx < x + w
            && my >= y
            && my < y + KeysetTheme.ROW_H;
    if (hovered) {
      ctx.fill(
          x,
          y,
          x + w,
          y + KeysetTheme.ROW_H,
          KeysetTheme.withAlpha(KeysetTheme.BG_HOVER, screenAlpha));
    }

    String displayName = row.displayName;
    int chipW = textRenderer.getWidth(row.keyLabel) + 10;
    int maxNameW = w - chipW - 22;
    while (textRenderer.getWidth(displayName) > maxNameW && displayName.length() > 1) {
      displayName = displayName.substring(0, displayName.length() - 1);
    }
    if (!displayName.equals(row.displayName)) displayName += "…";

    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal(displayName),
        x + 8,
        y + (KeysetTheme.ROW_H - 9) / 2,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));

    int chipX = x + w - chipW - 6;
    int chipY = y + (KeysetTheme.ROW_H - 14) / 2;
    int chipBg = row.conflict ? KeysetTheme.CHIP_ERR_BG : KeysetTheme.CHIP_BG;
    int chipBr = row.conflict ? KeysetTheme.CHIP_ERR_BR : KeysetTheme.BORDER;
    int chipText = row.conflict ? KeysetTheme.ERROR : KeysetTheme.TEXT_TITLE;
    ctx.fill(chipX, chipY, chipX + chipW, chipY + 14, KeysetTheme.withAlpha(chipBg, screenAlpha));
    ctx.drawBorder(chipX, chipY, chipW, 14, KeysetTheme.withAlpha(chipBr, screenAlpha));
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal(row.keyLabel),
        chipX + chipW / 2,
        chipY + 3,
        KeysetTheme.withAlpha(chipText, screenAlpha));
  }

  private List<BindingRow> buildBindingRows(MinecraftClient mc) {
    if (selectedProfileId == null || mc == null) return Collections.emptyList();
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

    String filter = bindingsSearch != null ? bindingsSearch.getText().trim().toLowerCase() : "";

    List<BindingRow> rows = new ArrayList<>();
    for (KeyBinding kb : mc.options.allKeys) {
      String id = kb.getTranslationKey();
      KeysetBindingSnapshot snap = profileBindings.get(id);
      if (snap == null) continue;

      String displayName = Text.translatable(id).getString();
      String category = kb.getCategory();
      String categoryName = Text.translatable(category).getString();

      // Rule 3: read live MC state, not profile snapshot
      String keyLabel = kb.getBoundKeyLocalizedText().getString();

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

  private void renderConflictsTab(DrawContext ctx, int mx, int my) {
    int listTop = contentY + KeysetTheme.GAP + 18 + KeysetTheme.GAP_SM;
    int listBot = mainY + mainH - KeysetTheme.GAP_SM;

    String filter = conflictsSearch != null ? conflictsSearch.getText().trim().toLowerCase() : "";
    List<ConflictGroup> visible = new ArrayList<>();
    for (ConflictGroup g : conflictGroups) {
      if (filter.isEmpty() || matchesConflictFilter(g, filter)) visible.add(g);
    }

    if (visible.isEmpty()) {
      int msgColor = conflictGroups.isEmpty() ? KeysetTheme.SUCCESS : KeysetTheme.TEXT_DISABLED;
      String msg =
          conflictGroups.isEmpty()
              ? Text.translatable("keyset.conflicts.none").getString()
              : "No conflicts match this search.";
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal(msg),
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
    conflictsScrollTarget = MathHelper.clamp(conflictsScrollTarget, 0, maxScroll);
    conflictsScrollSmooth =
        KeysetTheme.expLerp(conflictsScrollSmooth, conflictsScrollTarget, frameDt, 22f);

    ctx.enableScissor(mainX + 1, listTop, mainX + mainW - 1, listBot);
    conflictTargets.clear();

    int curY = listTop - (int) conflictsScrollSmooth;
    int rowW = mainW - KeysetTheme.GAP_SM * 2;
    int rowX = mainX + KeysetTheme.GAP_SM;

    for (ConflictGroup g : visible) {
      boolean expanded = expandedConflictGroups.contains(g.boundKey());
      String keyLabel = g.bindings().get(0).getBoundKeyLocalizedText().getString();

      conflictTargets.add(
          new ConflictTarget(
              true, g.boundKey(), rowX, curY, rowW, 28, null, null, null, null, null));

      if (curY + 28 > listTop && curY < listBot) {
        renderConflictGroup(ctx, keyLabel, g.bindings().size(), expanded, rowX, curY, rowW, mx, my);
      }
      curY += 28;

      if (expanded) {
        for (KeyBinding kb : g.bindings()) {
          String actionName = Text.translatable(kb.getTranslationKey()).getString();
          String categoryName = Text.translatable(kb.getCategory()).getString();
          List<String> others = new ArrayList<>();
          for (KeyBinding other : g.bindings()) {
            if (!other.getTranslationKey().equals(kb.getTranslationKey())) {
              others.add(Text.translatable(other.getTranslationKey()).getString());
            }
          }
          conflictTargets.add(
              new ConflictTarget(
                  false,
                  g.boundKey(),
                  rowX,
                  curY,
                  rowW,
                  KeysetTheme.ROW_H,
                  kb.getTranslationKey(),
                  actionName,
                  keyLabel,
                  categoryName,
                  others));
          if (curY + KeysetTheme.ROW_H > listTop && curY < listBot) {
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
      DrawContext ctx,
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

    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal(expanded ? "▼" : "▶"),
        x + 6,
        y + 10,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));

    int chipW = textRenderer.getWidth(keyLabel) + 10;
    ctx.fill(
        x + 18,
        y + 7,
        x + 18 + chipW,
        y + 21,
        KeysetTheme.withAlpha(KeysetTheme.CHIP_ERR_BG, screenAlpha));
    ctx.drawBorder(
        x + 18, y + 7, chipW, 14, KeysetTheme.withAlpha(KeysetTheme.CHIP_ERR_BR, screenAlpha));
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal(keyLabel),
        x + 18 + chipW / 2,
        y + 9,
        KeysetTheme.withAlpha(KeysetTheme.ERROR, screenAlpha));

    String cnt = bindCount + " binds";
    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal(cnt),
        x + w - textRenderer.getWidth(cnt) - 8,
        y + 10,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));
    ctx.fill(
        x + 4, y + 27, x + w - 4, y + 28, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
  }

  private void renderConflictBinding(
      DrawContext ctx, String actionName, String category, int x, int y, int w, int mx, int my) {
    int indent = 16;
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

    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal(actionName),
        x + indent,
        y + (KeysetTheme.ROW_H - 9) / 2,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));

    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal(category),
        x + w - textRenderer.getWidth(category) - 8,
        y + (KeysetTheme.ROW_H - 9) / 2,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));
  }

  private boolean matchesConflictFilter(ConflictGroup g, String filter) {
    if (g.bindings().get(0).getBoundKeyLocalizedText().getString().toLowerCase().contains(filter))
      return true;
    for (KeyBinding kb : g.bindings()) {
      if (Text.translatable(kb.getTranslationKey()).getString().toLowerCase().contains(filter))
        return true;
      if (Text.translatable(kb.getCategory()).getString().toLowerCase().contains(filter))
        return true;
    }
    return false;
  }

  private boolean profileDiffersFromLive(KeysetProfile profile, MinecraftClient mc) {
    Map<String, KeysetBindingSnapshot> bindings = profile.getBindings();
    for (KeyBinding kb : mc.options.allKeys) {
      KeysetBindingSnapshot snap = bindings.get(kb.getTranslationKey());
      if (snap == null) continue;
      boolean snapUnbound = snap.getKeyStroke().isUnbound();
      boolean liveUnbound = kb.isUnbound();
      if (snapUnbound != liveUnbound) return true;
      if (!snapUnbound && !snap.getKeyStroke().getKeyToken().equals(kb.getBoundKeyTranslationKey()))
        return true;
    }
    return false;
  }

  // ── Auto-switch tab ───────────────────────────────────────────────────────────

  private void renderAutoSwitchTab(DrawContext ctx, int mx, int my) {
    autoSwitchTargets.clear();

    List<AutoSwitchRule> rules;
    try {
      rules = service.getAutoSwitchRules(client);
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
    ctx.drawBorder(
        btnX,
        btnY,
        btnW,
        btnH,
        KeysetTheme.withAlpha(addHov ? KeysetTheme.ACCENT : KeysetTheme.BORDER, screenAlpha));
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal("+ Add Rule"),
        btnX + btnW / 2,
        btnY + 5,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));
    autoSwitchTargets.add(new AutoSwitchTarget(true, -1, btnX, btnY, btnW, btnH));

    int listTop = contentY + KeysetTheme.GAP + btnH + KeysetTheme.GAP_SM;
    int listBot = mainY + mainH - KeysetTheme.GAP_SM;
    int rowX = mainX + KeysetTheme.GAP_SM;
    int rowW = mainW - KeysetTheme.GAP_SM * 2;

    if (rules.isEmpty()) {
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal("No rules. Add a rule to auto-switch profiles when joining servers."),
          mainX + mainW / 2,
          listTop + (listBot - listTop) / 2 - 4,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_DISABLED, screenAlpha));
      return;
    }

    KeysetProfilesConfig cfg = null;
    try {
      cfg = service.getConfig(client);
    } catch (IOException ignored) {
    }

    ctx.enableScissor(mainX + 1, listTop, mainX + mainW - 1, listBot);
    int curY = listTop;
    int rowH = 28;
    for (int i = 0; i < rules.size(); i++) {
      AutoSwitchRule rule = rules.get(i);
      if (curY + rowH > listBot) break;

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

      // Pattern chip
      int patW = textRenderer.getWidth(rule.getPattern()) + 10;
      int patY = curY + (rowH - 14) / 2;
      ctx.fill(
          rowX + 6,
          patY,
          rowX + 6 + patW,
          patY + 14,
          KeysetTheme.withAlpha(KeysetTheme.CHIP_BG, screenAlpha));
      ctx.drawBorder(
          rowX + 6, patY, patW, 14, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal(rule.getPattern()),
          rowX + 6 + patW / 2,
          patY + 3,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));

      // Arrow + profile name
      String profileName =
          cfg != null && cfg.getProfile(rule.getProfileId()) != null
              ? cfg.getProfile(rule.getProfileId()).getName()
              : rule.getProfileId();
      String arrow = "→ " + profileName;
      ctx.drawTextWithShadow(
          textRenderer,
          Text.literal(arrow),
          rowX + 6 + patW + 8,
          curY + (rowH - 9) / 2,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));

      // Delete button [✕]
      int delW = 16;
      int delX = rowX + rowW - delW - 4;
      int delY = curY + (rowH - 14) / 2;
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
      ctx.drawBorder(
          delX,
          delY,
          delW,
          14,
          KeysetTheme.withAlpha(
              delHov ? KeysetTheme.CHIP_ERR_BR : KeysetTheme.BORDER, screenAlpha));
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal("✕"),
          delX + delW / 2,
          delY + 3,
          KeysetTheme.withAlpha(delHov ? KeysetTheme.ERROR : KeysetTheme.TEXT_MUTED, screenAlpha));
      autoSwitchTargets.add(new AutoSwitchTarget(false, i, delX, delY, delW, 14));

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
    if (mouseX >= sidebarX && mouseX < sidebarX + sidebarW) {
      sidebarScrollTarget -= (float) (verticalAmount * KeysetTheme.ROW_H);
      return true;
    }
    if (mouseX >= mainX && mouseX < mainX + mainW) {
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
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    int mx = (int) mouseX;
    int my = (int) mouseY;

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
    }

    // Done button
    int dbw = 60, dbh = 18;
    int dbx = width - dbw - KeysetTheme.PAD;
    int dby = height - dbh - KeysetTheme.PAD;
    if (button == 0 && mouseX >= dbx && mouseX < dbx + dbw && mouseY >= dby && mouseY < dby + dbh) {
      close();
      return true;
    }

    // Sidebar profile list click
    if (button == 0 && mouseX >= sidebarX && mouseX < sidebarX + sidebarW && client != null) {
      int listTop = sidebarY + 20;
      int listBot = sidebarY + mainH - 92;
      if (mouseY >= listTop && mouseY < listBot) {
        try {
          var profiles =
              new ArrayList<KeysetProfile>(service.getConfig(client).getProfiles().values());
          int idx = (int) ((mouseY - listTop + sidebarScrollSmooth) / KeysetTheme.ROW_H);
          if (idx >= 0 && idx < profiles.size()) {
            selectedProfileId = profiles.get(idx).getId();
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
      int idx = MathHelper.clamp((int) ((mouseX - mainX) / (mainW / 4)), 0, 3);
      currentTab = Tab.values()[idx];
      tabUnderlineTarget = mainX + idx * (mainW / 4f);
      bindingsScrollTarget = 0;
      bindingsScrollSmooth = 0;
      conflictsScrollTarget = 0;
      conflictsScrollSmooth = 0;
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
            client.setScreen(new AddRuleDialog(this, service));
          } else {
            try {
              service.deleteAutoSwitchRule(client, t.deleteIndex());
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
      for (ShareTarget t : shareTargets) {
        if (mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + t.h()) {
          switch (t.id()) {
            case "gen" -> doShareUpload();
            case "copy" -> doShareCopy();
            case "import" -> doShareImport();
          }
          return true;
        }
      }
    }

    return super.mouseClicked(mouseX, mouseY, button);
  }

  // ── Share tab ─────────────────────────────────────────────────────────────────

  private void renderShareTab(DrawContext ctx, int mx, int my) {
    shareTargets.clear();

    int gap = KeysetTheme.GAP;
    int gapSm = KeysetTheme.GAP_SM;
    int halfW = (mainW - gap * 3) / 2;
    int leftX = mainX + gap;
    int rightX = mainX + gap * 2 + halfW;
    int topY = contentY + gap;
    int botY = mainY + mainH;

    // Vertical divider
    int divX = mainX + gap + halfW + gap / 2;
    ctx.fill(
        divX,
        contentY + gapSm,
        divX + 1,
        botY - gapSm,
        KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));

    // ── Export (left) ──────────────────────────────────────────────────────
    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal("Share Profile"),
        leftX,
        topY,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));

    String profileName = "(no profile selected)";
    if (selectedProfileId != null && client != null) {
      try {
        KeysetProfile prof = service.getConfig(client).getProfile(selectedProfileId);
        if (prof != null) profileName = prof.getName();
      } catch (IOException ignored) {
      }
    }
    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal(profileName),
        leftX,
        topY + 14,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));

    int genBtnW = 110, genBtnH = 18;
    int genBtnX = leftX + (halfW - genBtnW) / 2;
    int genBtnY = topY + 14 + 12 + gapSm;
    boolean genDisabled = shareUploading || selectedProfileId == null;
    boolean genHov =
        !genDisabled
            && !isOverTutorialPanel(mx, my)
            && mx >= genBtnX
            && mx < genBtnX + genBtnW
            && my >= genBtnY
            && my < genBtnY + genBtnH;
    ctx.fill(
        genBtnX,
        genBtnY,
        genBtnX + genBtnW,
        genBtnY + genBtnH,
        KeysetTheme.withAlpha(
            genHov ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE,
            genDisabled ? screenAlpha * 0.5f : screenAlpha));
    ctx.drawBorder(
        genBtnX,
        genBtnY,
        genBtnW,
        genBtnH,
        KeysetTheme.withAlpha(
            genHov ? KeysetTheme.ACCENT : KeysetTheme.BORDER,
            genDisabled ? screenAlpha * 0.5f : screenAlpha));
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal("Generate Code"),
        genBtnX + genBtnW / 2,
        genBtnY + 5,
        KeysetTheme.withAlpha(
            genDisabled ? KeysetTheme.TEXT_DISABLED : KeysetTheme.TEXT_BODY, screenAlpha));
    if (!genDisabled) shareTargets.add(new ShareTarget("gen", genBtnX, genBtnY, genBtnW, genBtnH));

    int resultY = genBtnY + genBtnH + gap;

    if (shareUploading) {
      String[] frames = {"|", "/", "─", "\\"};
      int frame = (int) ((System.currentTimeMillis() / 150) % frames.length);
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal(frames[frame]),
          leftX + halfW / 2,
          resultY + 4,
          KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));
    } else if (!shareResultCode.isEmpty()) {
      int codeW = textRenderer.getWidth(shareResultCode) + 16;
      int codeX = leftX + (halfW - codeW) / 2;
      ctx.fill(
          codeX,
          resultY,
          codeX + codeW,
          resultY + 14,
          KeysetTheme.withAlpha(KeysetTheme.CHIP_BG, screenAlpha));
      ctx.drawBorder(
          codeX, resultY, codeW, 14, KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal(shareResultCode),
          codeX + codeW / 2,
          resultY + 3,
          KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));

      long daysLeft = Math.max(0, (shareExpiresAt - System.currentTimeMillis()) / 86_400_000L);
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal("Expires in " + daysLeft + " day" + (daysLeft == 1 ? "" : "s")),
          leftX + halfW / 2,
          resultY + 18,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));

      int copyW = 80, copyH = 16;
      int copyX = leftX + (halfW - copyW) / 2;
      int copyY = resultY + 32;
      boolean copyHov =
          !isOverTutorialPanel(mx, my)
              && mx >= copyX
              && mx < copyX + copyW
              && my >= copyY
              && my < copyY + copyH;
      ctx.fill(
          copyX,
          copyY,
          copyX + copyW,
          copyY + copyH,
          KeysetTheme.withAlpha(
              copyHov ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE, screenAlpha));
      ctx.drawBorder(
          copyX,
          copyY,
          copyW,
          copyH,
          KeysetTheme.withAlpha(copyHov ? KeysetTheme.ACCENT : KeysetTheme.BORDER, screenAlpha));
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal("Copy Code"),
          copyX + copyW / 2,
          copyY + 4,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_BODY, screenAlpha));
      shareTargets.add(new ShareTarget("copy", copyX, copyY, copyW, copyH));
    }

    // ── Import (right) ─────────────────────────────────────────────────────
    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal("Import Profile"),
        rightX,
        topY,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));

    int importBtnW = 56, importBtnH = 18;
    int fieldY = topY + 16 + gapSm;
    int fieldW = halfW - importBtnW - gapSm;
    int importBtnX = rightX + fieldW + gapSm;
    boolean importDisabled = shareDownloading;
    boolean importHov =
        !importDisabled
            && !isOverTutorialPanel(mx, my)
            && mx >= importBtnX
            && mx < importBtnX + importBtnW
            && my >= fieldY
            && my < fieldY + importBtnH;
    ctx.fill(
        importBtnX,
        fieldY,
        importBtnX + importBtnW,
        fieldY + importBtnH,
        KeysetTheme.withAlpha(
            importHov ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE,
            importDisabled ? screenAlpha * 0.5f : screenAlpha));
    ctx.drawBorder(
        importBtnX,
        fieldY,
        importBtnW,
        importBtnH,
        KeysetTheme.withAlpha(
            importHov ? KeysetTheme.ACCENT : KeysetTheme.BORDER,
            importDisabled ? screenAlpha * 0.5f : screenAlpha));
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal("Import"),
        importBtnX + importBtnW / 2,
        fieldY + 5,
        KeysetTheme.withAlpha(
            importDisabled ? KeysetTheme.TEXT_DISABLED : KeysetTheme.TEXT_BODY, screenAlpha));
    if (!importDisabled)
      shareTargets.add(new ShareTarget("import", importBtnX, fieldY, importBtnW, importBtnH));

    if (shareDownloading) {
      String[] frames = {"|", "/", "─", "\\"};
      int frame = (int) ((System.currentTimeMillis() / 150) % frames.length);
      ctx.drawTextWithShadow(
          textRenderer,
          Text.literal(frames[frame]),
          rightX,
          fieldY + importBtnH + gapSm,
          KeysetTheme.withAlpha(KeysetTheme.ACCENT, screenAlpha));
    }
  }

  private void doShareUpload() {
    if (selectedProfileId == null || client == null) return;
    String json;
    try {
      json = service.exportProfileJson(client, selectedProfileId);
    } catch (IOException | IllegalArgumentException e) {
      setStatus("Share: " + e.getMessage(), true);
      return;
    }
    shareUploading = true;
    shareResultCode = "";
    ShareApiClient.upload(json)
        .thenAcceptAsync(
            result -> {
              shareUploading = false;
              shareResultCode = result.code();
              shareExpiresAt = result.expiresAt();
            },
            MinecraftClient.getInstance()::execute)
        .exceptionally(
            ex -> {
              MinecraftClient.getInstance()
                  .execute(
                      () -> {
                        shareUploading = false;
                        setStatus("Upload failed: " + simplifyError(ex), true);
                      });
              return null;
            });
  }

  private void doShareCopy() {
    if (client == null || shareResultCode.isEmpty()) return;
    client.keyboard.setClipboard(shareResultCode);
    setStatus("Code copied to clipboard.", false);
  }

  private void doShareImport() {
    if (client == null || shareCodeField == null) return;
    String code = shareCodeField.getText().trim().toUpperCase();
    if (code.length() != 8) {
      setStatus("Enter a valid 8-character share code.", true);
      return;
    }
    shareDownloading = true;
    ShareApiClient.download(code)
        .thenAcceptAsync(
            result -> {
              try {
                KeysetFabricService.ImportResult ir = service.importProfiles(client, result.data());
                if (ir.getImportedCount() > 0) {
                  selectedProfileId = ir.getLastImportedProfileId();
                  setStatus(
                      Text.translatable("keyset.status.imported", ir.getImportedCount())
                          .getString(),
                      false);
                } else {
                  setStatus("No profiles found in shared data.", true);
                }
              } catch (IOException | IllegalArgumentException e) {
                setStatus("Import failed: " + e.getMessage(), true);
              }
              shareDownloading = false;
            },
            MinecraftClient.getInstance()::execute)
        .exceptionally(
            ex -> {
              MinecraftClient.getInstance()
                  .execute(
                      () -> {
                        shareDownloading = false;
                        String msg = simplifyError(ex);
                        if (msg.contains("not_found")) {
                          setStatus("Code not found or expired.", true);
                        } else {
                          setStatus("Download failed: " + msg, true);
                        }
                      });
              return null;
            });
  }

  private static String simplifyError(Throwable ex) {
    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
    String msg = t.getMessage();
    return msg != null ? msg : t.getClass().getSimpleName();
  }

  private void openConflictDialog(ConflictTarget t) {
    didOpenConflictDialog = true;
    String activeId;
    try {
      activeId = service.getConfig(client).getActiveProfileId();
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
                service.clearActiveBinding(client, t.bindingId());
                setStatus(Text.translatable("keyset.status.binding_cleared").getString(), false);
              } catch (IOException | IllegalArgumentException e) {
                setStatus(e.getMessage(), true);
              }
            };
    Runnable onJump =
        t.bindingId() == null
            ? () -> {}
            : () ->
                client.setScreen(
                    new KeysetKeybindsScreen(this, client.options, service, t.bindingId(), false));
    client.setScreen(
        new ConflictDialog(this, t.keyLabel(), t.actionName(), t.otherActions(), onClear, onJump));
  }

  // ── Render helpers ───────────────────────────────────────────────────────────

  private void renderDoneButton(DrawContext ctx, int mx, int my) {
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
    ctx.drawBorder(
        bx,
        by,
        bw,
        bh,
        KeysetTheme.withAlpha(hovered ? KeysetTheme.ACCENT : KeysetTheme.BORDER, screenAlpha));
    Text doneText = Text.translatable("keyset.action.done");
    int doneCol = KeysetTheme.withAlpha(hovered ? 0xFF1A0800 : KeysetTheme.TEXT_BODY, screenAlpha);
    if (hovered) {
      ctx.drawText(
          textRenderer,
          doneText,
          bx + bw / 2 - textRenderer.getWidth(doneText) / 2,
          by + 5,
          doneCol,
          false); // no shadow on amber bg
    } else {
      ctx.drawCenteredTextWithShadow(textRenderer, doneText, bx + bw / 2, by + 5, doneCol);
    }
  }

  private void renderFooter(DrawContext ctx) {
    if (statusMsg.isEmpty()) return;
    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal(statusMsg),
        KeysetTheme.PAD,
        height - KeysetTheme.FOOTER_H / 2 - 4,
        statusError ? KeysetTheme.ERROR : KeysetTheme.SUCCESS);
  }

  // ── Profile actions ───────────────────────────────────────────────────────────

  private void activateSelected() {
    if (selectedProfileId == null) return;
    try {
      service.activateProfile(client, selectedProfileId);
      didActivate = true;
      setStatus(Text.translatable("keyset.status.profile_applied").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void saveLiveSelected() {
    if (selectedProfileId == null) return;
    try {
      service.captureCurrentToProfile(client, selectedProfileId, true);
      didSaveLive = true;
      setStatus(Text.translatable("keyset.status.profile_captured").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void newProfile() {
    try {
      String id = service.createProfileFromCurrent(client, "New Profile");
      selectedProfileId = id;
      setStatus(Text.translatable("keyset.status.profile_created").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void renameSelected() {
    if (selectedProfileId == null) return;
    KeysetProfilesConfig cfg;
    try {
      cfg = service.getConfig(client);
    } catch (IOException e) {
      setStatus(e.getMessage(), true);
      return;
    }
    KeysetProfile profile = cfg.getProfile(selectedProfileId);
    if (profile == null) return;
    client.setScreen(new RenameProfileDialog(this, service, selectedProfileId, profile.getName()));
  }

  private void cloneSelected() {
    if (selectedProfileId == null) return;
    try {
      String id = service.duplicateProfile(client, selectedProfileId);
      selectedProfileId = id;
      setStatus(Text.translatable("keyset.status.profile_duplicated").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void confirmDelete() {
    if (selectedProfileId == null) return;
    KeysetProfilesConfig cfg;
    try {
      cfg = service.getConfig(client);
    } catch (IOException e) {
      setStatus(e.getMessage(), true);
      return;
    }
    KeysetProfile profile = cfg.getProfile(selectedProfileId);
    String name = profile != null ? profile.getName() : selectedProfileId;
    client.setScreen(
        new KeysetConfirmDialog(
            this,
            Text.translatable("keyset.confirm.delete_profile"),
            Text.translatable("keyset.confirm.delete_body", name),
            () -> {
              try {
                service.deleteProfile(client, selectedProfileId);
                selectedProfileId = service.getConfig(client).getActiveProfileId();
                setStatus(Text.translatable("keyset.status.profile_deleted").getString(), false);
              } catch (IOException | IllegalArgumentException e) {
                setStatus(e.getMessage(), true);
              }
            }));
  }

  private void moveUp() {
    if (selectedProfileId == null) return;
    try {
      service.moveProfileUp(client, selectedProfileId);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void moveDown() {
    if (selectedProfileId == null) return;
    try {
      service.moveProfileDown(client, selectedProfileId);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void copyToClipboard() {
    if (selectedProfileId == null) return;
    try {
      client.keyboard.setClipboard(service.exportProfileJson(client, selectedProfileId));
      setStatus(Text.translatable("keyset.status.exported").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void pasteFromClipboard() {
    try {
      KeysetFabricService.ImportResult result =
          service.importProfiles(client, client.keyboard.getClipboard());
      if (result.getImportedCount() > 0) {
        selectedProfileId = result.getLastImportedProfileId();
        setStatus(
            Text.translatable("keyset.status.imported", result.getImportedCount()).getString(),
            false);
      }
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  // ── Conflict auto-refresh ────────────────────────────────────────────────────

  private String computeKeybindHash() {
    if (client == null || client.options == null || client.options.allKeys == null) return "";
    StringBuilder sb = new StringBuilder();
    for (KeyBinding kb : client.options.allKeys) {
      sb.append(kb.getTranslationKey())
          .append('=')
          .append(kb.getBoundKeyTranslationKey())
          .append(';');
    }
    return sb.toString();
  }

  private void refreshConflicts() {
    conflictGroups.clear();
    conflictsScrollTarget = 0;
    conflictsScrollSmooth = 0;
    if (client == null || client.options == null || client.options.allKeys == null) return;
    Map<String, List<KeyBinding>> byKey = new LinkedHashMap<>();
    for (KeyBinding kb : client.options.allKeys) {
      String boundKey = kb.getBoundKeyTranslationKey();
      if (boundKey.equals("key.keyboard.unknown")) continue;
      byKey.computeIfAbsent(boundKey, k -> new ArrayList<>()).add(kb);
    }
    for (Map.Entry<String, List<KeyBinding>> entry : byKey.entrySet()) {
      if (entry.getValue().size() > 1) {
        conflictGroups.add(new ConflictGroup(entry.getKey(), entry.getValue()));
      }
    }
  }

  // ── Tutorial ─────────────────────────────────────────────────────────────────

  private int getProfileCount() {
    try {
      return service.getConfig(client).getProfiles().size();
    } catch (IOException e) {
      return 0;
    }
  }

  private String getSelectedProfileName() {
    if (selectedProfileId == null) return "";
    try {
      KeysetProfile p = service.getConfig(client).getProfile(selectedProfileId);
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
      default:
        return true;
    }
  }

  private void advanceTutorial() {
    TutorialStep[] steps = TutorialStep.values();
    tutorialStep = steps[tutorialStep.ordinal() + 1];
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
    } else if (tutorialStep == TutorialStep.DONE) {
      tutorialActive = false;
      service.setTutorialComplete(client, true);
    }
    clearChildren();
    init();
  }

  private void skipTutorialStep() {
    advanceTutorial();
  }

  private void closeTutorial() {
    tutorialActive = false;
    service.setTutorialComplete(client, true);
    clearChildren();
    init();
  }

  private void renderTutorialPanel(DrawContext ctx, int mx, int my) {
    int pw = tutorialPanelWidth();
    int ph = tutorialPanelHeight();
    int px = mainX + mainW - pw - 10;
    int py = mainY + mainH - ph - 10;

    ctx.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x60000000);
    ctx.fill(px, py, px + pw, py + ph, 0xFF1E2125); // fully opaque — nothing bleeds through
    ctx.drawBorder(px, py, pw, ph, KeysetTheme.ACCENT);
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
    ctx.drawBorder(nbx, nby, 70, 16, nextBr);
    Text nextLabel =
        Text.translatable(
            tutorialStep == TutorialStep.AUTO_SWITCH
                ? "keyset.tutorial.finish"
                : "keyset.tutorial.next");
    if (nextHov && tutNextEnabled) {
      ctx.drawText(
          textRenderer,
          nextLabel,
          nbx + 35 - textRenderer.getWidth(nextLabel) / 2,
          nby + 4,
          nextTxt,
          false);
    } else {
      ctx.drawCenteredTextWithShadow(textRenderer, nextLabel, nbx + 35, nby + 4, nextTxt);
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
    ctx.drawBorder(
        sbx,
        sby,
        sbw,
        16,
        skipHov ? KeysetTheme.BORDER : KeysetTheme.withAlpha(KeysetTheme.BORDER, 0.5f));
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.translatable("keyset.tutorial.skip_step"),
        sbx + sbw / 2,
        sby + 4,
        skipHov ? KeysetTheme.TEXT_BODY : KeysetTheme.TEXT_MUTED);
  }

  private void renderTutorialCloseButton(DrawContext ctx, int mx, int my, int px, int py) {
    boolean closeHov = mx >= px + 5 && mx < px + 15 && my >= py + 4 && my < py + 14;
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal("x"),
        px + 10,
        py + 5,
        closeHov ? KeysetTheme.ERROR : KeysetTheme.TEXT_MUTED);
  }

  private void renderTutorialIntroPage(DrawContext ctx, int mx, int my) {
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
        Text.literal("Keyset").styled(style -> style.withBold(true)),
        centerX,
        titleY + 38,
        2.4f * titlePulse,
        titleColor);
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal("Profiles for every way you play"),
        centerX,
        centerY - 24,
        KeysetTheme.TEXT_TITLE);

    var lines =
        textRenderer.wrapLines(
            Text.translatable("keyset.tutorial.intro.body"), Math.min(360, width - 48));
    for (int i = 0; i < Math.min(lines.size(), 3); i++) {
      ctx.drawCenteredTextWithShadow(
          textRenderer, lines.get(i), centerX, centerY - 4 + i * 12, KeysetTheme.TEXT_BODY);
    }

    renderIntroCredit(ctx, centerX, centerY + 36);

    int buttonY = centerY + 56;
    renderIntroButton(
        ctx,
        Text.translatable("keyset.tutorial.start_tutorial"),
        centerX - 128,
        buttonY,
        120,
        mx,
        my,
        true);
    renderIntroButton(
        ctx,
        Text.translatable("keyset.tutorial.skip_tutorial"),
        centerX + 8,
        buttonY,
        120,
        mx,
        my,
        false);
  }

  private void renderIntroKeycapBackground(DrawContext ctx, long now) {
    int keycapCount = introKeycapCount();
    for (int i = 0; i < keycapCount; i++) {
      int[] key = INTRO_KEYCAP_SEEDS[i];
      IntroAnim anim = introAnimation(now, i * 28);
      if (anim.alpha() <= 0f) {
        continue;
      }

      int size = key[2];
      int x = MathHelper.clamp((key[0] * width) / 100 - size / 2, 12, width - size - 12);
      int y = MathHelper.clamp((key[1] * height) / 100 - size / 2, 12, height - size - 12);
      int border = KeysetTheme.withAlpha(0xFF555B64, anim.alpha() * 0.17f);
      drawAnimatedRectOutline(ctx, x, y, size, size, 1, border, anim.reveal());
    }
  }

  private int introKeycapCount() {
    float widthProgress = (width - 400f) / (1500f - 400f);
    float areaProgress = ((width * height) - (400f * 280f)) / ((1500f * 900f) - (400f * 280f));
    float progress = MathHelper.clamp(Math.max(widthProgress, areaProgress), 0f, 1f);
    int count = Math.round(5f + progress * 45f);
    return MathHelper.clamp(count, 5, INTRO_KEYCAP_SEEDS.length);
  }

  private void renderIntroAmbientDetails(DrawContext ctx, long now) {
    IntroAnim profileA = introAnimation(now, 80);
    IntroAnim profileB = introAnimation(now, 180);
    IntroAnim route = introAnimation(now, 260);
    IntroAnim chip = introAnimation(now, 360);
    drawIntroProfileCard(ctx, 30, height / 2 - 112, 104, "PvP", "WASD / Mouse", profileA);
    drawIntroProfileCard(
        ctx, width - 134, height / 2 + 58, 104, "Build", "Shift / Blocks", profileB);

    int routeY = MathHelper.clamp(height / 2 + 116, 96, height - 88);
    int routeX = MathHelper.clamp(width / 2 - 238, 18, width - 476);
    drawIntroRoute(ctx, routeX, routeY, route);

    int chipY = MathHelper.clamp(height / 2 - 152, 26, height - 48);
    int chipX = MathHelper.clamp(width / 2 + 178, 22, width - 156);
    drawIntroServerChip(ctx, chipX, chipY, chip);
  }

  private void drawIntroProfileCard(
      DrawContext ctx, int x, int y, int w, String title, String detail, IntroAnim anim) {
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
      ctx.drawTextWithShadow(
          textRenderer,
          Text.literal(title),
          x + 9,
          y + 8,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, 0.42f * textAlpha));
      ctx.drawTextWithShadow(
          textRenderer,
          Text.literal(detail),
          x + 9,
          y + 23,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_DISABLED, 0.5f * textAlpha));
    }
  }

  private void drawIntroRoute(DrawContext ctx, int x, int y, IntroAnim anim) {
    if (anim.alpha() <= 0f) {
      return;
    }
    int accent = KeysetTheme.withAlpha(KeysetTheme.ACCENT, 0.18f * anim.alpha());
    int border = KeysetTheme.withAlpha(0xFF39404A, 0.16f * anim.alpha());
    float textAlpha = introTextAlpha(anim);
    int node = 16;
    for (int i = 0; i < 3; i++) {
      int nx = x + i * 106;
      float reveal = MathHelper.clamp(anim.reveal() * 5f - i * 1.2f, 0f, 1f);
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
            MathHelper.clamp(anim.reveal() * 5f - i * 1.2f - 0.8f, 0f, 1f));
      }
    }
    if (textAlpha > 0f) {
      ctx.drawTextWithShadow(
          textRenderer,
          Text.literal("server -> profile -> keys"),
          x + 2,
          y + 23,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, 0.42f * textAlpha));
    }
  }

  private void drawIntroServerChip(DrawContext ctx, int x, int y, IntroAnim anim) {
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
      ctx.drawTextWithShadow(
          textRenderer,
          Text.literal("auto-switch ready"),
          x + 16,
          y + 7,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, 0.42f * textAlpha));
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

  private void renderIntroCredit(DrawContext ctx, int centerX, int y) {
    Text made = Text.literal("Made with ");
    Text heart = Text.literal("\u2764");
    Text by = Text.literal(" by ");
    Text author = Text.literal("BeeBoyD");
    int totalWidth =
        textRenderer.getWidth(made)
            + textRenderer.getWidth(heart)
            + textRenderer.getWidth(by)
            + textRenderer.getWidth(author);
    int x = centerX - totalWidth / 2;
    ctx.drawTextWithShadow(textRenderer, made, x, y, KeysetTheme.TEXT_MUTED);
    x += textRenderer.getWidth(made);
    ctx.drawTextWithShadow(textRenderer, heart, x, y, 0xFFFF5C5C);
    x += textRenderer.getWidth(heart);
    ctx.drawTextWithShadow(textRenderer, by, x, y, KeysetTheme.TEXT_MUTED);
    x += textRenderer.getWidth(by);
    ctx.drawTextWithShadow(textRenderer, author, x, y, KeysetTheme.ACCENT);
  }

  private void drawAnimatedRectOutline(
      DrawContext ctx, int x, int y, int w, int h, int thickness, int color, float reveal) {
    drawAnimatedPath(ctx, thickness, color, reveal, x, y, x + w, y, x + w, y + h, x, y + h, x, y);
  }

  private void drawAnimatedPath(
      DrawContext ctx, int thickness, int color, float reveal, int... points) {
    int segments = points.length / 2 - 1;
    if (segments <= 0) {
      return;
    }
    for (int i = 0; i < segments; i++) {
      float segmentReveal = MathHelper.clamp(reveal * segments - i, 0f, 1f);
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
      DrawContext ctx, int x1, int y1, int x2, int y2, int thickness, int color, float reveal) {
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
      DrawContext ctx, Text label, int x, int y, int w, int mx, int my, boolean primary) {
    boolean hovered = mx >= x && mx < x + w && my >= y && my < y + 22;
    int bg =
        hovered ? KeysetTheme.ACCENT : primary ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE;
    int textColor = hovered ? 0xFF1A0800 : primary ? KeysetTheme.TEXT_TITLE : KeysetTheme.TEXT_BODY;
    ctx.fill(x, y, x + w, y + 22, bg);
    ctx.drawBorder(x, y, w, 22, hovered || primary ? KeysetTheme.ACCENT : KeysetTheme.BORDER);
    if (hovered) {
      ctx.drawText(
          textRenderer,
          label,
          x + w / 2 - textRenderer.getWidth(label) / 2,
          y + 7,
          textColor,
          false);
    } else {
      ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 7, textColor);
    }
  }

  private void renderTutorialStepContent(DrawContext ctx, int px, int py, int pw, int ph) {
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

    ctx.drawTextWithShadow(
        textRenderer, Text.literal("TUTORIAL"), px + 18, py + 5, KeysetTheme.TEXT_MUTED);
    ctx.drawTextWithShadow(
        textRenderer,
        Text.translatable(tutorialStep.titleKey()),
        px + 6,
        py + 24,
        KeysetTheme.TEXT_TITLE);

    var lines = textRenderer.wrapLines(Text.translatable(tutorialStep.bodyKey()), pw - 12);
    int bodyLineLimit =
        tutorialStep == TutorialStep.WELCOME
            ? Math.max(3, (ph - 60) / 11)
            : Math.max(3, (ph - 86) / 11);
    for (int i = 0; i < Math.min(lines.size(), bodyLineLimit); i++) {
      ctx.drawTextWithShadow(
          textRenderer, lines.get(i), px + 6, py + 36 + i * 11, KeysetTheme.TEXT_BODY);
    }

    boolean complete = isStepComplete();
    if (complete && tutorialStep != TutorialStep.WELCOME) {
      ctx.drawTextWithShadow(
          textRenderer, Text.literal("✓ Done!"), px + 6, py + ph - 38, KeysetTheme.SUCCESS);
    } else if (tutorialStep != TutorialStep.WELCOME) {
      ctx.drawTextWithShadow(
          textRenderer,
          Text.translatable(tutorialStep.hintKey()),
          px + 6,
          py + ph - 38,
          KeysetTheme.TEXT_MUTED);
    }
  }

  private void renderTutorialArrow(DrawContext ctx) {
    if (tutorialStep == TutorialStep.INTRO
        || tutorialStep == TutorialStep.WELCOME
        || tutorialStep == TutorialStep.DONE) return;
    float pulse = (float) (Math.sin(System.currentTimeMillis() / 400.0) * 0.3 + 0.7);
    int arrowCol = KeysetTheme.withAlpha(KeysetTheme.ACCENT, pulse);
    Text rightArrow = Text.literal("▶");
    Text downArrow = Text.literal("▼");

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
                  ctx.drawTextWithShadow(
                      textRenderer,
                      rightArrow,
                      btn.x() - 14,
                      btn.y() + (btn.h() - 9) / 2,
                      arrowCol));
    } else if (tutorialStep == TutorialStep.CONFLICTS
        || tutorialStep == TutorialStep.FIX_CONFLICT) {
      int tabW = mainW / 4;
      int tx = mainX + Tab.CONFLICTS.ordinal() * tabW;
      ctx.drawTextWithShadow(textRenderer, downArrow, tx + tabW / 2 - 3, tabBarY - 12, arrowCol);
    } else if (tutorialStep == TutorialStep.AUTO_SWITCH) {
      int tabW = mainW / 4;
      int tx = mainX + Tab.AUTO_SWITCH.ordinal() * tabW;
      ctx.drawTextWithShadow(textRenderer, downArrow, tx + tabW / 2 - 3, tabBarY - 12, arrowCol);
    }
  }

  private void renderTutorialDarkening(DrawContext ctx) {
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
    } else if (tutorialStep == TutorialStep.AUTO_SWITCH) {
      fx = mainX;
      fy = tabBarY;
      fw = mainW;
      fh = KeysetTheme.TAB_H + 4;
    } else {
      ctx.fill(0, 0, width, height, 0x55000000);
      return;
    }
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
    statusMsg = msg == null ? "" : msg;
    statusError = error;
  }

  @Override
  public boolean shouldPause() {
    return false;
  }

  @Override
  public void close() {
    this.client.setScreen(parent);
  }
}
