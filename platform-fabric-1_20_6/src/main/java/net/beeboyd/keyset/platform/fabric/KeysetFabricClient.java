package net.beeboyd.keyset.platform.fabric;

import java.lang.reflect.Field;
import java.util.List;
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

          List<ClickableWidget> buttons = Screens.getButtons(screen);
          int[] placement = findControlsButtonPlacement(buttons, scaledWidth, scaledHeight);
          ButtonWidget keysetButton =
              ButtonWidget.builder(
                      Text.translatable("keyset.open"),
                      button -> client.setScreen(new KeysetScreen(screen, SERVICE)))
                  .dimensions(
                      placement[0], placement[1], CONTROLS_BUTTON_WIDTH, CONTROLS_BUTTON_HEIGHT)
                  .build();
          keysetButton.setTooltip(Tooltip.of(Text.translatable("keyset.subtitle")));
          buttons.add(keysetButton);
        });
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
}
