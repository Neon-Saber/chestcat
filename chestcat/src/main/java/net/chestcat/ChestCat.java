package net.chestcat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

/**
 * Main mod entry point. Most of the actual logic lives in:
 *   - ChestSorter / InventorySorter (the algorithms)
 *   - ChestUtil (finding + reading/writing chests, incl. double chest merging)
 *   - data.ChestCategoryData (persisted per-chest category assignments)
 *   - network.NetworkHandler (packet registration + server-side packet handling)
 *   - client.* (keybinds, GUI screens, client-side packet handling) - CLIENT ONLY,
 *     safe on a dedicated server because it's never loaded there.
 */
@Mod("chestcat")
public class ChestCat {

    public ChestCat(IEventBus modEventBus, ModContainer modContainer) {
        // NetworkHandler, ChestCatClient and CategoryAssignInteractHandler self-register
        // via @EventBusSubscriber, so nothing else is required here for now.
        // If you add config options later, register them on modContainer here.
    }
}
