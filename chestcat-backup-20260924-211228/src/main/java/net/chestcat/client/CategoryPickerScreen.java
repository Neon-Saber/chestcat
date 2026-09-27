package net.chestcat.client;

import net.chestcat.CustomGroups;
import net.chestcat.ItemCategory;
import net.chestcat.ItemGrouping;
import net.chestcat.network.AssignCategoryPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Assigns a category to a chest. Ores / Wood / Stone open a second page to pick a sub-type
 * (or "Any" for the whole category), which is what lets several chests of one category
 * each take a different sub-type once Category Splitting is on.
 */
public class CategoryPickerScreen extends Screen {

    private final BlockPos pos;
    private final Screen parent;
    /** Non-null on the second page: the category whose sub-types are being listed. */
    private final ItemCategory subOf;

    public CategoryPickerScreen(BlockPos pos, Screen parent) {
        this(pos, parent, null);
    }

    private CategoryPickerScreen(BlockPos pos, Screen parent, ItemCategory subOf) {
        super(Component.literal(subOf == null ? "Assign Category" : "Assign " + subOf.getDisplayName() + " Type"));
        this.pos = pos;
        this.parent = parent;
        this.subOf = subOf;
    }

    @Override
    protected void init() {
        // Each option is {button label, value sent to the server}.
        List<String[]> options = new ArrayList<>();
        if (subOf == null) {
            for (ItemCategory category : ItemCategory.values()) {
                if (category == ItemCategory.CUSTOM) continue; // has its own entry below
                String label = category.getDisplayName() + (ItemGrouping.isSplittable(category) ? " >" : "");
                options.add(new String[]{label, category.name()});
            }
            options.add(new String[]{"Custom Groups >", ItemCategory.CUSTOM.name()});
        } else if (subOf == ItemCategory.CUSTOM) {
            CustomGroups.refresh();
            for (String name : CustomGroups.names()) {
                options.add(new String[]{name, ItemCategory.CUSTOM.name() + ":" + name});
            }
        } else {
            options.add(new String[]{"Any " + subOf.getDisplayName(), subOf.name()});
            for (String sub : ItemGrouping.pickerSubKeysFor(subOf)) {
                options.add(new String[]{ItemGrouping.capitalize(sub), subOf.name() + ":" + sub});
            }
        }

        int columns = 3;
        int gap = 6;
        int buttonWidth = Math.min(130, (this.width - 40) / columns - gap);
        int buttonHeight = 20;

        int startX = this.width / 2 - (columns * (buttonWidth + gap) - gap) / 2;
        int startY = 40;

        for (int i = 0; i < options.size(); i++) {
            String label = options.get(i)[0];
            String value = options.get(i)[1];
            int col = i % columns;
            int row = i / columns;
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> pick(value))
                    .bounds(startX + col * (buttonWidth + gap), startY + row * (buttonHeight + gap), buttonWidth, buttonHeight)
                    .build());
        }

        int resetRow = (options.size() + columns - 1) / columns;
        this.addRenderableWidget(Button.builder(Component.literal("Reset to Auto-Detect"), b -> assign("AUTO"))
                .bounds(this.width / 2 - 100, startY + resetRow * (buttonHeight + gap) + 10, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal(subOf == null ? "Cancel" : "Back"), b -> this.onClose())
                .bounds(this.width / 2 - 100, startY + resetRow * (buttonHeight + gap) + 36, 200, 20)
                .build());
    }

    /** First page: a splittable category opens the sub-type page; everything else assigns immediately. */
    private void pick(String value) {
        if (subOf == null && (value.equals(ItemCategory.CUSTOM.name())
                || ItemGrouping.isSplittable(ItemCategory.valueOf(value)))) {
            this.minecraft.setScreen(new CategoryPickerScreen(pos, parent, ItemCategory.valueOf(value)));
        } else {
            assign(value);
        }
    }

    private void assign(String categoryName) {
        PacketDistributor.sendToServer(new AssignCategoryPayload(pos, categoryName));
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (subOf != null) {
            this.minecraft.setScreen(new CategoryPickerScreen(pos, parent, null));
        } else {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // ChestCat: always a flat dim overlay, never blurred, regardless of any blur setting/mod.
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
}
