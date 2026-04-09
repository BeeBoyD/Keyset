package net.beeboyd.keyset.shim.client;

import java.util.List;

/** Shared button placement search for the controls screen. */
public final class KeysetControlsButtonPlacement {
  private KeysetControlsButtonPlacement() {}

  public interface BoundsView<T> {
    int getX(T widget);

    int getY(T widget);

    int getWidth(T widget);

    int getHeight(T widget);
  }

  public static <T> int[] findPlacement(
      List<? extends T> widgets,
      int screenWidth,
      int screenHeight,
      int buttonWidth,
      int buttonHeight,
      int buttonMargin,
      BoundsView<? super T> boundsView) {
    int centerX = (screenWidth - buttonWidth) / 2;
    int bottomY = Math.max(32, screenHeight - buttonHeight - 32);
    int[][] candidates = {
      {centerX, bottomY},
      {screenWidth - buttonWidth - buttonMargin, bottomY},
      {buttonMargin, bottomY},
      {screenWidth - buttonWidth - buttonMargin, buttonMargin},
      {buttonMargin, buttonMargin},
      {centerX, 32}
    };

    for (int[] candidate : candidates) {
      if (!overlapsExisting(
          widgets, candidate[0], candidate[1], buttonWidth, buttonHeight, boundsView)) {
        return candidate;
      }
    }

    return new int[] {screenWidth - buttonWidth - buttonMargin, Math.max(buttonMargin, bottomY)};
  }

  private static <T> boolean overlapsExisting(
      List<? extends T> widgets,
      int x,
      int y,
      int width,
      int height,
      BoundsView<? super T> boundsView) {
    for (T widget : widgets) {
      if (rectanglesOverlap(
          x,
          y,
          width,
          height,
          boundsView.getX(widget),
          boundsView.getY(widget),
          boundsView.getWidth(widget),
          boundsView.getHeight(widget))) {
        return true;
      }
    }
    return false;
  }

  private static boolean rectanglesOverlap(
      int x,
      int y,
      int width,
      int height,
      int otherX,
      int otherY,
      int otherWidth,
      int otherHeight) {
    return x < otherX + otherWidth
        && x + width > otherX
        && y < otherY + otherHeight
        && y + height > otherY;
  }
}
