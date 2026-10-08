package io.ib67.yuanban.enchant;

import io.ib67.yuanban.datapack.LoadFailedException;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.kyori.adventure.key.Key;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 附魔标签展开时对找不到的引用的处理，对照原版 {@code TagEntry.build} 和 {@code MappedRegistry.freeze}。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class EnchantmentTagTest {
    private static final String ENCHANTMENT = """
            {"description": "E", "supported_items": "#minecraft:enchantable/sword",
             "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
             "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"]}
            """;

    @TempDir
    Path tempDir;

    @Test
    void skipsMissingOptionalReferences(Env env) {
        var world = world()
                .vanilla("tags/enchantment/base.json", """
                        {"values": ["minecraft:sharpness"]}
                        """)
                .vanilla("tags/enchantment/mixed.json", """
                        {"values": [
                          {"id": "minecraft:missing", "required": false},
                          {"id": "#minecraft:missing_tag", "required": false},
                          {"id": "minecraft:smite", "required": false},
                          {"id": "#minecraft:base", "required": false}
                        ]}
                        """);
        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        try (var session = world.loader(process, enchantments, enchantmentTags, providers).start(EnchantWorld.createWorld())) {
            assertEquals(Map.of(
                    Key.key("minecraft:base"), List.of(Key.key("minecraft:sharpness")),
                    Key.key("minecraft:mixed"), List.of(Key.key("minecraft:smite"), Key.key("minecraft:sharpness"))
            ), Map.copyOf(enchantmentTags.current().tags()));
        }
    }

    @Test
    void rejectsMissingRequiredElements(Env env) {
        var world = world()
                .vanilla("tags/enchantment/first.json", """
                        {"values": ["minecraft:sharpness", "minecraft:missing", {"id": "example:absent", "required": true}]}
                        """)
                .vanilla("tags/enchantment/second.json", """
                        {"values": ["minecraft:missing"]}
                        """);
        assertEquals("Unbound values in registry ResourceKey[minecraft:root / minecraft:enchantment]: "
                + "[example:absent, minecraft:missing]", loadFailure(env, world).getMessage());
    }

    @Test
    void rejectsMissingRequiredTag(Env env) {
        var world = world()
                .vanilla("tags/enchantment/first.json", """
                        {"values": ["minecraft:sharpness", "#minecraft:missing_tag"]}
                        """);
        assertEquals("Couldn't load tag minecraft:first as it references missing tag minecraft:missing_tag",
                loadFailure(env, world).getMessage());
    }

    private EnchantWorld world() {
        return new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment/sharpness.json", ENCHANTMENT)
                .vanilla("enchantment/smite.json", ENCHANTMENT);
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
