package net.beeboyd.keyset.platform.fabric.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Keyset-styled text input with vanilla editing, selection, clipboard, and narration behavior. */
public final class KeysetTextFieldWidget extends TextFieldWidget {
  private static final int TEXT_PAD_X = 5;

  public KeysetTextFieldWidget(
      TextRenderer textRenderer, int x, int y, int width, int height, Text message) {
    super(textRenderer, x, y, width, height, message);
    setDrawsBackground(false);
    setEditableColor(KeysetTheme.TEXT_TITLE);
    setUneditableColor(KeysetTheme.TEXT_DISABLED);
  }

  @Override
  public void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
    if (!isVisible()) {
      return;
    }

    boolean hovered = isMouseOver(mouseX, mouseY);
    int border =
        isFocused() ? KeysetTheme.ACCENT : hovered ? KeysetTheme.BORDER_ACCENT : KeysetTheme.BORDER;
    ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), KeysetTheme.BG_SURFACE);
    ctx.drawBorder(getX(), getY(), getWidth(), getHeight(), border);
    if (isFocused()) {
      ctx.fill(getX() + 1, getBottom() - 2, getRight() - 1, getBottom() - 1, KeysetTheme.ACCENT);
    }

    ctx.getMatrices().push();
    try {
      ctx.getMatrices().translate(TEXT_PAD_X, textYOffset(), 0);
      super.renderButton(ctx, mouseX - TEXT_PAD_X, mouseY, delta);
    } finally {
      ctx.getMatrices().pop();
    }
  }

  @Override
  public void onClick(double mouseX, double mouseY) {
    super.onClick(mouseX - TEXT_PAD_X, mouseY);
  }

  @Override
  public int getInnerWidth() {
    return Math.max(0, getWidth() - TEXT_PAD_X * 2);
  }

  public int getRight() {
    return getX() + getWidth();
  }

  public int getBottom() {
    return getY() + getHeight();
  }

  private int textYOffset() {
    return Math.max(0, (getHeight() - 8) / 2);
  }
}
