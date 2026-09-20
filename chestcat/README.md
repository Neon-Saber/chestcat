# ChestCat

A NeoForge 1.21.1 mod that does two separate things, exactly as scoped:

1. **Cross-chest category sorting.** Press a keybind (default `N`) to open a
   menu listing every chest/barrel within 16 blocks, its assigned category,
   and how full it is. Hit "Sort All Nearby" and the server pulls every item
   out of every one of those chests, figures out each item's category, and
   redistributes everything so one chest ends up with blocks, another with
   tools, etc. It **never** touches your inventory.
2. **Inventory sorting.** A separate keybind (default `,`) sorts your own
   main inventory grid (stacks merged, grouped by category) in place. It
   **never** sends anything into a chest.

Categories can be assigned two ways, per your request:
- **Auto-detect**: the first time an unassigned chest is included in a sort,
  its category is guessed from majority-vote of whatever's already in it,
  and that guess is locked in (saved) so it doesn't keep flip-flopping.
- **Manual**: sneak + right-click a chest/barrel with an empty hand to open
  a category picker, or click a chest's row in the "Nearby Chests" menu.

Everything is GUI/keybind driven — no commands, no signs required.

## ⚠️ Important: this has not been compiled or run

This was written directly as source, in an environment with no access to
Mojang's or NeoForge's Maven repositories, so **it has not been built or
tested in-game**. Treat it as a solid architectural starting point, not a
finished download. Things most likely to need a small fix once you build it
against the real NeoForge 1.21.1 libraries in an IDE:

- **`ChestUtil.canonicalChestPos` / `ChestBlock.getContainer`** — the exact
  static method signature on `ChestBlock` for fetching a merged double-chest
  `Container` can vary slightly by mapping set (official vs Parchment). If it
  doesn't compile, open NeoForge's decompiled `ChestBlock` class in your IDE
  and match the real signature — the logic/intent won't need to change.
- **`RegisterPayloadHandlersEvent` / `PayloadRegistrar`** — NeoForge's
  networking API (`net.chestcat.network.NetworkHandler`) moved around a bit
  across 1.20.5–1.21.x. If your exact NeoForge build (`neo_version` in
  `gradle.properties`) uses different event/class names, check NeoForge's
  "Networking" docs page for that version. The payload record classes
  themselves are stable either way.
- **`ItemStack.getFoodProperties(null)`** in `ItemCategory.java` — recent
  1.21.x versions changed food-related APIs a couple of times; if this
  doesn't compile, swap it for whatever the current
  `FoodProperties`/`Consumable` accessor is on your NeoForge version.
- Any other small API drift — 1.21.1 is very close to what this was written
  against, but I'd budget 15–30 minutes of "red squiggly line" fixes in your
  IDE before your first successful build.

## Project layout

```
chestcat/
  build.gradle, settings.gradle, gradle.properties   - NeoGradle build setup
  src/main/resources/META-INF/neoforge.mods.toml      - mod metadata
  src/main/java/net/chestcat/
    ChestCat.java              - mod entry point
    ItemCategory.java          - the category enum + item classification
    ChestUtil.java             - finds nearby chests/barrels, merges double chests
    ChestSorter.java           - the cross-chest sort algorithm
    InventorySorter.java       - the inventory-only sort algorithm
    data/ChestCategoryData.java  - persists manual category assignments (SavedData)
    network/                   - packets + server-side handlers
    client/                    - keybinds, GUI screens, client-side packet handling
                                  (never loaded on a dedicated server)
```

## Building

1. Install a JDK 21.
2. Open the `chestcat/` folder in IntelliJ IDEA (recommended) or your editor
   of choice with Gradle support.
3. Let Gradle sync — this downloads NeoForge 1.21.1 (`neo_version` in
   `gradle.properties`) and generates run configurations.
4. Fix any compile errors per the caveats above.
5. Run the `client` Gradle run configuration to test in a dev environment,
   or `./gradlew build` to produce a `.jar` in `build/libs/` to drop into
   your real `mods/` folder (client) and your server's `mods/` folder.

Since you chose "server-side (reliable, reads real chest data directly)",
install the built jar on **both** your client and your server/singleplayer
world — the sorting logic runs authoritatively on the server, but the
keybind + GUI menu are client-side code that has to be present to trigger it.

## Tuning

- Search radius: `NetworkHandler.DEFAULT_RADIUS` (16 blocks by default) —
  used for both the chest-list menu and the actual sort.
- Categories: edit the `ItemCategory` enum and its `categorize()` method to
  add/remove/reclassify categories.
- Default keybinds: `ChestCatClient.OPEN_MENU_KEY` / `SORT_INVENTORY_KEY`
  (players can rebind in Controls regardless).
