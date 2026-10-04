package net.chestcat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * "What's new" popup: a version progression bar across the top (versions you already had, the ones you just
 * got, and the newest highlighted) above a scrollable list of every version's notes, newest first.
 * New versions are tagged NEW; older ones are dimmed.
 */
@OnlyIn(Dist.CLIENT)
public class UpdateLogScreen extends Screen {

    private record Row(FormattedCharSequence text, int color, int indent) {}

    private static final int LINE = 11;

    private final Screen parent;
    private final String previous; // version the player had before ("" = first time / unknown)
    private final List<Row> rows = new ArrayList<>();
    private int scroll = 0;
    private int viewLeft, viewRight, viewTop, viewBottom;

    public UpdateLogScreen(Screen parent, String previous) {
        super(Component.literal("ChestCat Update"));
        this.parent = parent;
        this.previous = previous == null ? "" : previous;
    }

    private boolean isNew(UpdateLog.Entry e) {
        if (previous.isEmpty()) return e == UpdateLog.ENTRIES.get(0);
        return UpdateLog.compare(e.version(), previous) > 0;
    }

    @Override
    protected void init() {
        int panelW = Math.min(this.width - 24, 340);
        viewLeft = this.width / 2 - panelW / 2;
        viewRight = viewLeft + panelW;
        viewTop = 58;
        viewBottom = Math.max(viewTop + 40, this.height - 34);

        buildRows(panelW - 14);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        this.addRenderableWidget(Button.builder(Component.literal("Got it!"), b -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - 26, 120, 20).build());
    }

    private void buildRows(int textWidth) {
        rows.clear();
        for (UpdateLog.Entry e : UpdateLog.ENTRIES) {
            boolean fresh = isNew(e);
            int headColor = fresh ? 0xFFFFD24A : 0xFF9AA4B2;
            int bodyColor = fresh ? 0xFFEDEDED : 0xFF8A93A0;
            String tag = fresh ? "   NEW" : "";
            addWrapped("v" + e.version() + "  -  " + e.title() + tag, headColor, 0, textWidth);
            addWrapped(e.date(), 0xFF6F7885, 0, textWidth);
            for (String note : e.notes()) {
                addWrapped("\u2022 " + note, bodyColor, 6, textWidth - 6);
            }
            rows.add(new Row(FormattedCharSequence.EMPTY, 0, 0));
        }
    }

    private void addWrapped(String text, int color, int indent, int width) {
        for (FormattedCharSequence line : this.font.split(Component.literal(text), width)) {
            rows.add(new Row(line, color, indent));
        }
    }

    private int contentHeight() {
        return rows.size() * LINE;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (viewBottom - viewTop));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (scrollY * LINE * 2)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Up / Down / Page Up / Page Down scroll the notes.
        int page = viewBottom - viewTop - LINE;
        switch (keyCode) {
            case 265 -> scroll = Math.max(0, scroll - LINE * 2);                  // up
            case 264 -> scroll = Math.min(maxScroll(), scroll + LINE * 2);        // down
            case 266 -> scroll = Math.max(0, scroll - page);                      // page up
            case 267 -> scroll = Math.min(maxScroll(), scroll + page);            // page down
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, "ChestCat has been updated!", this.width / 2, 10, 0xFFFFD24A);
        String sub = previous.isEmpty()
                ? "Here is what's new in v" + UpdateLog.latest()
                : "v" + previous + "  \u2192  v" + UpdateLog.latest();
        graphics.drawCenteredString(this.font, sub, this.width / 2, 23, 0xFFEDEDED);
        drawProgression(graphics, 38);

        // Notes panel
        graphics.fill(viewLeft - 2, viewTop - 2, viewRight + 2, viewBottom + 2, 0xFF3A3F4A);
        graphics.fill(viewLeft - 1, viewTop - 1, viewRight + 1, viewBottom + 1, 0xFF12151B);
        graphics.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        int y = viewTop + 3 - scroll;
        for (Row row : rows) {
            if (y + LINE >= viewTop && y <= viewBottom) {
                graphics.drawString(this.font, row.text(), viewLeft + 7 + row.indent(), y, row.color());
            }
            y += LINE;
        }
        graphics.disableScissor();

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int trackH = viewBottom - viewTop;
            int barH = Math.max(14, trackH * trackH / (trackH + max));
            int barY = viewTop + (int) ((trackH - barH) * (scroll / (double) max));
            graphics.fill(viewRight - 4, viewTop, viewRight - 1, viewBottom, 0xFF22262E);
            graphics.fill(viewRight - 4, barY, viewRight - 1, barY + barH, 0xFF8A93A0);
        }
    }

    /** v1.0.0 > v1.1.0 > v1.2.0: grey = what you already had, green = new for you, gold = the latest. */
    private void drawProgression(GuiGraphics graphics, int y) {
        List<UpdateLog.Entry> ascending = new ArrayList<>(UpdateLog.ENTRIES);
        java.util.Collections.reverse(ascending);

        String sep = "  >  ";
        int total = 0;
        for (int i = 0; i < ascending.size(); i++) {
            total += this.font.width("v" + ascending.get(i).version());
            if (i < ascending.size() - 1) total += this.font.width(sep);
        }
        int x = this.width / 2 - total / 2;
        for (int i = 0; i < ascending.size(); i++) {
            UpdateLog.Entry e = ascending.get(i);
            String label = "v" + e.version();
            boolean latest = e == UpdateLog.ENTRIES.get(0);
            int color = latest ? 0xFFFFD24A : (isNew(e) ? 0xFF7FD68A : 0xFF7C8594);
            if (latest) {
                int w = this.font.width(label);
                graphics.fill(x - 3, y - 2, x + w + 3, y + 10, 0xFF3A2F10);
            }
            graphics.drawString(this.font, label, x, y, color);
            x += this.font.width(label);
            if (i < ascending.size() - 1) {
                graphics.drawString(this.font, sep, x, y, 0xFF55606E);
                x += this.font.width(sep);
            }
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
        graphics.fill(0, 0, this.width, this.height, 0xD0101010);
    }
}
