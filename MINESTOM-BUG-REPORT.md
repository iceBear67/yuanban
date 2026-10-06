# Minestom issues found while implementing data-pack recipes, item tags and the bot client

Minestom version: `net.minestom:minestom:2026.10.05-26.2`.
Reference: decompiled vanilla 26.2 server (`server-26.2.jar`, SHA-1 of the bundle `823e2250d24b3ddac457a60c92a6a941943fcd6a`).

yuanban currently works around each issue below. Every workaround is marked in code with
`FIXME(Minestom M<n>)` and listed in the root `README.md`.

## M1. `RecipeManager` never invalidates the cached `DeclareRecipesPacket`

`RecipeManager.declareRecipesPacket` (`RecipeManager.java:38`) is a `CachedPacket` built from the
registered recipes. `addRecipe` and `removeRecipe` do not call `invalidate()` on it, so once the packet
has been computed (the first player join calls `Player.refreshRecipes()`), later changes to the recipe set
never reach the item property sets (`furnace_input`, `smithing_base`, ...) or the stonecutter recipe list.
Vanilla rebuilds both on every `/reload` (`RecipeManager.finalizeRecipeLoading`).

**Suggested fix:** call `declareRecipesPacket.invalidate()` at the end of `addRecipe` and `removeRecipe`.

**yuanban workaround:** after replacing its recipes, `:item-recipe` reads the private field via reflection,
calls `invalidate()`, then calls `Player.refreshRecipes()` for every online player.

## M2. `SlotDisplay.SmithingTrim` encodes the trim pattern with the wrong type

Vanilla 26.2 `SlotDisplay.SmithingTrimDemoSlotDisplay` is
`(SlotDisplay base, SlotDisplay material, Holder<TrimPattern> pattern)` and its stream codec writes the
pattern with `TrimPattern.STREAM_CODEC` (a registry holder: `VarInt(id + 1)`, or `0` followed by an inline
pattern). Minestom's `SlotDisplay.SmithingTrim(SlotDisplay base, SlotDisplay trimMaterial, SlotDisplay trimPattern)`
(`SlotDisplay.java:88`) writes the third field as another `SlotDisplay`, which the client decodes as a
different value. Any recipe display containing it breaks the packet.

**Suggested fix:** change the third component to `Holder<TrimPattern>` and encode it with
`TrimPattern` holder network type.

**yuanban workaround:** `smithing_trim` recipes produce no recipe displays. Their item properties
(`smithing_template`, `smithing_base`, `smithing_addition`) are still provided.

## M3. Stonecutter recipes are derived from displays and drop direct item ingredients

`RecipeManager.createDeclareRecipesPacket` rebuilds each stonecutter ingredient from the recipe display
(`ingredientFromSlotDisplay`, `RecipeManager.java:144`), which only accepts `SlotDisplay.Item` and
`SlotDisplay.Tag`. Vanilla `Ingredient.display()` turns a direct item list (including a single item) into
`SlotDisplay.Composite`, so a stonecutter recipe whose display follows vanilla is silently dropped from the
packet. All 319 vanilla 26.2 stonecutting recipes use a direct single-item ingredient.

Vanilla sends the `Ingredient` itself in `ClientboundUpdateRecipesPacket`
(`SelectableRecipe.SingleInputEntry(Ingredient input, SelectableRecipe recipe)`), and leaves out recipes that
cannot be placed (`PlacementInfo.isImpossibleToPlace()`, e.g. the ingredient is an empty tag). Deriving the list
from displays cannot express that either.

**Suggested fix:** let the recipe provide its stonecutter ingredient (for example a
`Recipe.stonecutterInput()` method) instead of reverse-engineering it from the display.

**yuanban workaround:** when a stonecutter ingredient is a single item without a crafting remainder, its
display is `SlotDisplay.Item` instead of `SlotDisplay.Composite`. Stonecutter recipes whose ingredient lists
several items are still missing on the client.

## M4. Recipe book groups are ignored

`RecipeManager.addRecipe` (`RecipeManager.java:54`, `//todo groups`) always passes `null` as the group of
`RecipeBookAddPacket.Entry` and never reads `Recipe.recipeBookGroup()`. Vanilla assigns an integer per
distinct non-empty group string, in recipe order (`RecipeManager.unpackRecipeInfo`), so recipes of the same
group are collapsed in the client recipe book.

**Suggested fix:** map `recipeBookGroup()` to a stable integer id in `RecipeManager` and pass it to the entry.

**yuanban workaround:** none. `recipeBookGroup()` is implemented so that the fix takes effect without
changes on our side.

## M5. No public API to change the contents of static registry tags

`Registry.getOrCreateTag` and `Registry.removeTag` are public, but the only way to change the entries of a
tag, `RegistryTagImpl.Backed.add` / `remove` (`RegistryTagImpl.java:71`, `:77`), is package-private. Item
tags therefore cannot follow data packs, although vanilla reloads them on every `/reload` and resolves
recipe ingredients against them.

**Suggested fix:** a public method that replaces the entries of a tag (keeping the tag instance), which
invalidates the cached tags packet once.

**yuanban workaround:** `:item-tag` contains a helper class in package `net.minestom.server.registry` that
calls the package-private methods. This only works while Minestom runs on the class path.

## M6. `Material` has no crafting remainder data

Vanilla `Item.getCraftingRemainder()` is used by `Ingredient.display()` to wrap each item of a direct
ingredient in `SlotDisplay.WithRemainder`. Minestom's material data does not contain it.

In 26.2 the remainders are: `water_bucket`, `lava_bucket`, `milk_bucket` → `bucket`;
`dragon_breath`, `honey_bottle` → `glass_bottle`.

**Suggested fix:** add the crafting remainder to the generated material data.

**yuanban workaround:** a hard-coded table of the 26.2 values above.

## M7. `Material` has no required feature flags

Vanilla filters recipe displays, item property sets and stonecutter recipes by
`Item.requiredFeatures()` against the enabled features. Minestom's material data does not contain the
required features. No 26.2 item requires a feature other than `minecraft:vanilla`, so this has no effect yet.

**Suggested fix:** add the required feature flags to the generated material data.

**yuanban workaround:** no filtering.

## M8. Dynamic registries cannot be built by a client in the same JVM

A client needs `Registries` whose ids follow the server's Registry Data, for example to decode play packets
and to resolve dimension types. `DynamicRegistry` is sealed and its only implementation, `DynamicRegistryImpl`,
can only be filled through `register` (`DynamicRegistryImpl.java:136`). `register` throws
`UnsupportedOperationException` once a server process in the same JVM has started (`isFrozen()`,
`DynamicRegistryImpl.java:330`), and `DynamicRegistry.fromMap` goes through `register` as well.

`fromMap` also creates the registry only after every value has been decoded. Entries that reference tags of
their own registry, such as the `exclusive_set` of enchantments, decode through `RegistryTag.codec`, which looks
the tag up in the registry being built (`RegistryCodecs.java:129`), so they cannot be decoded before the registry
exists. Minestom's own loader avoids this by creating the tags first and then registering entries one by one
(`loadStaticJsonRegistry`, `DynamicRegistryImpl.java:339`).

**Suggested fix:** a public builder for a detached `DynamicRegistry` that is not subject to the freeze, lets the
caller create tags before adding entries, and exposes each entry as soon as it is added.

**yuanban workaround:** `:bot` contains a helper class in package `net.minestom.server.registry` that calls the
package-private `DynamicRegistryImpl` constructor with collections it owns and appends entries to them directly.
This only works while Minestom runs on the class path.

## M9. Creating or removing a tag does not invalidate the cached tags packet

`ConnectionManager` caches the tags packet (`cachedTagsPacket`) and only invalidates it from
`RegistryTagImpl.Backed.add` / `remove`. `Registry.getOrCreateTag` and `Registry.removeTag`
(`StaticRegistry.java:112`, `:117`, `DynamicRegistryImpl.java:223`, `:228`) change the set of tags without
invalidating it. When a reload only removes an empty tag or creates an empty tag, no entry is added or removed,
so both newly joining players and players sent the packet with `sendRegistryTags` still receive the old tag list.
`ConnectionManager.invalidateTags()` is public but marked `@ApiStatus.Internal`.

**Suggested fix:** invalidate the cached tags packet in `getOrCreateTag` (when a tag is created) and in
`removeTag` (when a tag was removed).

**yuanban workaround:** `:item-tag` calls `ConnectionManager.invalidateTags()` once after it has replaced the
item tags, before sending them to online players.
