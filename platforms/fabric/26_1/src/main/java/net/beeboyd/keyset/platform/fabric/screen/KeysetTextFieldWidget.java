package net.beeboyd.keyset.platform.fabric.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Keyset-styled text input with vanilla editing, selection, clipboard, and narration behavior. */
public final class KeysetTextFieldWidget extends EditBox {
  private static final int TEXT_PAD_X = 5;

  public KeysetTextFieldWidget(Font font, int x, int y, int width, int height, Component message) {
    super(font, x, y, width, height, message);
    setBordered(false);
    setTextColor(KeysetTheme.TEXT_TITLE);
    setTextColorUneditable(KeysetTheme.TEXT_DISABLED);
  }

  @Override
  public void extractWidgetRenderState(
      GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    if (!isVisible()) {
      return;
    }

    boolean hovered = isMouseOver(mouseX, mouseY);
    int border =
        isFocused() ? KeysetTheme.ACCENT : hovered ? KeysetTheme.BORDER_ACCENT : KeysetTheme.BORDER;
    ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), KeysetTheme.BG_SURFACE);
    ctx.outline(getX(), getY(), getWidth(), getHeight(), border);
    if (isFocused()) {
      ctx.fill(getX() + 1, getBottom() - 2, getRight() - 1, getBottom() - 1, KeysetTheme.ACCENT);
    }

    ctx.pose().pushMatrix();
    try {
      ctx.pose().translate(TEXT_PAD_X, textYOffset());
      super.extractWidgetRenderState(ctx, mouseX - TEXT_PAD_X, mouseY, delta);
    } finally {
      ctx.pose().popMatrix();
    }
  }

  @Override
  public int getInnerWidth() {
    return Math.max(0, getWidth() - TEXT_PAD_X * 2);
  }

  private int textYOffset() {
    return Math.max(0, (getHeight() - 8) / 2);
  }
}
