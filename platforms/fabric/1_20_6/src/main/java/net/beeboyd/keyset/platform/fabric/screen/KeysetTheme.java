package net.beeboyd.keyset.platform.fabric.screen;

import net.minecraft.util.math.MathHelper;

public final class KeysetTheme {
  private KeysetTheme() {}

  // Backgrounds — Modrinth-app neutral gray-blue palette
  public static final int BG_BACKDROP = 0xF015181C;
  public static final int BG_SURFACE = 0xFF1E2125;
  public static final int BG_SIDEBAR = 0xFF191C20;
  public static final int BG_TAB_ACTIVE = 0xFF22262B;
  public static final int BG_HOVER = 0xFF252A30;

  // Borders
  public static final int BORDER = 0xFF2A2E33;
  public static final int BORDER_ACCENT = 0xFFFFB347;

  // Accent — BeeBoyD amber
  public static final int ACCENT = 0xFFFFB347;
  public static final int ACCENT_DIM = 0xFF996B2A;
  public static final int ACCENT_GLOW = 0x28FFB347;

  // Text
  public static final int TEXT_TITLE = 0xFFE8EAED;
  public static final int TEXT_BODY = 0xFFA8ACB1;
  public static final int TEXT_MUTED = 0xFF6C7178;
  public static final int TEXT_DISABLED = 0xFF4A4E54;

  // Status chips
  public static final int CHIP_BG = 0xFF2A2E33;
  public static final int CHIP_ERR_BG = 0xFF3A1818;
  public static final int CHIP_ERR_BR = 0xFF6A2C2C;
  public static final int CHIP_OK_BG = 0xFF182A1C;
  public static final int CHIP_OK_BR = 0xFF2C5A38;

  public static final int SUCCESS = 0xFF6EE7A0;
  public static final int ERROR = 0xFFFFA090;
  public static final int WARNING = 0xFFFFD878;

  // Spacing
  public static final int PAD = 12;
  public static final int GAP = 8;
  public static final int GAP_SM = 4;
  public static final int CARD_PAD = 12;

  // Heights
  public static final int TOPBAR_H = 36;
  public static final int TAB_H = 28;
  public static final int FOOTER_H = 28;
  public static final int ROW_H = 24;
  public static final int SIDEBAR_W = 160;

  // Animation
  public static final float OPEN_MS = 220f;

  public static int withAlpha(int rgb, float a) {
    int alpha = MathHelper.clamp((int) (a * 255f), 0, 255);
    return (rgb & 0x00FFFFFF) | (alpha << 24);
  }

  public static int scaleAlpha(int argb, float scale) {
    int a = (int) (((argb >>> 24) & 0xFF) * scale);
    return (argb & 0x00FFFFFF) | (MathHelper.clamp(a, 0, 255) << 24);
  }

  public static float easeOutQuart(float t) {
    float r = 1f - t;
    return 1f - r * r * r * r;
  }

  public static float expLerp(float cur, float tgt, float dtMs, float k) {
    return cur + (tgt - cur) * (1f - (float) Math.exp(-k * dtMs / 1000f));
  }
}
