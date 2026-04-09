package net.beeboyd.keyset.platform.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.platform.fabric.screen.KeysetKeybindsScreen;
import net.beeboyd.keyset.platform.fabric.screen.KeysetScreen;
import net.beeboyd.keyset.shim.client.KeysetClientHooks;
import net.beeboyd.keyset.shim.client.KeysetControlsButtonPlacement;
import net.beeboyd.keyset.shim.v261.Keyset261;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeysetFabricClient implements ClientModInitializer {
  private static final int CONTROLS_BUTTON_WIDTH = 72;
  private static final int CONTROLS_BUTTON_HEIGHT = 20;
  private static final int CONTROLS_BUTTON_MARGIN = 8;
  private static final Logger LOGGER = LoggerFactory.getLogger(KeysetCoreMetadata.MOD_ID);
  private static final KeysetFabricService SERVICE = new KeysetFabricService();

  private KeyMapping openScreenKeyMapping;
  private KeyMapping cycleNextKeyMapping;
  private KeyMapping cyclePrevKeyMapping;
  private final KeysetClientHooks<Minecraft, Screen> clientHooks =
      new KeysetClientHooks<Minecraft, Screen>();

  // Single-slot injection tracking — only one controls screen open at a time.
  private Screen lastInjectedScreen;
  private AbstractWidget lastInjectedButton;

  public static KeysetFabricService getService() {
    return SERVICE;
  }

  @Override
  public void onInitializeClient() {
    openScreenKeyMapping =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "keyset.key.open_screen",
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MISC));

    cycleNextKeyMapping =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "keyset.key.cycle_profile_next",
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MISC));

    cyclePrevKeyMapping =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "keyset.key.cycle_profile_prev",
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MISC));

    ClientLifecycleEvents.CLIENT_STARTED.register(
        client -> {
          try {
            SERVICE.onClientStarted(client);
            LOGGER.info(
                "{} initialized for Minecraft range {}",
                KeysetCoreMetadata.DISPLAY_NAME,
                Keyset261.RANGE_ID);
          } catch (Exception exception) {
            LOGGER.error("Failed to initialize Keyset profiles", exception);
          }
        });

    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          KeysetClientHooks.consumeAllPresses(
              openScreenKeyMapping::consumeClick,
              () ->
                  clientHooks.requestOpenScreen(client.screen, KeysetFabricClient::isKeysetScreen));

          KeysetClientHooks.consumeAllPresses(
              cycleNextKeyMapping::consumeClick,
              () -> queueCycleStatus(SERVICE.cycleToNextProfile(client)),
              exception -> LOGGER.warn("Failed to cycle to next profile", exception));

          KeysetClientHooks.consumeAllPresses(
              cyclePrevKeyMapping::consumeClick,
              () -> queueCycleStatus(SERVICE.cycleToPreviousProfile(client)),
              exception -> LOGGER.warn("Failed to cycle to previous profile", exception));

          flushHudStatusNotice(client);
          clientHooks.flushPendingOpen(
              client,
              currentClient -> currentClient.screen,
              Minecraft::setScreen,
              parentScreen -> new KeysetScreen(parentScreen, SERVICE),
              KeysetFabricClient::isKeysetScreen);
        });

    ScreenEvents.AFTER_INIT.register(
        (client, screen, scaledWidth, scaledHeight) -> {
          // In MC 26.1, Options → Controls opens ControlsScreen first; KeyBindsScreen is a
          // sub-screen reached via its "Key Binds" button. Inject into both.
          // Exclude KeysetKeybindsScreen (extends KeyBindsScreen) — it is Keyset's own screen.
          if (!(screen instanceof KeyBindsScreen) && !(screen instanceof ControlsScreen)) {
            // If we're navigating to a non-Controls screen, clear the injection slot.
            lastInjectedScreen = null;
            lastInjectedButton = null;
            return;
          }
          if (screen instanceof KeysetKeybindsScreen) {
            return;
          }

          List<AbstractWidget> buttons = Screens.getWidgets(screen);

          // Remove stale button from previous screen (same object reused after resize, etc.).
          if (lastInjectedScreen == screen && lastInjectedButton != null) {
            buttons.remove(lastInjectedButton);
          }

          int[] placement =
              KeysetControlsButtonPlacement.findPlacement(
                  buttons,
                  scaledWidth,
                  scaledHeight,
                  CONTROLS_BUTTON_WIDTH,
                  CONTROLS_BUTTON_HEIGHT,
                  CONTROLS_BUTTON_MARGIN,
                  new KeysetControlsButtonPlacement.BoundsView<AbstractWidget>() {
                    @Override
                    public int getX(AbstractWidget widget) {
                      return widget.getX();
                    }

                    @Override
                    public int getY(AbstractWidget widget) {
                      return widget.getY();
                    }

                    @Override
                    public int getWidth(AbstractWidget widget) {
                      return widget.getWidth();
                    }

                    @Override
                    public int getHeight(AbstractWidget widget) {
                      return widget.getHeight();
                    }
                  });
          Button keysetButton =
              Button.builder(
                      Component.translatable("keyset.open"), button -> requestOpenScreen(screen))
                  .bounds(placement[0], placement[1], CONTROLS_BUTTON_WIDTH, CONTROLS_BUTTON_HEIGHT)
                  .build();
          keysetButton.setTooltip(Tooltip.create(Component.translatable("keyset.subtitle")));
          buttons.add(keysetButton);

          lastInjectedScreen = screen;
          lastInjectedButton = keysetButton;
        });
  }

  private void requestOpenScreen(Screen parent) {
    clientHooks.requestOpenScreen(parent, KeysetFabricClient::isKeysetScreen);
  }

  private void flushHudStatusNotice(Minecraft client) {
    if (client == null || isKeysetScreen(client.screen)) {
      return;
    }

    SERVICE.flushStatusNoticeToHud(client);
  }

  private static boolean isKeysetScreen(Screen screen) {
    return screen instanceof KeysetScreen || screen instanceof KeysetKeybindsScreen;
  }

  private static void queueCycleStatus(KeysetFabricService.ActivationResult activationResult) {
    String message =
        Component.translatable("keyset.status.profile_cycled", activationResult.getProfileName())
            .getString();
    if (activationResult.hasConflicts()) {
      message +=
          " "
              + Component.translatable(
                      "keyset.status.profile_conflicts",
                      Integer.valueOf(activationResult.getConflictCount()),
                      Integer.valueOf(activationResult.getAffectedBindingCount()))
                  .getString();
    }

    SERVICE.reportStatusNotice(message, activationResult.hasConflicts());
  }
}
