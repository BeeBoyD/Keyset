package net.beeboyd.keyset.platform.fabric.screen;

import net.beeboyd.keyset.platform.fabric.KeysetFabricService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Interactive 4-step tutorial shown on first launch or via the ? button. */
public final class TutorialScreen extends Screen {

  private static final int STEPS = 4;
  private static final int PW = 360;
  private static final int PH = 220;

  private static final String[][] CONTENT = {
    {"keyset.tutorial.1.title", "keyset.tutorial.1.body"},
    {"keyset.tutorial.2.title", "keyset.tutorial.2.body"},
    {"keyset.tutorial.3.title", "keyset.tutorial.3.body"},
    {"keyset.tutorial.4.title", "keyset.tutorial.4.body"},
  };

  private final Screen parent;
  private final KeysetFabricService service;
  private int step = 0;

  public TutorialScreen(Screen parent, KeysetFabricService service) {
    super(Text.empty());
    this.parent = parent;
    this.service = service;
  }

  @Override
  protected void init() {
    int px = (width - PW) / 2;
    int py = (height - PH) / 2;

    // Back — hidden on step 0
    ButtonWidget btnBack =
        addDrawableChild(
            ButtonWidget.builder(
                    Text.translatable("keyset.tutorial.back"),
                    b -> {
                      step--;
                      clearChildren();
                      init();
                    })
                .dimensions(px + 20, py + PH - 35, 80, 20)
                .build());
    btnBack.visible = step > 0;

    // Skip — visible only on step 0
    ButtonWidget btnSkip =
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("keyset.tutorial.skip"), b -> finish())
                .dimensions(px + 20, py + PH - 35, 60, 20)
                .build());
    btnSkip.visible = step == 0;

    // Next / Finish
    boolean last = step == STEPS - 1;
    addDrawableChild(
        ButtonWidget.builder(
                Text.translatable(last ? "keyset.tutorial.finish" : "keyset.tutorial.next"),
                b -> {
                  if (last) {
                    finish();
                  } else {
                    step++;
                    clearChildren();
                    init();
                  }
                })
            .dimensions(px + PW - 20 - 80, py + PH - 35, 80, 20)
            .build());
  }

  private void finish() {
    service.setTutorialComplete(client, true);
    client.setScreen(parent);
  }

  @Override
  public void render(DrawContext ctx, int mx, int my, float delta) {
    // Dim layer
    ctx.fill(0, 0, width, height, 0xCC000000);

    int px = (width - PW) / 2;
    int py = (height - PH) / 2;

    // Drop shadow
    ctx.fill(px + 4, py + 4, px + PW + 4, py + PH + 4, 0x60000000);
    // Panel body
    ctx.fill(px, py, px + PW, py + PH, KeysetTheme.BG_SURFACE);
    ctx.drawBorder(px, py, PW, PH, KeysetTheme.ACCENT);
    // Header strip
    ctx.fill(px, py, px + PW, py + 24, KeysetTheme.BG_SIDEBAR);
    ctx.fill(px, py + 24, px + PW, py + 25, KeysetTheme.ACCENT_DIM);

    // Step indicator dots
    int dotSpacing = 16;
    int dotsW = STEPS * dotSpacing;
    int dotX = px + (PW - dotsW) / 2;
    for (int i = 0; i < STEPS; i++) {
      int col = i == step ? KeysetTheme.ACCENT : KeysetTheme.TEXT_MUTED;
      ctx.fill(dotX + i * dotSpacing + 4, py + 8, dotX + i * dotSpacing + 10, py + 14, col);
    }

    // Title
    ctx.drawCenteredTextWithShadow(
        textRenderer,
        Text.translatable(CONTENT[step][0]),
        px + PW / 2,
        py + 30,
        KeysetTheme.TEXT_TITLE);

    // Body (wrapped lines)
    var lines = textRenderer.wrapLines(Text.translatable(CONTENT[step][1]), PW - 40);
    for (int i = 0; i < lines.size(); i++) {
      ctx.drawTextWithShadow(
          textRenderer, lines.get(i), px + 20, py + 50 + i * 14, KeysetTheme.TEXT_BODY);
    }

    // Footer divider
    ctx.fill(px + 8, py + PH - 44, px + PW - 8, py + PH - 43, KeysetTheme.BORDER);

    // Widgets render on top
    super.render(ctx, mx, my, delta);
  }

  @Override
  public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    // Intentionally empty — background handled in render()
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
