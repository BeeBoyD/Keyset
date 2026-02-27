package net.beeboyd.keyset.platform.fabric;

import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.shim.v1201.Keyset1201Range;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeysetFabricClient implements ClientModInitializer {
  private static final Logger LOGGER = LoggerFactory.getLogger(KeysetCoreMetadata.MOD_ID);

  @Override
  public void onInitializeClient() {
    LOGGER.info(
        "{} scaffold initialized for Minecraft range {}",
        KeysetCoreMetadata.DISPLAY_NAME,
        Keyset1201Range.RANGE_ID);
  }
}
