package net.chestcat.client;

import net.chestcat.ItemCategory;
import net.chestcat.network.NetworkHandler;
import net.chestcat.network.NearbyChestsResponsePayload;
import net.chestcat.network.SortNearbyPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class NearbyChestsScreen extends Screen {

    private List<NearbyChestsResponsePayload.Entry> entries;
    private ChestList list;

    protected NearbyChestsScreen(List<NearbyChestsResponsePayload.Entry> entries) {
        super(Component.literal("Nearby Chests"));
        this.entries = entries;
    }

    public void updateEntries(List<NearbyChestsResponsePayload.Entry> entries) {
        this.entries = entries;
        if (this.list != null) rebuildList();
    }

    @Override
    protected void init() {
        int listTop = 34;
        int listBottom = this.height - 48;
        this.list = new ChestList(this.minecraft, this.width, listBottom - listTop, listTop, 26);
        rebuildList();
        this.addWidget(this.list);

        this.addRenderableWidget(Button.builder(Component.literal("Sort All Nearby"), b -> {
                    PacketDistributor.sendToServer(new SortNearbyPayload(NetworkHandler.DEFAULT_RADIUS, net.chestcat.ItemSortMode.REGISTRY_ORDER));
                    this.onClose();
                }).bounds(this.width / 2 - 154, this.height - 30, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 30, 150, 20).build());
    }

    private record Section(String title, List<NearbyChestsResponsePayload.Entry> entries) {}

    private List<Section> buildSections() {
        List<NearbyChestsResponsePayload.Entry> chests = new ArrayList<>();
        List<NearbyChestsResponsePayload.Entry> barrels = new ArrayList<>();
        List<NearbyChestsResponsePayload.Entry> enderChests = new ArrayList<>();
        Map<String, List<NearbyChestsResponsePayload.Entry>> byMod = new TreeMap<>();

        for (NearbyChestsResponsePayload.Entry e : entries) {
            switch (e.kindTag()) {
                case "CHEST" -> chests.add(e);
                case "BARREL" -> barrels.add(e);
                case "ENDER_CHEST" -> enderChests.add(e);
                default -> {
                    if (e.kindTag().startsWith("MODDED:")) {
                        String modId = e.kindTag().substring("MODDED:".length());
                        byMod.computeIfAbsent(modId, k -> new ArrayList<>()).add(e);
                    }
                }
            }
        }

        Comparator<NearbyChestsResponsePayload.Entry> byContents = Comparator.comparing(NearbyChestsScreen::sortLabel);
        chests.sort(byContents);
        barrels.sort(byContents);
        enderChests.sort(byContents);

        List<Section> sections = new ArrayList<>();
        if (!chests.isEmpty()) sections.add(new Section("Chests  ·  " + chests.size(), chests));
        if (!barrels.isEmpty()) sections.add(new Section("Barrels  ·  " + barrels.size(), barrels));
        if (!enderChests.isEmpty()) sections.add(new Section("Ender Chests  ·  " + enderChests.size(), enderChests));
        for (Map.Entry<String, List<NearbyChestsResponsePayload.Entry>> modEntry : byMod.entrySet()) {
            List<NearbyChestsResponsePayload.Entry> modList = modEntry.getValue();
            modList.sort(byContents);
            sections.add(new Section(prettyModName(modEntry.getKey()) + "  ·  " + modList.size(), modList));
        }
        return sections;
    }

    private static String sortLabel(NearbyChestsResponsePayload.Entry e) {
        if ("ENDER_CHEST".equals(e.kindTag())) return "Ender Chest";
        String name = "UNASSIGNED".equals(e.categoryName()) ? e.autoCategoryName() : e.categoryName();
        try {
            return ItemCategory.valueOf(name).getDisplayName();
        } catch (IllegalArgumentException ex) {
            return name;
        }
    }

    private static String prettyModName(String modId) {
        if (modId == null || modId.isEmpty()) return "Other Mods";
        String spaced = modId.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private void rebuildList() {
        List<ChestList.Row> rows = new ArrayList<>();
        int number = 1;
        for (Section section : buildSections()) {
            rows.add(list.new HeaderRow(section.title()));
            for (NearbyChestsResponsePayload.Entry e : section.entries()) {
                rows.add(list.new DataRow(e, number++));
            }
        }
        this.list.setEntries(rows);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.list.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, "No chests, barrels, or ender chests found nearby.", this.width / 2, 50, 0xAAAAAA);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String formatDistance(net.minecraft.core.BlockPos pos, net.minecraft.client.player.LocalPlayer player) {
        if (player == null) return "";
        double dist = Math.sqrt(player.blockPosition().distSqr(pos));
        return Math.round(dist) + "m away";
    }

    private class ChestList extends ObjectSelectionList<ChestList.Row> {

        ChestList(net.minecraft.client.Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        public void setEntries(List<Row> entries) { this.replaceEntries(entries); }

        abstract class Row extends ObjectSelectionList.Entry<Row> {}

        /** Non-interactive section divider - a label with a thin accent underline instead of a solid bar. */
        class HeaderRow extends Row {
            private final String title;
            HeaderRow(String title) { this.title = title; }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                int mouseX, int mouseY, boolean hovering, float partialTick) {
                int textY = top + height - 12;
                graphics.drawString(NearbyChestsScreen.this.font, title, left + 4, textY, 0xFFD9A441);
                int lineY = top + height - 3;
                graphics.fill(left + 4, lineY, left + width - 4, lineY + 1, 0x40D9A441);
            }

            @Override
            public Component getNarration() { return Component.literal(title); }
        }

        class DataRow extends Row {
            private final NearbyChestsResponsePayload.Entry data;
            private final int number;
            private int lastLeft, lastTop, lastWidth, lastHeight;

            DataRow(NearbyChestsResponsePayload.Entry data, int number) {
                this.data = data;
                this.number = number;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                int mouseX, int mouseY, boolean hovering, float partialTick) {
                this.lastLeft = left; this.lastTop = top; this.lastWidth = width; this.lastHeight = height;

                int rowPad = 2;
                int rowBg = hovering ? 0x40FFFFFF : (index % 2 == 0 ? 0x1AFFFFFF : 0x00000000);
                graphics.fill(left + 2, top + rowPad, left + width - 2, top + height - rowPad, rowBg);

                int badgeSize = 16;
                int badgeY = top + (height - badgeSize) / 2;
                graphics.fill(left + 4, badgeY, left + 4 + badgeSize, badgeY + badgeSize, 0xFF242730);
                String badge = String.valueOf(number);
                int badgeTextX = left + 4 + (badgeSize - NearbyChestsScreen.this.font.width(badge)) / 2;
                graphics.drawString(NearbyChestsScreen.this.font, badge, badgeTextX, badgeY + 4, 0xFF6FB4FF, false);

                int textLeft = left + 4 + badgeSize + 8;

                boolean isEnder = "ENDER_CHEST".equals(data.kindTag());
                String categoryLabel = isEnder
                        ? "Shared ender inventory"
                        : ("UNASSIGNED".equals(data.categoryName())
                                ? prettyName(data.autoCategoryName())
                                : prettyName(data.categoryName()));

                int locateWidth = NearbyChestsScreen.this.font.width("Locate") + 10;
                int locateX = left + width - locateWidth - 6;

                String distText = formatDistance(data.pos(), NearbyChestsScreen.this.minecraft.player);
                graphics.drawString(NearbyChestsScreen.this.font, categoryLabel, textLeft, top + 4, 0xFFFFFF);

                String subText = isEnder
                        ? distText
                        : data.itemCount() + " items  ·  " + data.freeSlots() + " free  ·  " + distText;
                graphics.drawString(NearbyChestsScreen.this.font, subText, textLeft, top + 14, 0xFF9A9A9A);

                int locateY = top + (height - 14) / 2;
                boolean locateHovered = mouseX >= locateX && mouseX < locateX + locateWidth
                        && mouseY >= top && mouseY < top + height;
                int bg = locateHovered ? 0xFF4A9EFF : 0xFF2A2E38;
                graphics.fill(locateX, locateY, locateX + locateWidth, locateY + 14, bg);
                int labelColor = locateHovered ? 0xFF10131A : 0xFF6FB4FF;
                graphics.drawString(NearbyChestsScreen.this.font, "Locate", locateX + 5, locateY + 3, labelColor);
            }

            private String prettyName(String enumName) {
                try { return ItemCategory.valueOf(enumName).getDisplayName(); }
                catch (IllegalArgumentException e) { return enumName; }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) return false;

                int locateWidth = NearbyChestsScreen.this.font.width("Locate") + 10;
                int locateX = lastLeft + lastWidth - locateWidth - 6;
                if (mouseX >= locateX) {
                    ChestCatClient.startLocate(data.pos());
                    NearbyChestsScreen.this.onClose();
                    return true;
                }

                if ("ENDER_CHEST".equals(data.kindTag())) return true;

                NearbyChestsScreen.this.minecraft.setScreen(new CategoryPickerScreen(data.pos(), NearbyChestsScreen.this));
                return true;
            }

            @Override
            public Component getNarration() { return Component.literal(data.pos().toShortString()); }
        }
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
}
