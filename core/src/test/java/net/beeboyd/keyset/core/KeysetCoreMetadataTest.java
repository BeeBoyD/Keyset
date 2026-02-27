package net.beeboyd.keyset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KeysetCoreMetadataTest {
  @Test
  void schemaVersionStartsAtOne() {
    assertEquals(1, KeysetCoreMetadata.CONFIG_SCHEMA);
  }

  @Test
  void configFileNameMatchesTheDocumentedPath() {
    assertEquals("keybindprofiles.json", KeysetCoreMetadata.CONFIG_FILE_NAME);
  }
}
