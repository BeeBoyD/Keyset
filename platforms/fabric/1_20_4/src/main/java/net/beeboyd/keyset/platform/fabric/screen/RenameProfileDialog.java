package net.beeboyd.keyset.platform.fabric.screen;

import java.io.IOException;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class RenameProfileDialog extends Screen {

  private static final int PW = 300;
  private static final int PH = 90;

  private final Screen parent;
  private final KeysetFabricService service;
  private final String profileId;
  private KeysetTextFieldWidget nameField;

  public RenameProfileDialog(
      Screen parent, KeysetFabricService service, String profileId, String currentName) {
    super(Text.empty());
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

    nameField =
        new KeysetTextFieldWidget(textRenderer, px + 10, py + 34, PW - 20, 18, Text.empty());
    nameField.setMaxLength(64);
    nameField.setText(pendingName);
    nameField.setFocused(true);
    addDrawableChild(nameField);

    addDrawableChild(
        KeysetButtonWidget.primary(
            px + PW - 90,
            py + PH - 26,
            80,
            18,
            Text.translatable("keyset.action.save"),
            b -> save()));
    addDrawableChild(
        KeysetButtonWidget.create(
            px + 10,
            py + PH - 26,
            70,
            18,
            Text.translatable("keyset.action.cancel"),
            b -> client.setScreen(parent)));
  }

  private void save() {
    String name = nameField.getText().trim();
    if (name.isEmpty()) return;
    try {
      service.renameProfile(client, profileId, name);
    } catch (IOException | IllegalArgumentException e) {
      // silently close — parent screen shows status
    }
    client.setScreen(parent);
  }

  @Override
  public void render(DrawContext ctx, int mx, int my, float delta) {
    int px = (width - PW) / 2;
    int py = (height - PH) / 2;
    ctx.fill(px + 3, py + 3, px + PW + 3, py + PH + 3, 0x60000000);
    ctx.fill(px, py, px + PW, py + PH, KeysetTheme.BG_SURFACE);
    ctx.drawBorder(px, py, PW, PH, KeysetTheme.ACCENT);
    ctx.fill(px, py, px + PW, py + 18, KeysetTheme.BG_SIDEBAR);
    ctx.drawTextWithShadow(
        textRenderer,
        Text.translatable("keyset.profile.rename"),
        px + 8,
        py + 5,
        KeysetTheme.TEXT_TITLE);
    super.render(ctx, mx, my, delta);
  }

  @Override
  public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    ctx.fill(0, 0, width, height, 0xCC000000);
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (keyCode == 257 || keyCode == 335) { // Enter / numpad Enter
      save();
      return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
  }

  @Override
  public boolean shouldCloseOnEsc() {
    return true;
  }

  @Override
  public void close() {
    client.setScreen(parent);
  }
}
