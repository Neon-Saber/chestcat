package net.chestcat;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Drops tag-derived caches whenever tags (re)load, so categories never go stale after a datapack reload or world join. */
@EventBusSubscriber(modid = "chestcat")
public final class SortCaches {

    private SortCaches() {}

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        SortCategory.clearCache();
        SortKeys.clearCaches();
    }
}
