package net.beeboyd.keyset.platform.forge;

import java.util.List;
import net.beeboyd.keyset.core.KeysetCoreMetadata;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.beeboyd.keyset.platform.fabric.screen.KeysetScreen;
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

    private KeyBinding openScreenKeyBinding;
    private Screen pendingParentScreen;
    private boolean openScreenRequested;
    private boolean started;

    private ClientOnly() {
      LOGGER.info("Keyset Forge client bootstrap loaded");
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      modBus.addListener(this::onRegisterKeyMappings);
      MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(this::onScreenInit);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
      LOGGER.info("Registering Keyset Forge key mapping");
      openScreenKeyBinding =
          new KeyBinding(
              "keyset.key.open_screen", InputUtil.UNKNOWN_KEY.getCode(), KeyBinding.MISC_CATEGORY);
      event.register(openScreenKeyBinding);
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

      if (openScreenKeyBinding != null) {
        while (openScreenKeyBinding.wasPressed()) {
          requestOpenScreen(client.currentScreen);
        }
      }

      flushPendingOpen(client);
    }

    private void onScreenInit(ScreenEvent.Init.Post event) {
      if (!(event.getScreen() instanceof ControlsOptionsScreen controlsScreen)) {
        return;
      }

      event.getListenersList().stream()
          .filter(ClickableWidget.class::isInstance)
          .map(ClickableWidget.class::cast)
          .filter(ClientOnly::isKeysetControlsButton)
          .forEach(event::removeListener);
      List<ClickableWidget> buttons =
          event.getListenersList().stream()
              .filter(ClickableWidget.class::isInstance)
              .map(ClickableWidget.class::cast)
              .filter(button -> !isKeysetControlsButton(button))
              .toList();
      int[] placement =
          findControlsButtonPlacement(buttons, controlsScreen.width, controlsScreen.height);
      ButtonWidget keysetButton =
          ButtonWidget.builder(
                  Text.translatable("keyset.open"), button -> requestOpenScreen(controlsScreen))
              .dimensions(placement[0], placement[1], CONTROLS_BUTTON_WIDTH, CONTROLS_BUTTON_HEIGHT)
              .build();
      event.addListener(keysetButton);
    }

    private static boolean isKeysetControlsButton(ClickableWidget button) {
      return button instanceof ButtonWidget
          && button.getWidth() == CONTROLS_BUTTON_WIDTH
          && button.getHeight() == CONTROLS_BUTTON_HEIGHT
          && button.getMessage().getString().equals(Text.translatable("keyset.open").getString());
    }

    private void requestOpenScreen(Screen parent) {
      pendingParentScreen = parent;
      openScreenRequested = true;
    }

    private void flushPendingOpen(MinecraftClient client) {
      if (!openScreenRequested || client == null) {
        return;
      }

      openScreenRequested = false;
      Screen parent = pendingParentScreen;
      pendingParentScreen = null;
      client.setScreen(new KeysetScreen(parent != null ? parent : client.currentScreen, SERVICE));
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
