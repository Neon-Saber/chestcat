package net.chestcat.client;

import net.chestcat.ItemSortMode;
import net.chestcat.SortChain;
import net.chestcat.SortKey;
import net.chestcat.SortKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the multi-level sort ("Category > Mod > Rarity > Name"). Rules are applied top to bottom: the first
 * rule groups items, the next one orders items inside each group, and so on. Click a rule's name to change it,
 * use the arrows to reorder, and flip its direction with the middle button.
 */
@OnlyIn(Dist.CLIENT)
public class SortChainScreen extends Screen {

    private final Screen parent;
    private final List<SortChain.Entry> working;

    public SortChainScreen(Screen parent) {
        super(Component.literal("Sort Chain"));
        this.parent = parent;
        this.working = new ArrayList<>(SortChain.parse(SortSettings.chain).entries());
    }

    private static boolean customActive() {
        return ChestCatClient.inventorySortMode == ItemSortMode.CUSTOM;
    }

    private List<String> usedIds() {
        List<String> ids = new ArrayList<>();
        for (SortChain.Entry e : working) ids.add(e.keyId());
        return ids;
    }

    private void commit() {
        SortSettings.chain = SortChain.of(working).serialize();
        working.clear();
        working.addAll(SortChain.parse(SortSettings.chain).entries());
        SortSettings.sync();
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        int rowW = 252;
        int x = this.width / 2 - rowW / 2;
        int y = Math.max(34, this.height / 2 - 112);

        this.addRenderableWidget(Button.builder(modeLabel(), b -> {
                    ItemSortMode target = customActive() ? ItemSortMode.CREATIVE_ORDER : ItemSortMode.CUSTOM;
                    ChestCatClient.inventorySortMode = target;
                    ChestCatClient.chestSortMode = target;
                    ChestCatClient.nearbySortMode = target;
                    b.setMessage(modeLabel());
                })
                .bounds(x, y, rowW, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "When on, the S button (and nearby sorting) uses this chain instead of a single sort mode.")))
                .build());
        y += 26;

        for (int i = 0; i < working.size(); i++) {
            final int index = i;
            SortChain.Entry entry = working.get(i);
            SortKey key = SortKeys.get(entry.keyId());
            String name = key == null ? entry.keyId() : key.displayName();

            Button up = Button.builder(Component.literal("^"), b -> move(index, -1))
                    .bounds(x, y, 20, 20).build();
            up.active = i > 0;
            this.addRenderableWidget(up);

            Button down = Button.builder(Component.literal("v"), b -> move(index, 1))
                    .bounds(x + 22, y, 20, 20).build();
            down.active = i < working.size() - 1;
            this.addRenderableWidget(down);

            this.addRenderableWidget(Button.builder(Component.literal((i + 1) + ". " + name), b -> cycleKey(index))
                    .bounds(x + 44, y, 122, 20)
                    .tooltip(Tooltip.create(Component.literal(
                            (key == null ? "" : key.description() + " ") + "Click to pick a different rule.")))
                    .build());

            boolean reversed = key != null && entry.descending() != key.defaultDescending();
            this.addRenderableWidget(Button.builder(Component.literal(reversed ? "Reversed" : "Normal"),
                            b -> flip(index))
                    .bounds(x + 168, y, 56, 20)
                    .tooltip(Tooltip.create(Component.literal("Flip this rule's direction.")))
                    .build());

            this.addRenderableWidget(Button.builder(Component.literal("x"), b -> remove(index))
                    .bounds(x + 226, y, 20, 20).build());
            y += 22;
        }

        y += 4;
        Button add = Button.builder(Component.literal("+ Add rule"), b -> addRule())
                .bounds(x, y, 122, 20).build();
        add.active = working.size() < SortChain.MAX_ENTRIES;
        this.addRenderableWidget(add);
        this.addRenderableWidget(Button.builder(Component.literal("Reset to default"), b -> {
                    working.clear();
                    working.addAll(SortChain.defaultChain().entries());
                    commit();
                })
                .bounds(x + 130, y, 122, 20).build());
        y += 28;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.minecraft.setScreen(parent))
                .bounds(x, y, rowW, 20).build());
    }

    private Component modeLabel() {
        return Component.literal(customActive()
                ? "Custom chain: ACTIVE (click to turn off)"
                : "Custom chain: off - click to use it for all sorting");
    }

    private void move(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= working.size()) return;
        SortChain.Entry e = working.remove(index);
        working.add(target, e);
        commit();
    }

    private void flip(int index) {
        SortChain.Entry e = working.get(index);
        working.set(index, new SortChain.Entry(e.keyId(), !e.descending()));
        commit();
    }

    private void remove(int index) {
        if (working.size() <= 1) return; // a chain always keeps at least one rule
        working.remove(index);
        commit();
    }

    private void cycleKey(int index) {
        SortChain.Entry e = working.get(index);
        String next = SortKeys.nextUnused(e.keyId(), usedIds());
        SortKey nextKey = SortKeys.get(next);
        working.set(index, new SortChain.Entry(next, nextKey != null && nextKey.defaultDescending()));
        commit();
    }

    private void addRule() {
        if (working.size() >= SortChain.MAX_ENTRIES) return;
        String next = SortKeys.nextUnused("", usedIds());
        SortKey nextKey = SortKeys.get(next);
        working.add(new SortChain.Entry(next, nextKey != null && nextKey.defaultDescending()));
        commit();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = Math.max(34, this.height / 2 - 112);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 26, 0xFFFFFF);
        String summary = this.font.plainSubstrByWidth(SortChain.of(working).describe(), this.width - 20);
        graphics.drawCenteredString(this.font, Component.literal(summary), this.width / 2, top - 14, 0xFFB8C4D8);
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
