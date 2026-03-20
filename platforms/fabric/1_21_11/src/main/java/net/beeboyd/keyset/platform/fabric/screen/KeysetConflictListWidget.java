package net.beeboyd.keyset.platform.fabric.screen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.beeboyd.keyset.core.binding.KeysetBindingDescriptor;
import net.beeboyd.keyset.core.conflict.KeysetConflict;
import net.beeboyd.keyset.core.conflict.KeysetConflictGroup;
import net.beeboyd.keyset.core.conflict.KeysetConflictGroupMode;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService.AutoResolveChange;
import net.beeboyd.keyset.platform.fabric.KeysetFabricService.AutoResolvePlan;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

/** Scrollable list used by the Keyset screen for conflicts and auto-resolve previews. */
public final class KeysetConflictListWidget
    extends ElementListWidget<KeysetConflictListWidget.Entry> {
  private static final int DEFAULT_ROW_HEIGHT = 28;

  private final Listener listener;
  private final boolean compactRows;
  private boolean hidden;

  public KeysetConflictListWidget(
      MinecraftClient client, int width, int top, int bottom, int rowHeight, Listener listener) {
    super(client, width, bottom - top, top, rowHeight);
    this.listener = listener;
    compactRows = rowHeight < DEFAULT_ROW_HEIGHT;
  }

  public void showConflicts(
      Collection<KeysetConflictGroup> groups,
      KeysetConflictGroupMode groupMode,
      String preferredBindingId) {
    List<Entry> entries = new ArrayList<Entry>();
    for (KeysetConflictGroup group : groups) {
      String firstBindingId = firstBindingId(group);
      entries.add(
          new GroupEntry(
              this,
              groupMode == KeysetConflictGroupMode.BY_KEY
                  ? Text.translatable(
                      "keyset.group.key", group.getTitle(), group.getConflictCount())
                  : Text.translatable(
                      "keyset.group.category", group.getTitle(), group.getConflictCount()),
              firstBindingId));

      for (KeysetConflict conflict : group.getConflicts()) {
        if (groupMode == KeysetConflictGroupMode.BY_CATEGORY) {
          entries.add(
              new DetailEntry(
                  this,
                  Text.translatable(
                      "keyset.conflict.key_row",
                      conflict.getKeyDisplayName(),
                      Integer.valueOf(conflict.getBindings().size())),
                  firstBindingId(conflict)));
        }

        for (KeysetBindingDescriptor binding : conflict.getBindings()) {
          entries.add(new BindingEntry(this, binding));
        }
      }
    }

    replaceEntries(entries);
    selectPreferredBinding(preferredBindingId);
  }

  public void showPreview(AutoResolvePlan previewPlan) {
    List<Entry> entries = new ArrayList<Entry>();
    entries.add(
        new PreviewEntry(
            this,
            Text.translatable(
                "keyset.resolve.summary",
                Integer.valueOf(previewPlan.getChanges().size()),
                Integer.valueOf(previewPlan.getUnresolvedBindings())),
            true));
    if (previewPlan.getChanges().isEmpty()) {
      entries.add(new PreviewEntry(this, Text.translatable("keyset.resolve.no_preview"), false));
    } else {
      for (AutoResolveChange change : previewPlan.getChanges()) {
        entries.add(
            new PreviewEntry(
                this,
                Text.translatable(
                    "keyset.resolve.change",
                    change.getBindingName(),
                    change.getOldKeyDisplayName(),
                    change.getNewKeyDisplayName()),
                false));
      }
    }

    replaceEntries(entries);
    setSelected(null);
    listener.onBindingSelected(null);
  }

  public void clearContents() {
    clearEntries();
    setSelected(null);
    listener.onBindingSelected(null);
  }

  public void setHidden(boolean hidden) {
    this.hidden = hidden;
    this.visible = !hidden;
    this.active = !hidden;
  }

  public void selectBinding(String bindingId) {
    if (bindingId == null) {
      setSelected(null);
      listener.onBindingSelected(null);
      return;
    }

    for (Entry entry : children()) {
      if (bindingId.equals(entry.bindingId())) {
        setSelected(entry);
        centerScrollOn(entry);
        listener.onBindingSelected(entry.bindingDescriptor());
        return;
      }
    }

    setSelected(null);
    listener.onBindingSelected(null);
  }

  @Override
  public int getRowWidth() {
    return Math.max(40, width - 18);
  }

  @Override
  protected int getScrollbarX() {
    return getRowRight() - 6;
  }

  @Override
  protected void drawMenuListBackground(DrawContext context) {}

  @Override
  protected void drawHeaderAndFooterSeparators(DrawContext context) {}

  @Override
  protected void drawSelectionHighlight(DrawContext context, Entry entry, int alpha) {}

  Text fitText(Text text, int maxWidth) {
    return Text.literal(ellipsize(text.getString(), maxWidth));
  }

  Text fitText(String text, int maxWidth) {
    return Text.literal(ellipsize(text, maxWidth));
  }

  // Keep row content centered from the actual row height so compact mode does not clip text.
  int singleLineTextY(int y, int entryHeight) {
    return y + Math.max(2, (entryHeight - client.textRenderer.fontHeight) / 2);
  }

  int bindingNameY(int y, int entryHeight) {
    int lineGap = compactRows ? 1 : 2;
    int totalTextHeight = (client.textRenderer.fontHeight * 2) + lineGap;
    return y + Math.max(2, (entryHeight - totalTextHeight) / 2);
  }

  int bindingMetaY(int nameY) {
    return nameY + client.textRenderer.fontHeight + (compactRows ? 1 : 2);
  }

  int badgeHeight() {
    return compactRows ? 12 : 14;
  }

  int badgeTextY(int badgeY) {
    return badgeY + Math.max(1, (badgeHeight() - client.textRenderer.fontHeight) / 2);
  }

  float emphasisFor(int y, int entryHeight) {
    int viewportTop = getY();
    int viewportHeight = getHeight();
    int rowCenter = y + (entryHeight / 2);
    int viewportCenter = viewportTop + (viewportHeight / 2);
    float distance =
        Math.abs((float) (rowCenter - viewportCenter)) / (float) Math.max(24, viewportHeight);
    return clamp(1.0F - (distance * 0.7F), 0.58F, 1.0F);
  }

  private void handleSelection(Entry entry) {
    if (entry == null || entry.bindingId() == null) {
      if (entry != null && entry.jumpTargetBindingId() != null) {
        selectBinding(entry.jumpTargetBindingId());
        return;
      }
      setSelected(null);
      listener.onBindingSelected(null);
      return;
    }

    setSelected(entry);
    centerScrollOn(entry);
    listener.onBindingSelected(entry.bindingDescriptor());
  }

  private void selectPreferredBinding(String preferredBindingId) {
    if (preferredBindingId != null) {
      selectBinding(preferredBindingId);
      if (getSelectedOrNull() != null) {
        return;
      }
    }

    for (Entry entry : children()) {
      if (entry.bindingId() != null) {
        setSelected(entry);
        centerScrollOn(entry);
        listener.onBindingSelected(entry.bindingDescriptor());
        return;
      }
    }

    setSelected(null);
    listener.onBindingSelected(null);
  }

  private static int scaleAlpha(int color, float alphaScale) {
    int alpha = (color >>> 24) & 0xFF;
    int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * alphaScale)));
    return (scaledAlpha << 24) | (color & 0x00FFFFFF);
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String firstBindingId(KeysetConflictGroup group) {
    for (KeysetConflict conflict : group.getConflicts()) {
      String firstBindingId = firstBindingId(conflict);
      if (firstBindingId != null) {
        return firstBindingId;
      }
    }
    return null;
  }

  private static String firstBindingId(KeysetConflict conflict) {
    return conflict.getBindings().isEmpty() ? null : conflict.getBindings().get(0).getId();
  }

  private String ellipsize(String value, int maxWidth) {
    if (value == null || client.textRenderer.getWidth(value) <= maxWidth) {
      return value == null ? "" : value;
    }

    String ellipsis = "...";
    String candidate = value;
    while (!candidate.isEmpty() && client.textRenderer.getWidth(candidate + ellipsis) > maxWidth) {
      candidate = candidate.substring(0, candidate.length() - 1);
    }
    return candidate + ellipsis;
  }

  public interface Listener {
    void onBindingSelected(KeysetBindingDescriptor bindingDescriptor);
  }

  abstract static class Entry extends ElementListWidget.Entry<Entry> {
    final KeysetConflictListWidget owner;

    Entry(KeysetConflictListWidget owner) {
      this.owner = owner;
    }

    KeysetBindingDescriptor bindingDescriptor() {
      return null;
    }

    String bindingId() {
      return null;
    }

    String jumpTargetBindingId() {
      return null;
    }

    @Override
    public List<? extends Element> children() {
      return Collections.emptyList();
    }

    @Override
    public List<? extends Selectable> selectableChildren() {
      return Collections.emptyList();
    }
  }

  private static final class GroupEntry extends Entry {
    private final Text text;
    private final String firstBindingId;

    private GroupEntry(KeysetConflictListWidget owner, Text text, String firstBindingId) {
      super(owner);
      this.text = text;
      this.firstBindingId = firstBindingId;
    }

    @Override
    String jumpTargetBindingId() {
      return firstBindingId;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
      owner.handleSelection(this);
      return true;
    }

    @Override
    public void render(
        DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
      int x = getX();
      int y = getY();
      int entryWidth = getWidth();
      int entryHeight = getHeight();
      int contentX = getContentX();
      int contentY = getContentY();
      int contentWidth = getContentWidth();
      float emphasis = owner.emphasisFor(y, entryHeight);
      int offset = Math.round((1.0F - emphasis) * 8.0F);

      context.fill(
          x, y + 3, x + entryWidth, y + entryHeight - 3, scaleAlpha(0xFF202A36, 0.72F * emphasis));
      context.fill(x, y + 3, x + 4, y + entryHeight - 3, scaleAlpha(0xFFF0C870, 0.9F * emphasis));
      context.drawTextWithShadow(
          owner.client.textRenderer,
          owner.fitText(text, Math.max(28, contentWidth - 20)),
          contentX + (owner.compactRows ? 8 : 10) + offset,
          owner.singleLineTextY(contentY, entryHeight),
          scaleAlpha(0xFFF5DEA0, emphasis));
    }
  }

  private static final class DetailEntry extends Entry {
    private final Text text;
    private final String firstBindingId;

    private DetailEntry(KeysetConflictListWidget owner, Text text, String firstBindingId) {
      super(owner);
      this.text = text;
      this.firstBindingId = firstBindingId;
    }

    @Override
    String jumpTargetBindingId() {
      return firstBindingId;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
      owner.handleSelection(this);
      return true;
    }

    @Override
    public void render(
        DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
      int contentX = getContentX();
      int contentY = getContentY();
      int contentWidth = getContentWidth();
      float emphasis = owner.emphasisFor(getY(), getHeight());
      int offset = Math.round((1.0F - emphasis) * 6.0F);
      context.drawTextWithShadow(
          owner.client.textRenderer,
          owner.fitText(text, Math.max(24, contentWidth - 22)),
          contentX + (owner.compactRows ? 12 : 14) + offset,
          owner.singleLineTextY(contentY, getHeight()),
          scaleAlpha(0xFF9FB4C9, 0.95F * emphasis));
    }
  }

  private static final class BindingEntry extends Entry {
    private final KeysetBindingDescriptor bindingDescriptor;

    private BindingEntry(
        KeysetConflictListWidget owner, KeysetBindingDescriptor bindingDescriptor) {
      super(owner);
      this.bindingDescriptor = bindingDescriptor;
    }

    @Override
    KeysetBindingDescriptor bindingDescriptor() {
      return bindingDescriptor;
    }

    @Override
    String bindingId() {
      return bindingDescriptor.getId();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
      owner.handleSelection(this);
      return true;
    }

    @Override
    public void render(
        DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
      int x = getX();
      int y = getY();
      int entryWidth = getWidth();
      int entryHeight = getHeight();
      int contentX = getContentX();
      int contentY = getContentY();
      int contentWidth = getContentWidth();
      boolean selected = owner.getSelectedOrNull() == this;
      float emphasis = owner.emphasisFor(y, entryHeight);
      int contentOffset = Math.round((1.0F - emphasis) * 6.0F);

      int fillColor =
          selected
              ? scaleAlpha(0xFF24384B, 0.92F)
              : hovered
                  ? scaleAlpha(0xFF1A2430, 0.92F * emphasis)
                  : scaleAlpha(0xFF141C26, 0.86F * emphasis);
      int borderColor =
          selected ? 0xFF8FD9F0 : hovered ? scaleAlpha(0xFF51697F, 0.92F) : 0x00000000;
      int accentColor = selected ? 0xFF8FD9F0 : scaleAlpha(0xFF4D5F73, emphasis);
      int nameColor = selected ? 0xFFF8FBFF : scaleAlpha(0xFFE6EEF6, emphasis);
      int metaColor = selected ? 0xFFD0E6F7 : scaleAlpha(0xFF99AEC3, emphasis);

      context.fill(x, y + 2, x + entryWidth, y + entryHeight - 2, fillColor);
      context.fill(x, y + 2, x + 3, y + entryHeight - 2, accentColor);
      if (borderColor != 0) {
        context.drawStrokedRectangle(x, y + 2, entryWidth, entryHeight - 4, borderColor);
      }

      String keyLabel = bindingDescriptor.getKeyDisplayName();
      int badgeWidth =
          clamp(
              owner.client.textRenderer.getWidth(keyLabel) + 14,
              40,
              Math.max(owner.compactRows ? 46 : 52, entryWidth / 3));
      int badgeHeight = owner.badgeHeight();
      int badgeX = x + entryWidth - badgeWidth - 10;
      int badgeY = y + Math.max(2, (entryHeight - badgeHeight) / 2);
      int keyBadgeFill = selected ? 0xFF33526B : scaleAlpha(0xFF273647, 0.92F * emphasis);
      int keyBadgeBorder = selected ? 0xFF8FD9F0 : scaleAlpha(0xFF60788C, emphasis);
      context.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, keyBadgeFill);
      context.drawStrokedRectangle(badgeX, badgeY, badgeWidth, badgeHeight, keyBadgeBorder);
      context.drawCenteredTextWithShadow(
          owner.client.textRenderer,
          owner.fitText(keyLabel, Math.max(22, badgeWidth - 8)),
          badgeX + (badgeWidth / 2),
          owner.badgeTextY(badgeY),
          selected ? 0xFFF4FBFF : scaleAlpha(0xFFD7E4F2, emphasis));

      int textMaxWidth = Math.max(24, badgeX - contentX - 18);
      int nameY = owner.bindingNameY(contentY, entryHeight);
      context.drawTextWithShadow(
          owner.client.textRenderer,
          owner.fitText(bindingDescriptor.getDisplayName(), textMaxWidth),
          contentX + (owner.compactRows ? 6 : 8) + contentOffset,
          nameY,
          nameColor);
      context.drawTextWithShadow(
          owner.client.textRenderer,
          owner.fitText(bindingDescriptor.getCategoryName(), textMaxWidth),
          contentX + (owner.compactRows ? 6 : 8) + contentOffset,
          owner.bindingMetaY(nameY),
          metaColor);
    }
  }

  private static final class PreviewEntry extends Entry {
    private final Text text;
    private final boolean summary;

    private PreviewEntry(KeysetConflictListWidget owner, Text text, boolean summary) {
      super(owner);
      this.text = text;
      this.summary = summary;
    }

    @Override
    public void render(
        DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
      int x = getX();
      int y = getY();
      int entryWidth = getWidth();
      int entryHeight = getHeight();
      int contentX = getContentX();
      int contentY = getContentY();
      int contentWidth = getContentWidth();
      float emphasis = owner.emphasisFor(y, entryHeight);
      int offset = Math.round((1.0F - emphasis) * 4.0F);

      if (summary) {
        context.fill(x, y + 2, x + entryWidth, y + entryHeight - 2, scaleAlpha(0xFF1E3124, 0.88F));
        context.fill(x, y + 2, x + 3, y + entryHeight - 2, 0xFF9DDA84);
      } else if (hovered) {
        context.fill(
            x,
            y + 2,
            x + entryWidth,
            y + entryHeight - 2,
            scaleAlpha(0xFF182029, 0.84F * emphasis));
      }

      context.drawTextWithShadow(
          owner.client.textRenderer,
          owner.fitText(text, Math.max(24, contentWidth - 14)),
          contentX + 8 + offset,
          owner.singleLineTextY(contentY, entryHeight),
          summary ? 0xFFF0F7ED : scaleAlpha(0xFFE2EAF3, emphasis));
    }
  }
}
