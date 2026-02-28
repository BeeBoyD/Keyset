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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

/** Scrollable list used by the Keyset screen for conflicts and auto-resolve previews. */
public final class KeysetConflictListWidget
    extends ElementListWidget<KeysetConflictListWidget.Entry> {
  private static final int ROW_HEIGHT = 24;

  private final Listener listener;

  public KeysetConflictListWidget(
      MinecraftClient client, int width, int top, int bottom, Listener listener) {
    super(client, width, bottom - top, top, bottom, ROW_HEIGHT);
    this.listener = listener;
    setRenderBackground(false);
    setRenderHorizontalShadows(false);
    setRenderSelection(false);
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
                Integer.valueOf(previewPlan.getUnresolvedBindings()))));
    if (previewPlan.getChanges().isEmpty()) {
      entries.add(new PreviewEntry(this, Text.translatable("keyset.resolve.no_preview")));
    } else {
      for (AutoResolveChange change : previewPlan.getChanges()) {
        entries.add(
            new PreviewEntry(
                this,
                Text.translatable(
                    "keyset.resolve.change",
                    change.getBindingName(),
                    change.getOldKeyDisplayName(),
                    change.getNewKeyDisplayName())));
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

  public void selectBinding(String bindingId) {
    if (bindingId == null) {
      setSelected(null);
      listener.onBindingSelected(null);
      return;
    }

    for (Entry entry : children()) {
      if (bindingId.equals(entry.bindingId())) {
        setSelected(entry);
        ensureVisible(entry);
        listener.onBindingSelected(entry.bindingDescriptor());
        return;
      }
    }

    setSelected(null);
    listener.onBindingSelected(null);
  }

  @Override
  public void setSelected(Entry entry) {
    super.setSelected(entry);
  }

  @Override
  public int getRowWidth() {
    return width - 16;
  }

  @Override
  protected int getScrollbarPositionX() {
    return right - 6;
  }

  void handleSelection(Entry entry) {
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
    ensureVisible(entry);
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
        listener.onBindingSelected(entry.bindingDescriptor());
        return;
      }
    }

    setSelected(null);
    listener.onBindingSelected(null);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
      owner.handleSelection(this);
      return true;
    }

    @Override
    public void render(
        DrawContext context,
        int index,
        int y,
        int x,
        int entryWidth,
        int entryHeight,
        int mouseX,
        int mouseY,
        boolean hovered,
        float tickDelta) {
      context.fill(x, y, x + entryWidth, y + entryHeight - 1, 0x6A1C2330);
      context.drawTextWithShadow(owner.client.textRenderer, text, x + 6, y + 8, 0xE8D7A0);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
      owner.handleSelection(this);
      return true;
    }

    @Override
    public void render(
        DrawContext context,
        int index,
        int y,
        int x,
        int entryWidth,
        int entryHeight,
        int mouseX,
        int mouseY,
        boolean hovered,
        float tickDelta) {
      context.drawTextWithShadow(owner.client.textRenderer, text, x + 12, y + 8, 0xB8C7D9);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
      owner.handleSelection(this);
      return true;
    }

    @Override
    public void render(
        DrawContext context,
        int index,
        int y,
        int x,
        int entryWidth,
        int entryHeight,
        int mouseX,
        int mouseY,
        boolean hovered,
        float tickDelta) {
      boolean selected = owner.getSelectedOrNull() == this;
      if (selected) {
        context.fill(x, y, x + entryWidth, y + entryHeight - 1, 0x7A304A68);
        context.drawBorder(x, y, entryWidth, entryHeight - 1, 0xFF8AB3D6);
      } else if (hovered) {
        context.fill(x, y, x + entryWidth, y + entryHeight - 1, 0x4A28313A);
      }

      context.drawTextWithShadow(
          owner.client.textRenderer,
          Text.literal(bindingDescriptor.getDisplayName()),
          x + 8,
          y + 5,
          0xF2F5F8);
      context.drawTextWithShadow(
          owner.client.textRenderer,
          Text.literal(
              bindingDescriptor.getCategoryName()
                  + "  |  "
                  + bindingDescriptor.getKeyDisplayName()),
          x + 8,
          y + 15,
          0xAFC0D4);
    }
  }

  private static final class PreviewEntry extends Entry {
    private final Text text;

    private PreviewEntry(KeysetConflictListWidget owner, Text text) {
      super(owner);
      this.text = text;
    }

    @Override
    public void render(
        DrawContext context,
        int index,
        int y,
        int x,
        int entryWidth,
        int entryHeight,
        int mouseX,
        int mouseY,
        boolean hovered,
        float tickDelta) {
      context.drawTextWithShadow(owner.client.textRenderer, text, x + 6, y + 8, 0xE0E6ED);
    }
  }
}
