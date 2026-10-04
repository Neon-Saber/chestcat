package net.chestcat.client;

import net.chestcat.GroupingConfig;
import net.chestcat.ItemCategory;
import net.chestcat.ItemGrouping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabbed screen for how finely auto-sort splits items between chests: ores (raw vs refined),
 * wood (per type), stone/deepslate (per family), and a per-category on/off switch.
 * Every change is pushed to the server immediately.
 */
@OnlyIn(Dist.CLIENT)
public class CategorySplitScreen extends Screen {

    private enum Tab {
        ORES("Ores", ItemCategory.ORES_AND_INGOTS, "Raw ores and raw metals vs. ingots, nuggets and gems"),
        WOOD("Wood", ItemCategory.WOOD, "Logs, planks, slabs and more, grouped per wood type"),
        STONE("Stone", ItemCategory.STONE_AND_DEEPSLATE, "Stone, cobblestone, deepslate and friends, per family"),
        CATEGORIES("Categories", null, "OFF = items of that category are treated as Misc");

        final String label;
        final ItemCategory category;
        final String hint;

        Tab(String label, ItemCategory category, String hint) {
            this.label = label;
            this.category = category;
            this.hint = hint;
        }
    }

    private final Screen parent;
    private Tab tab = Tab.ORES;

    public CategorySplitScreen(Screen parent) {
        super(Component.literal("Category Splitting"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int tabW = 74;
        int tabGap = 4;
        Tab[] tabs = Tab.values();
        int tx = this.width / 2 - (tabs.length * tabW + (tabs.length - 1) * tabGap) / 2;
        for (Tab t : tabs) {
            Button tabButton = Button.builder(Component.literal(t.label), b -> {
                        this.tab = t;
                        this.rebuildWidgets();
                    })
                    .bounds(tx, 24, tabW, 20).build();
            tabButton.active = t != this.tab;
            this.addRenderableWidget(tabButton);
            tx += tabW + tabGap;
        }

        int doneY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 100, doneY, 200, 20).build());

        if (tab == Tab.CATEGORIES) {
            buildCategoriesTab(doneY);
        } else {
            buildSplitTab(tab.category, doneY);
        }
    }

    private void buildSplitTab(ItemCategory category, int doneY) {
        boolean splitOn = GroupingSettings.current.splits(category);
        int top = 60;

        int toggleW = 220;
        this.addRenderableWidget(Button.builder(
                        Component.literal("Split " + tab.label + ": " + (splitOn ? "ON" : "OFF")),
                        b -> update(GroupingSettings.current.withSplit(category, !splitOn)))
                .bounds(this.width / 2 - toggleW / 2, top, toggleW, 20).build());

        List<String> subs = ItemGrouping.pickerSubKeysFor(category);
        int cols = subs.size() > 4 ? 2 : 1;
        int buttonW = cols == 2 ? 156 : 220;
        int gap = 6;
        int startY = top + 28;
        int rows = (subs.size() + cols - 1) / cols;
        int rowH = rowHeight(startY, doneY, rows);
        int gridX = this.width / 2 - (cols * buttonW + (cols - 1) * gap) / 2;

        for (int i = 0; i < subs.size(); i++) {
            String sub = subs.get(i);
            boolean merged = GroupingSettings.current.isMerged(category, sub);
            Button b = Button.builder(
                            Component.literal(ItemGrouping.capitalize(sub) + ": " + (merged ? "shared chest" : "own chest")),
                            btn -> update(GroupingSettings.current.withMerged(category, sub, !merged)))
                    .bounds(gridX + (i % cols) * (buttonW + gap), startY + (i / cols) * rowH, buttonW, rowH - 2)
                    .build();
            b.active = splitOn;
            this.addRenderableWidget(b);
        }
    }

    private void buildCategoriesTab(int doneY) {
        List<ItemCategory> categories = new ArrayList<>();
        for (ItemCategory c : ItemCategory.values()) {
            if (c != ItemCategory.MISC && c != ItemCategory.CUSTOM) categories.add(c); // Misc is where disabled categories go
        }

        int cols = 2;
        int buttonW = 150;
        int gap = 6;
        int startY = 60;
        int rows = (categories.size() + cols - 1) / cols;
        int rowH = rowHeight(startY, doneY, rows);
        int gridX = this.width / 2 - (cols * buttonW + (cols - 1) * gap) / 2;

        for (int i = 0; i < categories.size(); i++) {
            ItemCategory category = categories.get(i);
            boolean disabled = GroupingSettings.current.isDisabled(category);
            this.addRenderableWidget(Button.builder(
                            Component.literal(category.getDisplayName() + ": " + (disabled ? "OFF" : "ON")),
                            b -> update(GroupingSettings.current.withDisabled(category, !disabled)))
                    .bounds(gridX + (i % cols) * (buttonW + gap), startY + (i / cols) * rowH, buttonW, rowH - 2)
                    .build());
        }
    }

    /** 22px rows normally, squeezed down (to a floor of 16) when the window is short. */
    private static int rowHeight(int startY, int doneY, int rows) {
        int available = doneY - 6 - startY;
        return Math.max(16, Math.min(22, available / Math.max(1, rows)));
    }

    private void update(GroupingConfig.Settings next) {
        GroupingSettings.current = next;
        GroupingSettings.sync();
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(this.font, tab.hint, this.width / 2, 47, 0xFFAAAAAA);
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
        // ChestCat: always a flat dim overlay, never blurred, regardless of any blur setting/mod.
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
}
