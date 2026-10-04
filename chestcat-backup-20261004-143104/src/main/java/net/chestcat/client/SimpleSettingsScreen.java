package net.chestcat.client;

import net.chestcat.FavoriteMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The one settings screen most people ever need: five plain-language options, then a "More settings" door
 * to everything else (sort chain, ignore rules, order, look and sounds...). Every change applies at once.
 */
@OnlyIn(Dist.CLIENT)
public class SimpleSettingsScreen extends Screen {

    private final Screen parent;

    public SimpleSettingsScreen(Screen parent) {
        super(Component.literal("ChestCat Settings"));
        this.parent = parent;
    }

    private interface Label {
        String get();
    }

    private Button option(int y, int w, Label label, Runnable action, String tooltip) {
        return Button.builder(Component.literal(label.get()), b -> {
                    action.run();
                    SortSettings.sync();
                    b.setMessage(Component.literal(label.get()));
                })
                .bounds(this.width / 2 - w / 2, y, w, 20)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build();
    }

    /** Plain-words name for each favorite behavior. */
    public static String favoriteWords(FavoriteMode mode) {
        return switch (mode) {
            case PIN -> "Stay where they are";
            case FIRST -> "Go to the front";
            case LAST -> "Go to the back";
            case MARK -> "Just show a star";
            case ONLY -> "Only sort favorites";
        };
    }

    private static String autoWords() {
        return switch (ClientUi.autoSort) {
            case MANUAL -> "Never (use the S button)";
            case CONTAINERS -> "Chests";
            case INVENTORY -> "My inventory";
            case BOTH -> "Chests and inventory";
        };
    }

    @Override
    protected void init() {
        int w = 230;
        int step = 24;
        int y = Math.max(40, this.height / 2 - 78);

        this.addRenderableWidget(Button.builder(
                        Component.literal("How to sort: " + ChestCatClient.inventorySortMode.getDisplayName()),
                        b -> this.minecraft.setScreen(new SortModePickerScreen(this, picked -> {
                            ChestCatClient.inventorySortMode = picked;
                            ChestCatClient.chestSortMode = picked;
                            SortSettings.sync();
                        })))
                .bounds(this.width / 2 - w / 2, y, w, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Pick the way items are ordered (by type, name, mod, rarity...). Used for your inventory and chests.")))
                .build());
        y += step;

        this.addRenderableWidget(option(y, w,
                () -> "Favorites: " + favoriteWords(SortSettings.favoriteMode),
                () -> SortSettings.favoriteMode = SortSettings.favoriteMode.next(),
                "What sorting does with items you starred. Hold Alt and click an item (or press Z over it) to star it."));
        y += step;

        this.addRenderableWidget(option(y, w,
                () -> "Sort my hotbar too: " + (SortSettings.includeHotbar ? "Yes" : "No"),
                () -> SortSettings.includeHotbar = !SortSettings.includeHotbar,
                "No means the bottom row (your hotbar) is never rearranged."));
        y += step;

        this.addRenderableWidget(option(y, w,
                () -> "Sort when I open: " + autoWords(),
                () -> ClientUi.autoSort = ClientUi.autoSort.next(),
                "Sort by itself every time you open a chest or your inventory."));
        y += step;

        this.addRenderableWidget(Button.builder(Component.literal("Ready-made styles (Combat, Building, Mining...)"),
                        b -> this.minecraft.setScreen(new PresetPickerScreen(this)))
                .bounds(this.width / 2 - w / 2, y, w, 20)
                .tooltip(Tooltip.create(Component.literal("One click to switch to a ready-made sorting style.")))
                .build());
        y += step + 6;

        this.addRenderableWidget(Button.builder(Component.literal("More settings..."),
                        b -> this.minecraft.setScreen(new SortSettingsScreen(this)))
                .bounds(this.width / 2 - w / 2, y, w / 2 - 2, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Sort chain, ignore rules, hotbar details, look and sounds, saved profiles.")))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 + 2, y, w / 2 - 2, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = Math.max(40, this.height / 2 - 78);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 26, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Hover any button for help. Changes apply instantly."),
                this.width / 2, top - 14, 0xFF9AA4B2);
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
