package net.chestcat.client;

import net.chestcat.SortLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Options popup for how sorted items are laid out and ordered. Changes are pushed to the server immediately. */
@OnlyIn(Dist.CLIENT)
public class SortSettingsScreen extends Screen {

    private final Screen parent;

    public SortSettingsScreen(Screen parent) {
        super(Component.literal("Sort Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int colW = 150;
        int gap = 8;
        int left = this.width / 2 - colW - gap / 2;
        int right = this.width / 2 + gap / 2;
        int y0 = Math.max(28, this.height / 2 - 92);
        int step = 22;

        // ---- left column: how the sorted result is laid out
        int y = y0;
        this.addRenderableWidget(Button.builder(layoutLabel(), b -> {
                    SortSettings.layout = SortSettings.layout.next();
                    b.setMessage(layoutLabel());
                    SortSettings.sync();
                })
                .bounds(left, y, colW, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(startLabel(), b -> {
                    SortSettings.reverse = !SortSettings.reverse;
                    b.setMessage(startLabel());
                    SortSettings.sync();
                })
                .bounds(left, y, colW, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(hotbarLabel(), b -> {
                    SortSettings.includeHotbar = !SortSettings.includeHotbar;
                    b.setMessage(hotbarLabel());
                    SortSettings.sync();
                })
                .bounds(left, y, colW, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Sort Hotbar OFF: the hotbar is never rearranged. Middle-click a slot to lock just that slot "
                                + "(Shift+middle-click locks the row).")))
                .build());
        y += step;

        this.addRenderableWidget(Button.builder(armorSlotLabel(), b -> {
                    SortSettings.groupArmorBySlot = !SortSettings.groupArmorBySlot;
                    b.setMessage(armorSlotLabel());
                    SortSettings.sync();
                })
                .bounds(left, y, colW, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(enchantedLabel(), b -> {
                    SortSettings.floatEnchantedFirst = !SortSettings.floatEnchantedFirst;
                    b.setMessage(enchantedLabel());
                    SortSettings.sync();
                })
                .bounds(left, y, colW, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(tiebreakLabel(), b -> {
                    SortSettings.tiebreakPriority = SortSettings.tiebreakPriority.next();
                    b.setMessage(tiebreakLabel());
                    SortSettings.sync();
                })
                .bounds(left, y, colW, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(indicatorLabel(), b -> {
                    ChestCatIndicatorRenderer.enabled = !ChestCatIndicatorRenderer.enabled;
                    b.setMessage(indicatorLabel());
                })
                .bounds(left, y, colW, 20).build());

        // ---- right column: the bigger features
        y = y0;
        this.addRenderableWidget(Button.builder(Component.literal("Sort Chain..."),
                        b -> this.minecraft.setScreen(new SortChainScreen(this)))
                .bounds(right, y, colW, 20)
                .tooltip(Tooltip.create(Component.literal("Build your own multi-level sort, e.g. Category > Mod > Rarity > Name.")))
                .build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Presets..."),
                        b -> this.minecraft.setScreen(new PresetPickerScreen(this)))
                .bounds(right, y, colW, 20).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Favorites & Interface..."),
                        b -> this.minecraft.setScreen(new ChestCatOptionsScreen(this)))
                .bounds(right, y, colW, 20).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Ignore Rules..."),
                        b -> this.minecraft.setScreen(new ExclusionsScreen(this)))
                .bounds(right, y, colW, 20)
                .tooltip(Tooltip.create(Component.literal("Items, mods, categories or tags that sorting must never move.")))
                .build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Category Splitting..."),
                        b -> this.minecraft.setScreen(new CategorySplitScreen(this)))
                .bounds(right, y, colW, 20).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Saved Profiles..."),
                        b -> this.minecraft.setScreen(new SortProfilesScreen(this)))
                .bounds(right, y, colW, 20).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Behavior & Order..."),
                        b -> this.minecraft.setScreen(new BehaviorScreen(this)))
                .bounds(right, y, colW, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Hotbar handling, empty slots, A-Z direction, auto-sort, and your own category / mod order.")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(left, y0 + 7 * step + 6, colW * 2 + gap, 20).build());
    }

    private static Component layoutLabel() {
        return Component.literal("Fill: " + (SortSettings.layout == SortLayout.ROWS
                ? "Rows" : "Columns"));
    }

    private static Component startLabel() {
        return Component.literal("Start: " + (SortSettings.reverse ? "Bottom-right" : "Top-left"));
    }

    private static Component hotbarLabel() {
        return Component.literal("Sort hotbar: " + (SortSettings.includeHotbar ? "ON" : "OFF"));
    }

    private static Component armorSlotLabel() {
        return Component.literal("Armor: " + (SortSettings.groupArmorBySlot
                ? "By slot" : "By type"));
    }

    private static Component enchantedLabel() {
        return Component.literal("Enchanted: " + (SortSettings.floatEnchantedFirst
                ? "Float first" : "Normal"));
    }

    private static Component indicatorLabel() {
        return Component.literal("Chest icons: " + (ChestCatIndicatorRenderer.enabled ? "ON" : "OFF"));
    }

    private static Component tiebreakLabel() {
        return Component.literal("Ties: " + SortSettings.tiebreakPriority.getDisplayName());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, Math.max(28, this.height / 2 - 92) - 16, 0xFFFFFF);
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
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // ChestCat: always a flat dim overlay, never blurred, regardless of any blur setting/mod.
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
}
