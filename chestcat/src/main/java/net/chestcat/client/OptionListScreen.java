package net.chestcat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One reusable page layout for every settings page: a title, a short subtitle, a column (or two, when there are
 * many options) of plain-language buttons, an optional hint line and a Back button. Each option shows its current
 * value on the button and explains itself in a tooltip. Pages are described with {@link Row}s, so adding a setting
 * is one line, and every page looks and behaves the same.
 */
@OnlyIn(Dist.CLIENT)
public class OptionListScreen extends Screen {

    /** One option on a page. {@code label} is read again after every click so the button always shows the live value. */
    public record Row(Supplier<String> label, Consumer<OptionListScreen> action, String tooltip, boolean syncAfter) {

        /** A setting that changes a value: after the click the label refreshes and the settings are sent to the server. */
        public static Row setting(Supplier<String> label, Runnable change, String tooltip) {
            return new Row(label, s -> change.run(), tooltip, true);
        }

        /** A button that opens another screen. */
        public static Row open(String label, Supplier<Screen> next, String tooltip) {
            return new Row(() -> label, s -> net.minecraft.client.Minecraft.getInstance().setScreen(next.get()), tooltip, false);
        }

        /** Like {@link #open(String, Supplier, String)}, but the label is re-read each time the page is shown (e.g. "Sort by: Name"). */
        public static Row open(Supplier<String> label, Supplier<Screen> next, String tooltip) {
            return new Row(label, s -> net.minecraft.client.Minecraft.getInstance().setScreen(next.get()), tooltip, false);
        }

        /** A button that does something and wants to show a message on the page. */
        public static Row action(String label, Consumer<OptionListScreen> run, String tooltip) {
            return new Row(() -> label, run, tooltip, false);
        }
    }

    private final Screen parent;
    private final String subtitle;
    private final List<Row> rows;
    private final Supplier<String> hint;
    private final Runnable reset;
    private final boolean singleColumn;
    private String status = "";

    public OptionListScreen(Screen parent, String title, String subtitle, List<Row> rows,
                            Supplier<String> hint, Runnable reset, boolean singleColumn) {
        super(Component.literal(title));
        this.parent = parent;
        this.subtitle = subtitle;
        this.rows = rows;
        this.hint = hint;
        this.reset = reset;
        this.singleColumn = singleColumn;
    }

    public void setStatus(String text) {
        this.status = text == null ? "" : text;
    }

    private int columns() {
        return singleColumn || rows.size() <= 6 ? 1 : 2;
    }

    private int top() {
        int perColumn = (rows.size() + columns() - 1) / columns();
        int contentHeight = perColumn * 22 + 12 + 20; // rows + gap + Back
        return Math.max(40, (this.height - contentHeight) / 2 + 6);
    }

    @Override
    protected void init() {
        int cols = columns();
        int w = cols == 1 ? 230 : 150;
        int gap = 8;
        int totalW = cols * w + (cols - 1) * gap;
        int left = this.width / 2 - totalW / 2;
        int perColumn = (rows.size() + cols - 1) / cols;
        int y0 = top();

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int col = i / perColumn;
            int r = i % perColumn;
            int x = left + col * (w + gap);
            int y = y0 + r * 22;
            Button button = Button.builder(Component.literal(row.label().get()), b -> {
                        row.action().accept(this);
                        if (row.syncAfter()) SortSettings.sync();
                        b.setMessage(Component.literal(row.label().get()));
                    })
                    .bounds(x, y, w, 20)
                    .tooltip(Tooltip.create(Component.literal(row.tooltip())))
                    .build();
            this.addRenderableWidget(button);
        }

        int by = y0 + perColumn * 22 + 12;
        if (reset != null) {
            this.addRenderableWidget(Button.builder(Component.literal("Reset this page"), b -> {
                        reset.run();
                        SortSettings.sync();
                        status = "This page was reset.";
                        this.rebuildWidgets();
                    })
                    .bounds(left, by, totalW / 2 - 2, 20)
                    .tooltip(Tooltip.create(Component.literal("Puts the options on this page back to their defaults.")))
                    .build());
            this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                    .bounds(left + totalW / 2 + 2, by, totalW / 2 - 2, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                    .bounds(left, by, totalW, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = top();
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 28, 0xFFFFD24A);
        graphics.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(subtitle, this.width - 16)),
                this.width / 2, top - 15, 0xFF9AA4B2);

        int perColumn = (rows.size() + columns() - 1) / columns();
        int below = top + perColumn * 22 + 12 + 20 + 8;
        if (hint != null) {
            String text = hint.get();
            if (text != null && !text.isEmpty()) {
                int y = below;
                for (var line : this.font.split(Component.literal(text), Math.min(this.width - 16, 330))) {
                    graphics.drawCenteredString(this.font, line, this.width / 2, y, 0xFF8A93A0);
                    y += 10;
                }
            }
        }
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(status, this.width - 8)),
                    this.width / 2, this.height - 14, 0xFFB8E0B8);
        }
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
