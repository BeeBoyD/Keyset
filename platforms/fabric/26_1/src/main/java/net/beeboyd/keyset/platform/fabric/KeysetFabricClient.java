package net.beeboyd.keyset.platform.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.platform.fabric.screen.KeysetKeybindsScreen;
import net.beeboyd.keyset.platform.fabric.screen.KeysetScreen;
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
  private Screen pendingParentScreen;
  private boolean openScreenRequested;
  private final Map<Screen, AbstractWidget> injectedControlsButtons =
      new WeakHashMap<Screen, AbstractWidget>();

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
          while (openScreenKeyMapping.consumeClick()) {
            requestOpenScreen(client.screen);
          }

          flushPendingOpen(client);
        });

    ScreenEvents.AFTER_INIT.register(
        (client, screen, scaledWidth, scaledHeight) -> {
          // In MC 26.1, Options → Controls opens ControlsScreen first; KeyBindsScreen is a
          // sub-screen reached via its "Key Binds" button. Inject into both.
          // Exclude KeysetKeybindsScreen (extends KeyBindsScreen) — it is Keyset's own screen.
          if (!(screen instanceof KeyBindsScreen) && !(screen instanceof ControlsScreen)) {
            return;
          }
          if (screen instanceof KeysetKeybindsScreen) {
            return;
          }

          List<AbstractWidget> buttons = Screens.getWidgets(screen);
          removeInjectedControlsButton(screen, buttons);
          int[] placement = findControlsButtonPlacement(buttons, scaledWidth, scaledHeight);
          Button keysetButton =
              Button.builder(
                      Component.translatable("keyset.open"), button -> requestOpenScreen(screen))
                  .bounds(placement[0], placement[1], CONTROLS_BUTTON_WIDTH, CONTROLS_BUTTON_HEIGHT)
                  .build();
          keysetButton.setTooltip(Tooltip.create(Component.translatable("keyset.subtitle")));
          buttons.add(keysetButton);
          injectedControlsButtons.put(screen, keysetButton);
        });
  }

  private void removeInjectedControlsButton(Screen screen, List<AbstractWidget> buttons) {
    AbstractWidget existingButton = injectedControlsButtons.remove(screen);
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

  private void flushPendingOpen(Minecraft client) {
    if (!openScreenRequested || client == null) {
      return;
    }

    openScreenRequested = false;
    Screen parent = pendingParentScreen;
    pendingParentScreen = null;
    if (isKeysetScreen(client.screen) || isKeysetScreen(parent)) {
      return;
    }
    client.setScreen(new KeysetScreen(parent != null ? parent : client.screen, SERVICE));
  }

  private static boolean isKeysetScreen(Screen screen) {
    return screen instanceof KeysetScreen || screen instanceof KeysetKeybindsScreen;
  }

  private static int[] findControlsButtonPlacement(
      List<? extends AbstractWidget> buttons, int screenWidth, int screenHeight) {
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

  private static boolean overlapsExisting(List<? extends AbstractWidget> buttons, int x, int y) {
    for (AbstractWidget button : buttons) {
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
