# yuanban

yuanban reimplements vanilla (aka. Notchian) Minecraft contents in Minestom.

## Status

Reimplementation status:

 - Datapack loader framework is done.
 - Redesigned recipe system, and datapack recipe loader/parser module.
 - Datapack based tag loader
 - Datapack based loot table
 - Enchantments reimplementation.

Framework status:

 - Network protocol based player simulation (`bot`) library is mostly done. It creates bot and provides methods to simulate common player actions.  
Further improvements are expected. Like pathfinding etc.

For a detailed documentation, check [docs/bot/getting-started.md](docs/bot/getting-started.md)

   - An extensible integration test framework based on `bot`. It provides DSL to define stories.  
Stories are unit tests that describe expected server states after player interactions.
```kotlin
@Story
fun `Spleef 竞技场中最后留下的玩家获胜`() = story {
    val arena = arena("Spleef_Classic")
    val alice = player("alice").also { it.joins(arena) }
    val bob = player("bob").also { it.joins(arena) }

    "bob 挖掉 alice 脚下的雪块，alice 坠入虚空淘汰" {
        bob.breaks(alice.below)
    }

    // 断言竞技场胜利者
    arena.winner shouldBe bob.username
}
```

This testing framework helps us to write easily readable tests that guides agent as a ground fact. We call this executable specifications.    
It should lay a foundation of automatic implementation and verification for vanilla features.

For a detailed documentation (written by agents), check docs/story-testing.

## Vanilla server data in tests

Several modules test against the data of the vanilla 26.2 server. The root project downloads the server bundle
from Mojang once, checks its SHA-1 and extracts `server-26.2.jar` into the root `build/vanilla/server/`
(task `:extractVanillaServer`); every module uses this single copy. A module whose tests need it adds:

```groovy
tasks.test {
    dependsOn(":extractVanillaServer")
    systemProperty("yuanban.vanilla-server", rootProject.ext.vanillaServerJar)
}
```

## Minestom workarounds

The following places work around missing features or bugs in Minestom `2026.10.05-26.2`. Each one is marked
in code with `FIXME(Minestom M<n>)`; the details and suggested upstream fixes are in
[MINESTOM-BUG-REPORT.md](MINESTOM-BUG-REPORT.md). Remove the workaround once the upstream fix is released.

| Id | Module / location | Workaround | Remaining limitation |
|---|---|---|---|
| M2 | `crafting`, `SmithingTrimRecipe.display` | `smithing_trim` recipes produce no recipe displays | No recipe book entries for smithing trim recipes |
| M5 | `item-tag`, `net.minestom.server.registry.TagContentWriter` | Calls the package-private `RegistryTagImpl.Backed.add/remove` from a class in Minestom's package | Minestom must be on the class path |
| M6 | `crafting`, `CraftingRemainders` | Hard-coded crafting remainders of vanilla 26.2 | Only correct for 26.2 |
| M7 | `crafting`, `RecipeManager` | No feature flag filtering of recipe displays, item properties and stonecutter recipes | No 26.2 item requires a non-vanilla feature, so there is no effect yet |
| M9 | `item-tag`, `ItemTagSubscriber.apply` | Calls the `@ApiStatus.Internal` `ConnectionManager.invalidateTags()` after replacing the item tags | Depends on an internal Minestom method |
| M12 | `bot`, `movement.RidingSeat`, `entity.EntityDimensions` | Minestom's entity type data (`entity_type.json` in `net.minestom:data`) has no baby, sitting or pose dimensions and attachments, so the bot hard-codes the vanilla 26.2 baby and sitting dimensions and attachments of rideable entities, and the baby and player pose dimensions of entity bounding boxes | Only correct for 26.2 |
| M14 | `crafting`, `internal.menu.CraftingStats` | Finds the statistic entry with the same category and id by iterating `Player.getStatisticValueMap()` | None |
| M15 | `crafting`, `internal.menu.CraftingTableInventory.removeViewer` | Puts the cursor item back into the player inventory before calling `super.removeViewer` | Other inventories still drop the cursor item |
| M16 | `crafting`, `internal.menu.CraftingEvents` | Puts the cursor item back into the player inventory when window 0 is closed | None |
| M22 | `dfu-codecs`, `ComponentSerialization` | Decodes and encodes text components with a port of vanilla `ComponentSerialization.CODEC` (including the entity selector, NBT path and block position parsers) and converts the result to adventure `Component`, instead of using Minestom's `Codec.COMPONENT` | Data that adventure cannot hold fails to decode: relative block coordinates with a fraction in `nbt` block sources, and the cape, elytra and model of a player head skin. A hex colour equal to a named colour is encoded as the colour name. `show_item` components are encoded in another order |
| M23 | `dfu-codecs`, `MapDecorationTypes` | Lists the ids of the vanilla 26.2 `map_decoration_type` registry, because Minestom has no such registry | Only correct for 26.2 |
| M24 | `story`, `runtime.SharedServer` | Replaces the play and configuration listeners of `ClientKeepAlivePacket` so that Minestom's `KeepAliveListener` runs at the start of the next tick, on the same thread as `ConnectionManager.updateWaitingPlayers` | Code under test that broadcasts play packets off the tick thread can still reach a player after Start Configuration |

