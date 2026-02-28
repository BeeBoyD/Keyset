package net.beeboyd.keyset.core.conflict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable presentation group for a conflict report. */
public final class KeysetConflictGroup {
  private final String groupId;
  private final String title;
  private final KeysetConflictGroupMode groupMode;
  private final List<KeysetConflict> conflicts;

  public KeysetConflictGroup(
      String groupId,
      String title,
      KeysetConflictGroupMode groupMode,
      Collection<KeysetConflict> conflicts) {
    this.groupId = requireText(groupId, "groupId");
    this.title = requireText(title, "title");
    if (groupMode == null) {
      throw new IllegalArgumentException("groupMode must not be null");
    }
    this.groupMode = groupMode;
    this.conflicts = Collections.unmodifiableList(copyConflicts(conflicts));
    if (this.conflicts.isEmpty()) {
      throw new IllegalArgumentException("conflicts must not be empty");
    }
  }

  public String getGroupId() {
    return groupId;
  }

  public String getTitle() {
    return title;
  }

  public KeysetConflictGroupMode getGroupMode() {
    return groupMode;
  }

  public List<KeysetConflict> getConflicts() {
    return conflicts;
  }

  public int getConflictCount() {
    return conflicts.size();
  }

  private static String requireText(String value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " must not be null");
    }

    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }

    return normalized;
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

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KeysetConflictGroup)) {
      return false;
    }
    KeysetConflictGroup that = (KeysetConflictGroup) other;
    return groupId.equals(that.groupId)
        && title.equals(that.title)
        && groupMode == that.groupMode
        && conflicts.equals(that.conflicts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, title, groupMode, conflicts);
  }
}
