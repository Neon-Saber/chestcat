package net.chestcat.client;

import net.chestcat.ItemCategory;
import net.chestcat.network.AssignCategoryPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class CategoryPickerScreen extends Screen {

    private final BlockPos pos;
    private final Screen parent;

    public CategoryPickerScreen(BlockPos pos, Screen parent) {
        super(Component.literal("Assign Category"));
        this.pos = pos;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int columns = 3;
        int buttonWidth = 130;
        int buttonHeight = 20;
        int gap = 6;
        ItemCategory[] categories = ItemCategory.values();

        int startX = this.width / 2 - (columns * (buttonWidth + gap)) / 2;
        int startY = 40;

        for (int i = 0; i < categories.length; i++) {
            ItemCategory category = categories[i];
            int col = i % columns;
            int row = i / columns;
            this.addRenderableWidget(Button.builder(Component.literal(category.getDisplayName()), b -> assign(category.name()))
                    .bounds(startX + col * (buttonWidth + gap), startY + row * (buttonHeight + gap), buttonWidth, buttonHeight)
                    .build());
        }

        int resetRow = (categories.length + columns - 1) / columns;
        this.addRenderableWidget(Button.builder(Component.literal("Reset to Auto-Detect"), b -> assign("AUTO"))
                .bounds(this.width / 2 - 100, startY + resetRow * (buttonHeight + gap) + 10, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(this.width / 2 - 100, startY + resetRow * (buttonHeight + gap) + 36, 200, 20)
                .build());
    }

    private void assign(String categoryName) {
        PacketDistributor.sendToServer(new AssignCategoryPayload(pos, categoryName));
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                "Chest at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
                this.width / 2, 24, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
