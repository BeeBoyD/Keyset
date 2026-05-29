package net.beeboyd.keyset.platform.fabric.screen;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Modal popup shown when the user clicks a conflict binding row. */
public final class ConflictDialog extends Screen {

  private static final int DW = 300;
  private static final int DH = 148;

  private final Screen parent;
  private final String keyLabel;
  private final String actionName;
  private final List<String> otherActions;
  private final Runnable onClear;
  private final Runnable onJump;

  public ConflictDialog(
      Screen parent,
      String keyLabel,
      String actionName,
      List<String> otherActions,
      Runnable onClear,
      Runnable onJump) {
    super(Text.empty());
    this.parent = parent;
    this.keyLabel = keyLabel;
    this.actionName = actionName;
    this.otherActions = otherActions;
    this.onClear = onClear;
    this.onJump = onJump;
  }

  @Override
  protected void init() {
    int dw = panelW();
    int dh = panelH();
    int dx = (width - dw) / 2;
    int dy = (height - dh) / 2;
    int btnW = 84;
    int btnY = dy + dh - 26;

    addDrawableChild(
        KeysetButtonWidget.create(
            dx + 8, btnY, btnW, 20, Text.translatable("keyset.binding.jump"), b -> onJump.run()));

    addDrawableChild(
        KeysetButtonWidget.create(
            dx + 8 + btnW + 6,
            btnY,
            btnW,
            20,
            Text.translatable("keyset.binding.clear"),
            b -> {
              onClear.run();
              close();
            }));

    addDrawableChild(
        KeysetButtonWidget.primary(
            dx + dw - 8 - btnW,
            btnY,
            btnW,
            20,
            Text.translatable("keyset.action.done"),
            b -> close()));
  }

  @Override
  public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    // Full-screen dim so panel is visible over the parent screen.
    ctx.fill(0, 0, width, height, 0xCC000000);
  }

  @Override
  public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
    renderBackground(ctx, mouseX, mouseY, delta);

    int dw = panelW();
    int dh = panelH();
    int dx = (width - dw) / 2;
    int dy = (height - dh) / 2;

    // Shadow
    ctx.fill(dx + 4, dy + 4, dx + dw + 4, dy + dh + 4, 0x60000000);
    // Panel
    ctx.fill(dx, dy, dx + dw, dy + dh, KeysetTheme.BG_SURFACE);
    ctx.drawBorder(dx, dy, dw, dh, KeysetTheme.ACCENT);
    // Header stripe
    ctx.fill(dx, dy, dx + dw, dy + 22, KeysetTheme.BG_SIDEBAR);
    ctx.fill(dx, dy + 22, dx + dw, dy + 23, KeysetTheme.ACCENT_DIM);

    // Header: "Conflict — <key>"
    String header = "Conflict — " + keyLabel;
    ctx.drawTextWithShadow(
        textRenderer, Text.literal(header), dx + 8, dy + 7, KeysetTheme.TEXT_MUTED);

    // Action name
    ctx.drawTextWithShadow(
        textRenderer, Text.literal(actionName), dx + 8, dy + 30, KeysetTheme.TEXT_TITLE);

    // "Also bound to:" label
    ctx.drawTextWithShadow(
        textRenderer, Text.literal("Also bound to:"), dx + 8, dy + 46, KeysetTheme.TEXT_MUTED);

    // Other action chips
    int chipX = dx + 8;
    int chipY = dy + 58;
    int shown = 0;
    for (String other : otherActions) {
      if (shown >= 6 || chipY > dy + dh - 54) break;
      String label = textRenderer.trimToWidth(other, dw - 28);
      if (!label.equals(other)) label += "…";
      int cw = textRenderer.getWidth(label) + 10;
      if (chipX + cw > dx + dw - 8) {
        chipX = dx + 8;
        chipY += 18;
      }
      ctx.fill(chipX, chipY, chipX + cw, chipY + 14, KeysetTheme.CHIP_BG);
      ctx.drawBorder(chipX, chipY, cw, 14, KeysetTheme.BORDER);
      ctx.drawTextWithShadow(
          textRenderer, Text.literal(label), chipX + 5, chipY + 3, KeysetTheme.TEXT_BODY);
      chipX += cw + 4;
      shown++;
    }
    if (otherActions.size() > shown) {
      String more = "+" + (otherActions.size() - shown) + " more";
      ctx.drawTextWithShadow(
          textRenderer, Text.literal(more), dx + 8, dy + dh - 48, KeysetTheme.TEXT_DISABLED);
    }

    // Divider above buttons
    ctx.fill(dx + 8, dy + dh - 32, dx + dw - 8, dy + dh - 31, KeysetTheme.BORDER);

    // Buttons drawn by super
    super.render(ctx, mouseX, mouseY, delta);
  }

  @Override
  public boolean shouldCloseOnEsc() {
    return true;
  }

  @Override
  public void close() {
    client.setScreen(parent);
  }

  private int panelW() {
    return Math.max(1, Math.min(DW, width - 8));
  }

  private int panelH() {
    return Math.max(1, Math.min(DH, height - 8));
  }
}
