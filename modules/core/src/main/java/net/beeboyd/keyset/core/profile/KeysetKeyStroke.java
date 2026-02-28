package net.beeboyd.keyset.core.profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Loader-agnostic snapshot of a key assignment.
 *
 * <p>The {@code keyToken} is intentionally stored as a stable string so version-specific code can
 * translate Minecraft input objects without leaking them into the shared core.
 */
public final class KeysetKeyStroke {
  private static final KeysetKeyStroke UNBOUND =
      new KeysetKeyStroke(null, Collections.<KeysetModifier>emptyList());

  private final String keyToken;
  private final List<KeysetModifier> modifiers;

  private KeysetKeyStroke(String keyToken, List<KeysetModifier> modifiers) {
    this.keyToken = keyToken;
    this.modifiers = modifiers;
  }

  /** Returns a canonical unbound key stroke. */
  public static KeysetKeyStroke unbound() {
    return UNBOUND;
  }

  /** Creates a key stroke with no modifiers. */
  public static KeysetKeyStroke of(String keyToken) {
    return of(keyToken, Collections.<KeysetModifier>emptyList());
  }

  /** Creates a normalized key stroke from a token and optional modifiers. */
  public static KeysetKeyStroke of(String keyToken, Collection<KeysetModifier> modifiers) {
    String normalizedKeyToken = normalizeKeyToken(keyToken);
    if (normalizedKeyToken == null) {
      return UNBOUND;
    }

    return new KeysetKeyStroke(
        normalizedKeyToken, Collections.unmodifiableList(normalizeModifiers(modifiers)));
  }

  public String getKeyToken() {
    return keyToken;
  }

  public List<KeysetModifier> getModifiers() {
    return modifiers;
  }

  public boolean isUnbound() {
    return keyToken == null;
  }

  private static String normalizeKeyToken(String keyToken) {
    if (keyToken == null) {
      return null;
    }

    String normalized = keyToken.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private static List<KeysetModifier> normalizeModifiers(Collection<KeysetModifier> modifiers) {
    if (modifiers == null || modifiers.isEmpty()) {
      return Collections.emptyList();
    }

    LinkedHashSet<KeysetModifier> present = new LinkedHashSet<KeysetModifier>();
    for (KeysetModifier modifier : modifiers) {
      if (modifier != null) {
        present.add(modifier);
      }
    }

    if (present.isEmpty()) {
      return Collections.emptyList();
    }

    List<KeysetModifier> normalized = new ArrayList<KeysetModifier>(3);
    appendIfPresent(normalized, present, KeysetModifier.SHIFT);
    appendIfPresent(normalized, present, KeysetModifier.CTRL);
    appendIfPresent(normalized, present, KeysetModifier.ALT);
    return normalized;
  }

  private static void appendIfPresent(
      List<KeysetModifier> normalized,
      Collection<KeysetModifier> present,
      KeysetModifier candidate) {
    if (present.contains(candidate)) {
      normalized.add(candidate);
    }
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KeysetKeyStroke)) {
      return false;
    }
    KeysetKeyStroke that = (KeysetKeyStroke) other;
    return Objects.equals(keyToken, that.keyToken) && modifiers.equals(that.modifiers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keyToken, modifiers);
  }

  @Override
  public String toString() {
    if (isUnbound()) {
      return "unbound";
    }
    if (modifiers.isEmpty()) {
      return keyToken;
    }
    return modifiers + "+" + keyToken;
  }
}
