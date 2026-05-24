package net.beeboyd.keyset.platform.fabric.screen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.beeboyd.keyset.core.autoswitch.AutoSwitchRule;
import net.beeboyd.keyset.core.profile.KeysetProfile;
import net.beeboyd.keyset.core.profile.KeysetProfilesConfig;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
    super(Component.empty());
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
      KeysetProfilesConfig cfg = service.getConfig(minecraft);
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
        new KeysetTextFieldWidget(font, dx + 8, startY + 15, DW - 16, 18, Component.empty());
    patternField.setHint(Component.literal("e.g. hypixel.net or *.server.com"));
    patternField.setMaxLength(128);
    addRenderableWidget(patternField);

    profileCycleBtn =
        KeysetButtonWidget.create(
            dx + 8,
            startY + 57,
            DW - 16,
            18,
            Component.literal(currentProfileName()),
            b -> cycleProfile());
    addRenderableWidget(profileCycleBtn);

    addRenderableWidget(
        KeysetButtonWidget.create(
            dx + 8,
            dy + DH - 28,
            84,
            20,
            Component.translatable("keyset.action.cancel"),
            b -> onClose()));

    addRenderableWidget(
        KeysetButtonWidget.primary(
            dx + DW - 8 - 84,
            dy + DH - 28,
            84,
            20,
            Component.translatable("keyset.action.save"),
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
    profileCycleBtn.setMessage(Component.literal(currentProfileName()));
  }

  private void saveAndClose() {
    String pattern = patternField != null ? patternField.getValue().trim() : "";
    if (pattern.isEmpty() || selectedProfileId == null) {
      onClose();
      return;
    }
    try {
      service.addAutoSwitchRule(minecraft, new AutoSwitchRule(pattern, selectedProfileId));
    } catch (IOException | IllegalArgumentException ignored) {
    }
    onClose();
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    ctx.fill(0, 0, width, height, 0xCC000000);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    extractBackground(ctx, mouseX, mouseY, delta);

    int dx = (width - DW) / 2;
    int dy = (height - DH) / 2;

    ctx.fill(dx + 4, dy + 4, dx + DW + 4, dy + DH + 4, 0x60000000);
    ctx.fill(dx, dy, dx + DW, dy + DH, KeysetTheme.BG_SURFACE);
    ctx.outline(dx, dy, DW, DH, KeysetTheme.ACCENT);
    ctx.fill(dx, dy, dx + DW, dy + 22, KeysetTheme.BG_SIDEBAR);
    ctx.fill(dx, dy + 22, dx + DW, dy + 23, KeysetTheme.ACCENT_DIM);

    ctx.text(
        font,
        Component.literal("Add Auto-Switch Rule"),
        dx + 8,
        dy + 7,
        KeysetTheme.TEXT_MUTED,
        true);
    int sY = dy + 30;
    ctx.text(font, Component.literal("Server pattern"), dx + 8, sY, KeysetTheme.TEXT_BODY, true);
    ctx.text(
        font, Component.literal("Switch to profile"), dx + 8, sY + 47, KeysetTheme.TEXT_BODY, true);

    ctx.fill(dx + 8, dy + DH - 34, dx + DW - 8, dy + DH - 33, KeysetTheme.BORDER);

    super.extractRenderState(ctx, mouseX, mouseY, delta);
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
