package io.ib67.yuanban.bot.registry;

import io.ib67.yuanban.bot.testing.VanillaServer;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置阶段累积 Registry Data 的规则，与 {@link SyncedRegistries} 的完整内容分开检查。
 */
class SyncedRegistriesBuilderTest {
    private static ClientboundRegistryDataPacket packet(Key registry, String... entries) {
        return new ClientboundRegistryDataPacket(registry, java.util.Arrays.stream(entries)
                .map(name -> new RegistryEntry(Key.key(name), null))
                .toList());
    }

    @Test
    void theFirstRegistryDataOfAPhaseDropsThePreviousPhase() {
        var builder = new SyncedRegistriesBuilder();
        builder.startConfiguration();
        // 一个阶段中只有第一个 Registry Data 清空之前的内容，之后的不再清空
        assertTrue(builder.accept(packet(SyncedRegistries.BIOME, "minecraft:plains")));
        assertFalse(builder.accept(packet(SyncedRegistries.BIOME, "minecraft:desert")));
        assertEquals(List.of(Key.key("minecraft:plains"), Key.key("minecraft:desert")),
                builder.build().entries(SyncedRegistries.BIOME));

        // 进入下一个配置阶段：之前的注册表被清空
        builder.startConfiguration();
        assertTrue(builder.accept(packet(SyncedRegistries.DAMAGE_TYPE, "minecraft:generic")));
        var registries = builder.build();
        assertTrue(registries.entries(SyncedRegistries.BIOME).isEmpty());
        assertEquals(List.of(Key.key("minecraft:generic")), registries.entries(SyncedRegistries.DAMAGE_TYPE));
    }

    @Test
    void entriesOfTheSameRegistryAccumulateInOrder() {
        var builder = new SyncedRegistriesBuilder();
        builder.startConfiguration();
        var entries = VanillaServer.registryEntries(SyncedRegistries.DAMAGE_TYPE);
        var first = new ClientboundRegistryDataPacket(SyncedRegistries.DAMAGE_TYPE, entries.subList(0, 5));
        var second = new ClientboundRegistryDataPacket(SyncedRegistries.DAMAGE_TYPE, entries.subList(5, entries.size()));
        builder.accept(first);
        builder.accept(second);
        var registries = builder.build();
        assertEquals(entries.stream().map(RegistryEntry::getId).toList(), registries.entries(SyncedRegistries.DAMAGE_TYPE));
    }

    @Test
    void omittedDataIsTakenFromTheVanillaRegistry() {
        var builder = new SyncedRegistriesBuilder();
        builder.startConfiguration();
        builder.accept(VanillaServer.registryData(true).stream()
                .filter(packet -> packet.getRegistry().equals(SyncedRegistries.DIMENSION_TYPE))
                .findFirst().orElseThrow());
        var registries = builder.build();
        var overworld = registries.dimensionType(VanillaServer.overworldId());
        assertEquals(new DimensionInfo(VanillaServer.OVERWORLD, -64, 384), overworld);
    }

    @Test
    void omittedDataForAnUnknownEntryFails() {
        var builder = new SyncedRegistriesBuilder();
        builder.startConfiguration();
        builder.accept(new ClientboundRegistryDataPacket(SyncedRegistries.DIMENSION_TYPE,
                List.of(new RegistryEntry(Key.key("test:unknown"), null))));
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void dataInThePacketWinsOverTheVanillaData() {
        var builder = new SyncedRegistriesBuilder();
        builder.startConfiguration();
        var data = org.cloudburstmc.nbt.NbtMap.builder()
                .putInt("min_y", 0)
                .putInt("height", 256)
                .build();
        builder.accept(new ClientboundRegistryDataPacket(SyncedRegistries.DIMENSION_TYPE,
                List.of(new RegistryEntry(Key.key("test:custom"), data))));
        assertEquals(new DimensionInfo(Key.key("test:custom"), 0, 256), builder.build().dimensionType(0));
    }

    @Test
    void namesAreReportedBeforeBuilding() {
        var builder = new SyncedRegistriesBuilder();
        assertTrue(builder.registryNames().isEmpty());
        builder.startConfiguration();
        builder.accept(packet(SyncedRegistries.BIOME, "minecraft:plains"));
        assertEquals(java.util.Set.of(SyncedRegistries.BIOME), builder.registryNames());
    }
}
