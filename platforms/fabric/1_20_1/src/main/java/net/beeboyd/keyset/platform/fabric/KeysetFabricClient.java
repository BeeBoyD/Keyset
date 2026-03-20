package net.beeboyd.keyset.platform.fabric;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.platform.fabric.screen.KeysetKeybindsScreen;
import net.beeboyd.keyset.platform.fabric.screen.KeysetScreen;
import net.beeboyd.keyset.shim.v1201.Keyset1201Range;
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
  private Screen pendingParentScreen;
  private boolean openScreenRequested;
  private final Map<Screen, ClickableWidget> injectedControlsButtons =
      new WeakHashMap<Screen, ClickableWidget>();

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
                Keyset1201Range.RANGE_ID);
          } catch (Exception exception) {
            LOGGER.error("Failed to initialize Keyset profiles", exception);
          }
        });

    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          while (openScreenKeyBinding.wasPressed()) {
            requestOpenScreen(client.currentScreen);
          }

          flushPendingOpen(client);
        });

    ScreenEvents.AFTER_INIT.register(
        (client, screen, scaledWidth, scaledHeight) -> {
          if (!(screen instanceof ControlsOptionsScreen)) {
            return;
          }

          List<ClickableWidget> buttons = Screens.getButtons(screen);
          removeInjectedControlsButton(screen, buttons);
          int[] placement = findControlsButtonPlacement(buttons, scaledWidth, scaledHeight);
          ButtonWidget keysetButton =
              ButtonWidget.builder(
                      Text.translatable("keyset.open"), button -> requestOpenScreen(screen))
                  .dimensions(
                      placement[0], placement[1], CONTROLS_BUTTON_WIDTH, CONTROLS_BUTTON_HEIGHT)
                  .build();
          keysetButton.setTooltip(Tooltip.of(Text.translatable("keyset.subtitle")));
          buttons.add(keysetButton);
          injectedControlsButtons.put(screen, keysetButton);
        });
  }

  private void removeInjectedControlsButton(Screen screen, List<ClickableWidget> buttons) {
    ClickableWidget existingButton = injectedControlsButtons.remove(screen);
    if (existingButton != null) {
      buttons.remove(existingButton);
    }
  }

  private void requestOpenScreen(Screen parent) {
    if (isKeysetScreen(parent)) {
      return;
    }
    pendingParentScreen = parent;
    openScreenRequested = true;
  }

  private void flushPendingOpen(net.minecraft.client.MinecraftClient client) {
    if (!openScreenRequested || client == null) {
      return;
    }

    openScreenRequested = false;
    Screen parent = pendingParentScreen;
    pendingParentScreen = null;
    if (isKeysetScreen(client.currentScreen) || isKeysetScreen(parent)) {
      return;
    }
    client.setScreen(new KeysetScreen(parent != null ? parent : client.currentScreen, SERVICE));
  }

  private static boolean isKeysetScreen(Screen screen) {
    return screen instanceof KeysetScreen || screen instanceof KeysetKeybindsScreen;
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
    for (ClickableWidget button : buttons) {
      if (rectanglesOverlap(
          x,
          y,
          CONTROLS_BUTTON_WIDTH,
          CONTROLS_BUTTON_HEIGHT,
          button.getX(),
          button.getY(),
          button.getWidth(),
          button.getHeight())) {
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
}
