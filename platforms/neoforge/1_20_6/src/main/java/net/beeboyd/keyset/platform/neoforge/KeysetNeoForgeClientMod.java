package net.beeboyd.keyset.platform.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
  private static final String OPEN_SCREEN_KEY_ID = "keyset.key.open_screen";
  private static final String CYCLE_NEXT_KEY_ID = "keyset.key.cycle_profile_next";
  private static final String CYCLE_PREV_KEY_ID = "keyset.key.cycle_profile_prev";
  private static final String MISC_CATEGORY_KEY = "key.categories.misc";
  private static final String KEYSET_CATEGORY_KEY = "key.categories.keyset";
  private static final String LEGACY_MISC_CATEGORY_FIELD = "MISC_CATEGORY";
  private static final String MODERN_MISC_CATEGORY_FIELD = "MISC";

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

    private KeyBinding openScreenKeyBinding;
    private KeyBinding cycleNextKeyBinding;
    private KeyBinding cyclePrevKeyBinding;
    private final KeyBinding[] slotKeyBindings = new KeyBinding[5];
    private final KeysetClientHooks<MinecraftClient, Screen> clientHooks =
        new KeysetClientHooks<MinecraftClient, Screen>();
    private boolean started;
    private Screen lastInjectedScreen;
    private ClickableWidget lastInjectedButton;

    private ClientOnly(IEventBus modBus) {
      LOGGER.info("Keyset NeoForge client bootstrap loaded");
      modBus.addListener(this::onRegisterKeyMappings);
      NeoForge.EVENT_BUS.addListener(this::onClientTick);
      NeoForge.EVENT_BUS.addListener(this::onScreenInit);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
      LOGGER.info("Registering Keyset NeoForge key mapping");
      openScreenKeyBinding = createOpenScreenKeyBinding();
      cycleNextKeyBinding = createKeyBinding(CYCLE_NEXT_KEY_ID);
      cyclePrevKeyBinding = createKeyBinding(CYCLE_PREV_KEY_ID);
      event.register(openScreenKeyBinding);
      event.register(cycleNextKeyBinding);
      event.register(cyclePrevKeyBinding);
      for (int i = 0; i < 5; i++) {
        slotKeyBindings[i] = createKeyBinding("keyset.key.activate_slot_" + (i + 1), KEYSET_CATEGORY_KEY);
        event.register(slotKeyBindings[i]);
      }
    }

    private void onClientTick(ClientTickEvent.Post event) {
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

      for (int i = 0; i < 5; i++) {
        final int slotIndex = i;
        KeysetClientHooks.consumeAllPresses(
            slotKeyBindings[slotIndex] == null ? null : slotKeyBindings[slotIndex]::wasPressed,
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

    private static Field findField(Class<?> type, String name) {
      Class<?> current = type;
      while (current != null) {
        try {
          return current.getDeclaredField(name);
        } catch (NoSuchFieldException ignored) {
          current = current.getSuperclass();
        }
      }
      return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static KeyBinding createOpenScreenKeyBinding() {
      return createKeyBinding(OPEN_SCREEN_KEY_ID);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static KeyBinding createKeyBinding(String keyId) {
      return createKeyBinding(keyId, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static KeyBinding createKeyBinding(String keyId, String categoryKey) {
      for (var constructor : KeyBinding.class.getConstructors()) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (parameterTypes.length != 3
            || parameterTypes[0] != String.class
            || parameterTypes[1] != int.class) {
          continue;
        }

        try {
          Object categoryArgument = resolveCategoryArgument(parameterTypes[2], categoryKey);
          if (categoryArgument == null) {
            continue;
          }

          return (KeyBinding)
              constructor.newInstance(keyId, InputUtil.UNKNOWN_KEY.getCode(), categoryArgument);
        } catch (InstantiationException
            | IllegalAccessException
            | InvocationTargetException
            | IllegalArgumentException ignored) {
          // Try the next available constructor signature.
        }
      }

      throw new IllegalStateException("Unable to create Keyset NeoForge key binding");
    }

    private static Object resolveCategoryArgument(Class<?> categoryType, String categoryKey)
        throws IllegalAccessException {
      if (categoryType == String.class) {
        if (categoryKey != null) {
          return categoryKey;
        }
        Object legacyMiscCategory = staticFieldValue(KeyBinding.class, LEGACY_MISC_CATEGORY_FIELD);
        return legacyMiscCategory instanceof String ? legacyMiscCategory : MISC_CATEGORY_KEY;
      }

      if (categoryType.isEnum()) {
        return Enum.valueOf((Class<? extends Enum>) categoryType, MODERN_MISC_CATEGORY_FIELD);
      }

      Object modernMiscCategory = staticFieldValue(categoryType, MODERN_MISC_CATEGORY_FIELD);
      if (categoryType.isInstance(modernMiscCategory)) {
        return modernMiscCategory;
      }

      return null;
    }

    private static Object staticFieldValue(Class<?> type, String name)
        throws IllegalAccessException {
      Field field = findField(type, name);
      if (field == null) {
        return null;
      }

      field.setAccessible(true);
      return field.get(null);
    }
  }
}
