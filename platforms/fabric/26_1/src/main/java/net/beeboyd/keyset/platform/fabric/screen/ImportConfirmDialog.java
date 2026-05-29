package net.beeboyd.keyset.platform.fabric.screen;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
    super(Component.empty());
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
    addRenderableWidget(
        KeysetButtonWidget.create(
            px + pw - 20,
            py + 4,
            14,
            14,
            Component.literal("✕"),
            b -> {
              onDecline.run();
              onClose();
            }));
    addRenderableWidget(
        KeysetButtonWidget.primary(
            px + pw - 110,
            py + ph - 38,
            100,
            20,
            Component.translatable("keyset.share.dialog.accept"),
            b -> {
              onAccept.run();
              onClose();
            }));
    addRenderableWidget(
        KeysetButtonWidget.create(
            px + 8,
            py + ph - 38,
            100,
            20,
            Component.translatable("keyset.share.dialog.decline"),
            b -> {
              onDecline.run();
              onClose();
            }));
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    ctx.fill(0, 0, width, height, 0xCC000000);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
    extractBackground(ctx, mx, my, delta);
    int pw = panelW();
    int ph = panelH();
    int px = (width - pw) / 2;
    int py = (height - ph) / 2;
    int cx = px + pw / 2;
    // Shadow
    ctx.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x60000000);
    // Panel body
    ctx.fill(px, py, px + pw, py + ph, KeysetTheme.BG_SURFACE);
    ctx.outline(px, py, pw, ph, KeysetTheme.ACCENT);
    // Title bar
    ctx.fill(px, py, px + pw, py + 22, KeysetTheme.BG_TAB_ACTIVE);
    ctx.fill(px, py + 22, px + pw, py + 23, KeysetTheme.ACCENT_DIM);

    ctx.centeredText(
        font,
        Component.translatable("keyset.share.dialog.title"),
        cx,
        py + 7,
        KeysetTheme.TEXT_MUTED);

    String sender = (senderName == null || senderName.isEmpty()) ? "Someone" : senderName;
    sender = font.plainSubstrByWidth(sender + " shared:", pw - 20);
    ctx.centeredText(font, Component.literal(sender), cx, py + 30, KeysetTheme.TEXT_MUTED);

    // Profile name — pseudo-bold
    String profile =
        (profileName == null || profileName.isEmpty()) ? "Unknown Profile" : profileName;
    profile = font.plainSubstrByWidth(profile, pw - 20);
    Component profileText = Component.literal(profile);
    ctx.centeredText(font, profileText, cx + 1, py + 48, 0xFF222222);
    ctx.centeredText(font, profileText, cx, py + 49, 0xFF222222);
    ctx.centeredText(font, profileText, cx, py + 48, KeysetTheme.TEXT_TITLE);

    // Separator
    ctx.fill(px + 8, py + 70, px + pw - 8, py + 71, KeysetTheme.ACCENT_DIM);

    if (!missingBindings.isEmpty()) {
      int listed = Math.min(missingBindings.size(), 3);
      int warningY = py + 76;
      int warningH = 16 + listed * 11 + (missingBindings.size() > 3 ? 11 : 0) + 6;
      ctx.fill(px + 8, warningY, px + pw - 8, warningY + warningH, KeysetTheme.CHIP_ERR_BG);
      ctx.outline(px + 8, warningY, pw - 16, warningH, KeysetTheme.CHIP_ERR_BR);
      ctx.centeredText(
          font,
          Component.literal("⚠ " + missingBindings.size() + " keybind(s) not installed:"),
          cx,
          py + 80,
          KeysetTheme.WARNING);
      for (int i = 0; i < listed; i++) {
        String missing = font.plainSubstrByWidth(missingBindings.get(i), pw - 48);
        if (!missing.equals(missingBindings.get(i))) missing += "…";
        ctx.text(
            font,
            Component.literal("  • " + missing),
            px + 16,
            py + 94 + i * 11,
            KeysetTheme.TEXT_MUTED,
            true);
      }
      if (missingBindings.size() > 3) {
        ctx.text(
            font,
            Component.literal("  + " + (missingBindings.size() - 3) + " more"),
            px + 16,
            py + 94 + listed * 11,
            KeysetTheme.TEXT_DISABLED,
            true);
      }
    } else {
      ctx.centeredText(
          font, Component.translatable("keyset.share.compat.ok"), cx, py + 80, KeysetTheme.SUCCESS);
    }

    super.extractRenderState(ctx, mx, my, delta);
  }

  @Override
  public void onClose() {
    this.minecraft.setScreen(parent);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  private int panelW() {
    return Math.max(1, Math.min(PW, width - 8));
  }

  private int panelH() {
    return Math.max(1, Math.min(PH, height - 8));
  }
}
