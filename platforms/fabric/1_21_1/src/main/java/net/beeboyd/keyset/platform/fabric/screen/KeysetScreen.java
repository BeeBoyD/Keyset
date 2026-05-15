package net.beeboyd.keyset.platform.fabric.screen;

import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class KeysetScreen extends Screen {

  enum Tab {
    BINDINGS,
    CONFLICTS,
    AUTO_SWITCH,
    SHARE
  }

  private final Screen parent;
  private final KeysetFabricService service;
  private Tab currentTab = Tab.BINDINGS;

  // Layout (computed in init)
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
    addDrawableChild(
        ButtonWidget.builder(Text.translatable("keyset.action.done"), b -> close())
            .dimensions(width - 80 - KeysetTheme.PAD, height - 24 - KeysetTheme.PAD, 80, 20)
            .build());
  }

  private void computeLayout() {
    int p = KeysetTheme.PAD;
    topbarY = p;
    sidebarX = p;
    sidebarY = p + KeysetTheme.TOPBAR_H + KeysetTheme.GAP;
    mainX = sidebarX + KeysetTheme.SIDEBAR_W + KeysetTheme.GAP;
    mainY = sidebarY;
    mainW = width - mainX - p;
    mainH = height - sidebarY - KeysetTheme.FOOTER_H - KeysetTheme.GAP - p;
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
    float dt = lastFrameMs > 0 ? Math.min(now - lastFrameMs, 50f) : 16f;
    lastFrameMs = now;
    screenAlpha = KeysetTheme.easeOutQuart(Math.min(1f, (now - openedAtMs) / KeysetTheme.OPEN_MS));
    tabUnderlineX = KeysetTheme.expLerp(tabUnderlineX, tabUnderlineTarget, dt, 25f);

    ctx.fill(0, 0, width, height, KeysetTheme.scaleAlpha(KeysetTheme.BG_BACKDROP, screenAlpha));
    renderTopbar(ctx);
    renderSidebar(ctx);
    renderMain(ctx, mouseX, mouseY);

    super.render(ctx, mouseX, mouseY, delta);
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

  private void renderSidebar(DrawContext ctx) {
    int sx = sidebarX;
    int sy = sidebarY;
    int sw = KeysetTheme.SIDEBAR_W;
    int sh = mainH;
    ctx.fill(sx, sy, sx + sw, sy + sh, KeysetTheme.scaleAlpha(KeysetTheme.BG_SIDEBAR, screenAlpha));
    ctx.drawBorder(sx, sy, sw, sh, KeysetTheme.withAlpha(KeysetTheme.BORDER, screenAlpha));
    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal("PROFILES"),
        sx + KeysetTheme.CARD_PAD,
        sy + KeysetTheme.CARD_PAD,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));
    ctx.drawTextWithShadow(
        textRenderer,
        Text.literal("(profile list)"),
        sx + KeysetTheme.CARD_PAD,
        sy + KeysetTheme.CARD_PAD + 18,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_DISABLED, screenAlpha));
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

    int contentH = mainY + mainH - contentY;
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.literal(names[currentTab.ordinal()] + " — coming soon"),
        mainX + mainW / 2,
        contentY + contentH / 2 - 4,
        KeysetTheme.withAlpha(KeysetTheme.TEXT_MUTED, screenAlpha));
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0
        && mouseY >= tabBarY
        && mouseY < tabBarY + KeysetTheme.TAB_H
        && mouseX >= mainX
        && mouseX < mainX + mainW) {
      int idx = MathHelper.clamp((int) ((mouseX - mainX) / (mainW / 4)), 0, 3);
      currentTab = Tab.values()[idx];
      tabUnderlineTarget = mainX + idx * (mainW / 4f);
      return true;
    }
    return super.mouseClicked(mouseX, mouseY, button);
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
