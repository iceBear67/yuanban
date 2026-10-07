package io.ib67.yuanban.bot.registry;

import io.ib67.yuanban.bot.TestServer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.minestom.server.network.packet.server.configuration.RegistryDataPacket;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRegistriesTest {
    @Test
    void fullDataKeepsServerIds() {
        assertSameIds(build(false));
    }

    @Test
    void omittedDataIsTakenFromVanilla() {
        assertSameIds(build(true));
    }

    @Test
    void firstRegistryDataOfAReconfigurationClearsPreviousRegistries() {
        var builder = new ClientRegistriesBuilder();
        builder.startConfiguration();
        TestServer.registryData(false).forEach(packet -> builder.accept((RegistryDataPacket) packet));
        builder.startConfiguration();
        var chatTypes = TestServer.registryData(false).stream()
                .map(RegistryDataPacket.class::cast)
                .filter(packet -> packet.registryId().equals("minecraft:chat_type"))
                .findFirst().orElseThrow();
        assertTrue(builder.accept(chatTypes));
        var registries = builder.build(TagTable.EMPTY);
        assertEquals(TestServer.vanilla().chatType().size(), registries.chatType().size());
        assertEquals(0, registries.biome().size());
        assertEquals(0, registries.dimensionType().size());
    }

    @Test
    void tagsOfSyncedRegistriesAreCreated() {
        var tags = TagTable.EMPTY.apply(Registries.tagsPacket(TestServer.vanilla()));
        var builder = new ClientRegistriesBuilder();
        builder.startConfiguration();
        TestServer.registryData(false).forEach(packet -> builder.accept((RegistryDataPacket) packet));
        var registries = builder.build(tags);
        var vanillaTag = TestServer.vanilla().enchantment().tags().iterator().next();
        assertNotNull(registries.enchantment().getTag(vanillaTag.key()));
        assertTrue(tags.tagNames("minecraft:block").contains("minecraft:mineable/pickaxe"));
    }

    @Test
    void entriesMayReferenceEntriesDeclaredLater() {
        var builder = new ClientRegistriesBuilder();
        builder.startConfiguration();
        for (var packet : TestServer.registryData(false)) {
            var data = (RegistryDataPacket) packet;
            if (data.registryId().equals("minecraft:enchantment")) {
                var entries = new ArrayList<>(data.entries());
                var sharpness = (CompoundBinaryTag) entries.stream().filter(entry -> entry.id().equals("minecraft:sharpness"))
                        .findFirst().orElseThrow().data();
                // test:fire_blade 的 exclusive_set 列出排在它后面的 test:ice_blade：原版先为全部注册表登记条目名称，允许这样的引用
                var exclusive = ListBinaryTag.builder().add(StringBinaryTag.stringBinaryTag("test:ice_blade")).build();
                entries.add(new RegistryDataPacket.Entry("test:fire_blade", sharpness.put("exclusive_set", exclusive)));
                entries.add(new RegistryDataPacket.Entry("test:ice_blade", sharpness));
                data = new RegistryDataPacket(data.registryId(), entries);
            }
            builder.accept(data);
        }
        var registries = builder.build(TagTable.EMPTY.apply(Registries.tagsPacket(TestServer.vanilla())));
        var enchantments = registries.enchantment();
        var fireBlade = enchantments.get(Key.key("test:fire_blade"));
        assertNotNull(fireBlade);
        assertTrue(fireBlade.exclusiveSet().contains(RegistryKey.of("test:ice_blade")));
        assertEquals(enchantments.size() - 1, enchantments.getId(RegistryKey.of("test:ice_blade")));
    }

    private static ClientRegistries build(boolean excludeVanilla) {
        var builder = new ClientRegistriesBuilder();
        builder.startConfiguration();
        TestServer.registryData(excludeVanilla).forEach(packet -> builder.accept((RegistryDataPacket) packet));
        return builder.build(TagTable.EMPTY.apply(Registries.tagsPacket(TestServer.vanilla())));
    }

    private static void assertSameIds(ClientRegistries registries) {
        var vanilla = TestServer.vanilla();
        for (var spec : ClientRegistries.SYNCED) {
            assertSameIds(spec, vanilla, registries);
        }
    }

    private static <T> void assertSameIds(ClientRegistries.Spec<T> spec, Registries vanilla, ClientRegistries registries) {
        assertSameIds(spec.select().apply(vanilla), spec.select().apply(registries));
    }

    private static <T> void assertSameIds(DynamicRegistry<T> expected, DynamicRegistry<T> actual) {
        assertEquals(expected.size(), actual.size(), expected.key().asString());
        for (int id = 0; id < expected.size(); id++) {
            assertEquals(expected.getKey(id), actual.getKey(id), expected.key() + " id " + id);
            assertNotNull(actual.get(id));
        }
    }
}
