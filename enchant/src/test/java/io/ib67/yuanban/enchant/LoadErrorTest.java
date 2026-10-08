package io.ib67.yuanban.enchant;

import io.ib67.yuanban.datapack.LoadFailedException;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.server.item.Material;
import net.minestom.server.registry.TagKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
@ExtendWith(ResetItemTags.class)
class LoadErrorTest {
    private static final String ENCHANTMENT_REGISTRY = "ResourceKey[minecraft:root / minecraft:enchantment]";

    @TempDir
    Path tempDir;

    @Test
    void reportsEveryBrokenFile(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("tags/enchantment/exclusive_set/damage.json", """
                        {"values": ["minecraft:good"]}
                        """)
                .vanilla("enchantment/good.json", """
                        {"description": "Good", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"]}
                        """)
                .vanilla("enchantment/bad_weight.json", """
                        {"description": "Bad weight", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 0, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"]}
                        """)
                .vanilla("enchantment/bad_anvil.json", """
                        {"description": "Bad anvil", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": -1, "slots": ["mainhand"]}
                        """)
                .vanilla("enchantment/broken_json.json", """
                        {"description": "Broken", "supported_items":
                        """)
                .vanilla("enchantment/missing_tag.json", """
                        {"description": "Missing item tag", "supported_items": "#minecraft:enchantable/nope",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"]}
                        """)
                .vanilla("enchantment/missing_exclusive.json", """
                        {"description": "Missing exclusive", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"],
                         "exclusive_set": "#minecraft:exclusive_set/nope"}
                        """)
                .vanilla("enchantment/unknown_effect.json", """
                        {"description": "Unknown component", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"],
                         "effects": {"minecraft:nope": {}}}
                        """);

        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        var loader = world.loader(process, enchantments, enchantmentTags, providers);

        var exception = assertInstanceOf(EnchantmentException.class,
                assertThrows(LoadFailedException.class, () -> loader.start(EnchantWorld.createWorld())).getCause());
        List<String> lines = Arrays.asList(exception.getMessage().split("\n"));
        // 5 个坏文件按资源 ID 排序，最后是冻结注册表时的引用检查
        assertEquals(6, lines.size(), exception.getMessage());
        assertEquals("Couldn't parse data file 'minecraft:bad_anvil' from [vanilla] data/minecraft/enchantment/bad_anvil.json: "
                + "Value must be non-negative: -1", lines.get(0));
        assertEquals("Couldn't parse data file 'minecraft:bad_weight' from [vanilla] data/minecraft/enchantment/bad_weight.json: "
                + "Value must be within range [1;1024]: 0", lines.get(1));
        // JSON 语法错误和解码错误一起汇总
        assertTrue(lines.get(2).startsWith("Couldn't parse data file 'minecraft:broken_json' from [vanilla] data/minecraft/enchantment/broken_json.json: "),
                lines.get(2));
        assertEquals("Couldn't parse data file 'minecraft:missing_tag' from [vanilla] data/minecraft/enchantment/missing_tag.json: "
                + "Missing tag: 'minecraft:enchantable/nope' in 'minecraft:item'", lines.get(3));
        assertTrue(lines.get(4).startsWith("Couldn't parse data file 'minecraft:unknown_effect' from [vanilla] data/minecraft/enchantment/unknown_effect.json: "
                + "Unknown registry key in ResourceKey[minecraft:root / minecraft:enchantment_effect_component_type]: minecraft:nope"), lines.get(4));
        assertEquals("Unbound tags in registry " + ENCHANTMENT_REGISTRY + ": [minecraft:exclusive_set/nope]", lines.get(5));

        // 加载失败的时候没有调用任何 apply：注册表保持 Minestom 自带的状态
        assertNotNull(Material.staticRegistry().getTag(TagKey.of("minecraft:enchantable/equippable")));
        assertEquals(EnchantmentData.EMPTY, enchantments.current());
        assertEquals(EnchantmentTags.EMPTY, enchantmentTags.current());
        assertEquals(EnchantmentProviderData.EMPTY, providers.current());
        // 注册表里的附魔保持 Minestom 自带的 43 个
        assertNotNull(env.process().enchantment().get(net.kyori.adventure.key.Key.key("minecraft:aqua_affinity")));
    }

    @Test
    void badMaxLevelAndUnknownProviderType(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment/bad_max_level.json", """
                        {"description": "Bad max_level", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 300, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"]}
                        """)
                .pack("broken", "example", "enchantment_provider/bad_type.json", """
                        {"type": "minecraft:wand"}
                        """);
        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        var loader = world.loader(process, enchantments, enchantmentTags, providers);

        var exception = assertThrows(LoadFailedException.class, () -> loader.start(EnchantWorld.createWorld()));
        assertEquals("Couldn't parse data file 'minecraft:bad_max_level' from [vanilla] data/minecraft/enchantment/bad_max_level.json: "
                + "Value must be within range [1;255]: 300", assertInstanceOf(EnchantmentException.class, exception.getCause()).getMessage());
        // 附魔解码失败在前，提供器的文件连解码都没有开始
        assertTrue(Arrays.stream(exception.getSuppressed()).noneMatch(it -> it instanceof EnchantmentException),
                exception.getMessage());
    }

    @Test
    void accumulatesEveryFieldError(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment/many.json", """
                        {"description": "Many", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 0, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1}, "anvil_cost": -1, "slots": ["mainhand", "nope"]}
                        """);
        var message = loadFailure(env, world).getMessage();
        assertTrue(message.startsWith("Couldn't parse data file 'minecraft:many' from [vanilla] data/minecraft/enchantment/many.json: "), message);
        assertTrue(message.contains("Value must be within range [1;1024]: 0"), message);
        assertTrue(message.contains("No key per_level_above_first in MapLike["), message);
        assertTrue(message.contains("Value must be non-negative: -1"), message);
        assertTrue(message.contains("Unknown element name:nope"), message);
        assertEquals(1, message.split("\n").length, message);
    }

    @Test
    void reportsUnknownEffectAndOtherEntries(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment/effects.json", """
                        {"description": "Effects", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"],
                         "effects": {"minecraft:nope": {}, "minecraft:item_damage": 5, "minecraft:prevent_equipment_drop": {}}}
                        """);
        var message = loadFailure(env, world).getMessage();
        assertTrue(message.contains("Unknown registry key in ResourceKey[minecraft:root / minecraft:enchantment_effect_component_type]: minecraft:nope"),
                message);
        // 两个失败的条目都在 dispatchedMap 的 missed input 中，能够解码的条目不在其中
        assertTrue(message.endsWith(" missed input: {\"minecraft:nope\":{},\"minecraft:item_damage\":5}"), message);
    }

    @Test
    void reportsUnknownItem(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("enchantment/unknown_item.json", """
                        {"description": "Unknown item", "supported_items": ["minecraft:iron_sword", "minecraft:nope_item"],
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"]}
                        """);
        var message = loadFailure(env, world).getMessage();
        assertTrue(message.contains("Failed to get element minecraft:nope_item"), message);
    }

    @Test
    void reportsUnboundExclusiveSetElements(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment/good.json", """
                        {"description": "Good", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"],
                         "exclusive_set": ["example:b", "minecraft:good", "minecraft:a", "example:b"]}
                        """)
                .vanilla("enchantment/tagged.json", """
                        {"description": "Tagged", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"],
                         "exclusive_set": "#minecraft:nope"}
                        """);
        // 原版先比较路径再比较命名空间；有未绑定的元素时不再报告未绑定的标签
        assertEquals("Unbound values in registry " + ENCHANTMENT_REGISTRY + ": [minecraft:a, example:b]",
                loadFailure(env, world).getMessage());
    }

    @Test
    void reportsProviderJsonSyntaxErrors(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("enchantment_provider/broken.json", """
                        {"type": "minecraft:single",
                        """)
                .vanilla("enchantment_provider/wand.json", """
                        {"type": "minecraft:wand"}
                        """);
        var lines = loadFailure(env, world).getMessage().split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].startsWith("Couldn't parse data file 'minecraft:broken' from [vanilla] data/minecraft/enchantment_provider/broken.json: "),
                lines[0]);
        assertEquals("Couldn't parse data file 'minecraft:wand' from [vanilla] data/minecraft/enchantment_provider/wand.json: "
                + "Unknown registry key in ResourceKey[minecraft:root / minecraft:enchantment_provider_type]: minecraft:wand", lines[1]);
    }

    private static EnchantmentException loadFailure(Env env, EnchantWorld world) {
        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        return assertInstanceOf(EnchantmentException.class,
                assertThrows(LoadFailedException.class,
                        () -> world.loader(process, enchantments, enchantmentTags, providers).start(EnchantWorld.createWorld())).getCause());
    }
}
