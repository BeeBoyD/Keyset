package net.beeboyd.keyset.platform.fabric;

import net.minecraft.text.Text;

/** Text factory compatibility for 1.19.2, where static Text helpers are available. */
public final class KeysetTextCompat {
  private KeysetTextCompat() {}

  public static Text empty() {
    return Text.empty();
  }

  public static Text literal(String value) {
    return Text.literal(value == null ? "" : value);
  }

  public static Text translatable(String key, Object... args) {
    return Text.translatable(key, args);
  }
}
