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
import net.beeboyd.keyset.core.binding.KeysetBindingDescriptor;
import net.beeboyd.keyset.core.conflict.KeysetConflict;
import net.beeboyd.keyset.core.conflict.KeysetConflictReport;
import net.beeboyd.keyset.core.profile.KeysetBindingSnapshot;
import net.beeboyd.keyset.core.profile.KeysetProfile;
import net.beeboyd.keyset.core.profile.KeysetProfilesConfig;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class KeysetScreen extends Screen {

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
  private TextFieldWidget bindingsSearch;
  private boolean bindingsGroupByCategory = true;
  private float bindingsScrollTarget;
  private float bindingsScrollSmooth;

  // Conflicts tab
  private TextFieldWidget conflictsSearch;
  private float conflictsScrollTarget;
  private float conflictsScrollSmooth;
  private final Set<String> expandedConflictGroups = new HashSet<>();
  private final List<ConflictTarget> conflictTargets = new ArrayList<>();

  // Auto-switch tab
  private final List<AutoSwitchTarget> autoSwitchTargets = new ArrayList<>();

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

  public KeysetScreen(Screen parent, KeysetFabricService service) {
    super(Text.translatable("keyset.title"));
    this.parent = parent;
    this.service = service;
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

    // ? help button — always accessible in topbar
    int helpBtnX = width - KeysetTheme.PAD - 20;
    int helpBtnY = topbarY + (KeysetTheme.TOPBAR_H - 16) / 2;
    addDrawableChild(
        ButtonWidget.builder(
                Text.literal("?"), b -> client.setScreen(new TutorialScreen(this, service)))
            .dimensions(helpBtnX, helpBtnY, 16, 16)
            .build());
  }

  private void buildSidebarButtons() {
    sidebarButtons.clear();
    int bx = sidebarX + KeysetTheme.GAP_SM;
    int bw = sidebarW - KeysetTheme.GAP_SM * 2;
    int by = sidebarY + mainH - 84;
    int g = KeysetTheme.GAP_SM;
    int hw = (bw - g) / 2;
    int tw = (bw - g * 2) / 3;
    int qw = (bw - g * 3) / 4;

    sidebarButtons.add(
        new SidebarBtn("keyset.profile.apply", this::activateSelected, bx, by, hw, 18));
    sidebarButtons.add(
        new SidebarBtn("keyset.profile.capture", this::saveLiveSelected, bx + hw + g, by, hw, 18));
    sidebarButtons.add(new SidebarBtn("keyset.profile.new", this::newProfile, bx, by + 22, tw, 18));
    sidebarButtons.add(
        new SidebarBtn(
            "keyset.profile.duplicate", this::cloneSelected, bx + tw + g, by + 22, tw, 18));
    sidebarButtons.add(
        new SidebarBtn(
            "keyset.profile.delete", this::confirmDelete, bx + (tw + g) * 2, by + 22, tw, 18));
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
    int sx = mainX + KeysetTheme.GAP;
    int sy = contentY + KeysetTheme.GAP;
    int toggleW = 72;
    int searchW = mainW - KeysetTheme.GAP * 2 - toggleW - KeysetTheme.GAP_SM;
    if (currentTab == Tab.BINDINGS) {
      bindingsSearch = new TextFieldWidget(textRenderer, sx, sy, searchW, 18, Text.empty());
      bindingsSearch.setPlaceholder(Text.translatable("keyset.search.placeholder"));
      bindingsSearch.setMaxLength(64);
      addDrawableChild(bindingsSearch);
    } else if (currentTab == Tab.CONFLICTS) {
      conflictsSearch = new TextFieldWidget(textRenderer, sx, sy, searchW, 18, Text.empty());
      conflictsSearch.setPlaceholder(Text.translatable("keyset.search.placeholder"));
      conflictsSearch.setMaxLength(64);
      addDrawableChild(conflictsSearch);
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
    ctx.drawTextWithShadow(
        textRenderer,
        this.title,
        KeysetTheme.PAD + 12,
        topbarY + 13,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_TITLE, screenAlpha));
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
          mx >= sidebarX && mx < sidebarX + sidebarW && my >= rowY && my < rowY + rowH;

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
          mx >= btn.x() && mx < btn.x() + btn.w() && my >= btn.y() && my < btn.y() + btn.h();
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
    } else {
      int contentH = mainY + mainH - contentY;
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal(names[currentTab.ordinal()] + " — coming soon"),
          mainX + mainW / 2,
          contentY + contentH / 2 - 4,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));
    }
  }

  // ── Bindings tab ─────────────────────────────────────────────────────────────

  private void renderBindingsTab(DrawContext ctx, int mx, int my) {
    int toggleW = 72;
    int toggleX = mainX + mainW - KeysetTheme.GAP - toggleW;
    int toggleY = contentY + KeysetTheme.GAP;
    boolean toggleHovered =
        mx >= toggleX && mx < toggleX + toggleW && my >= toggleY && my < toggleY + 18;
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
    boolean hovered = mx >= x && mx < x + w && my >= y && my < y + KeysetTheme.ROW_H;
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

    if (selectedProfileId == null) {
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal("Select a profile to view conflicts."),
          mainX + mainW / 2,
          listTop + (listBot - listTop) / 2 - 4,
          KeysetTheme.withAlpha(KeysetTheme.TEXT_DISABLED, screenAlpha));
      return;
    }

    KeysetConflictReport report;
    try {
      report = service.buildConflictReport(client, selectedProfileId);
    } catch (IOException e) {
      return;
    }

    String filter = conflictsSearch != null ? conflictsSearch.getText().trim().toLowerCase() : "";
    List<KeysetConflict> conflicts = new ArrayList<>(report.getConflicts());
    if (!filter.isEmpty()) {
      List<KeysetConflict> filtered = new ArrayList<>();
      for (KeysetConflict c : conflicts) {
        if (matchesConflictFilter(c, filter)) filtered.add(c);
      }
      conflicts = filtered;
    }

    if (conflicts.isEmpty()) {
      int msgColor = report.isEmpty() ? KeysetTheme.SUCCESS : KeysetTheme.TEXT_DISABLED;
      String msg =
          report.isEmpty()
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

    // Compute content height
    int contentHeight = 0;
    for (KeysetConflict c : conflicts) {
      contentHeight += 28;
      if (expandedConflictGroups.contains(c.getKeySignature())) {
        contentHeight += c.getBindings().size() * KeysetTheme.ROW_H;
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

    for (KeysetConflict c : conflicts) {
      boolean expanded = expandedConflictGroups.contains(c.getKeySignature());

      conflictTargets.add(
          new ConflictTarget(
              true, c.getKeySignature(), rowX, curY, rowW, 28, null, null, null, null, null));

      if (curY + 28 > listTop && curY < listBot) {
        renderConflictGroup(
            ctx, c.getKeyDisplayName(), c.getBindings().size(), expanded, rowX, curY, rowW, mx, my);
      }
      curY += 28;

      if (expanded) {
        for (KeysetBindingDescriptor b : c.getBindings()) {
          List<String> others = new ArrayList<>();
          for (KeysetBindingDescriptor other : c.getBindings()) {
            if (!other.getId().equals(b.getId())) {
              others.add(other.getDisplayName());
            }
          }
          conflictTargets.add(
              new ConflictTarget(
                  false,
                  c.getKeySignature(),
                  rowX,
                  curY,
                  rowW,
                  KeysetTheme.ROW_H,
                  b.getId(),
                  b.getDisplayName(),
                  c.getKeyDisplayName(),
                  b.getCategoryName(),
                  others));
          if (curY + KeysetTheme.ROW_H > listTop && curY < listBot) {
            renderConflictBinding(
                ctx, b.getDisplayName(), b.getCategoryName(), rowX, curY, rowW, mx, my);
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
    boolean hov = mx >= x && mx < x + w && my >= y && my < y + 28;
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
    boolean hov = mx >= x + indent && mx < x + w && my >= y && my < y + KeysetTheme.ROW_H;
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

  private boolean matchesConflictFilter(KeysetConflict c, String filter) {
    if (c.getKeyDisplayName().toLowerCase().contains(filter)) return true;
    for (KeysetBindingDescriptor b : c.getBindings()) {
      if (b.getDisplayName().toLowerCase().contains(filter)) return true;
      if (b.getCategoryName().toLowerCase().contains(filter)) return true;
      if (b.getId().toLowerCase().contains(filter)) return true;
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
    boolean addHov = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;
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

      boolean rowHov = mx >= rowX && mx < rowX + rowW && my >= curY && my < curY + rowH;
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
      boolean delHov = mx >= delX && mx < delX + delW && my >= delY && my < delY + 14;
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

    return super.mouseClicked(mouseX, mouseY, button);
  }

  private void openConflictDialog(ConflictTarget t) {
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
    boolean hovered = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
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
      setStatus(Text.translatable("keyset.status.profile_applied").getString(), false);
    } catch (IOException | IllegalArgumentException e) {
      setStatus(e.getMessage(), true);
    }
  }

  private void saveLiveSelected() {
    if (selectedProfileId == null) return;
    try {
      service.captureCurrentToProfile(client, selectedProfileId, true);
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
        new ConfirmScreen(
            confirmed -> {
              if (confirmed) {
                try {
                  service.deleteProfile(client, selectedProfileId);
                  selectedProfileId = service.getConfig(client).getActiveProfileId();
                  setStatus(Text.translatable("keyset.status.profile_deleted").getString(), false);
                } catch (IOException | IllegalArgumentException e) {
                  setStatus(e.getMessage(), true);
                }
              }
              client.setScreen(this);
            },
            Text.translatable("keyset.confirm.delete_profile"),
            Text.translatable("keyset.confirm.delete_body", name)));
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
