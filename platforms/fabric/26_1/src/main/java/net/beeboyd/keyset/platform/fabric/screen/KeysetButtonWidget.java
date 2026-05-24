package net.beeboyd.keyset.platform.fabric.screen;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Keyset-styled button that keeps vanilla button input and narration behavior. */
public final class KeysetButtonWidget extends AbstractWidget {
  private final boolean primary;
  private final Consumer<KeysetButtonWidget> onPress;

  public static KeysetButtonWidget create(
      int x,
      int y,
      int width,
      int height,
      Component message,
      Consumer<KeysetButtonWidget> onPress) {
    return create(x, y, width, height, message, onPress, false);
  }

  public static KeysetButtonWidget primary(
      int x,
      int y,
      int width,
      int height,
      Component message,
      Consumer<KeysetButtonWidget> onPress) {
    return create(x, y, width, height, message, onPress, true);
  }

  public static KeysetButtonWidget create(
      int x,
      int y,
      int width,
      int height,
      Component message,
      Consumer<KeysetButtonWidget> onPress,
      boolean primary) {
    return new KeysetButtonWidget(x, y, width, height, message, onPress, primary);
  }

  private KeysetButtonWidget(
      int x,
      int y,
      int width,
      int height,
      Component message,
      Consumer<KeysetButtonWidget> onPress,
      boolean primary) {
    super(x, y, width, height, message);
    this.onPress = onPress;
    this.primary = primary;
  }

  @Override
  protected void extractWidgetRenderState(
      GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    Font font = net.minecraft.client.Minecraft.getInstance().font;
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
    ctx.outline(
        getX(), getY(), getWidth(), getHeight(), KeysetTheme.scaleAlpha(border, alphaScale));

    if (isFocused()) {
      ctx.outline(
          getX() - 1,
          getY() - 1,
          getWidth() + 2,
          getHeight() + 2,
          KeysetTheme.scaleAlpha(KeysetTheme.ACCENT_DIM, alphaScale));
    }

    int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
    int textX = getX() + getWidth() / 2 - font.width(getMessage()) / 2;
    if (hovered && active) {
      ctx.text(font, getMessage(), textX, textY, KeysetTheme.scaleAlpha(textColor, alpha), false);
    } else {
      ctx.centeredText(
          font,
          getMessage(),
          getX() + getWidth() / 2,
          textY,
          KeysetTheme.scaleAlpha(textColor, alpha));
    }
  }

  @Override
  public void onClick(MouseButtonEvent event, boolean doubleClick) {
    if (active && visible) {
      onPress.accept(this);
    }
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }
}
