# Minestom issues found while implementing data-pack recipes, crafting, item tags and the bot client

Minestom version: `net.minestom:minestom:2026.10.05-26.2`.
Reference: decompiled vanilla 26.2 server (`server-26.2.jar`, SHA-1 of the bundle `823e2250d24b3ddac457a60c92a6a941943fcd6a`).

yuanban currently works around each issue below. Every workaround is marked in code with
`FIXME(Minestom M<n>)` and listed in the root `README.md`.

## M2. `SlotDisplay.SmithingTrim` encodes the trim pattern with the wrong type ~~PRed~~

Vanilla 26.2 `SlotDisplay.SmithingTrimDemoSlotDisplay` is
`(SlotDisplay base, SlotDisplay material, Holder<TrimPattern> pattern)` and its stream codec writes the
pattern with `TrimPattern.STREAM_CODEC` (a registry holder: `VarInt(id + 1)`, or `0` followed by an inline
pattern). Minestom's `SlotDisplay.SmithingTrim(SlotDisplay base, SlotDisplay trimMaterial, SlotDisplay trimPattern)`
(`SlotDisplay.java:88`) writes the third field as another `SlotDisplay`, which the client decodes as a
different value. Any recipe display containing it breaks the packet.

**Suggested fix:** change the third component to `Holder<TrimPattern>` and encode it with
`TrimPattern` holder network type.

**yuanban workaround:** in `:crafting`, `smithing_trim` recipes produce no recipe displays. Their item properties
(`smithing_template`, `smithing_base`, `smithing_addition`) are still provided.

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
ingredient in `SlotDisplay.WithRemainder`, and by `CraftingRecipe.defaultCraftingReminder` (and the special
recipes that override `getRemainingItems`) to decide what stays in the crafting grid. Minestom's material data
does not contain it.

In 26.2 the remainders are: `water_bucket`, `lava_bucket`, `milk_bucket` → `bucket`;
`dragon_breath`, `honey_bottle` → `glass_bottle`.

**Suggested fix:** add the crafting remainder to the generated material data.

**yuanban workaround:** `:crafting` contains a hard-coded table of the 26.2 values above (`CraftingRemainders`).

## M7. `Material` has no required feature flags

Vanilla filters recipe displays, item property sets and stonecutter recipes by
`Item.requiredFeatures()` against the enabled features. Minestom's material data does not contain the
required features. No 26.2 item requires a feature other than `minecraft:vanilla`, so this has no effect yet.

**Suggested fix:** add the required feature flags to the generated material data.

**yuanban workaround:** `:crafting` does not filter recipe displays, item property sets, stonecutter recipes or
crafting results.

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

Registering entries one by one is not enough for Registry Data either: an entry may reference an entry that comes
later in the same registry, or an entry of a registry that is synced later. The reference codecs resolve the name
while decoding (`RegistryCodecs.RegistryKeyImpl` and `HolderCodec`, `RegistryCodecs.java:35`, `:70`, which fail
with `Unknown key`), and `getKey(Key)` only knows names that already have a value (`DynamicRegistryImpl.java:124`).
The vanilla client accepts references in any order because `RegistryDataLoader` creates the lookups of every
registry before it decodes any entry.

**Suggested fix:** a public builder for a detached `DynamicRegistry` that is not subject to the freeze, lets the
caller create tags and declare every entry name before the values are decoded, and exposes each entry as soon as
its value is added.

**yuanban workaround:** `:bot` contains a helper class in package `net.minestom.server.registry` that calls the
package-private `DynamicRegistryImpl` constructor with collections it owns and writes to them directly. It first
declares the names of all entries of all synced registries (a name without a value yet, so `getKey(Key)` resolves
it), then adds the values in the server's order. This only works while Minestom runs on the class path.

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

## M10. `ChunkData` drops block entities whose type name is not a block name

`ChunkData.readBlockEntities` (`ChunkData.java:71`) resolves each block entity with
`Block.fromKey(blockEntityType.key())` and skips the entry when no block has that name (`ChunkData.java:80-81`).
Many block entity types are shared by several blocks and have no block of the same name: `sign`, `hanging_sign`,
`banner`, `skull`, `mob_spawner`, `brushable_block`, `shelf`, and others. Their data is lost when a client decodes a
chunk. For the remaining types the entry gets the default state of the same-named block instead of the state in the
chunk. Vanilla applies the data to the block entity created from the block at that position and ignores it when the
block has no block entity of that type (`LevelChunk.replaceWithPacketData`).

**Suggested fix:** keep the block entity type and NBT of each entry in `ChunkData` (or resolve the block from the
decoded sections once the dimension's minimum Y is known), instead of looking a block up by the type name.

**yuanban workaround:** the bot's TCP inbound thread (`decode.TcpInbound`) reads the Chunk Data and Update Light
payload itself: the block of each block entity is the block at that position in the section palette, with the NBT
attached; entries whose block has no block entity of that type are ignored, as in vanilla.

## M11. `PacketReading` applies server-side length limits to every reader

`PacketReading.readPacket` takes its limit from `maxPacketSize(state)` (`PacketReading.java:210-212`, `:292-296`),
which reads the global `ServerProperties.MAX_PACKET_SIZE_PRE_AUTH` (8192 bytes, `ServerProperties.java:52`) for
HANDSHAKE, STATUS and LOGIN, and `MAX_PACKET_SIZE` (2097151 bytes) otherwise. The same limit is applied to the
decompressed length (`PacketReading.java:248`). These are the limits a server puts on unauthenticated clients; a client
reading server packets gets disconnected by a large Login Plugin Request or Login Success, and by any packet whose
decompressed size exceeds 2 MiB. Vanilla clients only limit the frame length to a 3-byte VarInt
(`Varint21FrameDecoder`), do not check the decompressed length (`CompressionDecoder` with `validateDecompressed`
false), and fail when bytes are left after reading a packet (`PacketDecoder`), which `PacketReading` only logs.
The limits cannot be changed per reader without changing global system properties.

**Suggested fix:** let callers pass the frame and decompression limits (and the handling of leftover bytes) to
`readPacket`, or provide client-side presets.

**yuanban workaround:** the bot's TCP inbound thread (`decode.TcpInbound`) frames, decompresses and parses packets
itself with the vanilla client rules, using `PacketVanilla.SERVER_PACKET_PARSER` for the payload.

## M12. Entity type data has no baby or pose-specific dimensions and attachments

`EntityType.width`, `height` and `entityAttachments` describe the default adult dimensions only. Vanilla entity
classes replace the dimensions of babies (for example `Horse.BABY_DIMENSIONS` with a passenger attachment at
`height - 0.125` scaled by 0.7, `Pig.BABY_DIMENSIONS`, `Llama.BABY_DIMENSIONS`, `Zombie.BABY_DIMENSIONS`) and of some
poses (camel sitting dimensions, the `Avatar.POSES` of players and mannequins, including the fixed dying dimensions that
the scale attribute does not affect), so a client cannot compute where a passenger sits on such an entity, or the
bounding box of a baby or of a crouching player, from Minestom's data.

**Suggested fix:** include the baby and pose-specific dimensions with their attachments, and whether they are fixed, in
the generated entity data.

**yuanban workaround:** the bot's riding position (`movement.RidingSeat`) contains the vanilla 26.2 values for the
vehicle types it supports (horse, skeleton horse, zombie horse, donkey, mule, llama, pig, strider, nautilus, chicken,
camel, happy ghast), and its entity bounding boxes (`entity.EntityDimensions`) contain the vanilla 26.2 baby dimensions
of every entity class that can be a baby and the pose dimensions of players and mannequins.

## M13. Class initialization cycle between `GameProfile.Property`, `Codec` and `ResolvableProfile`

`GameProfile.Property` initializes `Codec` while building its `CODEC` (`GameProfile.java:63`). `Codec` initializes
`ComponentCodecs` for `Codec.COMPONENT` (`Codec.java:129`); `ComponentCodecs` initializes `ResolvableProfile` for the
player head object contents (`ComponentCodecs.java:253-259`); `ResolvableProfile.Partial` reads
`GameProfile.Property.LIST_CODEC` (`ResolvableProfile.java:75`), which is still null because the same thread is in the
middle of initializing `GameProfile.Property`. The resulting `NullPointerException` fails the initialization of
`ResolvableProfile`, `ComponentCodecs`, `Codec`, `GameProfile.Property` and `GameProfile`, and every later use of them
in the JVM throws `NoClassDefFoundError`. This happens whenever the first of these classes initialized in a JVM is
`GameProfile` (for example `new GameProfile(...)`, or `new LoginSuccessPacket(...)` whose serializer references
`GameProfile.SERIALIZER`) or `ResolvableProfile`. Initializing anything that initializes `Codec` first (most of
Minestom, including `MinecraftServer.init()` and `PacketVanilla`) avoids the cycle.

**Suggested fix:** break the cycle, for example by moving `Codec.COMPONENT` and `Codec.COMPONENT_STYLE` into a lazily
initialized holder so that initializing `Codec` does not initialize `ComponentCodecs`, or by making the
`ResolvableProfile` codecs reference `GameProfile.Property.LIST_CODEC` lazily.

**yuanban workaround:** the bot initializes `Codec` before it uses any of these classes (creating a bot initializes the
attribute, registry and packet classes), so the library itself is not affected. The bot tests construct every
`GameProfile` through `TestServer.profile`, which initializes `Codec` first.

## M14. `PlayerStatistic` has no `equals` or `hashCode` (PRed)

`Player.getStatisticValueMap()` (`Player.java:2239`) is a `Map<PlayerStatistic, Integer>`, but `PlayerStatistic`
(`PlayerStatistic.java:10`) is a plain class without `equals` and `hashCode`. Two instances for the same category and
statistic id are different keys, so a statistic cannot be looked up or incremented by key: every
`new PlayerStatistic(...)` adds a new entry, and the client receives duplicate statistics. Vanilla increments the
existing value (`ServerStatsCounter.increment`, capped at `Integer.MAX_VALUE`).

**Suggested fix:** make `PlayerStatistic` a record (or implement `equals` and `hashCode` over category and id).

**yuanban workaround:** `:crafting` finds the entry with the same category and id by iterating the map and updates
its value, and only puts a new key when there is none.

## M15. `AbstractInventory.removeViewer` drops the cursor item (not pr yet)

When a player stops viewing an inventory, `AbstractInventory.removeViewer` (`AbstractInventory.java:95`) drops the
cursor item with `Player.dropItem`, and adds it to the player inventory only when the `ItemDropEvent` is cancelled.
Vanilla `AbstractContainerMenu.removed` puts the carried item back into the player inventory
(`Inventory.placeItemBackInInventory`, which prefers the selected slot, then the offhand, then stacks with room,
then free slots) and drops it only when the player has been removed or disconnected.

**Suggested fix:** in `removeViewer`, place the cursor item back into the player inventory in vanilla order and
drop only what does not fit, or when the player is disconnecting.

**yuanban workaround:** the crafting table inventory of `:crafting` overrides `removeViewer` and puts the cursor item
back in vanilla order before calling `super.removeViewer`. Other inventories still drop the cursor item.

## M16. Closing the player inventory keeps the cursor item （not pr yet)

When the client closes its own inventory screen (window 0), `Player.closeInventory(true, 0)` (`Player.java:1941`)
fires `InventoryCloseEvent` and refreshes the inventory, but leaves the cursor item in the `PlayerInventory`. Vanilla
`InventoryMenu.removed` (through `AbstractContainerMenu.removed`) puts the carried item back into the player
inventory, so no item stays on the cursor while no screen is open.

**Suggested fix:** when window 0 is closed, place the cursor item back into the player inventory as vanilla does.

**yuanban workaround:** `:crafting` listens to `InventoryCloseEvent` for window 0 and puts the cursor item back in
vanilla order, together with the items of the 2×2 crafting grid.

## M17. `Material` prototypes are bound to the first `Registries.vanilla()` of the JVM

The constructor of `VanillaRegistries` ends by calling `bindComponents(this)` on every `Material`
(`VanillaRegistries.java:115-117`), which decodes the global, static item prototypes against that instance
(`RegistryData.java:544`). `bindComponents` returns early once a prototype is bound (`RegistryData.java:545`), so the
first `VanillaRegistries` created in a JVM decides the prototypes of every item for the rest of the JVM, and there is
no way to bind them again. `ServerProcessImpl` creates its registries with `Registries.vanilla()`
(`ServerProcessImpl.java:84`). When another `Registries.vanilla()` was created first, for example by a client that runs
without a server process, the prototypes of the server reference that other instance: the `damage_resistant` component
of netherite items and the `blocks_attacks` component of the shield hold damage type tags of the other instance, so
changes the server makes to its own tags do not reach them.

**Suggested fix:** keep item prototypes per `Registries` instance, or decode them lazily against the registries in use
(for example the server process), or offer a way to rebind them when a server process is created.

**yuanban workaround:** the bot uses the registries of the server process when one exists and constructs its own
`Registries.vanilla()` only when there is none. It cannot undo the binding, so `VanillaReference` throws an
`IllegalStateException` when it finds a server process after it has constructed its own registries; a JVM that needs a
server process must create it before using bots.

## M18. Chat packets cannot carry a chat type defined inline

The chat type of Player Chat Message and Disguised Chat Message is an `ID or Chat Type` field (vanilla
`ChatType.Bound.STREAM_CODEC`, which encodes the holder with `ByteBufCodecs.holder`): `0` means that the definition of
the chat type follows in the packet, any other value is the `minecraft:chat_type` registry id plus one.
`PlayerChatMessagePacket.msgTypeId` (`PlayerChatMessagePacket.java:38`) and `DisguisedChatPacket.type`
(`DisguisedChatPacket.java:20`) are plain `VAR_INT`s, so a packet with an inline chat type cannot be built, and when
one is read the definition is parsed as the fields that follow (the sender name and the rest of the packet). `ChatType`
has no network type, so `Holder.networkType` cannot be used for it either.

**Suggested fix:** add a network type for `ChatType` (the chat and narration `ChatTypeDecoration`s: translation key,
parameters and style) and change both fields to `Holder<ChatType>` encoded with
`Holder.networkType(Registries::chatType, ChatType.NETWORK_TYPE)`.

**yuanban workaround:** none possible on the client side. The bot resolves the chat type of both packets as registry id
`value - 1` and ends with an `IllegalStateException` when the value is `0`. Over TCP the definition is read as the
sender name first, which fails for translation keys such as `chat.type.text` (`Invalid NBT type id: 14`), so the bot
ends while decoding. Vanilla servers only send chat types from the registry.

## M19. `PlayerConnection.requestKnownPacks` can drop the client's response

`PlayerConnection.requestKnownPacks` (`PlayerConnection.java:286-292`) sends the Known Packs request first and only then
stores the pending future in `knownPacksFuture`, a plain field that is not volatile (`PlayerConnection.java:57`). The
response is handled on another thread: `receiveKnownPacksResponse` (`PlayerConnection.java:295`, called from
`LoginListener.java:233`) reads the field, and when the response arrives before the assignment (or before the assignment
is visible to that thread) it reads `null` and drops the response. `ConnectionManager.doConfiguration` then waits for
`KNOWN_PACKS_RESPONSE_TIMEOUT` (5 minutes by default, `ConnectionManager.java:270`) and disconnects the player; the field
stays set, so a later request throws `Known packs already pending`. A connection whose `sendPacket` delivers the
response before it returns reproduces this every time.

**Suggested fix:** store the future before sending the request, in a volatile field or an `AtomicReference` that
`receiveKnownPacksResponse` clears with `getAndSet(null)`.

**yuanban workaround:** the bot's embedded connection (`transport.embedded.EmbeddedPlayerConnection`) overrides
`requestKnownPacks` and `receiveKnownPacksResponse` and keeps the pending future in an `AtomicReference` that is set
before the request is sent. Over TCP the server side is Minestom's `PlayerSocketConnection`, which the bot cannot change:
the response has to pass the server's write thread, the network and the bot's scheduler, so it usually arrives after the
assignment, but nothing guarantees it.

## M20. Direct biome palettes use one bit too many when the biome count is a power of two

`Palette.biomeSerializer` (`Palette.java:229-231`) computes the bits of a direct biome palette with
`MathUtils.bitsToRepresent(biomeCount)`, the bit length of `biomeCount` itself. Vanilla uses `Mth.ceillog2(registry.size())`
(`Strategy.minimumBitsRequiredForDistinctValues`), the bits needed for the largest id `biomeCount - 1`. The two differ when
the biome count is a power of two: for 64 biomes Minestom uses 7 bits and 8 longs, vanilla 6 bits and 7 longs.
`ChunkData.Section.networkType` and `DynamicChunk` (`DynamicChunk.java:285`) use this serializer, so:

- a client that reads with Minestom rejects the direct biome palettes a vanilla server sends
  (`Invalid bitsPerEntry: 6`, `Palette.java:262`);
- a Minestom server writes palettes that vanilla clients misread: for a global palette `PalettedContainer.read` sizes the
  storage with `ceillog2` whatever bit count the packet declares, so it reads one long too few and the following sections
  are shifted.

Direct biome palettes are only used for sections with more than 8 distinct biomes. The default 26.2 biome registry has 66
entries, for which both give 7 bits.

**Suggested fix:** use `ceillog2(biomeCount)`, that is `bitsToRepresent(biomeCount - 1)` for counts above 1 and `0`
otherwise.

**yuanban workaround:** the bot reads the biome palettes of Chunk Data and Update Light and of Chunk Biomes with
`Palette.serializer` and `ceillog2(biome count)` direct bits (`decode.SectionDecoder.biomeSerializer`). A section that a
Minestom server writes with a direct biome palette while the biome count is a power of two ends the bot with
`Invalid bitsPerEntry`; vanilla clients cannot read it either.
