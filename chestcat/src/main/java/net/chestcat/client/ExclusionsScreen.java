package net.chestcat.client;

import net.chestcat.ExclusionRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * "Never move this" rules. Anything matching a rule is left exactly where it is by every ChestCat sort,
 * quick stack and dump. Type a rule (mod:create, cat:food, item:minecraft:netherite_sword, tag:c:ores) or
 * use the quick toggles for common categories.
 */
@OnlyIn(Dist.CLIENT)
public class ExclusionsScreen extends Screen {

    private static final String[][] QUICK = {
            {"Food", "cat:food"}, {"Armor", "cat:armor"}, {"Tools", "cat:tools"}, {"Combat", "cat:combat"}
    };

    private final Screen parent;
    private final List<String> rules;
    private EditBox input;
    private String error = "";
    private int listTop = 0;

    public ExclusionsScreen(Screen parent) {
        super(Component.literal("Ignore Rules"));
        this.parent = parent;
        this.rules = new ArrayList<>(SortSettings.exclusions);
    }

    private void commit() {
        SortSettings.exclusions = new ArrayList<>(rules);
        SortSettings.sync();
        this.rebuildWidgets();
    }

    private int top() {
        return Math.max(30, this.height / 2 - 100);
    }

    @Override
    protected void init() {
        String keep = input == null ? "" : input.getValue();
        int cx = this.width / 2;
        int y = top();

        input = new EditBox(this.font, cx - 154, y, 200, 20, Component.literal("Rule"));
        input.setMaxLength(80);
        input.setHint(Component.literal("mod:create  cat:food  item:...  tag:c:ores  menu:..."));
        input.setValue(keep);
        this.addRenderableWidget(input);
        this.addRenderableWidget(Button.builder(Component.literal("Add"), b -> addTyped())
                .bounds(cx + 50, y, 104, 20).build());
        y += 24;

        // Quick toggles for the most common "never sort this" categories.
        int bw = 74;
        for (int i = 0; i < QUICK.length; i++) {
            String label = QUICK[i][0];
            String rule = QUICK[i][1];
            boolean on = rules.contains(rule);
            this.addRenderableWidget(Button.builder(Component.literal((on ? "[x] " : "[ ] ") + label), b -> {
                        if (rules.contains(rule)) rules.remove(rule);
                        else if (rules.size() < ExclusionRules.MAX_RULES) rules.add(rule);
                        commit();
                    })
                    .bounds(cx - 154 + i * (bw + 2), y, bw, 20).build());
        }
        y += 24;

        // Ignore the type of container that was open when the options were opened (shulker box, hopper, modded chest...).
        String menuId = ClientUi.currentMenuId;
        if (menuId != null && !menuId.isEmpty()) {
            String rule = "menu:" + menuId;
            boolean on = rules.contains(rule);
            this.addRenderableWidget(Button.builder(
                            Component.literal((on ? "[x] " : "[ ] ") + "Never sort this container type"),
                            b -> {
                                if (rules.contains(rule)) rules.remove(rule);
                                else if (rules.size() < ExclusionRules.MAX_RULES) rules.add(rule);
                                commit();
                            })
                    .bounds(cx - 154, y, 308, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                            "Containers of type " + menuId + " are skipped by sorting and auto-sort.")))
                    .build());
            y += 24;
        }
        y += 6;
        listTop = y;

        // Current rules: two columns, text drawn in render(), an "x" button each.
        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            int col = i / 6;
            int row = i % 6;
            int rx = cx - 154 + col * 156;
            this.addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                        rules.remove(index);
                        commit();
                    })
                    .bounds(rx + 134, y + row * 20, 18, 18).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.minecraft.setScreen(parent))
                .bounds(cx - 154, y + 6 * 20 + 8, 308, 20).build());
        this.setInitialFocus(input);
    }

    private void addTyped() {
        String text = input.getValue().strip();
        if (text.isEmpty()) return;
        String normalized = ExclusionRules.normalize(text);
        if (normalized.isEmpty()) {
            error = "Not a valid rule. Use item:, mod:, cat:, tag: or menu: followed by a name.";
            return;
        }
        error = "";
        for (String r : normalized.split(";")) {
            if (!rules.contains(r) && rules.size() < ExclusionRules.MAX_RULES) rules.add(r);
        }
        input.setValue("");
        commit();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && input != null && input.isFocused()) {
            addTyped();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, top() - 18, 0xFFFFFF);
        if (!error.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(error, this.width - 10)),
                    cx, top() + 22 + 22 + 4 - 12, 0xFFFF6060);
        }

        if (rules.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal("No rules yet - everything is sorted normally."),
                    cx, listTop + 6, 0xFF9AA4B2);
        }
        for (int i = 0; i < rules.size(); i++) {
            int col = i / 6;
            int row = i % 6;
            int rx = cx - 154 + col * 156;
            String text = this.font.plainSubstrByWidth(rules.get(i), 128);
            graphics.drawString(this.font, text, rx + 2, listTop + row * 20 + 5, 0xFFEDEDED);
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
