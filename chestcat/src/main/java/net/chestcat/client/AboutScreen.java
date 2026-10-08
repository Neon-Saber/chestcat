package net.chestcat.client;

import net.chestcat.CompatInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * About & Compatibility: the real version numbers of what is running, what ChestCat was built for (read from its
 * own metadata), a plain compatible / not-compatible verdict, and a one-click diagnostics copy for bug reports.
 */
@OnlyIn(Dist.CLIENT)
public class AboutScreen extends Screen {

    private final Screen parent;
    private String status = "";

    public AboutScreen(Screen parent) {
        super(Component.literal("About ChestCat"));
        this.parent = parent;
    }

    private record Line(String label, String value, int valueColor) {}

    private List<Line> lines() {
        CompatInfo.Report r = CompatInfo.get();
        List<Line> list = new ArrayList<>();
        list.add(new Line("Version", r.chestcatVersion(), 0xFFFFFFFF));
        list.add(new Line("Minecraft", r.minecraftVersion() + req(r, "Minecraft"), 0xFFFFFFFF));
        list.add(new Line("Mod loader", r.loaderName() + " " + r.loaderVersion() + req(r, "NeoForge"), 0xFFFFFFFF));
        list.add(new Line("Java", r.javaRunning() + (r.javaRequired() > 0 ? "  (needs " + r.javaRequired() + "+)" : ""), 0xFFFFFFFF));
        list.add(new Line("Installed mods", String.valueOf(r.installedMods()), 0xFFFFFFFF));
        list.add(new Line("Works on", "Singleplayer and servers (install on both)", 0xFFFFFFFF));
        list.add(new Line("Status", r.compatible() ? "\u2713 Compatible" : "\u26A0 Not fully compatible",
                r.compatible() ? 0xFF7FD68A : 0xFFFFD27A));
        return list;
    }

    /** " (supports 1.21.1 up to ...)" - shown next to what is installed, from ChestCat's own metadata. */
    private static String req(CompatInfo.Report r, String name) {
        for (CompatInfo.Requirement q : r.requirements()) {
            if (q.name().equals(name)) return "  (supports " + q.supported() + ")";
        }
        return "";
    }

    @Override
    protected void init() {
        int w = 230;
        int x = this.width / 2 - w / 2;
        int by = Math.min(this.height - 26, top() + lines().size() * 13 + 18 + problemHeight());
        this.addRenderableWidget(Button.builder(Component.literal("Copy diagnostics"), b -> {
                    this.minecraft.keyboardHandler.setClipboard(CompatInfo.diagnostics());
                    status = "Copied! Paste it into your bug report.";
                })
                .bounds(x, by, w / 2 - 2, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Copies versions and system info for bug reports. It contains no names, paths or account details.")))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .bounds(x + w / 2 + 2, by, w / 2 - 2, 20).build());
    }

    private int top() {
        return Math.max(46, this.height / 2 - 80);
    }

    private int problemHeight() {
        CompatInfo.Report r = CompatInfo.get();
        int h = 0;
        for (String p : r.problems()) {
            h += this.font.split(Component.literal(p), Math.min(this.width - 24, 330)).size() * 10 + 2;
        }
        return h;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = top();
        graphics.drawCenteredString(this.font, "ChestCat", this.width / 2, top - 30, 0xFFFFD24A);
        graphics.drawCenteredString(this.font, "Inventory sorting, done better.", this.width / 2, top - 18, 0xFF9AA4B2);

        int labelX = this.width / 2 - 115;
        int valueX = labelX + 92;
        int y = top;
        for (Line line : lines()) {
            graphics.drawString(this.font, line.label(), labelX, y, 0xFF8A93A0);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(line.value(), this.width - valueX - 6),
                    valueX, y, line.valueColor());
            y += 13;
        }
        y += 6;
        for (String problem : CompatInfo.get().problems()) {
            for (var part : this.font.split(Component.literal(problem), Math.min(this.width - 24, 330))) {
                graphics.drawCenteredString(this.font, part, this.width / 2, y, 0xFFFFD27A);
                y += 10;
            }
            y += 2;
        }
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(status), this.width / 2, this.height - 12, 0xFFB8E0B8);
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
