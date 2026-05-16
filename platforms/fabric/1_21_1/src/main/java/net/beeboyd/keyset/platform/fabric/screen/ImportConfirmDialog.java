package net.beeboyd.keyset.platform.fabric.screen;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Modal shown after downloading a shared profile, before actually importing it. */
public final class ImportConfirmDialog extends Screen {

  private static final int PW = 360;
  private static final int PH = 210;

  private final Screen parent;
  private final String senderName;
  private final String profileName;
  private final List<String> missingBindings;
  private final Runnable onAccept;
  private final Runnable onDecline;

  public ImportConfirmDialog(
      Screen parent,
      String senderName,
      String profileName,
      List<String> missingBindings,
      Runnable onAccept,
      Runnable onDecline) {
    super(Text.empty());
    this.parent = parent;
    this.senderName = senderName;
    this.profileName = profileName;
    this.missingBindings = missingBindings;
    this.onAccept = onAccept;
    this.onDecline = onDecline;
  }

  @Override
  protected void init() {
    int px = (width - PW) / 2;
    int py = (height - PH) / 2;
    addDrawableChild(
        KeysetButtonWidget.primary(
            px + PW - 110,
            py + PH - 28,
            100,
            20,
            Text.translatable("keyset.share.dialog.accept"),
            b -> {
              onAccept.run();
              close();
            }));
    addDrawableChild(
        KeysetButtonWidget.create(
            px + 8,
            py + PH - 28,
            100,
            20,
            Text.translatable("keyset.share.dialog.decline"),
            b -> {
              onDecline.run();
              close();
            }));
  }

  @Override
  public void render(DrawContext ctx, int mx, int my, float delta) {
    int px = (width - PW) / 2;
    int py = (height - PH) / 2;

    // Backdrop dim
    ctx.fill(0, 0, width, height, 0xCC000000);

    // Shadow
    ctx.fill(px + 3, py + 3, px + PW + 3, py + PH + 3, 0x60000000);
    // Panel body
    ctx.fill(px, py, px + PW, py + PH, KeysetTheme.BG_SURFACE);
    ctx.drawBorder(px, py, PW, PH, KeysetTheme.ACCENT);
    // Title bar
    ctx.fill(px, py, px + PW, py + 22, KeysetTheme.BG_TAB_ACTIVE);
    ctx.fill(px, py + 22, px + PW, py + 23, KeysetTheme.ACCENT_DIM);

    ctx.drawTextWithShadow(
        textRenderer,
        Text.translatable("keyset.share.dialog.title"),
        px + 8,
        py + 7,
        KeysetTheme.TEXT_TITLE);

    String sender = (senderName == null || senderName.isEmpty()) ? "Someone" : senderName;
    ctx.drawTextWithShadow(
        textRenderer, Text.literal(sender + " shared:"), px + 8, py + 32, KeysetTheme.TEXT_MUTED);
    ctx.drawTextWithShadow(
        textRenderer, Text.literal(profileName), px + 8, py + 46, KeysetTheme.TEXT_TITLE);

    if (!missingBindings.isEmpty()) {
      ctx.fill(px + 8, py + 70, px + PW - 8, py + 71, KeysetTheme.CHIP_ERR_BR);
      ctx.drawTextWithShadow(
          textRenderer,
          Text.literal("⚠ " + missingBindings.size() + " keybind(s) not installed:"),
          px + 8,
          py + 76,
          KeysetTheme.WARNING);
      int listed = Math.min(missingBindings.size(), 3);
      for (int i = 0; i < listed; i++) {
        ctx.drawTextWithShadow(
            textRenderer,
            Text.literal("  • " + missingBindings.get(i)),
            px + 8,
            py + 90 + i * 11,
            KeysetTheme.TEXT_MUTED);
      }
      if (missingBindings.size() > 3) {
        ctx.drawTextWithShadow(
            textRenderer,
            Text.literal("  + " + (missingBindings.size() - 3) + " more"),
            px + 8,
            py + 90 + listed * 11,
            KeysetTheme.TEXT_DISABLED);
      }
    } else {
      ctx.drawTextWithShadow(
          textRenderer,
          Text.translatable("keyset.share.compat.ok"),
          px + 8,
          py + 70,
          KeysetTheme.SUCCESS);
    }

    super.render(ctx, mx, my, delta);
  }

  @Override
  public void close() {
    this.client.setScreen(parent);
  }

  @Override
  public boolean shouldPause() {
    return false;
  }
}
