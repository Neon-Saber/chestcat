package net.chestcat.client;

import net.chestcat.FavoriteMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The basic view: the few things a new player needs, nothing else.
 *
 * <pre>
 *   ChestCat
 *   Sort by:  [ Survival      ]
 *   [        SORT INVENTORY        ]
 *   [          SORT CHEST          ]   (only when a chest is open)
 *   Favorites: Stay where they are
 *   Locked slots: 3
 *   [ More options > ]   [ Done ]
 * </pre>
 *
 * Everything else lives one click away under "More options".
 */
@OnlyIn(Dist.CLIENT)
public class ChestCatHomeScreen extends Screen {

    private final Screen parent;

    public ChestCatHomeScreen(Screen parent) {
        super(Component.literal("ChestCat"));
        this.parent = parent;
    }

    private boolean chestOpen() {
        return parent instanceof AbstractContainerScreen<?> c && ChestCatActions.isStorageScreen(c);
    }

    @Override
    protected void init() {
        int w = 220;
        int x = this.width / 2 - w / 2;
        int y = Math.max(44, this.height / 2 - 84);

        this.addRenderableWidget(Button.builder(
                        Component.literal("Sort by: " + ChestCatClient.inventorySortMode.getDisplayName()),
                        b -> this.minecraft.setScreen(new SortModePickerScreen(this, picked -> {
                            ChestCatClient.inventorySortMode = picked;
                            ChestCatClient.chestSortMode = picked;
                            SortSettings.sync();
                        })))
                .bounds(x, y, w, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "How items are ordered. Pick a ready-made style or a method like name, type, mod or rarity.")))
                .build());
        y += 28;

        this.addRenderableWidget(Button.builder(Component.literal("SORT INVENTORY"), b -> {
                    ChestCatActions.sortInventory(ChestCatClient.inventorySortMode, false);
                    this.onClose();
                })
                .bounds(x, y, w, 26)
                .tooltip(Tooltip.create(Component.literal("Organize everything in your inventory.")))
                .build());
        y += 30;

        if (chestOpen()) {
            this.addRenderableWidget(Button.builder(Component.literal("SORT CHEST"), b -> {
                        ChestCatActions.sortContainer(ChestCatClient.chestSortMode, false);
                        this.onClose();
                    })
                    .bounds(x, y, w, 26)
                    .tooltip(Tooltip.create(Component.literal("Organize everything in the chest you have open.")))
                    .build());
            y += 30;
        }

        y += 4;
        FavoriteMode mode = SortSettings.favoriteMode;
        this.addRenderableWidget(Button.builder(
                        Component.literal("\u2605 Favorites: " + SettingsPages.favoriteWords(mode)),
                        b -> {
                            SortSettings.favoriteMode = SortSettings.favoriteMode.next();
                            SortSettings.sync();
                            b.setMessage(Component.literal("\u2605 Favorites: " + SettingsPages.favoriteWords(SortSettings.favoriteMode)));
                        })
                .bounds(x, y, w, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "What sorting does with items you starred. Click to change. "
                                + "To star an item: hold Alt and click it, or hover it and press Z.")))
                .build());
        y += 22;

        int locked = SlotLockHandler.lockedMirror.size();
        Button lockInfo = Button.builder(
                        Component.literal("Locked slots: " + (locked == 0 ? "none" : locked)), b -> {})
                .bounds(x, y, w, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Items in a locked slot won't be moved when sorting. "
                                + "Middle-click a slot to lock it; Shift + middle-click locks the whole row.")))
                .build();
        lockInfo.active = false; // an information row, not an action
        this.addRenderableWidget(lockInfo);
        y += 30;

        this.addRenderableWidget(Button.builder(Component.literal("More options \u2192"),
                        b -> this.minecraft.setScreen(SettingsPages.hub(this)))
                .bounds(x, y, w / 2 - 2, 20)
                .tooltip(Tooltip.create(Component.literal("Hotbar, appearance, controls, profiles and more.")))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(x + w / 2 + 2, y, w / 2 - 2, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = Math.max(44, this.height / 2 - 84);
        graphics.drawCenteredString(this.font, "ChestCat", this.width / 2, top - 30, 0xFFFFD24A);
        graphics.drawCenteredString(this.font, "Inventory sorting, done better.", this.width / 2, top - 18, 0xFF9AA4B2);
        var report = net.chestcat.CompatInfo.get();
        if (!report.compatible()) {
            graphics.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(
                            "\u26A0 Compatibility problem - see More options > About", this.width - 8)),
                    this.width / 2, this.height - 14, 0xFFFFD27A);
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
