package net.beeboyd.keyset.platform.neoforge;

import java.util.List;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.beeboyd.keyset.platform.fabric.screen.KeysetKeybindsScreen;
import net.beeboyd.keyset.platform.fabric.screen.KeysetScreen;
import net.beeboyd.keyset.shim.client.KeysetClientHooks;
import net.beeboyd.keyset.shim.client.KeysetControlsButtonPlacement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(KeysetCoreMetadata.MOD_ID)
public final class KeysetNeoForgeClientMod {
  private static final Logger LOGGER = LoggerFactory.getLogger(KeysetCoreMetadata.MOD_ID);

  public KeysetNeoForgeClientMod() {
    LOGGER.info("Keyset NeoForge root mod constructed");
    if (FMLEnvironment.dist == Dist.CLIENT) {
      new ClientOnly();
    }
  }

  private static final class ClientOnly {
    private static final int CONTROLS_BUTTON_WIDTH = 72;
    private static final int CONTROLS_BUTTON_HEIGHT = 20;
    private static final int CONTROLS_BUTTON_MARGIN = 8;
    private static final Logger LOGGER = LoggerFactory.getLogger(KeysetCoreMetadata.MOD_ID);
    private static final KeysetFabricService SERVICE = new KeysetFabricService();

    private KeyBinding openScreenKeyBinding;
    private KeyBinding cycleNextKeyBinding;
    private KeyBinding cyclePrevKeyBinding;
    private final KeysetClientHooks<MinecraftClient, Screen> clientHooks =
        new KeysetClientHooks<MinecraftClient, Screen>();
    private boolean started;
    private Screen lastInjectedScreen;
    private ClickableWidget lastInjectedButton;

    private ClientOnly() {
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      modBus.addListener(this::onRegisterKeyMappings);
      MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(this::onScreenInit);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
      openScreenKeyBinding =
          new KeyBinding(
              "keyset.key.open_screen", InputUtil.UNKNOWN_KEY.getCode(), KeyBinding.MISC_CATEGORY);
      cycleNextKeyBinding =
          new KeyBinding(
              "keyset.key.cycle_profile_next",
              InputUtil.UNKNOWN_KEY.getCode(),
              KeyBinding.MISC_CATEGORY);
      cyclePrevKeyBinding =
          new KeyBinding(
              "keyset.key.cycle_profile_prev",
              InputUtil.UNKNOWN_KEY.getCode(),
              KeyBinding.MISC_CATEGORY);
      event.register(openScreenKeyBinding);
      event.register(cycleNextKeyBinding);
      event.register(cyclePrevKeyBinding);
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
        return;
      }

      MinecraftClient client = MinecraftClient.getInstance();
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
          openScreenKeyBinding == null ? null : openScreenKeyBinding::wasPressed,
          () -> clientHooks.requestOpenScreen(client.currentScreen, ClientOnly::isKeysetScreen));

      KeysetClientHooks.consumeAllPresses(
          cycleNextKeyBinding == null ? null : cycleNextKeyBinding::wasPressed,
          () -> queueCycleStatus(SERVICE.cycleToNextProfile(client)),
          exception -> LOGGER.warn("Failed to cycle to next profile", exception));

      KeysetClientHooks.consumeAllPresses(
          cyclePrevKeyBinding == null ? null : cyclePrevKeyBinding::wasPressed,
          () -> queueCycleStatus(SERVICE.cycleToPreviousProfile(client)),
          exception -> LOGGER.warn("Failed to cycle to previous profile", exception));

      flushHudStatusNotice(client);
      clientHooks.flushPendingOpen(
          client,
          currentClient -> currentClient.currentScreen,
          MinecraftClient::setScreen,
          parentScreen -> new KeysetScreen(parentScreen, SERVICE),
          ClientOnly::isKeysetScreen);
    }

    private void onScreenInit(ScreenEvent.Init.Post event) {
      if (!(event.getScreen() instanceof ControlsOptionsScreen controlsScreen)) {
        lastInjectedScreen = null;
        lastInjectedButton = null;
        return;
      }

      if (lastInjectedScreen == controlsScreen && lastInjectedButton != null) {
        event.removeListener(lastInjectedButton);
      } else if (lastInjectedScreen != null && lastInjectedButton != null) {
        lastInjectedScreen = null;
        lastInjectedButton = null;
      }
      List<ClickableWidget> buttons =
          event.getListenersList().stream()
              .filter(ClickableWidget.class::isInstance)
              .map(ClickableWidget.class::cast)
              .toList();
      int[] placement =
          KeysetControlsButtonPlacement.findPlacement(
              buttons,
              controlsScreen.width,
              controlsScreen.height,
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
                  Text.translatable("keyset.open"), button -> requestOpenScreen(controlsScreen))
              .dimensions(placement[0], placement[1], CONTROLS_BUTTON_WIDTH, CONTROLS_BUTTON_HEIGHT)
              .build();
      event.addListener(keysetButton);
      lastInjectedScreen = controlsScreen;
      lastInjectedButton = keysetButton;
    }

    private void requestOpenScreen(Screen parent) {
      clientHooks.requestOpenScreen(parent, ClientOnly::isKeysetScreen);
    }

    private void flushHudStatusNotice(MinecraftClient client) {
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
}
