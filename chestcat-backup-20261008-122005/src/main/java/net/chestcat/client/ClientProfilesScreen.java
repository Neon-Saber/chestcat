package net.chestcat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Profiles stored on this computer (config/chestcat/profiles.json): they follow you into every world and server,
 * unlike the world-bound "Saved Profiles". A profile is a snapshot of all your sorting settings.
 */
@OnlyIn(Dist.CLIENT)
public class ClientProfilesScreen extends Screen {

    private final Screen parent;
    private EditBox nameBox;
    private int page = 0;
    private String status = "";

    public ClientProfilesScreen(Screen parent) {
        super(Component.literal("My Profiles (this computer)"));
        this.parent = parent;
    }

    private int rowsPerPage() {
        return Math.max(3, Math.min(10, (this.height - 150) / 22));
    }

    @Override
    protected void init() {
        int w = 260;
        int x = this.width / 2 - w / 2;
        String keep = nameBox == null ? "" : nameBox.getValue();

        nameBox = new EditBox(this.font, x, 34, w - 70, 20, Component.literal("Profile name"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.literal("New profile name..."));
        nameBox.setValue(keep);
        this.addRenderableWidget(nameBox);
        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
                    String name = nameBox.getValue();
                    if (name.isBlank()) {
                        status = "Type a name first.";
                        return;
                    }
                    status = ClientProfiles.save(name)
                            ? "Saved \"" + name.strip() + "\"."
                            : "Profile limit reached (" + ClientProfiles.MAX_PROFILES + ").";
                    nameBox.setValue("");
                    this.rebuildWidgets();
                })
                .bounds(x + w - 64, 34, 64, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Saves your current sort modes, chain, favorites behavior, hotbar options, order and ignore rules.")))
                .build());

        List<String> names = ClientProfiles.names();
        int rows = rowsPerPage();
        int pages = Math.max(1, (names.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        int y = 62;
        for (int i = page * rows; i < Math.min(names.size(), page * rows + rows); i++) {
            String name = names.get(i);
            Button label = Button.builder(Component.literal(this.font.plainSubstrByWidth(name, w - 130)), b -> {})
                    .bounds(x, y, w - 126, 20).build();
            label.active = false;
            this.addRenderableWidget(label);
            this.addRenderableWidget(Button.builder(Component.literal("Load"), b -> {
                        status = ClientProfiles.load(name) ? "Loaded \"" + name + "\"." : "Could not load \"" + name + "\".";
                    })
                    .bounds(x + w - 122, y, 58, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
                        ClientProfiles.delete(name);
                        status = "Deleted \"" + name + "\".";
                        this.rebuildWidgets();
                    })
                    .bounds(x + w - 62, y, 62, 20).build());
            y += 22;
        }

        int by = 62 + rows * 22 + 4;
        Button prev = Button.builder(Component.literal("<"), b -> {
                    page--;
                    this.rebuildWidgets();
                }).bounds(x, by, 20, 20).build();
        prev.active = page > 0;
        this.addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), b -> {
                    page++;
                    this.rebuildWidgets();
                }).bounds(x + w - 20, by, 20, 20).build();
        next.active = page < pages - 1;
        this.addRenderableWidget(next);
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 60, by, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Works in every world and on every server."),
                this.width / 2, 23, 0xFF9AA4B2);
        if (ClientProfiles.names().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal("No profiles yet - type a name and press Save."),
                    this.width / 2, 74, 0xFF9AA4B2);
        }
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(status, this.width - 10)),
                    this.width / 2, this.height - 16, 0xFFB8E0B8);
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
