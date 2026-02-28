package net.beeboyd.keyset.core.profile;

import java.util.Objects;

/**
 * Stored profile entry for a single keybind.
 *
 * <p>{@code sticky} marks assignments the user explicitly customized in a profile so later
 * auto-resolve logic can respect those choices by default.
 */
public final class KeysetBindingSnapshot {
  private final KeysetKeyStroke keyStroke;
  private final boolean sticky;

  public KeysetBindingSnapshot(KeysetKeyStroke keyStroke, boolean sticky) {
    this.keyStroke = keyStroke == null ? KeysetKeyStroke.unbound() : keyStroke;
    this.sticky = sticky;
  }

  public static KeysetBindingSnapshot of(KeysetKeyStroke keyStroke) {
    return new KeysetBindingSnapshot(keyStroke, false);
  }

  public static KeysetBindingSnapshot sticky(KeysetKeyStroke keyStroke) {
    return new KeysetBindingSnapshot(keyStroke, true);
  }

  public KeysetKeyStroke getKeyStroke() {
    return keyStroke;
  }

  public boolean isSticky() {
    return sticky;
  }

  public KeysetBindingSnapshot withSticky(boolean updatedSticky) {
    return new KeysetBindingSnapshot(keyStroke, updatedSticky);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KeysetBindingSnapshot)) {
      return false;
    }
    KeysetBindingSnapshot that = (KeysetBindingSnapshot) other;
    return sticky == that.sticky && keyStroke.equals(that.keyStroke);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keyStroke, sticky);
  }
}
