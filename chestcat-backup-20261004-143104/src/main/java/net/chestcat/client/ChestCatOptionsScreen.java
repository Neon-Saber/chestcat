package net.chestcat.client;

import net.chestcat.FavoriteMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Favorites behaviour/looks and interface options (panel position, sizes, animation, sound). Every change is saved automatically. */
@OnlyIn(Dist.CLIENT)
public class ChestCatOptionsScreen extends Screen {

    private static final int[] BUTTON_SIZES = {12, 14, 16};
    private static final int[] VOLUMES = {0, 25, 50, 75, 100};
    private static final int[] SCALES = {75, 100, 125, 150};

    private final Screen parent;

    public ChestCatOptionsScreen(Screen parent) {
        super(Component.literal("Favorites & Interface"));
        this.parent = parent;
    }

    private interface Label {
        String get();
    }

    private Button toggle(int x, int y, int w, Label label, Runnable action, String tooltip) {
        return Button.builder(Component.literal(label.get()), b -> {
                    action.run();
                    b.setMessage(Component.literal(label.get()));
                })
                .bounds(x, y, w, 20)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build();
    }

    @Override
    protected void init() {
        int colW = 150;
        int gap = 8;
        int left = this.width / 2 - colW - gap / 2;
        int right = this.width / 2 + gap / 2;
        int y0 = Math.max(34, this.height / 2 - 82);
        int step = 22;

        // ---- left column: favorites
        int y = y0;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "When sorting: " + shortFavorite(SortSettings.favoriteMode),
                () -> {
                    SortSettings.favoriteMode = SortSettings.favoriteMode.next();
                    SortSettings.sync();
                },
                "Pinned: favorites never move. Sorted first/last: favorites are grouped at the start/end. "
                        + "Marker only: just the star, sorted like everything else. "
                        + "Favorites only: only favorites are sorted, everything else stays put."));
        y += step;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "Favorite star: " + (ClientUi.showFavorites ? "ON" : "OFF"),
                () -> ClientUi.showFavorites = !ClientUi.showFavorites,
                "Show the gold star above favorited items and the hover star on others."));
        y += step;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "Style: " + ClientUi.favoriteStyle.getDisplayName(),
                () -> ClientUi.favoriteStyle = ClientUi.favoriteStyle.next(),
                "How favorited items are marked."));
        y += step;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "Star at: " + ClientUi.starPosition.getDisplayName(),
                () -> ClientUi.starPosition = ClientUi.starPosition.next(),
                "Where the star sits on the item slot."));
        y += step;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "High contrast: " + (ClientUi.highContrast ? "ON" : "OFF"),
                () -> ClientUi.highContrast = !ClientUi.highContrast,
                "Thicker outline and a dark backdrop behind the star."));
        y += step;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "Favorites filter: " + (ClientUi.favoritesFilter ? "ON" : "OFF"),
                () -> ClientUi.favoritesFilter = !ClientUi.favoritesFilter,
                "Dims every item that isn't a favorite so favorites stand out. Also on the F button and a hotkey."));

        // ---- right column: interface
        y = y0;
        this.addRenderableWidget(toggle(right, y, colW,
                () -> "Panel: " + ClientUi.preset.getDisplayName(),
                () -> ClientUi.preset = ClientUi.preset.next(),
                "Where the button panel appears. You can also Ctrl+drag any button to place it exactly."));
        y += step;
        this.addRenderableWidget(toggle(right, y, colW,
                () -> "Panel scale: " + ClientUi.panelScale + "%",
                () -> {
                    int next = SCALES[0];
                    for (int v : SCALES) {
                        if (v > ClientUi.panelScale) {
                            next = v;
                            break;
                        }
                    }
                    ClientUi.panelScale = next;
                },
                "Size of the whole button panel (buttons and their letters). 75% to 150%."));
        y += step;
        this.addRenderableWidget(toggle(right, y, colW,
                () -> "Button size: " + ClientUi.buttonSize,
                () -> {
                    int next = BUTTON_SIZES[0];
                    for (int i = 0; i < BUTTON_SIZES.length; i++) {
                        if (BUTTON_SIZES[i] == ClientUi.buttonSize) {
                            next = BUTTON_SIZES[(i + 1) % BUTTON_SIZES.length];
                            break;
                        }
                    }
                    ClientUi.buttonSize = next;
                },
                "Base size of the panel buttons (before scaling)."));
        y += step;
        this.addRenderableWidget(toggle(right, y, colW,
                () -> "Animations: " + (ClientUi.animations ? "ON" : "OFF"),
                () -> ClientUi.animations = !ClientUi.animations,
                "Gentle pulse on favorite outlines."));
        y += step;
        this.addRenderableWidget(toggle(right, y, colW,
                () -> "Hover effects: " + (ClientUi.hoverEffects ? "ON" : "OFF"),
                () -> ClientUi.hoverEffects = !ClientUi.hoverEffects,
                "Highlight panel buttons when the mouse is over them."));
        y += step;
        this.addRenderableWidget(toggle(right, y, colW,
                () -> "Sounds: " + (ClientUi.sounds && ClientUi.soundVolume > 0 ? ClientUi.soundVolume + "%" : "OFF"),
                () -> {
                    int cur = ClientUi.sounds ? ClientUi.soundVolume : 0;
                    int next = VOLUMES[0];
                    for (int v : VOLUMES) {
                        if (v > cur) {
                            next = v;
                            break;
                        }
                    }
                    ClientUi.soundVolume = next;
                    ClientUi.sounds = next > 0;
                    LockSound.preview();
                },
                "Volume of ChestCat's click sounds when favoriting or locking (OFF = silent)."));

        // ---- footer
        y = y0 + 6 * step + 8;
        this.addRenderableWidget(Button.builder(Component.literal("Reset panel position"), b -> {
                    ClientUi.resetPanelPosition();
                    this.rebuildWidgets();
                })
                .bounds(left, y, colW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset ALL settings"), b -> {
                    ClientPrefs.resetAll();
                    this.rebuildWidgets();
                })
                .bounds(right, y, colW, 20)
                .tooltip(Tooltip.create(Component.literal("Restores every ChestCat client setting to its default.")))
                .build());
        y += 26;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.minecraft.setScreen(parent))
                .bounds(left, y, colW * 2 + gap, 20).build());
    }

    private static String shortFavorite(FavoriteMode mode) {
        return switch (mode) {
            case PIN -> "Pinned";
            case FIRST -> "First";
            case LAST -> "Last";
            case MARK -> "Marker only";
            case ONLY -> "Favorites only";
        };
    }

    /** Tiny helper so the volume button can give an audible preview. */
    private static final class LockSound {
        static void preview() {
            SlotLockHandler.playFeedback(true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int y0 = Math.max(34, this.height / 2 - 82);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y0 - 22, 0xFFFFFF);
        graphics.drawString(this.font, "Favorites", this.width / 2 - 158, y0 - 11, 0xFFFFD24A);
        graphics.drawString(this.font, "Interface", this.width / 2 + 4, y0 - 11, 0xFF9AC4FF);
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
