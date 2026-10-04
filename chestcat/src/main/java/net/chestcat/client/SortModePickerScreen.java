package net.chestcat.client;

import net.chestcat.ItemSortMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

/** Small popup listing every ItemSortMode as a clickable button. */
@OnlyIn(Dist.CLIENT)
public class SortModePickerScreen extends Screen {

    private final Screen parent;
    private final Consumer<ItemSortMode> onPicked;

    public SortModePickerScreen(Screen parent, Consumer<ItemSortMode> onPicked) {
        super(Component.literal("Sort Mode"));
        this.parent = parent;
        this.onPicked = onPicked;
    }

    @Override
    protected void init() {
        ItemSortMode[] modes = ItemSortMode.values();
        int n = modes.length;

        // Flow into as many columns as the screen height needs, so the list never runs off a small GUI.
        int perCol = Math.max(4, Math.min(n, (this.height - 50) / 22));
        int cols = (n + perCol - 1) / perCol;
        int colW = 190;
        int gap = 6;
        int totalW = cols * colW + (cols - 1) * gap;
        int x0 = this.width / 2 - totalW / 2;
        int rows = Math.min(n, perCol);
        int y0 = Math.max(6, this.height / 2 - (rows * 22 + 28) / 2);

        for (int i = 0; i < n; i++) {
            ItemSortMode mode = modes[i];
            int col = i / perCol;
            int row = i % perCol;
            this.addRenderableWidget(Button.builder(Component.literal(mode.getDisplayName()),
                            b -> {
                                onPicked.accept(mode);
                                this.minecraft.setScreen(parent);
                            })
                    .bounds(x0 + col * (colW + gap), y0 + row * 22, colW, 20)
                    .build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 100, y0 + rows * 22 + 6, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // ChestCat: always a flat dim overlay, never blurred, regardless of any blur setting/mod.
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
}