package net.beeboyd.keyset.platform.fabric.screen;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Modal shown after downloading a shared profile, before actually importing it. */
public final class ImportConfirmDialog extends Screen {

  private static final int PW = 360;
  private static final int PH = 220;

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
    int pw = panelW();
    int ph = panelH();
    int px = (width - pw) / 2;
    int py = (height - ph) / 2;
    addDrawableChild(
        KeysetButtonWidget.create(
            px + pw - 20,
            py + 4,
            14,
            14,
            Text.literal("✕"),
            b -> {
              onDecline.run();
              close();
            }));
    addDrawableChild(
        KeysetButtonWidget.primary(
            px + pw - 110,
            py + ph - 38,
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
            py + ph - 38,
            100,
            20,
            Text.translatable("keyset.share.dialog.decline"),
            b -> {
              onDecline.run();
              close();
            }));
  }

  public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    ctx.fill(0, 0, width, height, 0xCC000000);
  }

  @Override
  public void render(DrawContext ctx, int mx, int my, float delta) {
    renderBackground(ctx, mx, my, delta);
    int pw = panelW();
    int ph = panelH();
    int px = (width - pw) / 2;
    int py = (height - ph) / 2;
    int cx = px + pw / 2;
    // Shadow
    ctx.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x60000000);
    // Panel body
    ctx.fill(px, py, px + pw, py + ph, KeysetTheme.BG_SURFACE);
    ctx.drawBorder(px, py, pw, ph, KeysetTheme.ACCENT);
    // Title bar
    ctx.fill(px, py, px + pw, py + 22, KeysetTheme.BG_TAB_ACTIVE);
    ctx.fill(px, py + 22, px + pw, py + 23, KeysetTheme.ACCENT_DIM);

    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.translatable("keyset.share.dialog.title"),
        cx,
        py + 7,
        KeysetTheme.TEXT_MUTED);

    String sender = (senderName == null || senderName.isEmpty()) ? "Someone" : senderName;
    sender = textRenderer.trimToWidth(sender + " shared:", pw - 20);
    ctx.drawCenteredTextWithShadow(
        textRenderer, Text.literal(sender), cx, py + 30, KeysetTheme.TEXT_MUTED);

    // Profile name — pseudo-bold
    String profile =
        (profileName == null || profileName.isEmpty()) ? "Unknown Profile" : profileName;
    profile = textRenderer.trimToWidth(profile, pw - 20);
    Text profileText = Text.literal(profile);
    ctx.drawCenteredTextWithShadow(textRenderer, profileText, cx + 1, py + 48, 0xFF222222);
    ctx.drawCenteredTextWithShadow(textRenderer, profileText, cx, py + 49, 0xFF222222);
    ctx.drawCenteredTextWithShadow(textRenderer, profileText, cx, py + 48, KeysetTheme.TEXT_TITLE);

    // Separator
    ctx.fill(px + 8, py + 70, px + pw - 8, py + 71, KeysetTheme.ACCENT_DIM);

    if (!missingBindings.isEmpty()) {
      int listed = Math.min(missingBindings.size(), 3);
      int warningY = py + 76;
      int warningH = 16 + listed * 11 + (missingBindings.size() > 3 ? 11 : 0) + 6;
      ctx.fill(px + 8, warningY, px + pw - 8, warningY + warningH, KeysetTheme.CHIP_ERR_BG);
      ctx.drawBorder(px + 8, warningY, pw - 16, warningH, KeysetTheme.CHIP_ERR_BR);
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.literal("⚠ " + missingBindings.size() + " keybind(s) not installed:"),
          cx,
          py + 80,
          KeysetTheme.WARNING);
      for (int i = 0; i < listed; i++) {
        String missing = textRenderer.trimToWidth(missingBindings.get(i), pw - 48);
        if (!missing.equals(missingBindings.get(i))) missing += "…";
        ctx.drawTextWithShadow(
            textRenderer,
            Text.literal("  • " + missing),
            px + 16,
            py + 94 + i * 11,
            KeysetTheme.TEXT_MUTED);
      }
      if (missingBindings.size() > 3) {
        ctx.drawTextWithShadow(
            textRenderer,
            Text.literal("  + " + (missingBindings.size() - 3) + " more"),
            px + 16,
            py + 94 + listed * 11,
            KeysetTheme.TEXT_DISABLED);
      }
    } else {
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          Text.translatable("keyset.share.compat.ok"),
          cx,
          py + 80,
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

  private int panelW() {
    return Math.max(1, Math.min(PW, width - 8));
  }

  private int panelH() {
    return Math.max(1, Math.min(PH, height - 8));
  }
}
