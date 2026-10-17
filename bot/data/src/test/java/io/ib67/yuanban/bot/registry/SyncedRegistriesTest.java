package io.ib67.yuanban.bot.registry;

import io.ib67.yuanban.bot.testing.VanillaServer;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundUpdateTagsPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncedRegistriesTest {
    @Test
    void fullDataKeepsServerOrderAndIds() {
        assertSameEntries(build(VanillaServer.registryData(false)));
    }

    @Test
    void omittedDataIsTakenFromTheVanillaNetworkCodec() {
        var registries = build(VanillaServer.registryData(true));
        assertSameEntries(registries);
        var overworld = registries.dimensionType(VanillaServer.overworldId());
        assertEquals(new DimensionInfo(VanillaServer.OVERWORLD, -64, 384), overworld);
    }

    @Test
    void dimensionTypeDataInThePacketIsUsed() {
        var packets = new ArrayList<ClientboundRegistryDataPacket>();
        for (var packet : VanillaServer.registryData(false)) {
            if (packet.getRegistry().equals(SyncedRegistries.DIMENSION_TYPE)) {
                var entries = new ArrayList<>(packet.getEntries());
                var overworld = entries.get(VanillaServer.overworldId());
                var data = Objects.requireNonNull(overworld.getData()).toBuilder().putInt("min_y", 0).putInt("height", 256).build();
                entries.add(new RegistryEntry(Key.key("test:flat"), data));
                packet = new ClientboundRegistryDataPacket(packet.getRegistry(), entries);
            }
            packets.add(packet);
        }
        var registries = build(packets);
        var id = registries.size(SyncedRegistries.DIMENSION_TYPE) - 1;
        assertEquals(new DimensionInfo(Key.key("test:flat"), 0, 256), registries.dimensionType(id));
        assertThrows(IllegalStateException.class, () -> registries.dimensionType(id + 1));
    }

    @Test
    void omittedDataOfAnUnknownEntryFails() {
        var builder = new SyncedRegistriesBuilder();
        builder.startConfiguration();
        builder.accept(new ClientboundRegistryDataPacket(SyncedRegistries.DIMENSION_TYPE,
                List.of(new RegistryEntry(Key.key("test:unknown"), null))));
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void firstRegistryDataOfAReconfigurationClearsPreviousRegistries() {
        var builder = new SyncedRegistriesBuilder();
        builder.startConfiguration();
        VanillaServer.registryData(false).forEach(builder::accept);
        builder.startConfiguration();
        var chatTypes = VanillaServer.registryData(false).stream()
                .filter(packet -> packet.getRegistry().equals(SyncedRegistries.CHAT_TYPE))
                .findFirst().orElseThrow();
        assertTrue(builder.accept(chatTypes));
        assertEquals(Set.of(SyncedRegistries.CHAT_TYPE), builder.registryNames());
        var registries = builder.build();
        assertEquals(VanillaServer.registryEntries(SyncedRegistries.CHAT_TYPE).size(), registries.size(SyncedRegistries.CHAT_TYPE));
        assertEquals(0, registries.biomeCount());
        assertEquals(0, registries.size(SyncedRegistries.DIMENSION_TYPE));
        // 同一个配置阶段中之后的 Registry Data 不再清空
        var damageTypes = VanillaServer.registryData(false).stream()
                .filter(packet -> packet.getRegistry().equals(SyncedRegistries.DAMAGE_TYPE))
                .findFirst().orElseThrow();
        assertFalse(builder.accept(damageTypes));
        assertEquals(Set.of(SyncedRegistries.CHAT_TYPE, SyncedRegistries.DAMAGE_TYPE), builder.registryNames());
    }

    @Test
    void idsAreReplacedByEntryNames() {
        var registries = build(VanillaServer.registryData(true));
        for (var registry : List.of(SyncedRegistries.CHAT_TYPE, SyncedRegistries.DAMAGE_TYPE, SyncedRegistries.WORLD_CLOCK)) {
            var vanilla = VanillaServer.registryEntries(registry);
            assertEquals(vanilla.getLast().getId(), registries.requireEntry(registry, vanilla.size() - 1));
            assertNull(registries.entry(registry, vanilla.size()));
            assertNull(registries.entry(registry, -1));
            assertThrows(IllegalStateException.class, () -> registries.requireEntry(registry, vanilla.size()));
        }
        assertEquals(Key.key("chat"), registries.requireEntry(SyncedRegistries.CHAT_TYPE,
                VanillaServer.registryId(SyncedRegistries.CHAT_TYPE, Key.key("chat"))));
    }

    @Test
    void biomeCountIsTheSizeOfTheBiomeRegistry() {
        var registries = build(VanillaServer.registryData(true));
        assertEquals(VanillaServer.biomeCount(), registries.biomeCount());
        assertTrue(registries.biomeCount() > 0);
    }

    @Test
    void tagsAreReplacedOneByOne() {
        var block = Key.key("block");
        var pickaxe = Key.key("mineable/pickaxe");
        var tags = TagTable.EMPTY.apply(VanillaServer.tags());
        assertTrue(tags.tagNames(block).contains(pickaxe));
        tags = tags.apply(new ClientboundUpdateTagsPacket(Map.of(block, Map.of(pickaxe, new int[]{1, 2}))));
        assertTrue(tags.contains(block, pickaxe, 2));
        assertFalse(tags.contains(block, pickaxe, 3));
        // 数据包中没有提到的标签保留
        assertTrue(tags.tagNames(block).contains(Key.key("mineable/axe")));
        assertFalse(tags.contains(Key.key("test:unknown"), pickaxe, 1));
        assertFalse(tags.contains(block, Key.key("test:unknown"), 1));
    }

    @Test
    void tagsOfRemovedRegistriesAreDropped() {
        var tags = TagTable.EMPTY.apply(VanillaServer.tags());
        var enchantment = Key.key("enchantment");
        assertFalse(tags.tagNames(enchantment).isEmpty());
        tags = tags.without(Set.of(enchantment));
        assertTrue(tags.tagNames(enchantment).isEmpty());
        assertFalse(tags.tagNames(Key.key("block")).isEmpty());
    }

    private static SyncedRegistries build(List<ClientboundRegistryDataPacket> packets) {
        var builder = new SyncedRegistriesBuilder();
        builder.startConfiguration();
        packets.forEach(builder::accept);
        return builder.build();
    }

    private static void assertSameEntries(SyncedRegistries registries) {
        for (var packet : VanillaServer.registryData(false)) {
            var registry = packet.getRegistry();
            var names = packet.getEntries().stream().map(RegistryEntry::getId).toList();
            assertEquals(names, registries.entries(registry), registry.asString());
        }
        var dimensionTypes = VanillaServer.registryEntries(SyncedRegistries.DIMENSION_TYPE);
        for (int id = 0; id < dimensionTypes.size(); id++) {
            var data = Objects.requireNonNull(dimensionTypes.get(id).getData());
            var info = registries.dimensionType(id);
            assertEquals(dimensionTypes.get(id).getId(), info.name());
            assertEquals(data.getInt("min_y"), info.minY());
            assertEquals(data.getInt("height"), info.height());
        }
    }
}
