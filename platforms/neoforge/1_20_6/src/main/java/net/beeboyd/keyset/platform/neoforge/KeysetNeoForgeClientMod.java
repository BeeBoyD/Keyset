package net.beeboyd.keyset.platform.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.beeboyd.keyset.platform.fabric.screen.KeysetScreen;
import net.minecraft.client.MinecraftClient;
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
  private static final String MISC_CATEGORY_KEY = "key.categories.misc";
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
    private boolean started;

    private ClientOnly(IEventBus modBus) {
      LOGGER.info("Keyset NeoForge client bootstrap loaded");
      modBus.addListener(this::onRegisterKeyMappings);
      NeoForge.EVENT_BUS.addListener(this::onClientTick);
      NeoForge.EVENT_BUS.addListener(this::onScreenInit);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
      LOGGER.info("Registering Keyset NeoForge key mapping");
      openScreenKeyBinding = createOpenScreenKeyBinding();
      event.register(openScreenKeyBinding);
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

      if (client == null || openScreenKeyBinding == null) {
        return;
      }

      while (openScreenKeyBinding.wasPressed()) {
        client.setScreen(new KeysetScreen(client.currentScreen, SERVICE));
      }
    }

    private void onScreenInit(ScreenEvent.Init.Post event) {
      if (!(event.getScreen() instanceof ControlsOptionsScreen controlsScreen)) {
        return;
      }

      List<ClickableWidget> buttons =
          event.getListenersList().stream()
              .filter(ClickableWidget.class::isInstance)
              .map(ClickableWidget.class::cast)
              .toList();
      int[] placement =
          findControlsButtonPlacement(buttons, controlsScreen.width, controlsScreen.height);
      ButtonWidget keysetButton =
          ButtonWidget.builder(
                  Text.translatable("keyset.open"),
                  button ->
                      MinecraftClient.getInstance()
                          .setScreen(new KeysetScreen(controlsScreen, SERVICE)))
              .dimensions(placement[0], placement[1], CONTROLS_BUTTON_WIDTH, CONTROLS_BUTTON_HEIGHT)
              .build();
      event.addListener(keysetButton);
    }

    private static int[] findControlsButtonPlacement(
        List<? extends ClickableWidget> buttons, int screenWidth, int screenHeight) {
      int centerX = (screenWidth - CONTROLS_BUTTON_WIDTH) / 2;
      int bottomY = Math.max(32, screenHeight - CONTROLS_BUTTON_HEIGHT - 32);
      int[][] candidates = {
        {centerX, bottomY},
        {screenWidth - CONTROLS_BUTTON_WIDTH - CONTROLS_BUTTON_MARGIN, bottomY},
        {CONTROLS_BUTTON_MARGIN, bottomY},
        {screenWidth - CONTROLS_BUTTON_WIDTH - CONTROLS_BUTTON_MARGIN, CONTROLS_BUTTON_MARGIN},
        {CONTROLS_BUTTON_MARGIN, CONTROLS_BUTTON_MARGIN},
        {centerX, 32}
      };

      for (int[] candidate : candidates) {
        if (!overlapsExisting(buttons, candidate[0], candidate[1])) {
          return candidate;
        }
      }

      return new int[] {
        screenWidth - CONTROLS_BUTTON_WIDTH - CONTROLS_BUTTON_MARGIN,
        Math.max(CONTROLS_BUTTON_MARGIN, bottomY)
      };
    }

    private static boolean overlapsExisting(List<? extends ClickableWidget> buttons, int x, int y) {
      for (Object button : buttons) {
        int otherX = intField(button, "x", -1000);
        int otherY = intField(button, "y", -1000);
        int otherWidth = intField(button, "width", 150);
        int otherHeight = intField(button, "height", CONTROLS_BUTTON_HEIGHT);
        if (rectanglesOverlap(
            x,
            y,
            CONTROLS_BUTTON_WIDTH,
            CONTROLS_BUTTON_HEIGHT,
            otherX,
            otherY,
            otherWidth,
            otherHeight)) {
          return true;
        }
      }
      return false;
    }

    private static boolean rectanglesOverlap(
        int x,
        int y,
        int width,
        int height,
        int otherX,
        int otherY,
        int otherWidth,
        int otherHeight) {
      return x < otherX + otherWidth
          && x + width > otherX
          && y < otherY + otherHeight
          && y + height > otherY;
    }

    private static int intField(Object target, String name, int fallback) {
      Field field = findField(target.getClass(), name);
      if (field == null) {
        return fallback;
      }

      try {
        field.setAccessible(true);
        return field.getInt(target);
      } catch (IllegalAccessException ignored) {
        return fallback;
      }
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
      for (var constructor : KeyBinding.class.getConstructors()) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (parameterTypes.length != 3
            || parameterTypes[0] != String.class
            || parameterTypes[1] != int.class) {
          continue;
        }

        try {
          Object categoryArgument = resolveMiscCategoryArgument(parameterTypes[2]);
          if (categoryArgument == null) {
            continue;
          }

          return (KeyBinding)
              constructor.newInstance(
                  OPEN_SCREEN_KEY_ID, InputUtil.UNKNOWN_KEY.getCode(), categoryArgument);
        } catch (InstantiationException
            | IllegalAccessException
            | InvocationTargetException
            | IllegalArgumentException ignored) {
          // Try the next available constructor signature.
        }
      }

      throw new IllegalStateException("Unable to create Keyset NeoForge key binding");
    }

    private static Object resolveMiscCategoryArgument(Class<?> categoryType)
        throws IllegalAccessException {
      if (categoryType == String.class) {
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
