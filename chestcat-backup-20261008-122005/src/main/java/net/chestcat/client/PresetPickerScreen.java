package net.chestcat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/** Quick switcher: built-in presets, plus a shortcut to your saved profiles. */
@OnlyIn(Dist.CLIENT)
public class PresetPickerScreen extends Screen {

    private final Screen parent;

    public PresetPickerScreen(Screen parent) {
        super(Component.literal("Sort Presets"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<SortPresets.Preset> presets = SortPresets.BUILT_IN;
        int colW = 140;
        int gap = 6;
        int x0 = this.width / 2 - colW - gap / 2;
        int rows = (presets.size() + 1) / 2;
        int y0 = Math.max(24, this.height / 2 - (rows * 22 + 60) / 2);

        for (int i = 0; i < presets.size(); i++) {
            SortPresets.Preset preset = presets.get(i);
            int col = i % 2;
            int row = i / 2;
            this.addRenderableWidget(Button.builder(Component.literal(preset.name()), b -> {
                        SortPresets.apply(preset);
                        if (this.minecraft.player != null) {
                            this.minecraft.player.displayClientMessage(
                                    Component.literal("ChestCat preset: " + preset.name()), true);
                        }
                        this.minecraft.setScreen(parent);
                    })
                    .bounds(x0 + col * (colW + gap), y0 + row * 22, colW, 20)
                    .tooltip(Tooltip.create(Component.literal(preset.description())))
                    .build());
        }

        int y = y0 + rows * 22 + 8;
        this.addRenderableWidget(Button.builder(Component.literal("My Saved Profiles..."),
                        b -> this.minecraft.setScreen(new SortProfilesScreen(this)))
                .bounds(this.width / 2 - colW - gap / 2, y, colW * 2 + gap, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - colW - gap / 2, y, colW * 2 + gap, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
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
