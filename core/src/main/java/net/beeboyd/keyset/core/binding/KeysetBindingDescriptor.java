package net.beeboyd.keyset.core.binding;

import java.util.Objects;
import net.beeboyd.keyset.core.profile.KeysetKeyStroke;

/** Immutable shared-core snapshot of a currently available keybinding. */
public final class KeysetBindingDescriptor {
  private final String id;
  private final String displayName;
  private final String categoryId;
  private final String categoryName;
  private final KeysetKeyStroke keyStroke;
  private final String keyDisplayName;

  public KeysetBindingDescriptor(
      String id,
      String displayName,
      String categoryId,
      String categoryName,
      KeysetKeyStroke keyStroke,
      String keyDisplayName) {
    this.id = requireText(id, "id");
    this.displayName = fallbackText(displayName, this.id);
    this.categoryId = requireText(categoryId, "categoryId");
    this.categoryName = fallbackText(categoryName, this.categoryId);
    this.keyStroke = keyStroke == null ? KeysetKeyStroke.unbound() : keyStroke;
    this.keyDisplayName = fallbackText(keyDisplayName, defaultKeyDisplayName(this.keyStroke));
  }

  public String getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public KeysetKeyStroke getKeyStroke() {
    return keyStroke;
  }

  public String getKeyDisplayName() {
    return keyDisplayName;
  }

  public boolean isBound() {
    return !keyStroke.isUnbound();
  }

  private static String requireText(String value, String fieldName) {
    String normalized = normalizeNullableText(value);
    if (normalized == null) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return normalized;
  }

  private static String fallbackText(String value, String fallbackValue) {
    String normalized = normalizeNullableText(value);
    return normalized == null ? fallbackValue : normalized;
  }

  private static String normalizeNullableText(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private static String defaultKeyDisplayName(KeysetKeyStroke keyStroke) {
    return keyStroke.isUnbound() ? "Unbound" : keyStroke.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KeysetBindingDescriptor)) {
      return false;
    }
    KeysetBindingDescriptor that = (KeysetBindingDescriptor) other;
    return id.equals(that.id)
        && displayName.equals(that.displayName)
        && categoryId.equals(that.categoryId)
        && categoryName.equals(that.categoryName)
        && keyStroke.equals(that.keyStroke)
        && keyDisplayName.equals(that.keyDisplayName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, displayName, categoryId, categoryName, keyStroke, keyDisplayName);
  }
}
