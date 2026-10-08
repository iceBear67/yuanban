package io.ib67.yuanban.codec;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.Holder;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.TagKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.ib67.yuanban.codec.CodecTestSupport.decode;
import static io.ib67.yuanban.codec.CodecTestSupport.encode;
import static io.ib67.yuanban.codec.CodecTestSupport.error;
import static io.ib67.yuanban.codec.CodecTestSupport.registryOps;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RegistryCodecs}、{@link RegistryOps}、{@link RegistryLookup}、{@link HolderSet}：错误文本取自原版 26.2 服务端。
 */
@EnvTest
class RegistryCodecsTest {
    private static final Codec<HolderSet<Material>> ITEMS = RegistryCodecs.homogeneousList(RegistryIds.ITEM);

    @Test
    void homogeneousList(Env env) {
        DynamicOps<JsonElement> ops = registryOps(env.process());
        assertEquals(HolderSet.direct(List.of(RegistryKey.of("minecraft:apple"))), decode(ITEMS, ops, "\"minecraft:apple\""));
        assertEquals(HolderSet.direct(List.of(RegistryKey.of("minecraft:apple"), RegistryKey.of("minecraft:stone"))),
                decode(ITEMS, ops, "[\"minecraft:apple\", \"stone\"]"));
        assertEquals(HolderSet.named(Key.key("minecraft:planks")), decode(ITEMS, ops, "\"#minecraft:planks\""));
        assertEquals("\"#minecraft:planks\"", encode(ITEMS, ops, HolderSet.named(Key.key("minecraft:planks"))));
        assertEquals("\"minecraft:apple\"", encode(ITEMS, ops, HolderSet.direct(List.of(RegistryKey.of("minecraft:apple")))));
        assertEquals("Missing tag: 'minecraft:x' in 'minecraft:item'", error(ITEMS, ops, "\"#minecraft:x\""));
        assertEquals("Failed to parse either. First: Not a tag id; Second: Failed to parse either. First: Not a json array: "
                + "\"minecraft:nope\"; Second: Failed to get element minecraft:nope", error(ITEMS, ops, "\"minecraft:nope\""));
        assertEquals("Failed to get element minecraft:nope", error(ITEMS, ops, "[\"minecraft:apple\", \"minecraft:nope\"]"));
        assertEquals("Failed to parse either. First: Not a valid resource location: Bad Non [a-z0-9/._-] character in path of location: "
                + "minecraft:Bad; Second: Failed to parse either. First: Not a json array: \"#Bad\"; Second: Not a valid resource location: "
                + "#Bad Non [a-z0-9/._-] character in path of location: minecraft:#Bad", error(ITEMS, ops, "\"#Bad\""));
        assertEquals("Failed to parse either. First: Not a string: 5; Second: Failed to parse either. First: Not a json array: 5; "
                + "Second: Not a string: 5", error(ITEMS, ops, "5"));
    }

    @Test
    void homogeneousListWithoutRegistry() {
        assertEquals("Can't access registry ResourceKey[minecraft:root / minecraft:item]", error(ITEMS, JsonOps.INSTANCE, "[\"minecraft:apple\"]"));
        assertEquals(HolderSet.direct(List.of()), decode(ITEMS, JsonOps.INSTANCE, "[]"));
    }

    @Test
    void fixed(Env env) {
        Codec<RegistryKey<Enchantment>> enchantment = RegistryCodecs.fixed(RegistryIds.ENCHANTMENT);
        DynamicOps<JsonElement> ops = registryOps(env.process());
        assertEquals(RegistryKey.of("minecraft:sharpness"), decode(enchantment, ops, "\"minecraft:sharpness\""));
        assertEquals("Failed to get element minecraft:nope", error(enchantment, ops, "\"minecraft:nope\""));
        assertEquals("Can't access registry ResourceKey[minecraft:root / minecraft:enchantment]",
                error(enchantment, JsonOps.INSTANCE, "\"minecraft:sharpness\""));
    }

    @Test
    void file(Env env) {
        Codec<Holder<TrimPattern>> pattern = RegistryCodecs.file(RegistryIds.TRIM_PATTERN, MinestomCodecs.adapt(TrimPattern.REGISTRY_CODEC), true);
        DynamicOps<JsonElement> ops = registryOps(env.process());
        assertEquals(RegistryKey.of("minecraft:sentry"), decode(pattern, ops, "\"minecraft:sentry\""));
        assertEquals("Failed to get element ResourceKey[minecraft:trim_pattern / minecraft:nope]", error(pattern, ops, "\"minecraft:nope\""));
        Holder<TrimPattern> inline = decode(pattern, ops,
                "{\"asset_id\": \"foo:bar\", \"description\": \"x\", \"decal\": false}");
        assertInstanceOf(TrimPattern.class, inline);
        Codec<Holder<TrimPattern>> noInline = RegistryCodecs.file(RegistryIds.TRIM_PATTERN, MinestomCodecs.adapt(TrimPattern.REGISTRY_CODEC), false);
        assertEquals("Inline definitions not allowed here", error(noInline, ops, "{\"asset_id\": \"foo:bar\", \"description\": \"x\"}"));
    }

    @Test
    void byNameAndTagKeys() {
        Codec<Block> block = RegistryCodecs.byName(RegistryIds.BLOCK, Block::fromKey, Block::key);
        assertEquals(Block.STONE, decode(block, "\"stone\""));
        assertEquals("Unknown registry key in ResourceKey[minecraft:root / minecraft:block]: minecraft:nope", error(block, "\"minecraft:nope\""));
        assertEquals("Not a tag id", error(RegistryCodecs.hashedTagKey(RegistryIds.ITEM), "\"foo\""));
        assertEquals(TagKey.<Material>of(Key.key("foo:bar")), decode(RegistryCodecs.hashedTagKey(RegistryIds.ITEM), "\"#foo:bar\""));
        assertEquals(TagKey.<Material>of(Key.key("foo:bar")), decode(RegistryCodecs.tagKey(RegistryIds.ITEM), "\"foo:bar\""));
        assertEquals(RegistryKey.<Material>of("foo:bar"), decode(RegistryCodecs.resourceKey(RegistryIds.ITEM), "\"foo:bar\""));
    }

    @Test
    void lookupOverride(Env env) {
        // 订阅者用同一次加载的结果替换物品标签
        var lookup = RegistryLookup.builder(env.process())
                .put(RegistryIds.ITEM, RegistryGetter.of(List.of(Key.key("minecraft:apple")),
                        Map.of(Key.key("example:fruit"), List.of(Key.key("minecraft:apple")))))
                .build();
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        assertEquals(HolderSet.named(Key.key("example:fruit")), decode(ITEMS, ops, "\"#example:fruit\""));
        assertEquals("Missing tag: 'minecraft:planks' in 'minecraft:item'", error(ITEMS, ops, "\"#minecraft:planks\""));
        assertEquals("Failed to get element minecraft:stone", error(ITEMS, ops, "[\"minecraft:stone\"]"));
        // 注册表不存在时报告原版文本
        DynamicOps<JsonElement> empty = RegistryOps.create(JsonOps.INSTANCE, lookup);
        assertEquals("Can't access registry ResourceKey[minecraft:root / minecraft:worldgen/structure]",
                error(RegistryCodecs.fixed(RegistryIds.STRUCTURE), empty, "\"minecraft:village_plains\""));
    }

    @Test
    void registrationGetterAcceptsEverything(Env env) {
        // 同一次加载的注册表：解码时引用一律成立，全部文件解码之后再检查
        var lookup = RegistryLookup.builder(env.process()).put(RegistryIds.ENCHANTMENT, RegistryGetter.registration()).build();
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        assertEquals(RegistryKey.of("example:anything"), decode(RegistryCodecs.fixed(RegistryIds.ENCHANTMENT), ops, "\"example:anything\""));
        assertEquals(HolderSet.named(Key.key("example:any")),
                decode(RegistryCodecs.homogeneousList(RegistryIds.ENCHANTMENT), ops, "\"#example:any\""));
    }

    @Test
    void registryOpsDelegates(Env env) {
        var lookup = RegistryLookup.builder(env.process()).build();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        JsonElement element = CodecTestSupport.json("{\"a\": 1}");
        assertTrue(ops.convertTo(JsonOps.INSTANCE, element) == element);
        RegistryOps<net.kyori.adventure.nbt.BinaryTag> nbt = ops.withParent(NbtOps.INSTANCE);
        assertEquals(net.kyori.adventure.nbt.CompoundBinaryTag.builder().putByte("a", (byte) 1).build(), ops.convertTo(nbt, element));
        assertTrue(nbt.lookup() == lookup);
        assertEquals(RegistryKey.of("minecraft:apple"), RegistryCodecs.fixed(RegistryIds.ITEM)
                .parse(nbt, net.kyori.adventure.nbt.StringBinaryTag.stringBinaryTag("minecraft:apple")).getOrThrow());
        assertTrue(HolderSet.direct(List.of(RegistryKey.<Material>of("minecraft:apple"))).toRegistryTag()
                .contains(RegistryKey.of("minecraft:apple")));
    }

    @Test
    void holderSetContains(Env env) {
        var registry = env.process().material();
        assertTrue(HolderSet.<Material>named(Key.key("minecraft:planks")).contains(Key.key("minecraft:oak_planks"), registry));
        assertFalse(HolderSet.<Material>named(Key.key("example:missing")).contains(Key.key("minecraft:oak_planks"), registry));
        assertTrue(HolderSet.direct(List.of(RegistryKey.<Material>of("minecraft:apple"))).contains(Key.key("minecraft:apple"), registry));
    }

    @Test
    void registryIdPrinting() {
        assertEquals("ResourceKey[minecraft:root / minecraft:item]", RegistryIds.ITEM.toString());
        assertEquals("ResourceKey[minecraft:trim_pattern / minecraft:sentry]", RegistryIds.TRIM_PATTERN.element(Key.key("minecraft:sentry")));
        assertEquals(35, MapDecorationTypes.IDS.size());
        assertEquals(Key.key("minecraft:player"), MapDecorationTypes.IDS.getFirst());
    }
}
