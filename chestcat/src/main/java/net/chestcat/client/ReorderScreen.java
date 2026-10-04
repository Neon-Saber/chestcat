package net.chestcat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A paged "put these in your order" list with up / down / to-top buttons, used for the category order and the
 * mod order. Every change is committed immediately through {@code onChange} (which saves and syncs), so there is
 * nothing to confirm; Reset hands control back to the caller.
 */
@OnlyIn(Dist.CLIENT)
public class ReorderScreen extends Screen {

    public record Item(String id, String label) {}

    private final Screen parent;
    private final List<Item> items;
    private final Consumer<List<String>> onChange;
    private final Runnable onReset;
    private final String hint;
    private int page = 0;

    public ReorderScreen(Screen parent, String title, String hint, List<Item> items,
                         Consumer<List<String>> onChange, Runnable onReset) {
        super(Component.literal(title));
        this.parent = parent;
        this.hint = hint;
        this.items = new ArrayList<>(items);
        this.onChange = onChange;
        this.onReset = onReset;
    }

    private int rowsPerPage() {
        return Math.max(4, Math.min(12, (this.height - 112) / 22));
    }

    private int pages() {
        return Math.max(1, (items.size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private int top() {
        return 44;
    }

    private void commit() {
        List<String> ids = new ArrayList<>();
        for (Item i : items) ids.add(i.id());
        onChange.accept(ids);
        this.rebuildWidgets();
    }

    private void move(int index, int target) {
        if (target < 0 || target >= items.size() || target == index) return;
        Item it = items.remove(index);
        items.add(target, it);
        commit();
    }

    @Override
    protected void init() {
        int rows = rowsPerPage();
        page = Math.max(0, Math.min(page, pages() - 1));
        int w = 250;
        int x = this.width / 2 - w / 2;
        int y = top();

        int first = page * rows;
        int last = Math.min(items.size(), first + rows);
        for (int i = first; i < last; i++) {
            final int index = i;
            Item item = items.get(i);
            Button up = Button.builder(Component.literal("^"), b -> move(index, index - 1))
                    .bounds(x, y, 20, 20).build();
            up.active = i > 0;
            this.addRenderableWidget(up);
            Button down = Button.builder(Component.literal("v"), b -> move(index, index + 1))
                    .bounds(x + 22, y, 20, 20).build();
            down.active = i < items.size() - 1;
            this.addRenderableWidget(down);
            Button topBtn = Button.builder(Component.literal("Top"), b -> move(index, 0))
                    .bounds(x + 44, y, 30, 20)
                    .tooltip(Tooltip.create(Component.literal("Move to the very top.")))
                    .build();
            topBtn.active = i > 0;
            this.addRenderableWidget(topBtn);
            Button label = Button.builder(Component.literal((i + 1) + ". " + item.label()), b -> {})
                    .bounds(x + 76, y, w - 76, 20).build();
            label.active = false;
            this.addRenderableWidget(label);
            y += 22;
        }

        int by = top() + rows * 22 + 6;
        Button prev = Button.builder(Component.literal("<"), b -> {
                    page--;
                    this.rebuildWidgets();
                }).bounds(x, by, 20, 20).build();
        prev.active = page > 0;
        this.addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), b -> {
                    page++;
                    this.rebuildWidgets();
                }).bounds(x + w - 20, by, 20, 20).build();
        next.active = page < pages() - 1;
        this.addRenderableWidget(next);
        this.addRenderableWidget(Button.builder(Component.literal("Reset to default"), b -> {
                    onReset.run();
                    this.onClose();
                })
                .bounds(x + 24, by, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(x + w - 24 - 100, by, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(hint, this.width - 16)),
                this.width / 2, 26, 0xFFB8C4D8);
        int rows = rowsPerPage();
        graphics.drawCenteredString(this.font, Component.literal("Page " + (page + 1) + " / " + pages()),
                this.width / 2, top() + rows * 22 + 12, 0xFF9AA4B2);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
}
