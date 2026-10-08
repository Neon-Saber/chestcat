package net.chestcat.client;

import net.chestcat.ModInfo;
import net.chestcat.SortCategory;
import net.chestcat.SortMore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Behaviour options: hotbar handling, empty slots, name direction, stack merging, auto-sort and the custom
 * category / mod order. Every change is saved straight away and sent to the server.
 */
@OnlyIn(Dist.CLIENT)
public class BehaviorScreen extends Screen {

    private final Screen parent;

    public BehaviorScreen(Screen parent) {
        super(Component.literal("Behavior & Order"));
        this.parent = parent;
    }

    private interface Label {
        String get();
    }

    private Button toggle(int x, int y, int w, Label label, Runnable action, String tooltip) {
        return Button.builder(Component.literal(label.get()), b -> {
                    action.run();
                    SortSettings.sync();
                    b.setMessage(Component.literal(label.get()));
                })
                .bounds(x, y, w, 20)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build();
    }

    private static String onOff(boolean v) {
        return v ? "ON" : "OFF";
    }

    @Override
    protected void init() {
        int colW = 150;
        int gap = 8;
        int left = this.width / 2 - colW - gap / 2;
        int right = this.width / 2 + gap / 2;
        int y0 = Math.max(34, this.height / 2 - 82);
        int step = 22;

        // ---- left: hotbar, slots and stacks
        int y = y0;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "Sort hotbar: " + onOff(SortSettings.includeHotbar),
                () -> SortSettings.includeHotbar = !SortSettings.includeHotbar,
                "OFF: the hotbar is never rearranged. ON: it takes part in inventory sorting."));
        y += step;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "Hotbar: " + (SortSettings.more.hotbarSeparate() ? "Sorted separately" : "With inventory"),
                () -> SortSettings.more = SortSettings.more.withHotbarSeparate(!SortSettings.more.hotbarSeparate()),
                "Separately: the hotbar and the three main rows are sorted on their own, so items never cross between them. "
                        + "Only matters when Sort hotbar is ON."));
        y += step;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "Hotbar favorites: " + (SortSettings.more.hotbarFavoritesStay() ? "Stay put" : "Can move"),
                () -> SortSettings.more = SortSettings.more.withHotbarFavoritesStay(!SortSettings.more.hotbarFavoritesStay()),
                "Stay put: a favorited item in the hotbar keeps its exact hotbar position."));
        y += step;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "Selected slot: " + (SortSettings.more.preserveSelected() ? "Preserved" : "Can move"),
                () -> SortSettings.more = SortSettings.more.withPreserveSelected(!SortSettings.more.preserveSelected()),
                "Preserved: the hotbar slot you have selected is never moved, so what's in your hand stays in your hand."));
        y += step;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> SortSettings.more.emptySlots().getDisplayName(),
                () -> SortSettings.more = SortSettings.more.withEmptySlots(SortSettings.more.emptySlots().next()),
                "Where the empty slots end up after sorting: after the items (normal) or before them."));
        y += step;
        this.addRenderableWidget(toggle(left, y, colW,
                () -> "Merge partial stacks: " + onOff(SortSettings.mergeStacks),
                () -> SortSettings.mergeStacks = !SortSettings.mergeStacks,
                "When on, partial stacks of the same item are combined while sorting. "
                        + "When off, every stack stays as it is and only changes place."));

        // ---- right: order and timing
        y = y0;
        this.addRenderableWidget(toggle(right, y, colW,
                () -> "Names: " + (SortSettings.more.alphaDescending() ? "Z - A" : "A - Z"),
                () -> SortSettings.more = SortSettings.more.withAlphaDescending(!SortSettings.more.alphaDescending()),
                "Direction of every alphabetical order (the Name sort key and all name tiebreaks)."));
        y += step;
        this.addRenderableWidget(toggle(right, y, colW,
                () -> "Auto-sort: " + ClientUi.autoSort.getDisplayName(),
                () -> ClientUi.autoSort = ClientUi.autoSort.next(),
                "Sort by itself when a screen opens. Manual only: just the buttons and hotkeys. "
                        + "Uses your current sort modes; favorites, locks and ignore rules still apply."));
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Category order..."),
                        b -> this.minecraft.setScreen(categoryScreen()))
                .bounds(right, y, colW, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Choose which categories come first when sorting by Category"
                                + (SortSettings.more.categoryOrder().isEmpty() ? " (default order)." : " (custom order active)."))))
                .build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Mod order..."),
                        b -> this.minecraft.setScreen(modScreen()))
                .bounds(right, y, colW, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Choose which mods come first when sorting by Mod"
                                + (SortSettings.more.modOrder().isEmpty() ? " (default: Minecraft, then A-Z)." : " (custom order active)."))))
                .build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Reset this page"), b -> {
                    SortSettings.more = SortMore.DEFAULT;
                    SortSettings.mergeStacks = true;
                    ClientUi.autoSort = ClientUi.AutoSort.MANUAL;
                    SortSettings.sync();
                    this.rebuildWidgets();
                })
                .bounds(right, y, colW, 20)
                .tooltip(Tooltip.create(Component.literal("Restores these options (not the sort chain or favorites) to their defaults.")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.minecraft.setScreen(parent))
                .bounds(left, y0 + 6 * step + 6, colW * 2 + gap, 20).build());
    }

    private Screen categoryScreen() {
        List<ReorderScreen.Item> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String name : SortSettings.more.categoryList()) {
            SortCategory c = SortCategory.byName(name);
            if (c != null && seen.add(c.name())) items.add(new ReorderScreen.Item(c.name(), c.getDisplayName()));
        }
        for (SortCategory c : SortCategory.values()) {
            if (seen.add(c.name())) items.add(new ReorderScreen.Item(c.name(), c.getDisplayName()));
        }
        return new ReorderScreen(this, "Category Order",
                "Categories higher in the list come first when sorting by Category.", items,
                ids -> {
                    SortSettings.more = SortSettings.more.withCategoryOrder(String.join(",", ids));
                    SortSettings.sync();
                },
                () -> {
                    SortSettings.more = SortSettings.more.withCategoryOrder("");
                    SortSettings.sync();
                });
    }

    private Screen modScreen() {
        // Every mod that adds items, found from the item registry itself - nothing here is a fixed list.
        Set<String> installed = new TreeSet<>();
        BuiltInRegistries.ITEM.keySet().forEach(id -> installed.add(id.getNamespace()));

        List<ReorderScreen.Item> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : SortSettings.more.modList()) {
            if (installed.contains(id) && seen.add(id)) items.add(new ReorderScreen.Item(id, ModInfo.displayName(id)));
        }
        List<String> rest = new ArrayList<>();
        for (String id : installed) {
            if (!seen.contains(id)) rest.add(id);
        }
        // Default order for the rest: Minecraft first, then by display name.
        rest.sort((a, b) -> {
            if (a.equals("minecraft")) return -1;
            if (b.equals("minecraft")) return 1;
            return ModInfo.displayName(a).toLowerCase(Locale.ROOT).compareTo(ModInfo.displayName(b).toLowerCase(Locale.ROOT));
        });
        for (String id : rest) items.add(new ReorderScreen.Item(id, ModInfo.displayName(id)));

        return new ReorderScreen(this, "Mod Order",
                "Mods higher in the list come first when sorting by Mod (first " + SortMore.MAX_MODS + " are kept).", items,
                ids -> {
                    List<String> kept = ids.size() > SortMore.MAX_MODS ? ids.subList(0, SortMore.MAX_MODS) : ids;
                    SortSettings.more = SortSettings.more.withModOrder(String.join(",", kept));
                    SortSettings.sync();
                },
                () -> {
                    SortSettings.more = SortSettings.more.withModOrder("");
                    SortSettings.sync();
                });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int y0 = Math.max(34, this.height / 2 - 82);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y0 - 22, 0xFFFFFF);
        graphics.drawString(this.font, "Hotbar, slots & stacks", this.width / 2 - 158, y0 - 11, 0xFFFFD24A);
        graphics.drawString(this.font, "Order & timing", this.width / 2 + 4, y0 - 11, 0xFF9AC4FF);
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
