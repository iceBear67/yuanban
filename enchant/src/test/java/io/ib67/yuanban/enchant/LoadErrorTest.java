package io.ib67.yuanban.enchant;

import io.ib67.yuanban.datapack.LoadFailedException;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
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
    @TempDir
    Path tempDir;

    @Test
    void reportsEveryBrokenFile(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("tags/enchantment/exclusive_set/damage.json", """
                        {"values": ["minecraft:sharpness"]}
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
        // 5 个坏文件，顺序按资源 ID 排序
        assertEquals(5, lines.size());
        assertTrue(lines.get(0).startsWith("Couldn't parse data file 'minecraft:bad_anvil' from [vanilla] data/minecraft/enchantment/bad_anvil.json: "), lines.get(0));
        assertTrue(lines.get(0).contains("anvil_cost"), lines.get(0));
        assertTrue(lines.get(1).startsWith("Couldn't parse data file 'minecraft:bad_weight' from [vanilla] data/minecraft/enchantment/bad_weight.json: "), lines.get(1));
        assertTrue(lines.get(1).contains("weight"), lines.get(1));
        assertTrue(lines.get(2).startsWith("Couldn't parse data file 'minecraft:missing_exclusive' from [vanilla] data/minecraft/enchantment/missing_exclusive.json: "), lines.get(2));
        assertTrue(lines.get(2).contains("Missing enchantment tag"), lines.get(2));
        assertTrue(lines.get(3).startsWith("Couldn't parse data file 'minecraft:missing_tag' from [vanilla] data/minecraft/enchantment/missing_tag.json: "), lines.get(3));
        assertTrue(lines.get(3).contains("Missing item tag"), lines.get(3));
        assertTrue(lines.get(4).startsWith("Couldn't parse data file 'minecraft:unknown_effect' from [vanilla] data/minecraft/enchantment/unknown_effect.json: "), lines.get(4));
        assertTrue(lines.get(4).contains("Unknown enchantment effect component"), lines.get(4));

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
        assertTrue(exception.getMessage().contains("max_level"), exception.getMessage());
        // 附魔解码失败在前，提供器的文件连解码都没有开始
        assertTrue(Arrays.stream(exception.getSuppressed()).noneMatch(it -> it instanceof EnchantmentException),
                exception.getMessage());
    }
}
