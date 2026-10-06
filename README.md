# yuanban

yuanban reimplements vanilla (aka. Notchian) Minecraft contents in Minestom.

## Minestom workarounds

The following places work around missing features or bugs in Minestom `2026.10.05-26.2`. Each one is marked
in code with `FIXME(Minestom M<n>)`; the details and suggested upstream fixes are in
[MINESTOM-BUG-REPORT.md](MINESTOM-BUG-REPORT.md). Remove the workaround once the upstream fix is released.

| Id | Module / location | Workaround | Remaining limitation |
|---|---|---|---|
| M1 | `item-recipe`, `MinestomWorkarounds.invalidateDeclareRecipes` | Invalidates the private `RecipeManager.declareRecipesPacket` via reflection after replacing recipes, then calls `Player.refreshRecipes()` for online players | Minestom must be on the class path (or `--add-opens net.minestom.server/net.minestom.server.recipe=ALL-UNNAMED`) |
| M2 | `item-recipe`, `MinestomRecipe.displays` | `smithing_trim` recipes produce no recipe displays | No recipe book entries for smithing trim recipes |
| M3 | `item-recipe`, `MinestomRecipe.stonecutterDisplay` | A stonecutter ingredient that is a single item without remainder is displayed as `SlotDisplay.Item` | Stonecutter recipes whose ingredient lists several items are not sent to the client |
| M4 | `item-recipe`, `MinestomRecipe.recipeBookGroup` | None | Recipe book entries are not grouped |
| M5 | `item-tag`, `net.minestom.server.registry.TagContentWriter` | Calls the package-private `RegistryTagImpl.Backed.add/remove` from a class in Minestom's package | Minestom must be on the class path |
| M6 | `item-recipe`, `MinestomWorkarounds.craftingRemainder` | Hard-coded crafting remainders of vanilla 26.2 | Only correct for 26.2 |
| M7 | `item-recipe`, `MinestomRecipe` | No feature flag filtering of recipe displays and item properties | No 26.2 item requires a non-vanilla feature, so there is no effect yet |
| M8 | `bot`, `net.minestom.server.registry.ClientDynamicRegistry` | Creates `DynamicRegistryImpl` through its package-private constructor and appends entries to the collections it was given | Minestom must be on the class path |
| M9 | `item-tag`, `ItemTagSubscriber.apply` | Calls the `@ApiStatus.Internal` `ConnectionManager.invalidateTags()` after replacing the item tags | Depends on an internal Minestom method |

