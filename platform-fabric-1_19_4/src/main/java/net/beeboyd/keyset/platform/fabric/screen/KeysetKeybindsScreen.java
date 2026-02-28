package net.beeboyd.keyset.platform.fabric.screen;

import java.lang.reflect.Field;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsListWidget;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Version-local bridge into vanilla's keybind editor so Keyset can jump directly to a conflicting
 * bind and optionally arm it for immediate reassignment.
 */
public final class KeysetKeybindsScreen extends KeybindsScreen {
  private final KeysetFabricService service;
  private final String targetBindingId;
  private final boolean reassignOnOpen;
  private final Text helperText;

  public KeysetKeybindsScreen(
      Screen parent,
      GameOptions gameOptions,
      KeysetFabricService service,
      String targetBindingId,
      boolean reassignOnOpen) {
    super(parent, gameOptions);
    this.service = service;
    this.targetBindingId = targetBindingId;
    this.reassignOnOpen = reassignOnOpen;
    this.helperText =
        Text.translatable(
            reassignOnOpen ? "keyset.keybinds.reassign_helper" : "keyset.keybinds.jump_helper");
  }

  @Override
  protected void init() {
    super.init();
    focusTargetBinding();
  }

  @Override
  public void removed() {
    try {
      if (client != null) {
        service.syncActiveProfileFromCurrentManual(client);
      }
    } catch (Exception ignored) {
      // The parent Keyset screen will surface any later load failure.
    }

    super.removed();
  }

  @Override
  public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
    super.render(matrices, mouseX, mouseY, delta);
    drawCenteredTextWithShadow(matrices, textRenderer, helperText, width / 2, 20, 0xB8C7D9);
  }

  private void focusTargetBinding() {
    try {
      ControlsListWidget controlsList = controlsList();
      if (controlsList == null) {
        return;
      }

      KeyBinding targetBinding = null;
      int index = 0;
      for (Object entry : controlsList.children()) {
        if (!(entry instanceof ControlsListWidget.Entry)) {
          index++;
          continue;
        }

        KeyBinding binding = extractBinding(entry);
        if (binding != null && targetBindingId.equals(binding.getTranslationKey())) {
          controlsList.setSelected((ControlsListWidget.Entry) entry);
          controlsList.setScrollAmount(Math.max(0.0D, (index * 24) - 48));
          targetBinding = binding;
          break;
        }
        index++;
      }

      if (reassignOnOpen && targetBinding != null) {
        selectedKeyBinding = targetBinding;
        lastKeyCodeUpdateTime = Util.getMeasuringTimeMs();
      }
    } catch (ReflectiveOperationException ignored) {
      // Falling back to vanilla list behavior is acceptable for this version-local convenience.
    }
  }

  private ControlsListWidget controlsList() throws ReflectiveOperationException {
    Field controlsListField = KeybindsScreen.class.getDeclaredField("controlsList");
    controlsListField.setAccessible(true);
    return (ControlsListWidget) controlsListField.get(this);
  }

  private KeyBinding extractBinding(Object entry) throws ReflectiveOperationException {
    if (!entry.getClass().getName().endsWith("KeyBindingEntry")) {
      return null;
    }

    Field bindingField = entry.getClass().getDeclaredField("binding");
    bindingField.setAccessible(true);
    return (KeyBinding) bindingField.get(entry);
  }
}
