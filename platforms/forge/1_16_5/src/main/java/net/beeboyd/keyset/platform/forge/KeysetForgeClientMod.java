package net.beeboyd.keyset.platform.forge;

import static net.beeboyd.keyset.platform.fabric.KeysetTextCompat.translatable;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(KeysetCoreMetadata.MOD_ID)
public final class KeysetForgeClientMod {
  public KeysetForgeClientMod() {
    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientOnly::new);
  }

  private static final class ClientOnly {
    private static final int CONTROLS_BUTTON_WIDTH = 72;
    private static final int CONTROLS_BUTTON_HEIGHT = 20;
    private static final int CONTROLS_BUTTON_MARGIN = 8;
    private static final Logger LOGGER = LogManager.getLogger(KeysetCoreMetadata.MOD_ID);
    private static final KeysetFabricService SERVICE = new KeysetFabricService();

    private final KeyBinding openScreenKeyBinding =
        new KeyBinding(
            "keyset.key.open_screen", InputUtil.UNKNOWN_KEY.getCode(), "key.categories.misc");
    private Screen pendingParentScreen;
    private boolean openScreenRequested;
    private boolean started;

    private ClientOnly() {
      FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
      MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(this::onScreenInit);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
      ClientRegistry.registerKeyBinding(openScreenKeyBinding);
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

      while (openScreenKeyBinding.wasPressed()) {
        requestOpenScreen(client.currentScreen);
      }

      flushPendingOpen(client);
    }

    private void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
      if (!(event.getGui() instanceof ControlsOptionsScreen)) {
        return;
      }
      ControlsOptionsScreen controlsScreen = (ControlsOptionsScreen) event.getGui();

      event.getWidgetList().stream()
          .filter(ClickableWidget.class::isInstance)
          .map(ClickableWidget.class::cast)
          .filter(ClientOnly::isKeysetControlsButton)
          .forEach(event::removeWidget);
      List<? extends ClickableWidget> buttons = event.getWidgetList();
      int[] placement =
          findControlsButtonPlacement(buttons, controlsScreen.width, controlsScreen.height);
      ButtonWidget keysetButton =
          new ButtonWidget(
              placement[0],
              placement[1],
              CONTROLS_BUTTON_WIDTH,
              CONTROLS_BUTTON_HEIGHT,
              translatable("keyset.open"),
              button -> requestOpenScreen(controlsScreen));
      event.addWidget(keysetButton);
    }

    private static boolean isKeysetControlsButton(ClickableWidget button) {
      return button instanceof ButtonWidget
          && button.getWidth() == CONTROLS_BUTTON_WIDTH
          && button.getHeight() == CONTROLS_BUTTON_HEIGHT
          && button.getMessage().getString().equals(translatable("keyset.open").getString());
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
      client.openScreen(new KeysetScreen(parent != null ? parent : client.currentScreen, SERVICE));
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
        int otherX = button.x;
        int otherY = button.y;
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
