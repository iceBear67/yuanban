# yuanban

yuanban reimplements vanilla (aka. Notchian) Minecraft contents in Minestom.

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
| M8 | `bot`, `net.minestom.server.registry.ClientDynamicRegistry` | Creates `DynamicRegistryImpl` through its package-private constructor, declares the names of all entries first and then adds the values to the collections it was given | Minestom must be on the class path |
| M9 | `item-tag`, `ItemTagSubscriber.apply` | Calls the `@ApiStatus.Internal` `ConnectionManager.invalidateTags()` after replacing the item tags | Depends on an internal Minestom method |
| M10 | `bot`, `decode.TcpInbound` | Reads the block entities of Chunk Data itself; the block of each entry is the block at that position in the section palette | Depends on the internal `PacketVanilla` and on the Chunk Data layout |
| M11 | `bot`, `decode.TcpInbound` | Frames, decompresses and parses server packets itself with the vanilla client limits instead of `PacketReading` | Depends on the internal `PacketVanilla` |
| M12 | `bot`, `movement.RidingSeat` | Hard-coded vanilla 26.2 baby and sitting dimensions and attachments of rideable entities | Only correct for 26.2 |
| M13 | `bot` tests, `TestServer.profile` | Initializes `Codec` before constructing a `GameProfile` | Code that initializes `GameProfile`, `LoginSuccessPacket` or `ResolvableProfile` before any other Minestom class still fails |
| M14 | `crafting`, `internal.menu.CraftingStats` | Finds the statistic entry with the same category and id by iterating `Player.getStatisticValueMap()` | None |
| M15 | `crafting`, `internal.menu.CraftingTableInventory.removeViewer` | Puts the cursor item back into the player inventory before calling `super.removeViewer` | Other inventories still drop the cursor item |
| M16 | `crafting`, `internal.menu.CraftingEvents` | Puts the cursor item back into the player inventory when window 0 is closed | None |
| M17 | `bot`, `registry.VanillaReference` | Uses the registries of the server process when there is one; throws when a server process appears after the bot constructed its own vanilla registries | A JVM that needs a server process must create it before using bots |
| M18 | `bot`, `protocol.ProtocolMachine.chatType` | Resolves the chat type of Player Chat Message and Disguised Chat Message from the registry and throws for `0` (a chat type defined inline) | Chat messages with an inline chat type end the bot |
| M19 | `bot`, `transport.embedded.EmbeddedPlayerConnection` | Overrides `requestKnownPacks` and `receiveKnownPacksResponse` so that the pending future is stored before the request is sent | Over TCP a Minestom server can still drop the response; depends on two `@ApiStatus.Internal` methods |
| M20 | `bot`, `decode.SectionDecoder.biomeSerializer` | Reads direct biome palettes of Chunk Data and Chunk Biomes with `ceillog2(biome count)` bits, as vanilla does | Sections a Minestom server writes with a direct biome palette while the biome count is a power of two end the bot, as they break vanilla clients |

