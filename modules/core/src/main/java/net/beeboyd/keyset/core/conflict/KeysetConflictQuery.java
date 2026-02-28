package net.beeboyd.keyset.core.conflict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable query used to regroup or search a cached conflict report. */
public final class KeysetConflictQuery {
  private final KeysetConflictGroupMode groupMode;
  private final String searchText;
  private final List<String> searchTerms;

  public KeysetConflictQuery(KeysetConflictGroupMode groupMode, String searchText) {
    if (groupMode == null) {
      throw new IllegalArgumentException("groupMode must not be null");
    }

    this.groupMode = groupMode;
    this.searchText = normalizeSearchText(searchText);
    this.searchTerms = Collections.unmodifiableList(tokenize(this.searchText));
  }

  public static KeysetConflictQuery byKey(String searchText) {
    return new KeysetConflictQuery(KeysetConflictGroupMode.BY_KEY, searchText);
  }

  public static KeysetConflictQuery byCategory(String searchText) {
    return new KeysetConflictQuery(KeysetConflictGroupMode.BY_CATEGORY, searchText);
  }

  public KeysetConflictGroupMode getGroupMode() {
    return groupMode;
  }

  public String getSearchText() {
    return searchText;
  }

  boolean isUnfiltered() {
    return searchTerms.isEmpty();
  }

  List<String> getSearchTerms() {
    return searchTerms;
  }

  private static String normalizeSearchText(String searchText) {
    if (searchText == null) {
      return "";
    }

    String normalized = searchText.trim().toLowerCase(Locale.ROOT);
    return normalized.isEmpty() ? "" : normalized;
  }

  private static List<String> tokenize(String normalizedSearchText) {
    if (normalizedSearchText.isEmpty()) {
      return Collections.emptyList();
    }

    String[] parts = normalizedSearchText.split("\\s+");
    List<String> tokens = new ArrayList<String>(parts.length);
    for (String part : parts) {
      if (!part.isEmpty()) {
        tokens.add(part);
      }
    }
    return tokens;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KeysetConflictQuery)) {
      return false;
    }
    KeysetConflictQuery that = (KeysetConflictQuery) other;
    return groupMode == that.groupMode
        && searchText.equals(that.searchText)
        && searchTerms.equals(that.searchTerms);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupMode, searchText, searchTerms);
  }
}
