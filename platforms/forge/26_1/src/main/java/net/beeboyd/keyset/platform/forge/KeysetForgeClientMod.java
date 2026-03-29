package net.beeboyd.keyset.platform.forge;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.beeboyd.keyset.platform.fabric.screen.KeysetKeybindsScreen;
import net.beeboyd.keyset.platform.fabric.screen.KeysetScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.KeybindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(KeysetCoreMetadata.MOD_ID)
public final class KeysetForgeClientMod {
  public KeysetForgeClientMod() {
    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientOnly::new);
  }

  private static final class ClientOnly {
    private static final int CONTROLS_BUTTON_WIDTH = 72;
    private static final int CONTROLS_BUTTON_HEIGHT = 20;
    private static final int CONTROLS_BUTTON_MARGIN = 8;
    private static final Logger LOGGER = LoggerFactory.getLogger(KeysetCoreMetadata.MOD_ID);
    private static final KeysetFabricService SERVICE = new KeysetFabricService();

    private KeyMapping openScreenKeyMapping;
    private Screen pendingParentScreen;
    private boolean openScreenRequested;
    private boolean started;
    private final Map<Screen, AbstractWidget> injectedControlsButtons =
        new WeakHashMap<Screen, AbstractWidget>();

    private ClientOnly() {
      LOGGER.info("Keyset Forge client bootstrap loaded");
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      modBus.addListener(this::onRegisterKeyMappings);
      MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(this::onScreenInit);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
      LOGGER.info("Registering Keyset Forge key mapping");
      openScreenKeyMapping =
          new KeyMapping(
              "keyset.key.open_screen",
              InputConstants.UNKNOWN.getValue(),
              "key.categories.misc");
      event.register(openScreenKeyMapping);
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
        return;
      }

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

      if (openScreenKeyMapping != null) {
        while (openScreenKeyMapping.consumeClick()) {
          requestOpenScreen(client.screen);
        }
      }

      flushPendingOpen(client);
    }

    private void onScreenInit(ScreenEvent.Init.Post event) {
      if (!(event.getScreen() instanceof KeybindsScreen controlsScreen)) {
        return;
      }

      removeInjectedControlsButton(event, controlsScreen);
      List<AbstractWidget> buttons =
          event.getListenersList().stream()
              .filter(AbstractWidget.class::isInstance)
              .map(AbstractWidget.class::cast)
              .toList();
      int[] placement =
          findControlsButtonPlacement(buttons, controlsScreen.width, controlsScreen.height);
      Button keysetButton =
          Button.builder(
                  Component.translatable("keyset.open"),
                  button -> requestOpenScreen(controlsScreen))
              .bounds(placement[0], placement[1], CONTROLS_BUTTON_WIDTH, CONTROLS_BUTTON_HEIGHT)
              .build();
      event.addListener(keysetButton);
      injectedControlsButtons.put(controlsScreen, keysetButton);
    }

    private void removeInjectedControlsButton(ScreenEvent.Init.Post event, Screen screen) {
      AbstractWidget existingButton = injectedControlsButtons.remove(screen);
      if (existingButton != null) {
        event.removeListener(existingButton);
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

    private static boolean overlapsExisting(
        List<? extends AbstractWidget> buttons, int x, int y) {
      for (AbstractWidget button : buttons) {
        int otherX = button.getX();
        int otherY = button.getY();
        int otherWidth = button.getWidth();
        int otherHeight = button.getHeight();
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
  }
}
