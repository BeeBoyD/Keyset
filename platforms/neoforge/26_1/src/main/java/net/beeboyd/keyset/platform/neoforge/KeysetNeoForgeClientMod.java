package net.beeboyd.keyset.platform.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.beeboyd.keyset.platform.fabric.screen.KeysetKeybindsScreen;
import net.beeboyd.keyset.platform.fabric.screen.KeysetScreen;
import net.beeboyd.keyset.shim.client.KeysetClientHooks;
import net.beeboyd.keyset.shim.client.KeysetControlsButtonPlacement;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(KeysetCoreMetadata.MOD_ID)
public final class KeysetNeoForgeClientMod {
  private static final Logger LOGGER = LoggerFactory.getLogger(KeysetCoreMetadata.MOD_ID);

  public KeysetNeoForgeClientMod(IEventBus modEventBus, Dist dist, ModContainer container) {
    LOGGER.info("Keyset NeoForge root mod constructed");
    if (dist.isClient()) {
      new ClientOnly(modEventBus);
    }
  }

  private static final class ClientOnly {
    private static final int CONTROLS_BUTTON_WIDTH = 72;
    private static final int CONTROLS_BUTTON_HEIGHT = 20;
    private static final int CONTROLS_BUTTON_MARGIN = 8;
    private static final Logger LOGGER = LoggerFactory.getLogger(KeysetCoreMetadata.MOD_ID);
    private static final KeysetFabricService SERVICE = new KeysetFabricService();

    private static final KeyMapping.Category KEYSET_CATEGORY =
        new KeyMapping.Category(net.minecraft.resources.Identifier.fromNamespaceAndPath("keyset", "keyset"));

    private KeyMapping openScreenKeyMapping;
    private KeyMapping cycleNextKeyMapping;
    private KeyMapping cyclePrevKeyMapping;
    private final KeyMapping[] slotKeyMappings = new KeyMapping[5];
    private final KeysetClientHooks<Minecraft, Screen> clientHooks =
        new KeysetClientHooks<Minecraft, Screen>();
    private boolean started;
    private Screen lastInjectedScreen;
    private AbstractWidget lastInjectedButton;

    private ClientOnly(IEventBus modBus) {
      LOGGER.info("Keyset NeoForge client bootstrap loaded");
      modBus.addListener(this::onRegisterKeyMappings);
      NeoForge.EVENT_BUS.addListener(this::onClientTick);
      NeoForge.EVENT_BUS.addListener(this::onScreenInit);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
      LOGGER.info("Registering Keyset NeoForge key mapping");
      openScreenKeyMapping =
          new KeyMapping(
              "keyset.key.open_screen",
              InputConstants.UNKNOWN.getValue(),
              KEYSET_CATEGORY);
      cycleNextKeyMapping =
          new KeyMapping(
              "keyset.key.cycle_profile_next",
              InputConstants.UNKNOWN.getValue(),
              KEYSET_CATEGORY);
      cyclePrevKeyMapping =
          new KeyMapping(
              "keyset.key.cycle_profile_prev",
              InputConstants.UNKNOWN.getValue(),
              KEYSET_CATEGORY);
      event.register(openScreenKeyMapping);
      event.register(cycleNextKeyMapping);
      event.register(cyclePrevKeyMapping);
      for (int i = 0; i < 5; i++) {
        slotKeyMappings[i] =
            new KeyMapping(
                "keyset.key.activate_slot_" + (i + 1),
                InputConstants.UNKNOWN.getValue(),
                KEYSET_CATEGORY);
        event.register(slotKeyMappings[i]);
      }
    }

    private void onClientTick(ClientTickEvent.Post event) {
      Minecraft client = Minecraft.getInstance();
      if (!started && client != null && client.options != null) {
        started = true;
        try {
          SERVICE.onClientStarted(client);
          LOGGER.info("{} initialized", KeysetCoreMetadata.DISPLAY_NAME);
        } catch (Exception exception) {
          LOGGER.error("Failed to initialize Keyset profiles", exception);
        }
      }

      if (client == null) {
        return;
      }

      KeysetClientHooks.consumeAllPresses(
          openScreenKeyMapping == null ? null : openScreenKeyMapping::consumeClick,
          () -> clientHooks.requestOpenScreen(client.screen, ClientOnly::isKeysetScreen));

      KeysetClientHooks.consumeAllPresses(
          cycleNextKeyMapping == null ? null : cycleNextKeyMapping::consumeClick,
          () -> queueCycleStatus(SERVICE.cycleToNextProfile(client)),
          exception -> LOGGER.warn("Failed to cycle to next profile", exception));

      KeysetClientHooks.consumeAllPresses(
          cyclePrevKeyMapping == null ? null : cyclePrevKeyMapping::consumeClick,
          () -> queueCycleStatus(SERVICE.cycleToPreviousProfile(client)),
          exception -> LOGGER.warn("Failed to cycle to previous profile", exception));

      for (int i = 0; i < 5; i++) {
        final int slotIndex = i;
        KeysetClientHooks.consumeAllPresses(
            slotKeyMappings[slotIndex] == null ? null : slotKeyMappings[slotIndex]::consumeClick,
            () -> {
              KeysetFabricService.ActivationResult result =
                  SERVICE.activateProfileByIndex(client, slotIndex);
              if (result != null) {
                queueCycleStatus(result);
              }
            },
            exception ->
                LOGGER.warn("Failed to activate profile slot {}", slotIndex + 1, exception));
      }

      flushHudStatusNotice(client);
      clientHooks.flushPendingOpen(
          client,
          currentClient -> currentClient.screen,
          Minecraft::setScreen,
          parentScreen -> new KeysetScreen(parentScreen, SERVICE),
          ClientOnly::isKeysetScreen);
    }

    private void onScreenInit(ScreenEvent.Init.Post event) {
      Screen screen = event.getScreen();
      // In MC 26.1, Options → Controls opens ControlsScreen first; KeyBindsScreen is a
      // sub-screen reached via its "Key Binds" button. Inject into both.
      // Exclude KeysetKeybindsScreen (extends KeyBindsScreen) — it is Keyset's own screen.
      if (!(screen instanceof KeyBindsScreen) && !(screen instanceof ControlsScreen)) {
        lastInjectedScreen = null;
        lastInjectedButton = null;
        return;
      }
      if (screen instanceof KeysetKeybindsScreen) {
        return;
      }

      if (lastInjectedScreen == screen && lastInjectedButton != null) {
        event.removeListener(lastInjectedButton);
      } else if (lastInjectedScreen != null && lastInjectedButton != null) {
        lastInjectedScreen = null;
        lastInjectedButton = null;
      }
      List<AbstractWidget> buttons =
          event.getListenersList().stream()
              .filter(AbstractWidget.class::isInstance)
              .map(AbstractWidget.class::cast)
              .toList();
      int[] placement =
          KeysetControlsButtonPlacement.findPlacement(
              buttons,
              screen.width,
              screen.height,
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
          Button.builder(Component.translatable("keyset.open"), button -> requestOpenScreen(screen))
              .bounds(placement[0], placement[1], CONTROLS_BUTTON_WIDTH, CONTROLS_BUTTON_HEIGHT)
              .build();
      event.addListener(keysetButton);
      lastInjectedScreen = screen;
      lastInjectedButton = keysetButton;
    }

    private void requestOpenScreen(Screen parent) {
      clientHooks.requestOpenScreen(parent, ClientOnly::isKeysetScreen);
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
}
