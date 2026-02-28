package net.beeboyd.keyset.platform.fabric;

import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

/** Text factory compatibility for Fabric versions before the static Text helpers existed. */
public final class KeysetTextCompat {
  private KeysetTextCompat() {}

  public static Text empty() {
    return new LiteralText("");
  }

  public static Text literal(String value) {
    return new LiteralText(value == null ? "" : value);
  }

  public static Text translatable(String key, Object... args) {
    return new TranslatableText(key, args);
  }
}
