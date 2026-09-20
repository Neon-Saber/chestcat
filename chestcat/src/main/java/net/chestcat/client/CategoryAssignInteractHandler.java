package net.chestcat.client;

import net.chestcat.ChestUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = "chestcat", value = Dist.CLIENT)
public class CategoryAssignInteractHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!mc.player.isShiftKeyDown()) return;
        if (!event.getEntity().getMainHandItem().isEmpty()) return;

        var be = mc.level.getBlockEntity(event.getPos());
        if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) return;

        event.setCanceled(true);
        mc.setScreen(new CategoryPickerScreen(event.getPos(), null));
    }
}
