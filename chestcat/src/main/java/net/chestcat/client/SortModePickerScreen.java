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
        int y = this.height / 2 - (ItemSortMode.values().length * 22) / 2;
        for (ItemSortMode mode : ItemSortMode.values()) {
            int fy = y;
            this.addRenderableWidget(Button.builder(Component.literal(mode.getDisplayName()),
                            b -> {
                                onPicked.accept(mode);
                                this.minecraft.setScreen(parent);
                            })
                    .bounds(this.width / 2 - 100, fy, 200, 20)
                    .build());
            y += 22;
        }
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 100, y + 6, 200, 20)
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