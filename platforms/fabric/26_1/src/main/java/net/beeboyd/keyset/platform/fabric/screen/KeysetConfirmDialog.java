package net.beeboyd.keyset.platform.fabric.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small Keyset-styled confirmation dialog. */
public final class KeysetConfirmDialog extends Screen {
  private static final int DW = 320;
  private static final int DH = 118;

  private final Screen parent;
  private final Component title;
  private final Component body;
  private final Runnable onConfirm;

  public KeysetConfirmDialog(Screen parent, Component title, Component body, Runnable onConfirm) {
    super(Component.empty());
    this.parent = parent;
    this.title = title;
    this.body = body;
    this.onConfirm = onConfirm;
  }

  @Override
  protected void init() {
    int dw = panelW();
    int dh = panelH();
    int dx = (width - dw) / 2;
    int dy = (height - dh) / 2;
    int btnY = dy + dh - 28;
    addRenderableWidget(
        KeysetButtonWidget.create(
            dx + 8, btnY, 84, 20, Component.translatable("keyset.action.cancel"), b -> onClose()));
    addRenderableWidget(
        KeysetButtonWidget.primary(
            dx + dw - 92,
            btnY,
            84,
            20,
            Component.translatable("keyset.action.delete"),
            b -> {
              onConfirm.run();
              onClose();
            }));
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    ctx.fill(0, 0, width, height, 0xCC000000);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    extractBackground(ctx, mouseX, mouseY, delta);
    int dw = panelW();
    int dh = panelH();
    int dx = (width - dw) / 2;
    int dy = (height - dh) / 2;

    ctx.fill(dx + 4, dy + 4, dx + dw + 4, dy + dh + 4, 0x60000000);
    ctx.fill(dx, dy, dx + dw, dy + dh, KeysetTheme.BG_SURFACE);
    ctx.outline(dx, dy, dw, dh, KeysetTheme.ACCENT);
    ctx.fill(dx, dy, dx + dw, dy + 22, KeysetTheme.BG_SIDEBAR);
    ctx.fill(dx, dy + 22, dx + dw, dy + 23, KeysetTheme.ACCENT_DIM);

    ctx.text(font, title, dx + 8, dy + 7, KeysetTheme.TEXT_TITLE, true);
    var lines = font.split(body, dw - 16);
    for (int i = 0; i < Math.min(lines.size(), 4); i++) {
      ctx.text(font, lines.get(i), dx + 8, dy + 34 + i * 12, KeysetTheme.TEXT_BODY, true);
    }
    ctx.fill(dx + 8, dy + dh - 34, dx + dw - 8, dy + dh - 33, KeysetTheme.BORDER);
    super.extractRenderState(ctx, mouseX, mouseY, delta);
  }

  @Override
  public boolean shouldCloseOnEsc() {
    return true;
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }

  private int panelW() {
    return Math.max(1, Math.min(DW, width - 8));
  }

  private int panelH() {
    return Math.max(1, Math.min(DH, height - 8));
  }
}
