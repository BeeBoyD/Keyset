package net.beeboyd.keyset.core.conflict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.beeboyd.keyset.core.binding.KeysetBindingDescriptor;
import net.beeboyd.keyset.core.profile.KeysetKeyStroke;
import net.beeboyd.keyset.core.profile.KeysetModifier;
import org.junit.jupiter.api.Test;

class KeysetConflictsTest {
  @Test
  void analyzeDetectsConflictsAndIgnoresUniqueOrUnboundBindings() {
    KeysetConflictReport report =
        KeysetConflicts.analyze(
            Arrays.asList(
                binding("key.jump", "Jump", "movement", "Movement", "key.keyboard.space", "Space"),
                binding(
                    "key.sprint", "Sprint", "movement", "Movement", "key.keyboard.space", "Space"),
                binding("key.drop", "Drop", "inventory", "Inventory", "key.keyboard.q", "Q"),
                binding(
                    "mod.unbound", "Unbound", "misc", "Misc", KeysetKeyStroke.unbound(), null)));

    assertEquals(1, report.getConflictCount());
    assertEquals(2, report.getAffectedBindingCount());
    assertEquals(1, report.getGroups(KeysetConflictGroupMode.BY_KEY).size());

    KeysetConflict conflict = report.getConflicts().get(0);
    assertEquals("key.keyboard.space", conflict.getKeySignature());
    assertFalse(conflict.isCrossCategoryConflict());
    assertEquals(Arrays.asList("movement"), conflict.getCategoryIds());
  }

  @Test
  void analyzeBuildsCategoryGroupsForCrossCategoryConflicts() {
    KeysetConflictReport report =
        KeysetConflicts.analyze(
            Arrays.asList(
                binding("key.jump", "Jump", "movement", "Movement", "key.keyboard.space", "Space"),
                binding(
                    "mod.bag",
                    "Open Backpack",
                    "storage",
                    "Storage",
                    "key.keyboard.space",
                    "Space"),
                binding("mod.map", "Map", "utility", "Utility", "key.keyboard.m", "M"),
                binding("mod.mail", "Mail", "utility", "Utility", "key.keyboard.m", "M")));

    List<KeysetConflictGroup> groups = report.getGroups(KeysetConflictGroupMode.BY_CATEGORY);

    assertEquals(3, groups.size());
    assertEquals("Movement", groups.get(0).getTitle());
    assertEquals("Storage", groups.get(1).getTitle());
    assertEquals("Utility", groups.get(2).getTitle());
    assertEquals(1, groups.get(0).getConflictCount());
    assertTrue(groups.get(0).getConflicts().get(0).isCrossCategoryConflict());
    assertEquals("movement", report.getConflicts().get(1).getCategoryIds().get(0));
  }

  @Test
  void queryFiltersByKeyBindingAndCategoryText() {
    KeysetConflictReport report =
        KeysetConflicts.analyze(
            Arrays.asList(
                binding("key.jump", "Jump", "movement", "Movement", "key.keyboard.space", "Space"),
                binding(
                    "mod.bag",
                    "Open Backpack",
                    "storage",
                    "Storage",
                    "key.keyboard.space",
                    "Space"),
                binding("mod.mail", "Check Mail", "utility", "Utility", "key.keyboard.m", "M"),
                binding("mod.map", "Map", "utility", "Utility", "key.keyboard.m", "M")));

    List<KeysetConflictGroup> keyGroups = report.query(KeysetConflictQuery.byKey("space jump"));
    List<KeysetConflictGroup> storageGroups =
        report.query(KeysetConflictQuery.byCategory("storage backpack"));
    List<KeysetConflictGroup> utilityGroups =
        report.query(KeysetConflictQuery.byCategory("utility"));

    assertEquals(1, keyGroups.size());
    assertEquals("Space", keyGroups.get(0).getTitle());

    assertEquals(1, storageGroups.size());
    assertEquals("Storage", storageGroups.get(0).getTitle());

    assertEquals(1, utilityGroups.size());
    assertEquals("Utility", utilityGroups.get(0).getTitle());
    assertEquals("key.keyboard.m", utilityGroups.get(0).getConflicts().get(0).getKeySignature());
  }

  @Test
  void analyzeRemainsDeterministicAcrossInputOrderAndDuplicateIds() {
    List<KeysetBindingDescriptor> firstOrdering =
        Arrays.asList(
            binding("key.jump", "Jump", "movement", "Movement", "key.keyboard.space", "Space"),
            binding("key.sprint", "Sprint", "movement", "Movement", "key.keyboard.space", "Space"),
            binding("mod.map", "Map", "utility", "Utility", "key.keyboard.m", "M"),
            binding("mod.mail", "Mail", "utility", "Utility", "key.keyboard.m", "M"));
    List<KeysetBindingDescriptor> secondOrdering =
        Arrays.asList(
            binding("mod.mail", "Mail", "utility", "Utility", "key.keyboard.m", "M"),
            binding("mod.map", "Map", "utility", "Utility", "key.keyboard.m", "M"),
            binding("key.sprint", "Sprint", "movement", "Movement", "key.keyboard.space", "Space"),
            binding(
                "key.jump",
                "Jump Duplicate",
                "movement",
                "Movement",
                "key.keyboard.space",
                "Space"),
            binding("key.jump", "Jump", "movement", "Movement", "key.keyboard.space", "Space"));

    KeysetConflictReport firstReport = KeysetConflicts.analyze(firstOrdering);
    KeysetConflictReport secondReport = KeysetConflicts.analyze(secondOrdering);

    assertEquals(firstReport, secondReport);
    assertEquals(2, secondReport.getConflictCount());
  }

  @Test
  void analyzerTreatsModifierCombosAsSeparateConflicts() {
    KeysetConflictReport report =
        KeysetConflicts.analyze(
            Arrays.asList(
                binding(
                    "mod.zoom",
                    "Zoom",
                    "utility",
                    "Utility",
                    KeysetKeyStroke.of(
                        "key.keyboard.c", Arrays.asList(KeysetModifier.SHIFT, KeysetModifier.CTRL)),
                    "Shift+Ctrl+C"),
                binding(
                    "mod.chatwheel",
                    "Chat Wheel",
                    "utility",
                    "Utility",
                    KeysetKeyStroke.of(
                        "key.keyboard.c", Arrays.asList(KeysetModifier.CTRL, KeysetModifier.SHIFT)),
                    "Shift+Ctrl+C"),
                binding("mod.camera", "Camera", "utility", "Utility", "key.keyboard.c", "C")));

    assertEquals(1, report.getConflictCount());
    assertEquals("shift+ctrl+key.keyboard.c", report.getConflicts().get(0).getKeySignature());
  }

  private static KeysetBindingDescriptor binding(
      String id,
      String displayName,
      String categoryId,
      String categoryName,
      String keyToken,
      String keyDisplayName) {
    return binding(
        id,
        displayName,
        categoryId,
        categoryName,
        keyToken == null ? KeysetKeyStroke.unbound() : KeysetKeyStroke.of(keyToken),
        keyDisplayName);
  }

  private static KeysetBindingDescriptor binding(
      String id,
      String displayName,
      String categoryId,
      String categoryName,
      KeysetKeyStroke keyStroke,
      String keyDisplayName) {
    return new KeysetBindingDescriptor(
        id, displayName, categoryId, categoryName, keyStroke, keyDisplayName);
  }
}
