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
    int dx = (width - DW) / 2;
    int dy = (height - DH) / 2;
    int btnY = dy + DH - 28;
    addRenderableWidget(
        KeysetButtonWidget.create(
            dx + 8, btnY, 84, 20, Component.translatable("keyset.action.cancel"), b -> onClose()));
    addRenderableWidget(
        KeysetButtonWidget.primary(
            dx + DW - 92,
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
    int dx = (width - DW) / 2;
    int dy = (height - DH) / 2;

    ctx.fill(dx + 4, dy + 4, dx + DW + 4, dy + DH + 4, 0x60000000);
    ctx.fill(dx, dy, dx + DW, dy + DH, KeysetTheme.BG_SURFACE);
    ctx.outline(dx, dy, DW, DH, KeysetTheme.ACCENT);
    ctx.fill(dx, dy, dx + DW, dy + 22, KeysetTheme.BG_SIDEBAR);
    ctx.fill(dx, dy + 22, dx + DW, dy + 23, KeysetTheme.ACCENT_DIM);

    ctx.text(font, title, dx + 8, dy + 7, KeysetTheme.TEXT_TITLE, true);
    var lines = font.split(body, DW - 16);
    for (int i = 0; i < Math.min(lines.size(), 4); i++) {
      ctx.text(font, lines.get(i), dx + 8, dy + 34 + i * 12, KeysetTheme.TEXT_BODY, true);
    }
    ctx.fill(dx + 8, dy + DH - 34, dx + DW - 8, dy + DH - 33, KeysetTheme.BORDER);
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
}
