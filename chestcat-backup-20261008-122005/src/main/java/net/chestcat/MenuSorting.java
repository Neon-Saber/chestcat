package net.chestcat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes ChestCat work with containers that aren't vanilla chests (shulker boxes, hoppers, dispensers and
 * most modded storage screens) without knowing about any of them. A menu counts as "storage" when every slot
 * that isn't the player's own inventory accepts an ordinary block and can be picked up - which excludes
 * furnaces, crafting grids, anvils, brewing stands, villager trades and horse saddle slots automatically,
 * since each of those has a slot that refuses arbitrary items.
 */
public final class MenuSorting {

    private MenuSorting() {}

    private static final int MIN_SLOTS = 5;

    /** The menu's non-player slots if it is a plain storage menu, otherwise an empty list. */
    public static List<Slot> storageSlots(AbstractContainerMenu menu, Player player) {
        if (menu == null || player == null) return List.of();
        Inventory inv = player.getInventory();
        ItemStack probe = new ItemStack(Items.COBBLESTONE);
        List<Slot> result = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container == inv) continue;
            if (!slot.mayPlace(probe) || !slot.mayPickup(player)) return List.of();
            result.add(slot);
        }
        return result.size() >= MIN_SLOTS ? result : List.of();
    }

    public static boolean isStorageMenu(AbstractContainerMenu menu, Player player) {
        return !storageSlots(menu, player).isEmpty();
    }

    /** Registry id of the menu type ("minecraft:generic_9x3"), or "" when it has none (e.g. the survival inventory). */
    public static String menuId(AbstractContainerMenu menu) {
        if (menu == null) return "";
        try {
            var key = BuiltInRegistries.MENU.getKey(menu.getType());
            return key == null ? "" : key.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** A list of menu slots presented as one {@link Container}, so the normal container sorter can handle it. */
    public static Container view(List<Slot> slots) {
        return new SlotsView(slots);
    }

    private static final class SlotsView implements Container {
        private final List<Slot> slots;

        SlotsView(List<Slot> slots) {
            this.slots = slots;
        }

        @Override
        public int getContainerSize() {
            return slots.size();
        }

        @Override
        public boolean isEmpty() {
            for (Slot s : slots) {
                if (s.hasItem()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            return slots.get(index).getItem();
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            return slots.get(index).remove(count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            Slot slot = slots.get(index);
            ItemStack stack = slot.getItem();
            slot.set(ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            slots.get(index).set(stack);
        }

        @Override
        public void setChanged() {
            for (Slot s : slots) s.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (Slot s : slots) s.set(ItemStack.EMPTY);
        }
    }
}
