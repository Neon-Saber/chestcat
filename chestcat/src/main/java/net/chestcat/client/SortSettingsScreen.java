package net.chestcat.client;

import net.chestcat.SortLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Options popup for how sorted items are laid out. Changes are pushed to the server immediately. */
@OnlyIn(Dist.CLIENT)
public class SortSettingsScreen extends Screen {

    private final Screen parent;

    public SortSettingsScreen(Screen parent) {
        super(Component.literal("Sort Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 220;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 40;

        this.addRenderableWidget(Button.builder(layoutLabel(), b -> {
                    SortSettings.layout = SortSettings.layout.next();
                    b.setMessage(layoutLabel());
                    SortSettings.sync();
                })
                .bounds(x, y, w, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(startLabel(), b -> {
                    SortSettings.reverse = !SortSettings.reverse;
                    b.setMessage(startLabel());
                    SortSettings.sync();
                })
                .bounds(x, y, w, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(hotbarLabel(), b -> {
                    SortSettings.includeHotbar = !SortSettings.includeHotbar;
                    b.setMessage(hotbarLabel());
                    SortSettings.sync();
                })
                .bounds(x, y, w, 20).build());
        y += 30;

        this.addRenderableWidget(Button.builder(Component.literal("Done"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(x, y, w, 20).build());
    }

    private static Component layoutLabel() {
        return Component.literal("Fill: " + (SortSettings.layout == SortLayout.ROWS
                ? "Rows (left to right)" : "Columns (top to bottom)"));
    }

    private static Component startLabel() {
        return Component.literal("Start: " + (SortSettings.reverse ? "Bottom-right corner" : "Top-left corner"));
    }

    private static Component hotbarLabel() {
        return Component.literal("Hotbar: " + (SortSettings.includeHotbar ? "Sorted too" : "Left alone"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 65, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
