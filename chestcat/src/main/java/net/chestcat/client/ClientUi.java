package net.chestcat.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Client-only interface preferences (panel position, favorite look, sounds). Saved by {@link ClientPrefs}. */
@OnlyIn(Dist.CLIENT)
public final class ClientUi {

    private ClientUi() {}

    public enum GuiPreset {
        DEFAULT("Default"),
        ABOVE("Above the inventory"),
        BELOW("Below the inventory"),
        LEFT("Left of the inventory"),
        RIGHT("Right of the inventory");

        private final String displayName;

        GuiPreset(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public GuiPreset next() {
            GuiPreset[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public enum FavoriteStyle {
        STAR_AND_OUTLINE("Gold star + outline"),
        STAR("Gold star only"),
        OUTLINE("Gold outline only");

        private final String displayName;

        FavoriteStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public FavoriteStyle next() {
            FavoriteStyle[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    /** Where the gold favorite star sits on an item slot. */
    public enum StarPosition {
        TOP_RIGHT("Top right"),
        TOP_LEFT("Top left"),
        TOP_CENTER("Above the item"),
        BOTTOM_LEFT("Bottom left");

        private final String displayName;

        StarPosition(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public StarPosition next() {
            StarPosition[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    /** When ChestCat sorts by itself as a screen opens. MANUAL = only when you press a button or key. */
    public enum AutoSort {
        MANUAL("Manual only"),
        CONTAINERS("Chests when opened"),
        INVENTORY("Inventory when opened"),
        BOTH("Chests + inventory");

        private final String displayName;

        AutoSort(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public AutoSort next() {
            AutoSort[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        public boolean containers() {
            return this == CONTAINERS || this == BOTH;
        }

        public boolean inventory() {
            return this == INVENTORY || this == BOTH;
        }
    }

    // Button panel
    public static GuiPreset preset = GuiPreset.DEFAULT;
    public static int offsetX = 0;
    public static int offsetY = 0;
    public static int buttonSize = 14;
    /** Panel scale in percent (the button squares and their letters grow / shrink together). */
    public static int panelScale = 100;
    public static boolean hoverEffects = true;

    // Favorites
    public static boolean showFavorites = true;
    public static FavoriteStyle favoriteStyle = FavoriteStyle.STAR_AND_OUTLINE;
    public static boolean highContrast = false;
    public static StarPosition starPosition = StarPosition.TOP_RIGHT;
    /** Dims every non-favorite item in container screens so only favorites stand out (not saved). */
    public static boolean favoritesFilter = false;

    // Behavior
    public static AutoSort autoSort = AutoSort.MANUAL;

    /** Menu type id of the container screen the panel is currently shown on ("" = none). Runtime only. */
    public static String currentMenuId = "";

    // Feedback
    public static boolean animations = true;
    public static boolean sounds = true;
    public static int soundVolume = 60;

    public static void resetPanelPosition() {
        preset = GuiPreset.DEFAULT;
        offsetX = 0;
        offsetY = 0;
    }

    public static void resetDefaults() {
        resetPanelPosition();
        buttonSize = 14;
        panelScale = 100;
        hoverEffects = true;
        showFavorites = true;
        favoriteStyle = FavoriteStyle.STAR_AND_OUTLINE;
        highContrast = false;
        starPosition = StarPosition.TOP_RIGHT;
        favoritesFilter = false;
        autoSort = AutoSort.MANUAL;
        animations = true;
        sounds = true;
        soundVolume = 60;
    }
}
