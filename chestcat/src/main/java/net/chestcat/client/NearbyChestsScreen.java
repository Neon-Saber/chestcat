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

import java.util.List;

public class NearbyChestsScreen extends Screen {

    private List<NearbyChestsResponsePayload.Entry> entries;
    private ChestList list;

    protected NearbyChestsScreen(List<NearbyChestsResponsePayload.Entry> entries) {
        super(Component.literal("Nearby Chests"));
        this.entries = entries;
    }

    public void updateEntries(List<NearbyChestsResponsePayload.Entry> entries) {
        this.entries = entries;
        if (this.list != null) {
            rebuildList();
        }
    }

    @Override
    protected void init() {
        int listTop = 32;
        int listBottom = this.height - 48;
        this.list = new ChestList(this.minecraft, this.width, listBottom - listTop, listTop, 24);
        rebuildList();
        this.addWidget(this.list);

        this.addRenderableWidget(Button.builder(Component.literal("Sort All Nearby"), b -> {
                    PacketDistributor.sendToServer(new SortNearbyPayload(NetworkHandler.DEFAULT_RADIUS, net.chestcat.ItemSortMode.REGISTRY_ORDER));
                    this.onClose();
                }).bounds(this.width / 2 - 154, this.height - 30, 150, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 30, 150, 20)
                .build());
    }

    private void rebuildList() {
        this.list.setEntries(entries.stream().map(e -> list.new Row(e)).toList());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.list.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, "No chests or barrels found nearby.", this.width / 2, 48, 0xAAAAAA);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class ChestList extends ObjectSelectionList<ChestList.Row> {

        ChestList(net.minecraft.client.Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        public void setEntries(List<Row> entries) {
            this.replaceEntries(entries);
        }

        class Row extends ObjectSelectionList.Entry<Row> {
            private final NearbyChestsResponsePayload.Entry data;

            Row(NearbyChestsResponsePayload.Entry data) {
                this.data = data;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                int mouseX, int mouseY, boolean hovering, float partialTick) {
                String categoryLabel = "UNASSIGNED".equals(data.categoryName())
                        ? "auto: " + guessLabel()
                        : prettyName(data.categoryName());

                String posText = String.format("(%d, %d, %d)", data.pos().getX(), data.pos().getY(), data.pos().getZ());
                graphics.drawString(NearbyChestsScreen.this.font, posText, left + 4, top + 2, 0xFFFFFF);
                graphics.drawString(NearbyChestsScreen.this.font,
                        categoryLabel + "  -  " + data.itemCount() + " items, " + data.freeSlots() + " free",
                        left + 4, top + 12, 0xAAAAAA);
            }

            private String guessLabel() {
                return "unset";
            }

            private String prettyName(String enumName) {
                try {
                    return ItemCategory.valueOf(enumName).getDisplayName();
                } catch (IllegalArgumentException e) {
                    return enumName;
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    NearbyChestsScreen.this.minecraft.setScreen(
                            new CategoryPickerScreen(data.pos(), NearbyChestsScreen.this));
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(data.pos().toShortString());
            }
        }
    }
}