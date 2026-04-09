package net.beeboyd.keyset.platform.fabric;

import java.util.List;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.platform.fabric.screen.KeysetKeybindsScreen;
import net.beeboyd.keyset.platform.fabric.screen.KeysetScreen;
import net.beeboyd.keyset.shim.client.KeysetClientHooks;
import net.beeboyd.keyset.shim.client.KeysetControlsButtonPlacement;
import net.beeboyd.keyset.shim.v1203.Keyset1203Range;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeysetFabricClient implements ClientModInitializer {
  private static final int CONTROLS_BUTTON_WIDTH = 72;
  private static final int CONTROLS_BUTTON_HEIGHT = 20;
  private static final int CONTROLS_BUTTON_MARGIN = 8;
  private static final Logger LOGGER = LoggerFactory.getLogger(KeysetCoreMetadata.MOD_ID);
  private static final KeysetFabricService SERVICE = new KeysetFabricService();

  private KeyBinding openScreenKeyBinding;
  private KeyBinding cycleNextKeyBinding;
  private KeyBinding cyclePrevKeyBinding;
  private final KeysetClientHooks<net.minecraft.client.MinecraftClient, Screen> clientHooks =
      new KeysetClientHooks<net.minecraft.client.MinecraftClient, Screen>();

  // Single-slot injection tracking — only one ControlsOptionsScreen open at a time.
  private Screen lastInjectedScreen;
  private ClickableWidget lastInjectedButton;

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

    cycleNextKeyBinding =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "keyset.key.cycle_profile_next",
                InputUtil.UNKNOWN_KEY.getCode(),
                KeyBinding.MISC_CATEGORY));

    cyclePrevKeyBinding =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "keyset.key.cycle_profile_prev",
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
          KeysetClientHooks.consumeAllPresses(
              openScreenKeyBinding::wasPressed,
              () ->
                  clientHooks.requestOpenScreen(
                      client.currentScreen, KeysetFabricClient::isKeysetScreen));

          KeysetClientHooks.consumeAllPresses(
              cycleNextKeyBinding::wasPressed,
              () -> queueCycleStatus(SERVICE.cycleToNextProfile(client)),
              exception -> LOGGER.warn("Failed to cycle to next profile", exception));

          KeysetClientHooks.consumeAllPresses(
              cyclePrevKeyBinding::wasPressed,
              () -> queueCycleStatus(SERVICE.cycleToPreviousProfile(client)),
              exception -> LOGGER.warn("Failed to cycle to previous profile", exception));

          flushHudStatusNotice(client);
          clientHooks.flushPendingOpen(
              client,
              currentClient -> currentClient.currentScreen,
              net.minecraft.client.MinecraftClient::setScreen,
              parentScreen -> new KeysetScreen(parentScreen, SERVICE),
              KeysetFabricClient::isKeysetScreen);
        });

    ScreenEvents.AFTER_INIT.register(
        (client, screen, scaledWidth, scaledHeight) -> {
          if (!(screen instanceof ControlsOptionsScreen)) {
            // If we're navigating to a non-Controls screen, clear the injection slot.
            lastInjectedScreen = null;
            lastInjectedButton = null;
            return;
          }

          List<ClickableWidget> buttons = Screens.getButtons(screen);

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
                  new KeysetControlsButtonPlacement.BoundsView<ClickableWidget>() {
                    @Override
                    public int getX(ClickableWidget widget) {
                      return widget.getX();
                    }

                    @Override
                    public int getY(ClickableWidget widget) {
                      return widget.getY();
                    }

                    @Override
                    public int getWidth(ClickableWidget widget) {
                      return widget.getWidth();
                    }

                    @Override
                    public int getHeight(ClickableWidget widget) {
                      return widget.getHeight();
                    }
                  });
          ButtonWidget keysetButton =
              ButtonWidget.builder(
                      Text.translatable("keyset.open"), button -> requestOpenScreen(screen))
                  .dimensions(
                      placement[0], placement[1], CONTROLS_BUTTON_WIDTH, CONTROLS_BUTTON_HEIGHT)
                  .build();
          keysetButton.setTooltip(Tooltip.of(Text.translatable("keyset.subtitle")));
          buttons.add(keysetButton);

          lastInjectedScreen = screen;
          lastInjectedButton = keysetButton;
        });
  }

  private void requestOpenScreen(Screen parent) {
    clientHooks.requestOpenScreen(parent, KeysetFabricClient::isKeysetScreen);
  }

  private void flushHudStatusNotice(net.minecraft.client.MinecraftClient client) {
    if (client == null || isKeysetScreen(client.currentScreen)) {
      return;
    }

    SERVICE.flushStatusNoticeToHud(client);
  }

  private static boolean isKeysetScreen(Screen screen) {
    return screen instanceof KeysetScreen || screen instanceof KeysetKeybindsScreen;
  }

  private static void queueCycleStatus(KeysetFabricService.ActivationResult activationResult) {
    String message =
        Text.translatable("keyset.status.profile_cycled", activationResult.getProfileName())
            .getString();
    if (activationResult.hasConflicts()) {
      message +=
          " "
              + Text.translatable(
                      "keyset.status.profile_conflicts",
                      Integer.valueOf(activationResult.getConflictCount()),
                      Integer.valueOf(activationResult.getAffectedBindingCount()))
                  .getString();
    }

    SERVICE.reportStatusNotice(message, activationResult.hasConflicts());
  }
}
