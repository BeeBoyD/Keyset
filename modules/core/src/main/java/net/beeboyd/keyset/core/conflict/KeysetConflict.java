package net.beeboyd.keyset.core.conflict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.beeboyd.keyset.core.binding.KeysetBindingDescriptor;
import net.beeboyd.keyset.core.profile.KeysetKeyStroke;
import net.beeboyd.keyset.core.profile.KeysetModifier;

/** Immutable set of bindings that currently share the same assigned key stroke. */
public final class KeysetConflict {
  private static final Comparator<KeysetBindingDescriptor> BINDING_ORDER =
      new Comparator<KeysetBindingDescriptor>() {
        @Override
        public int compare(KeysetBindingDescriptor left, KeysetBindingDescriptor right) {
          int categoryNameComparison = compareText(left.getCategoryName(), right.getCategoryName());
          if (categoryNameComparison != 0) {
            return categoryNameComparison;
          }

          int categoryIdComparison = compareText(left.getCategoryId(), right.getCategoryId());
          if (categoryIdComparison != 0) {
            return categoryIdComparison;
          }

          int displayNameComparison = compareText(left.getDisplayName(), right.getDisplayName());
          if (displayNameComparison != 0) {
            return displayNameComparison;
          }

          return compareText(left.getId(), right.getId());
        }
      };

  private final String keySignature;
  private final KeysetKeyStroke keyStroke;
  private final String keyDisplayName;
  private final List<KeysetBindingDescriptor> bindings;
  private final List<String> categoryIds;
  private final boolean crossCategoryConflict;
  private final String searchableText;
  private final Map<String, String> categorySearchTexts;

  public KeysetConflict(
      KeysetKeyStroke keyStroke,
      String keyDisplayName,
      Collection<KeysetBindingDescriptor> bindings) {
    if (keyStroke == null || keyStroke.isUnbound()) {
      throw new IllegalArgumentException("conflicts require a bound keyStroke");
    }

    this.keyStroke = keyStroke;
    this.bindings = Collections.unmodifiableList(copyBindings(bindings, keyStroke));
    if (this.bindings.size() < 2) {
      throw new IllegalArgumentException("conflicts require at least two bindings");
    }

    this.keySignature = buildKeySignature(keyStroke);
    this.keyDisplayName = selectKeyDisplayName(keyDisplayName, this.bindings, keyStroke);
    this.categoryIds = Collections.unmodifiableList(extractCategoryIds(this.bindings));
    this.crossCategoryConflict = categoryIds.size() > 1;
    this.searchableText =
        buildSearchableText(this.keySignature, this.keyDisplayName, this.bindings);
    this.categorySearchTexts =
        Collections.unmodifiableMap(
            buildCategorySearchTexts(this.keySignature, this.keyDisplayName, this.bindings));
  }

  public String getKeySignature() {
    return keySignature;
  }

  public KeysetKeyStroke getKeyStroke() {
    return keyStroke;
  }

  public String getKeyDisplayName() {
    return keyDisplayName;
  }

  public List<KeysetBindingDescriptor> getBindings() {
    return bindings;
  }

  public List<String> getCategoryIds() {
    return categoryIds;
  }

  public boolean isCrossCategoryConflict() {
    return crossCategoryConflict;
  }

  public boolean involvesCategory(String categoryId) {
    return categorySearchTexts.containsKey(categoryId);
  }

  boolean matches(List<String> searchTerms) {
    return matchesSearchTerms(searchableText, searchTerms);
  }

  boolean matchesCategory(String categoryId, List<String> searchTerms) {
    String categorySearchText = categorySearchTexts.get(categoryId);
    return categorySearchText != null && matchesSearchTerms(categorySearchText, searchTerms);
  }

  static String buildKeySignature(KeysetKeyStroke keyStroke) {
    StringBuilder signature = new StringBuilder();
    for (KeysetModifier modifier : keyStroke.getModifiers()) {
      if (signature.length() > 0) {
        signature.append('+');
      }
      signature.append(modifier.name().toLowerCase(Locale.ROOT));
    }

    if (signature.length() > 0) {
      signature.append('+');
    }
    signature.append(keyStroke.getKeyToken());
    return signature.toString();
  }

  static Comparator<KeysetBindingDescriptor> bindingOrder() {
    return BINDING_ORDER;
  }

  private static List<KeysetBindingDescriptor> copyBindings(
      Collection<KeysetBindingDescriptor> bindings, KeysetKeyStroke keyStroke) {
    if (bindings == null) {
      throw new IllegalArgumentException("bindings must not be null");
    }

    List<KeysetBindingDescriptor> copy = new ArrayList<KeysetBindingDescriptor>(bindings.size());
    for (KeysetBindingDescriptor binding : bindings) {
      if (binding == null) {
        continue;
      }
      if (!keyStroke.equals(binding.getKeyStroke())) {
        throw new IllegalArgumentException(
            "binding " + binding.getId() + " does not match conflict key");
      }
      copy.add(binding);
    }

    Collections.sort(copy, BINDING_ORDER);
    return copy;
  }

  private static String selectKeyDisplayName(
      String requestedKeyDisplayName,
      List<KeysetBindingDescriptor> bindings,
      KeysetKeyStroke keyStroke) {
    String normalized = normalizeNullableText(requestedKeyDisplayName);
    if (normalized != null) {
      return normalized;
    }

    String selected = null;
    for (KeysetBindingDescriptor binding : bindings) {
      String candidate = normalizeNullableText(binding.getKeyDisplayName());
      if (candidate == null) {
        continue;
      }

      if (selected == null || compareText(candidate, selected) < 0) {
        selected = candidate;
      }
    }

    return selected == null ? keyStroke.toString() : selected;
  }

  private static List<String> extractCategoryIds(List<KeysetBindingDescriptor> bindings) {
    LinkedHashSet<String> categories = new LinkedHashSet<String>();
    for (KeysetBindingDescriptor binding : bindings) {
      categories.add(binding.getCategoryId());
    }
    return new ArrayList<String>(categories);
  }

  private static String buildSearchableText(
      String keySignature, String keyDisplayName, List<KeysetBindingDescriptor> bindings) {
    StringBuilder search = new StringBuilder();
    appendSearchText(search, keySignature);
    appendSearchText(search, keyDisplayName);
    for (KeysetBindingDescriptor binding : bindings) {
      appendBindingSearchText(search, binding);
    }
    return search.toString();
  }

  private static Map<String, String> buildCategorySearchTexts(
      String keySignature, String keyDisplayName, List<KeysetBindingDescriptor> bindings) {
    Map<String, StringBuilder> builders = new LinkedHashMap<String, StringBuilder>();
    for (KeysetBindingDescriptor binding : bindings) {
      String categoryId = binding.getCategoryId();
      StringBuilder builder = builders.get(categoryId);
      if (builder == null) {
        builder = new StringBuilder();
        appendSearchText(builder, keySignature);
        appendSearchText(builder, keyDisplayName);
        appendSearchText(builder, binding.getCategoryId());
        appendSearchText(builder, binding.getCategoryName());
        builders.put(categoryId, builder);
      }
      appendBindingSearchText(builder, binding);
    }

    Map<String, String> searchTexts = new LinkedHashMap<String, String>();
    for (Map.Entry<String, StringBuilder> entry : builders.entrySet()) {
      searchTexts.put(entry.getKey(), entry.getValue().toString());
    }
    return searchTexts;
  }

  private static void appendBindingSearchText(
      StringBuilder search, KeysetBindingDescriptor binding) {
    appendSearchText(search, binding.getId());
    appendSearchText(search, binding.getDisplayName());
    appendSearchText(search, binding.getCategoryId());
    appendSearchText(search, binding.getCategoryName());
    appendSearchText(search, binding.getKeyDisplayName());
    appendSearchText(search, binding.getKeyStroke().getKeyToken());
    for (KeysetModifier modifier : binding.getKeyStroke().getModifiers()) {
      appendSearchText(search, modifier.name());
    }
  }

  private static void appendSearchText(StringBuilder search, String value) {
    String normalized = normalizeNullableText(value);
    if (normalized == null) {
      return;
    }

    if (search.length() > 0) {
      search.append(' ');
    }
    search.append(normalized.toLowerCase(Locale.ROOT));
  }

  private static boolean matchesSearchTerms(String searchableText, List<String> searchTerms) {
    if (searchTerms == null || searchTerms.isEmpty()) {
      return true;
    }

    for (String term : searchTerms) {
      if (!searchableText.contains(term)) {
        return false;
      }
    }
    return true;
  }

  private static String normalizeNullableText(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private static int compareText(String left, String right) {
    int insensitive = left.compareToIgnoreCase(right);
    return insensitive != 0 ? insensitive : left.compareTo(right);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KeysetConflict)) {
      return false;
    }
    KeysetConflict that = (KeysetConflict) other;
    return crossCategoryConflict == that.crossCategoryConflict
        && keySignature.equals(that.keySignature)
        && keyStroke.equals(that.keyStroke)
        && keyDisplayName.equals(that.keyDisplayName)
        && bindings.equals(that.bindings)
        && categoryIds.equals(that.categoryIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        keySignature, keyStroke, keyDisplayName, bindings, categoryIds, crossCategoryConflict);
  }
}
