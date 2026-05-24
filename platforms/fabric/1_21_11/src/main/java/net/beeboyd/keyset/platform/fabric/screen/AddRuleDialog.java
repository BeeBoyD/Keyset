package net.beeboyd.keyset.platform.fabric.screen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.beeboyd.keyset.core.autoswitch.AutoSwitchRule;
import net.beeboyd.keyset.core.profile.KeysetProfile;
import net.beeboyd.keyset.core.profile.KeysetProfilesConfig;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Modal dialog for adding a new auto-switch rule. */
public final class AddRuleDialog extends Screen {

  private static final int DW = 320;
  private static final int DH = 148;

  private final Screen parent;
  private final KeysetFabricService service;

  private KeysetTextFieldWidget patternField;
  private KeysetButtonWidget profileCycleBtn;

  private final List<String> profileIds = new ArrayList<>();
  private final List<String> profileNames = new ArrayList<>();
  private String selectedProfileId;

  public AddRuleDialog(Screen parent, KeysetFabricService service) {
    super(Text.empty());
    this.parent = parent;
    this.service = service;
  }

  @Override
  protected void init() {
    int dx = (width - DW) / 2;
    int dy = (height - DH) / 2;

    profileIds.clear();
    profileNames.clear();
    try {
      KeysetProfilesConfig cfg = service.getConfig(client);
      for (Map.Entry<String, KeysetProfile> e : cfg.getProfiles().entrySet()) {
        profileIds.add(e.getKey());
        profileNames.add(e.getValue().getName());
      }
    } catch (IOException ignored) {
    }
    if (selectedProfileId == null || !profileIds.contains(selectedProfileId)) {
      selectedProfileId = profileIds.isEmpty() ? null : profileIds.get(0);
    }

    int startY = dy + 30;
    patternField =
        new KeysetTextFieldWidget(textRenderer, dx + 8, startY + 15, DW - 16, 18, Text.empty());
    patternField.setPlaceholder(Text.literal("e.g. hypixel.net or *.server.com"));
    patternField.setMaxLength(128);
    addDrawableChild(patternField);

    profileCycleBtn =
        KeysetButtonWidget.create(
            dx + 8,
            startY + 57,
            DW - 16,
            18,
            Text.literal(currentProfileName()),
            b -> cycleProfile());
    addDrawableChild(profileCycleBtn);

    addDrawableChild(
        KeysetButtonWidget.create(
            dx + 8, dy + DH - 28, 84, 20, Text.translatable("keyset.action.cancel"), b -> close()));

    addDrawableChild(
        KeysetButtonWidget.primary(
            dx + DW - 8 - 84,
            dy + DH - 28,
            84,
            20,
            Text.translatable("keyset.action.save"),
            b -> saveAndClose()));
  }

  private String currentProfileName() {
    if (selectedProfileId == null || profileIds.isEmpty()) return "No profiles";
    int idx = profileIds.indexOf(selectedProfileId);
    return idx >= 0 ? profileNames.get(idx) : profileNames.get(0);
  }

  private void cycleProfile() {
    if (profileIds.isEmpty()) return;
    int idx = profileIds.indexOf(selectedProfileId);
    selectedProfileId = profileIds.get((idx + 1) % profileIds.size());
    profileCycleBtn.setMessage(Text.literal(currentProfileName()));
  }

  private void saveAndClose() {
    String pattern = patternField != null ? patternField.getText().trim() : "";
    if (pattern.isEmpty() || selectedProfileId == null) {
      close();
      return;
    }
    try {
      service.addAutoSwitchRule(client, new AutoSwitchRule(pattern, selectedProfileId));
    } catch (IOException | IllegalArgumentException ignored) {
    }
    close();
  }

  @Override
  public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    ctx.fill(0, 0, width, height, 0xCC000000);
  }

  @Override
  public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
    renderBackground(ctx, mouseX, mouseY, delta);

    int dx = (width - DW) / 2;
    int dy = (height - DH) / 2;

    ctx.fill(dx + 4, dy + 4, dx + DW + 4, dy + DH + 4, 0x60000000);
    ctx.fill(dx, dy, dx + DW, dy + DH, KeysetTheme.BG_SURFACE);
    ctx.drawStrokedRectangle(dx, dy, DW, DH, KeysetTheme.ACCENT);
    ctx.fill(dx, dy, dx + DW, dy + 22, KeysetTheme.BG_SIDEBAR);
    ctx.fill(dx, dy + 22, dx + DW, dy + 23, KeysetTheme.ACCENT_DIM);

    ctx.drawTextWithShadow(
        textRenderer, Text.literal("Add Auto-Switch Rule"), dx + 8, dy + 7, KeysetTheme.TEXT_MUTED);
    int sY = dy + 30;
    ctx.drawTextWithShadow(
        textRenderer, Text.literal("Server pattern"), dx + 8, sY, KeysetTheme.TEXT_BODY);
    ctx.drawTextWithShadow(
        textRenderer, Text.literal("Switch to profile"), dx + 8, sY + 47, KeysetTheme.TEXT_BODY);

    ctx.fill(dx + 8, dy + DH - 34, dx + DW - 8, dy + DH - 33, KeysetTheme.BORDER);

    super.render(ctx, mouseX, mouseY, delta);
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
