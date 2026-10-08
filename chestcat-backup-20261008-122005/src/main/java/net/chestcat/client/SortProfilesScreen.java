package net.chestcat.client;

import net.chestcat.network.DeleteSortProfilePayload;
import net.chestcat.network.LoadSortProfilePayload;
import net.chestcat.network.RequestSortProfilesPayload;
import net.chestcat.network.SaveSortProfilePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Save/load/delete named sort profiles - bundles of the inventory/chest/nearby sort mode
 * plus the current layout and Category Splitting settings. Reached from Sort Options.
 * Does NOT cover per-chest categories, exclusions, the whitelist, or protected items -
 * those live in the world, not in a player preference bundle.
 */
@OnlyIn(Dist.CLIENT)
public class SortProfilesScreen extends Screen {

    private final Screen parent;
    private EditBox nameBox;
    private ProfileList list;

    public SortProfilesScreen(Screen parent) {
        super(Component.literal("Sort Profiles"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 230;
        int x = this.width / 2 - w / 2;

        this.nameBox = new EditBox(this.font, x, 34, w - 70, 20, Component.literal("Profile name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setHint(Component.literal("New profile name..."));
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
                    String name = this.nameBox.getValue();
                    if (name.isBlank()) return;
                    SortSettings.sync(); // make sure the server has our latest layout+grouping first
                    PacketDistributor.sendToServer(new SaveSortProfilePayload(name,
                            ChestCatClient.inventorySortMode, ChestCatClient.chestSortMode, ChestCatClient.nearbySortMode));
                    this.nameBox.setValue("");
                })
                .bounds(x + w - 64, 34, 64, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("This computer..."),
                        b -> this.minecraft.setScreen(new ClientProfilesScreen(this)))
                .bounds(6, 6, 104, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Profiles stored on this computer that work in every world and server.")))
                .build());

        this.list = new ProfileList(this.minecraft, this.width, this.height - 100, 62, 24);
        this.list.setNames(ClientPacketHandlers.latestProfileNames);
        this.addWidget(this.list);
        PacketDistributor.sendToServer(new RequestSortProfilesPayload());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    public void updateNames(List<String> names) {
        if (this.list != null) this.list.setNames(names);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.list.render(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        if (ClientPacketHandlers.latestProfileNames.isEmpty()) {
            graphics.drawCenteredString(this.font, "No saved profiles yet.", this.width / 2, this.height / 2, 0xAAAAAA);
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

    private class ProfileList extends ObjectSelectionList<ProfileList.Row> {

        ProfileList(net.minecraft.client.Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        void setNames(List<String> names) {
            java.util.List<Row> rows = new java.util.ArrayList<>();
            for (String name : names) rows.add(new Row(name));
            this.replaceEntries(rows);
        }

        class Row extends ObjectSelectionList.Entry<Row> {
            private final String name;
            private int lastLeft, lastTop, lastWidth;

            Row(String name) {
                this.name = name;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                int mouseX, int mouseY, boolean hovering, float partialTick) {
                this.lastLeft = left; this.lastTop = top; this.lastWidth = width;
                graphics.drawString(SortProfilesScreen.this.font, name, left + 4, top + 6, 0xFFFFFF);

                int btnW = 56;
                int loadX = left + width - btnW * 2 - 8;
                int delX = left + width - btnW - 4;
                boolean overLoad = mouseX >= loadX && mouseX < loadX + btnW && mouseY >= top && mouseY < top + height;
                boolean overDel = mouseX >= delX && mouseX < delX + btnW && mouseY >= top && mouseY < top + height;

                graphics.fill(loadX, top + 2, loadX + btnW, top + height - 2, overLoad ? 0xFF4A9EFF : 0xFF2A2E38);
                graphics.drawCenteredString(SortProfilesScreen.this.font, "Load", loadX + btnW / 2, top + 6,
                        overLoad ? 0xFF10131A : 0xFF6FB4FF);

                graphics.fill(delX, top + 2, delX + btnW, top + height - 2, overDel ? 0xFFB4443A : 0xFF2A2E38);
                graphics.drawCenteredString(SortProfilesScreen.this.font, "Delete", delX + btnW / 2, top + 6,
                        overDel ? 0xFFFFEFEF : 0xFFE08080);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) return false;
                int btnW = 56;
                int loadX = lastLeft + lastWidth - btnW * 2 - 8;
                int delX = lastLeft + lastWidth - btnW - 4;
                if (mouseX >= loadX && mouseX < loadX + btnW) {
                    PacketDistributor.sendToServer(new LoadSortProfilePayload(name));
                    return true;
                }
                if (mouseX >= delX && mouseX < delX + btnW) {
                    PacketDistributor.sendToServer(new DeleteSortProfilePayload(name));
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(name);
            }
        }
    }
}
