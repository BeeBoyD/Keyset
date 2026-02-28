package net.beeboyd.keyset.platform.fabric;

import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.platform.fabric.screen.KeysetScreen;
import net.beeboyd.keyset.shim.v1203.Keyset1203Range;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeysetFabricClient implements ClientModInitializer {
  private static final Logger LOGGER = LoggerFactory.getLogger(KeysetCoreMetadata.MOD_ID);
  private static final KeysetFabricService SERVICE = new KeysetFabricService();

  private KeyBinding openScreenKeyBinding;

  public static KeysetFabricService getService() {
    return SERVICE;
  }

  @Override
  public void onInitializeClient() {
    openScreenKeyBinding =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "keyset.key.open_screen",
                InputUtil.UNKNOWN_KEY.getCode(),
                KeyBinding.MISC_CATEGORY));

    ClientLifecycleEvents.CLIENT_STARTED.register(
        client -> {
          try {
            SERVICE.onClientStarted(client);
            LOGGER.info(
                "{} initialized for Minecraft range {}",
                KeysetCoreMetadata.DISPLAY_NAME,
                Keyset1203Range.RANGE_ID);
          } catch (Exception exception) {
            LOGGER.error("Failed to initialize Keyset profiles", exception);
          }
        });

    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          while (openScreenKeyBinding.wasPressed()) {
            client.setScreen(new KeysetScreen(client.currentScreen, SERVICE));
          }
        });

    ScreenEvents.AFTER_INIT.register(
        (client, screen, scaledWidth, scaledHeight) -> {
          if (!(screen instanceof ControlsOptionsScreen)) {
            return;
          }

          ButtonWidget keysetButton =
              ButtonWidget.builder(
                      Text.translatable("keyset.open"),
                      button -> client.setScreen(new KeysetScreen(screen, SERVICE)))
                  .dimensions(scaledWidth - 88, 8, 72, 20)
                  .build();
          keysetButton.setTooltip(Tooltip.of(Text.translatable("keyset.subtitle")));
          Screens.getButtons(screen).add(keysetButton);
        });
  }
}
