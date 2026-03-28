package net.beeboyd.keyset.platform.fabric.screen;

import java.util.Arrays;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Version-local bridge into vanilla's keybind editor so Keyset can jump directly to a conflicting
 * bind and optionally arm it for immediate reassignment.
 */
public final class KeysetKeybindsScreen extends KeyBindsScreen {
  private final KeysetFabricService service;
  private final Options gameOptions;
  private final String targetBindingId;
  private final boolean reassignOnOpen;
  private final Component helperText;

  public KeysetKeybindsScreen(
      Screen parent,
      Options gameOptions,
      KeysetFabricService service,
      String targetBindingId,
      boolean reassignOnOpen) {
    super(parent, gameOptions);
    this.service = service;
    this.gameOptions = gameOptions;
    this.targetBindingId = targetBindingId;
    this.reassignOnOpen = reassignOnOpen;
    this.helperText =
        Component.translatable(
            reassignOnOpen ? "keyset.keybinds.reassign_helper" : "keyset.keybinds.jump_helper");
  }

  @Override
  protected void init() {
    clearWidgets();
    super.init();
    focusTargetBinding();
  }

  @Override
  public void removed() {
    try {
      if (minecraft != null) {
        service.syncActiveProfileFromCurrentManual(minecraft);
      }
    } catch (Exception exception) {
      String detail = exception.getMessage();
      service.reportStatusNotice(
          Component.translatable(
                  "keyset.error.manual_sync_failed",
                  detail == null || detail.trim().isEmpty()
                      ? exception.getClass().getSimpleName()
                      : detail)
              .getString(),
          true);
    }

    super.removed();
  }

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    super.extractRenderState(context, mouseX, mouseY, delta);
    context.centeredText(font, helperText, width / 2, 20, 0xFFB8C7D9);
  }

  private void focusTargetBinding() {
    KeyBindsList controlsList = controlsList();
    KeyMapping targetBinding = targetBinding();
    int targetIndex = targetBinding == null ? -1 : targetEntryIndex(targetBinding);
    if (controlsList == null || targetIndex < 0 || targetIndex >= controlsList.children().size()) {
      return;
    }

    Object entry = controlsList.children().get(targetIndex);
    if (entry instanceof KeyBindsList.Entry) {
      controlsList.setSelected((KeyBindsList.Entry) entry);
      controlsList.setScrollAmount(Math.max(0.0D, (targetIndex * 24) - 48));
    }

    if (reassignOnOpen) {
      selectedKey = targetBinding;
      lastKeySelection = Util.getMillis();
    }
  }

  private KeyBindsList controlsList() {
    for (Object child : children()) {
      if (child instanceof KeyBindsList) {
        return (KeyBindsList) child;
      }
    }
    return null;
  }

  private KeyMapping targetBinding() {
    for (KeyMapping binding : gameOptions.keyMappings) {
      if (targetBindingId.equals(binding.getName())) {
        return binding;
      }
    }
    return null;
  }

  private int targetEntryIndex(KeyMapping targetBinding) {
    KeyMapping[] bindings = Arrays.copyOf(gameOptions.keyMappings, gameOptions.keyMappings.length);
    Arrays.sort(bindings);
    KeyMapping.Category currentCategory = null;
    int index = 0;
    for (KeyMapping binding : bindings) {
      KeyMapping.Category category = binding.getCategory();
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
