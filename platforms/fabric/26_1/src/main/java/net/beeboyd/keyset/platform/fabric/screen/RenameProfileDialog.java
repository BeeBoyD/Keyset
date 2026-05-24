package net.beeboyd.keyset.platform.fabric.screen;

import java.io.IOException;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class RenameProfileDialog extends Screen {

  private static final int PW = 300;
  private static final int PH = 90;

  private final Screen parent;
  private final KeysetFabricService service;
  private final String profileId;
  private KeysetTextFieldWidget nameField;

  public RenameProfileDialog(
      Screen parent, KeysetFabricService service, String profileId, String currentName) {
    super(Component.empty());
    this.parent = parent;
    this.service = service;
    this.profileId = profileId;
    // currentName passed to field after init
    this.pendingName = currentName;
  }

  private final String pendingName;

  @Override
  protected void init() {
    int px = (width - PW) / 2;
    int py = (height - PH) / 2;

    nameField = new KeysetTextFieldWidget(font, px + 10, py + 34, PW - 20, 18, Component.empty());
    nameField.setMaxLength(64);
    nameField.setValue(pendingName);
    nameField.setFocused(true);
    addRenderableWidget(nameField);

    addRenderableWidget(
        KeysetButtonWidget.primary(
            px + PW - 90,
            py + PH - 26,
            80,
            18,
            Component.translatable("keyset.action.save"),
            b -> save()));
    addRenderableWidget(
        KeysetButtonWidget.create(
            px + 10,
            py + PH - 26,
            70,
            18,
            Component.translatable("keyset.action.cancel"),
            b -> minecraft.setScreen(parent)));
  }

  private void save() {
    String name = nameField.getValue().trim();
    if (name.isEmpty()) return;
    try {
      service.renameProfile(minecraft, profileId, name);
    } catch (IOException | IllegalArgumentException e) {
      // silently close — parent screen shows status
    }
    minecraft.setScreen(parent);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
    int px = (width - PW) / 2;
    int py = (height - PH) / 2;
    ctx.fill(px + 3, py + 3, px + PW + 3, py + PH + 3, 0x60000000);
    ctx.fill(px, py, px + PW, py + PH, KeysetTheme.BG_SURFACE);
    ctx.outline(px, py, PW, PH, KeysetTheme.ACCENT);
    ctx.fill(px, py, px + PW, py + 18, KeysetTheme.BG_SIDEBAR);
    ctx.text(
        font,
        Component.translatable("keyset.profile.rename"),
        px + 8,
        py + 5,
        KeysetTheme.TEXT_TITLE,
        true);
    super.extractRenderState(ctx, mx, my, delta);
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    ctx.fill(0, 0, width, height, 0xCC000000);
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (event.key() == 257 || event.key() == 335) { // Enter / numpad Enter
      save();
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public boolean shouldCloseOnEsc() {
    return true;
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
