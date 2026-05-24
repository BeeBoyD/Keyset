package net.beeboyd.keyset.platform.fabric.screen;

import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Keyset-styled button that keeps vanilla button input and narration behavior. */
public final class KeysetButtonWidget extends ClickableWidget {
  private final boolean primary;
  private final Consumer<KeysetButtonWidget> onPress;

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
    super(x, y, width, height, message);
    this.onPress = onPress;
    this.primary = primary;
  }

  @Override
  protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
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
    ctx.drawStrokedRectangle(
        getX(), getY(), getWidth(), getHeight(), KeysetTheme.scaleAlpha(border, alphaScale));

    if (isFocused()) {
      ctx.drawStrokedRectangle(
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

  @Override
  public void onClick(Click click, boolean doubleClick) {
    if (active && visible) {
      onPress.accept(this);
    }
  }

  @Override
  public boolean keyPressed(KeyInput keyInput) {
    if (!active || !visible) {
      return false;
    }
    int keyCode = keyInput.key();
    if (keyCode == GLFW.GLFW_KEY_ENTER
        || keyCode == GLFW.GLFW_KEY_KP_ENTER
        || keyCode == GLFW.GLFW_KEY_SPACE) {
      playDownSound(MinecraftClient.getInstance().getSoundManager());
      onPress.accept(this);
      return true;
    }
    return false;
  }

  @Override
  protected void appendClickableNarrations(NarrationMessageBuilder builder) {
    appendDefaultNarrations(builder);
  }
}
