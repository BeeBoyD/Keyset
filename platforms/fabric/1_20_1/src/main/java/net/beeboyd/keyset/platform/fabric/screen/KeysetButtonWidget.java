package net.beeboyd.keyset.platform.fabric.screen;

import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Keyset-styled button that keeps vanilla button input and narration behavior. */
public final class KeysetButtonWidget extends ButtonWidget {
  private final boolean primary;

  public static KeysetButtonWidget create(
      int x, int y, int width, int height, Text message, Consumer<KeysetButtonWidget> onPress) {
    return create(x, y, width, height, message, onPress, false);
  }

  public static KeysetButtonWidget primary(
      int x, int y, int width, int height, Text message, Consumer<KeysetButtonWidget> onPress) {
    return create(x, y, width, height, message, onPress, true);
  }

  public static KeysetButtonWidget create(
      int x,
      int y,
      int width,
      int height,
      Text message,
      Consumer<KeysetButtonWidget> onPress,
      boolean primary) {
    return new KeysetButtonWidget(x, y, width, height, message, onPress, primary);
  }

  private KeysetButtonWidget(
      int x,
      int y,
      int width,
      int height,
      Text message,
      Consumer<KeysetButtonWidget> onPress,
      boolean primary) {
    super(
        x,
        y,
        width,
        height,
        message,
        button -> onPress.accept((KeysetButtonWidget) button),
        DEFAULT_NARRATION_SUPPLIER);
    this.primary = primary;
  }

  @Override
  protected void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
    TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    boolean hovered = isHovered();
    float alphaScale = active ? alpha : alpha * 0.55f;
    int bg =
        !active
            ? KeysetTheme.BG_SURFACE
            : hovered
                ? KeysetTheme.ACCENT
                : primary ? KeysetTheme.BG_TAB_ACTIVE : KeysetTheme.BG_SURFACE;
    int border =
        !active ? KeysetTheme.BORDER : hovered || primary ? KeysetTheme.ACCENT : KeysetTheme.BORDER;
    int textColor =
        !active
            ? KeysetTheme.TEXT_DISABLED
            : hovered ? 0xFF1A0800 : primary ? KeysetTheme.TEXT_TITLE : KeysetTheme.TEXT_BODY;

    ctx.fill(
        getX(),
        getY(),
        getX() + getWidth(),
        getY() + getHeight(),
        KeysetTheme.scaleAlpha(bg, alphaScale));
    ctx.drawBorder(
        getX(), getY(), getWidth(), getHeight(), KeysetTheme.scaleAlpha(border, alphaScale));

    if (isFocused()) {
      ctx.drawBorder(
          getX() - 1,
          getY() - 1,
          getWidth() + 2,
          getHeight() + 2,
          KeysetTheme.scaleAlpha(KeysetTheme.ACCENT_DIM, alphaScale));
    }

    int textY = getY() + (getHeight() - textRenderer.fontHeight) / 2 + 1;
    int textX = getX() + getWidth() / 2 - textRenderer.getWidth(getMessage()) / 2;
    if (hovered && active) {
      ctx.drawText(
          textRenderer,
          getMessage(),
          textX,
          textY,
          KeysetTheme.scaleAlpha(textColor, alpha),
          false);
    } else {
      ctx.drawCenteredTextWithShadow(
          textRenderer,
          getMessage(),
          getX() + getWidth() / 2,
          textY,
          KeysetTheme.scaleAlpha(textColor, alpha));
    }
  }
}
