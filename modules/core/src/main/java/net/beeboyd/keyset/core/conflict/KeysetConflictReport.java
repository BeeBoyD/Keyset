package net.beeboyd.keyset.core.conflict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.beeboyd.keyset.core.binding.KeysetBindingDescriptor;

/** Immutable canonical conflict report built from a live binding snapshot. */
public final class KeysetConflictReport {
  private static final KeysetConflictReport EMPTY =
      new KeysetConflictReport(
          Collections.<KeysetConflict>emptyList(),
          Collections.<KeysetConflictGroup>emptyList(),
          Collections.<KeysetConflictGroup>emptyList());

  private final List<KeysetConflict> conflicts;
  private final List<KeysetConflictGroup> keyGroups;
  private final List<KeysetConflictGroup> categoryGroups;
  private final int affectedBindingCount;

  KeysetConflictReport(
      Collection<KeysetConflict> conflicts,
      Collection<KeysetConflictGroup> keyGroups,
      Collection<KeysetConflictGroup> categoryGroups) {
    this.conflicts = Collections.unmodifiableList(copyConflicts(conflicts));
    this.keyGroups = Collections.unmodifiableList(copyGroups(keyGroups));
    this.categoryGroups = Collections.unmodifiableList(copyGroups(categoryGroups));
    this.affectedBindingCount = countAffectedBindings(this.conflicts);
  }

  public static KeysetConflictReport empty() {
    return EMPTY;
  }

  public List<KeysetConflict> getConflicts() {
    return conflicts;
  }

  public List<KeysetConflictGroup> getGroups(KeysetConflictGroupMode groupMode) {
    if (groupMode == null) {
      throw new IllegalArgumentException("groupMode must not be null");
    }

    return groupMode == KeysetConflictGroupMode.BY_CATEGORY ? categoryGroups : keyGroups;
  }

  public List<KeysetConflictGroup> query(KeysetConflictQuery query) {
    KeysetConflictQuery normalizedQuery = query == null ? KeysetConflictQuery.byKey("") : query;
    List<KeysetConflictGroup> groups = getGroups(normalizedQuery.getGroupMode());
    if (normalizedQuery.isUnfiltered()) {
      return groups;
    }

    List<KeysetConflictGroup> filtered = new ArrayList<KeysetConflictGroup>();
    for (KeysetConflictGroup group : groups) {
      List<KeysetConflict> matches = new ArrayList<KeysetConflict>();
      for (KeysetConflict conflict : group.getConflicts()) {
        if (matchesQuery(group, conflict, normalizedQuery)) {
          matches.add(conflict);
        }
      }

      if (!matches.isEmpty()) {
        filtered.add(
            new KeysetConflictGroup(
                group.getGroupId(), group.getTitle(), group.getGroupMode(), matches));
      }
    }

    return Collections.unmodifiableList(filtered);
  }

  public boolean isEmpty() {
    return conflicts.isEmpty();
  }

  public int getConflictCount() {
    return conflicts.size();
  }

  public int getAffectedBindingCount() {
    return affectedBindingCount;
  }

  private static boolean matchesQuery(
      KeysetConflictGroup group, KeysetConflict conflict, KeysetConflictQuery query) {
    if (group.getGroupMode() == KeysetConflictGroupMode.BY_CATEGORY) {
      return conflict.matchesCategory(group.getGroupId(), query.getSearchTerms());
    }
    return conflict.matches(query.getSearchTerms());
  }

  private static List<KeysetConflict> copyConflicts(Collection<KeysetConflict> conflicts) {
    if (conflicts == null || conflicts.isEmpty()) {
      return Collections.emptyList();
    }

    List<KeysetConflict> copy = new ArrayList<KeysetConflict>(conflicts.size());
    for (KeysetConflict conflict : conflicts) {
      if (conflict != null) {
        copy.add(conflict);
      }
    }
    return copy;
  }

  private static List<KeysetConflictGroup> copyGroups(Collection<KeysetConflictGroup> groups) {
    if (groups == null || groups.isEmpty()) {
      return Collections.emptyList();
    }

    List<KeysetConflictGroup> copy = new ArrayList<KeysetConflictGroup>(groups.size());
    for (KeysetConflictGroup group : groups) {
      if (group != null) {
        copy.add(group);
      }
    }
    return copy;
  }

  private static int countAffectedBindings(List<KeysetConflict> conflicts) {
    Set<String> bindingIds = new HashSet<String>();
    for (KeysetConflict conflict : conflicts) {
      for (KeysetBindingDescriptor binding : conflict.getBindings()) {
        bindingIds.add(binding.getId());
      }
    }
    return bindingIds.size();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KeysetConflictReport)) {
      return false;
    }
    KeysetConflictReport that = (KeysetConflictReport) other;
    return affectedBindingCount == that.affectedBindingCount
        && conflicts.equals(that.conflicts)
        && keyGroups.equals(that.keyGroups)
        && categoryGroups.equals(that.categoryGroups);
  }

  @Override
  public int hashCode() {
    return Objects.hash(conflicts, keyGroups, categoryGroups, affectedBindingCount);
  }
}
