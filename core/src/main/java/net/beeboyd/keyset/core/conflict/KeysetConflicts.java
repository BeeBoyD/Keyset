package net.beeboyd.keyset.core.conflict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.beeboyd.keyset.core.binding.KeysetBindingDescriptor;
import net.beeboyd.keyset.core.profile.KeysetKeyStroke;

/** Conflict analysis entry point for loader-agnostic keybinding snapshots. */
public final class KeysetConflicts {
  private static final Comparator<KeysetConflict> CONFLICT_ORDER =
      new Comparator<KeysetConflict>() {
        @Override
        public int compare(KeysetConflict left, KeysetConflict right) {
          int signatureComparison = compareText(left.getKeySignature(), right.getKeySignature());
          if (signatureComparison != 0) {
            return signatureComparison;
          }

          return compareText(left.getBindings().get(0).getId(), right.getBindings().get(0).getId());
        }
      };

  private static final Comparator<CategoryGroupBuilder> CATEGORY_GROUP_ORDER =
      new Comparator<CategoryGroupBuilder>() {
        @Override
        public int compare(CategoryGroupBuilder left, CategoryGroupBuilder right) {
          int titleComparison = compareText(left.title, right.title);
          if (titleComparison != 0) {
            return titleComparison;
          }

          return compareText(left.categoryId, right.categoryId);
        }
      };

  private KeysetConflicts() {}

  /** Builds a canonical report of every currently conflicting bound key assignment. */
  public static KeysetConflictReport analyze(Collection<KeysetBindingDescriptor> bindings) {
    List<KeysetBindingDescriptor> normalizedBindings = normalizeBindings(bindings);
    if (normalizedBindings.isEmpty()) {
      return KeysetConflictReport.empty();
    }

    Map<String, List<KeysetBindingDescriptor>> conflictsByKey =
        new TreeMap<String, List<KeysetBindingDescriptor>>();
    Map<String, KeysetKeyStroke> keyStrokesBySignature =
        new LinkedHashMap<String, KeysetKeyStroke>();
    for (KeysetBindingDescriptor binding : normalizedBindings) {
      if (!binding.isBound()) {
        continue;
      }

      String keySignature = KeysetConflict.buildKeySignature(binding.getKeyStroke());
      List<KeysetBindingDescriptor> conflictBindings = conflictsByKey.get(keySignature);
      if (conflictBindings == null) {
        conflictBindings = new ArrayList<KeysetBindingDescriptor>();
        conflictsByKey.put(keySignature, conflictBindings);
        keyStrokesBySignature.put(keySignature, binding.getKeyStroke());
      }
      conflictBindings.add(binding);
    }

    List<KeysetConflict> conflicts = new ArrayList<KeysetConflict>();
    for (Map.Entry<String, List<KeysetBindingDescriptor>> entry : conflictsByKey.entrySet()) {
      if (entry.getValue().size() < 2) {
        continue;
      }

      conflicts.add(
          new KeysetConflict(
              keyStrokesBySignature.get(entry.getKey()),
              selectKeyDisplayName(entry.getValue()),
              entry.getValue()));
    }

    if (conflicts.isEmpty()) {
      return KeysetConflictReport.empty();
    }

    Collections.sort(conflicts, CONFLICT_ORDER);
    return new KeysetConflictReport(
        conflicts, buildKeyGroups(conflicts), buildCategoryGroups(conflicts));
  }

  private static List<KeysetBindingDescriptor> normalizeBindings(
      Collection<KeysetBindingDescriptor> bindings) {
    if (bindings == null || bindings.isEmpty()) {
      return Collections.emptyList();
    }

    List<KeysetBindingDescriptor> sortedBindings =
        new ArrayList<KeysetBindingDescriptor>(bindings.size());
    for (KeysetBindingDescriptor binding : bindings) {
      if (binding != null) {
        sortedBindings.add(binding);
      }
    }

    Collections.sort(sortedBindings, KeysetConflict.bindingOrder());

    Map<String, KeysetBindingDescriptor> uniqueBindings =
        new LinkedHashMap<String, KeysetBindingDescriptor>();
    for (KeysetBindingDescriptor binding : sortedBindings) {
      if (!uniqueBindings.containsKey(binding.getId())) {
        uniqueBindings.put(binding.getId(), binding);
      }
    }

    return new ArrayList<KeysetBindingDescriptor>(uniqueBindings.values());
  }

  private static String selectKeyDisplayName(List<KeysetBindingDescriptor> bindings) {
    String selected = null;
    for (KeysetBindingDescriptor binding : bindings) {
      String candidate = binding.getKeyDisplayName();
      if (selected == null || compareText(candidate, selected) < 0) {
        selected = candidate;
      }
    }
    return selected == null ? bindings.get(0).getKeyStroke().toString() : selected;
  }

  private static List<KeysetConflictGroup> buildKeyGroups(List<KeysetConflict> conflicts) {
    List<KeysetConflictGroup> groups = new ArrayList<KeysetConflictGroup>(conflicts.size());
    for (KeysetConflict conflict : conflicts) {
      groups.add(
          new KeysetConflictGroup(
              conflict.getKeySignature(),
              conflict.getKeyDisplayName(),
              KeysetConflictGroupMode.BY_KEY,
              Collections.singletonList(conflict)));
    }
    return groups;
  }

  private static List<KeysetConflictGroup> buildCategoryGroups(List<KeysetConflict> conflicts) {
    Map<String, CategoryGroupBuilder> categories =
        new LinkedHashMap<String, CategoryGroupBuilder>();
    for (KeysetConflict conflict : conflicts) {
      for (KeysetBindingDescriptor binding : conflict.getBindings()) {
        CategoryGroupBuilder category = categories.get(binding.getCategoryId());
        if (category == null) {
          category = new CategoryGroupBuilder(binding.getCategoryId(), binding.getCategoryName());
          categories.put(category.categoryId, category);
        } else if (compareText(binding.getCategoryName(), category.title) < 0) {
          category.title = binding.getCategoryName();
        }
      }

      for (String categoryId : conflict.getCategoryIds()) {
        categories.get(categoryId).conflicts.add(conflict);
      }
    }

    List<CategoryGroupBuilder> orderedCategories =
        new ArrayList<CategoryGroupBuilder>(categories.values());
    Collections.sort(orderedCategories, CATEGORY_GROUP_ORDER);

    List<KeysetConflictGroup> groups = new ArrayList<KeysetConflictGroup>(orderedCategories.size());
    for (CategoryGroupBuilder category : orderedCategories) {
      groups.add(
          new KeysetConflictGroup(
              category.categoryId,
              category.title,
              KeysetConflictGroupMode.BY_CATEGORY,
              category.conflicts));
    }
    return groups;
  }

  private static int compareText(String left, String right) {
    int insensitive = left.compareToIgnoreCase(right);
    return insensitive != 0 ? insensitive : left.compareTo(right);
  }

  private static final class CategoryGroupBuilder {
    private final String categoryId;
    private String title;
    private final List<KeysetConflict> conflicts = new ArrayList<KeysetConflict>();

    private CategoryGroupBuilder(String categoryId, String title) {
      this.categoryId = categoryId;
      this.title = title;
    }
  }
}
