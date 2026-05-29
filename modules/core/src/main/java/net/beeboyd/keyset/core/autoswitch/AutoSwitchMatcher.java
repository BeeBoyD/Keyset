package net.beeboyd.keyset.core.autoswitch;

/** Glob pattern matcher for server address auto-switch rules. */
public final class AutoSwitchMatcher {
  private AutoSwitchMatcher() {}

  /**
   * Returns true if {@code input} matches the glob {@code pattern}. Supports {@code *} (any
   * sequence) and {@code ?} (single char). Comparison is case-insensitive.
   */
  public static boolean matchesGlob(String pattern, String input) {
    if (pattern == null || input == null) return false;
    String lowerPattern = pattern.trim().toLowerCase(java.util.Locale.ROOT);
    String lowerInput = input.trim().toLowerCase(java.util.Locale.ROOT);
    String regex =
        "\\Q"
            + lowerPattern
                .replace("\\E", "\\E\\\\E\\Q")
                .replace("*", "\\E.*\\Q")
                .replace("?", "\\E.\\Q")
            + "\\E";
    return lowerInput.matches(regex);
  }
}
