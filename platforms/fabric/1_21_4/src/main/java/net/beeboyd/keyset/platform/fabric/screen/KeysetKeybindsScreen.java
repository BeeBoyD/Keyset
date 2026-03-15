package net.beeboyd.keyset.platform.fabric.screen;

import java.util.Arrays;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsListWidget;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Version-local bridge into vanilla's keybind editor so Keyset can jump directly to a conflicting
 * bind and optionally arm it for immediate reassignment.
 */
public final class KeysetKeybindsScreen extends KeybindsScreen {
  private final KeysetFabricService service;
  private final GameOptions gameOptions;
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
    this.gameOptions = gameOptions;
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
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    super.render(context, mouseX, mouseY, delta);
    context.drawCenteredTextWithShadow(textRenderer, helperText, width / 2, 20, 0xFFB8C7D9);
  }

  private void focusTargetBinding() {
    ControlsListWidget controlsList = controlsList();
    KeyBinding targetBinding = targetBinding();
    int targetIndex = targetBinding == null ? -1 : targetEntryIndex(targetBinding);
    if (controlsList == null || targetIndex < 0 || targetIndex >= controlsList.children().size()) {
      return;
    }

    Object entry = controlsList.children().get(targetIndex);
    if (entry instanceof ControlsListWidget.Entry) {
      controlsList.setSelected((ControlsListWidget.Entry) entry);
      controlsList.setScrollY(Math.max(0.0D, (targetIndex * 24) - 48));
    }

    if (reassignOnOpen) {
      selectedKeyBinding = targetBinding;
      lastKeyCodeUpdateTime = Util.getMeasuringTimeMs();
    }
  }

  private ControlsListWidget controlsList() {
    for (Object child : children()) {
      if (child instanceof ControlsListWidget) {
        return (ControlsListWidget) child;
      }
    }
    return null;
  }

  private KeyBinding targetBinding() {
    for (KeyBinding binding : gameOptions.allKeys) {
      if (targetBindingId.equals(binding.getTranslationKey())) {
        return binding;
      }
    }
    return null;
  }

  private int targetEntryIndex(KeyBinding targetBinding) {
    KeyBinding[] bindings = Arrays.copyOf(gameOptions.allKeys, gameOptions.allKeys.length);
    Arrays.sort(bindings);
    Object currentCategory = null;
    int index = 0;
    for (KeyBinding binding : bindings) {
      Object category = binding.getCategory();
      if (currentCategory == null || !currentCategory.equals(category)) {
        currentCategory = category;
        index++;
      }
      if (binding == targetBinding) {
        return index;
      }
      index++;
    }
    return -1;
  }
}
