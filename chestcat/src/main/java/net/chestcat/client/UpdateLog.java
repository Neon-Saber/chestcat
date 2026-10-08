package net.chestcat.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * The in-game changelog. To ship a new update, add a new {@link Entry} at the TOP of {@link #ENTRIES} - the
 * popup then appears once for every player the first time they join a world after updating, and the version
 * progression bar in the screen picks the new version up on its own. Nothing else needs to change.
 */
@OnlyIn(Dist.CLIENT)
public final class UpdateLog {

    private UpdateLog() {}

    /** One released version: its number, a short title, a date and the notes. */
    public record Entry(String version, String title, String date, List<String> notes) {}

    /** Newest first. */
    public static final List<Entry> ENTRIES = List.of(
            new Entry("1.3.0", "A simpler ChestCat", "October 2026", List.of(
                    "New home screen: pick how to sort, press SORT INVENTORY, see your favorites and locked slots. That's it.",
                    "\"More options\" now opens six small pages (Sorting, Inventory, Favorites, Appearance, Controls, Advanced) in plain language.",
                    "Every setting has a short explanation when you hover it.",
                    "New About & Compatibility page: your real ChestCat, Minecraft, NeoForge and Java versions, what ChestCat supports, and a Copy Diagnostics button for bug reports.",
                    "ChestCat now warns you in chat if it is running on a version it wasn't built for.",
                    "Clearer feedback: \"Inventory sorted\", \"Added to favorites\", \"Slot locked\", and a warning when a container can't be sorted.",
                    "The sort-order editor now reads \"Sort by\" and \"Then by\".")),
            new Entry("1.2.0", "Smarter sorting, simpler settings", "October 2026", List.of(
                    "New sort methods: Recently Acquired, Recently Used, Most Used, Least Used, Exact Duplicates and Favorites First.",
                    "Empty slots can now go first or last, and names can sort Z-A.",
                    "Hotbar options: sort it separately, keep favorites in place, never move the selected slot.",
                    "Auto-sort when you open a chest or your inventory (off by default).",
                    "Sort your own category order and mod order.",
                    "Favorites: gold star tooltip, favorites filter (dims everything else), star position, and \"favorites only\" sorting.",
                    "Favorites now work in the creative inventory too.",
                    "Works on shulker boxes, hoppers, dispensers and most modded storage screens.",
                    "Profiles saved on your computer work in every world and server.",
                    "New simple settings screen with plain-language options; the old screens are under \"More settings\".",
                    "Fewer buttons: M now opens the sort list, and L, F and R are gone.",
                    "Better hotkeys: G sorts (Shift+G sorts your inventory from inside a chest), B sorts nearby chests, X quick-stacks. All rebindable in Controls > ChestCat, or press \"Comfortable keybinds\" in settings to auto-pick free keys.",
                    "Hotkeys only work in your inventory and chests - never in crafting tables, furnaces or anvils, and not while the recipe book or a search box is open.",
                    "Fixed: your settings are now applied on login, so the sort key and Sort Nearby use them.")),
            new Entry("1.1.0", "The sorting overhaul", "September 2026", List.of(
                    "Multi-level sort chains, like Category > Mod > Rarity > Name.",
                    "Sort by mod, category, rarity, durability, tags and more - modded items work automatically.",
                    "Favorites tied to the item (not the slot) so they follow it through sorting and chests.",
                    "Lock single slots or whole rows so sorting never touches them.",
                    "Ignore rules: never sort an item, mod, category or tag.",
                    "Sort presets (Combat, Building, Mining...) and server-saved profiles.",
                    "Settings are saved and come back when you restart Minecraft.")),
            new Entry("1.0.0", "First release", "Original release", List.of(
                    "Sort every chest and barrel nearby by category in one click.",
                    "Sort your own inventory with a keybind.",
                    "Assign a category to a chest by hand or let ChestCat guess it.")));

    public static String latest() {
        return ENTRIES.get(0).version();
    }

    /** Compares dotted version numbers: negative if a is older than b. Non-numeric parts count as 0. */
    public static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? number(x[i]) : 0;
            int yi = i < y.length ? number(y[i]) : 0;
            if (xi != yi) return Integer.compare(xi, yi);
        }
        return 0;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Called once per world join. Opens the update screen only if this version has not been shown yet, and
     * marks it as seen right away so it can never appear twice (even if the game crashes with it open).
     */
    public static void showIfNew() {
        String previous = ClientPrefs.lastSeenVersion;
        if (latest().equals(previous)) return;
        ClientPrefs.lastSeenVersion = latest();
        ClientPrefs.saveIfChanged();
        Minecraft.getInstance().setScreen(new UpdateLogScreen(null, previous));
    }

    /** Opens the screen on demand (from the settings screen); nothing is marked as new. */
    public static void open(net.minecraft.client.gui.screens.Screen parent) {
        Minecraft.getInstance().setScreen(new UpdateLogScreen(parent, latest()));
    }
}
