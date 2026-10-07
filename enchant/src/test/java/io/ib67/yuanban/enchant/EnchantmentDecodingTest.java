package io.ib67.yuanban.enchant;

import net.kyori.adventure.key.Key;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.item.enchant.LevelBasedValue;
import net.minestom.server.item.enchant.ValueEffect;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.ib67.yuanban.itemtag.testing.ResetItemTags;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
@ExtendWith(ResetItemTags.class)
class EnchantmentDecodingTest {
    @TempDir
    Path tempDir;

    @Test
    void decodesMinimalEnchantment(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword", "minecraft:stone_sword"]}
                        """)
                .vanilla("enchantment/sharpness.json", """
                        {"description": {"translate": "enchantment.minecraft.sharpness"},
                         "supported_items": "#minecraft:enchantable/sword",
                         "weight": 10, "max_level": 5,
                         "min_cost": {"base": 1, "per_level_above_first": 11},
                         "max_cost": {"base": 21, "per_level_above_first": 11},
                         "anvil_cost": 1, "slots": ["mainhand"]}
                        """);
        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        try (var session = world.loader(process, enchantments, enchantmentTags, providers).start(EnchantWorld.createWorld())) {
            var data = enchantments.current();
            assertEquals(Set.of(Key.key("minecraft:sharpness")), data.enchantments().keySet());
            var sharpness = data.enchantments().get(Key.key("minecraft:sharpness"));
            assertEquals(10, sharpness.weight());
            assertEquals(5, sharpness.maxLevel());
            assertEquals(1, sharpness.anvilCost());
            assertEquals(new Enchantment.Cost(1, 11), sharpness.minCost());
            assertEquals(new Enchantment.Cost(21, 11), sharpness.maxCost());
            assertEquals(List.of(net.minestom.server.entity.EquipmentSlotGroup.MAIN_HAND), sharpness.slots());
            assertTrue(sharpness.effects().isEmpty());
        }
    }

    @Test
    void decodesTagsAndExclusiveSet(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("tags/enchantment/exclusive_set/damage.json", """
                        {"values": ["minecraft:sharpness", "minecraft:smite"]}
                        """)
                .vanilla("enchantment/sharpness.json", """
                        {"description": "Sharpness", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 10, "max_level": 5,
                         "min_cost": {"base": 1, "per_level_above_first": 11},
                         "max_cost": {"base": 21, "per_level_above_first": 11},
                         "anvil_cost": 1, "slots": ["mainhand"],
                         "exclusive_set": "#minecraft:exclusive_set/damage"}
                        """);
        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        try (var session = world.loader(process, enchantments, enchantmentTags, providers).start(EnchantWorld.createWorld())) {
            var tags = enchantmentTags.current();
            assertEquals(1, tags.tags().size());
            assertEquals(Map.of(Key.key("exclusive_set/damage"), List.of(Key.key("minecraft:sharpness"), Key.key("minecraft:smite"))),
                    Map.copyOf(tags.tags()));
            var sharpness = enchantments.current().enchantments().get(Key.key("minecraft:sharpness"));
            assertEquals(Key.key("minecraft:exclusive_set/damage"), sharpness.exclusiveSet());
        }
    }

    @Test
    void decodesValueEffects(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment/unbreaking.json", """
                        {"description": "Unbreaking", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 5, "max_level": 3,
                         "min_cost": {"base": 5, "per_level_above_first": 8},
                         "max_cost": {"base": 55, "per_level_above_first": 8},
                         "anvil_cost": 2, "slots": ["any"],
                         "effects": {
                           "minecraft:item_damage": [
                             {"effect": {"type": "minecraft:remove_binomial",
                                          "chance": {"type": "minecraft:linear", "base": 1.0, "per_level_above_first": 1.0}},
                              "requirements": {"condition": "minecraft:match_tool",
                                                "predicate": {"minecraft:enchantments": []}}}
                           ]}}
                        """);
        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        try (var session = world.loader(process, enchantments, enchantmentTags, providers).start(EnchantWorld.createWorld())) {
            var unbreaking = enchantments.current().enchantments().get(Key.key("minecraft:unbreaking"));
            assertEquals(1, unbreaking.effects().size());
            var effect = unbreaking.effects().getFirst();
            assertEquals("item_damage", effect.getKey().key().value());
            var list = (java.util.List<?>) effect.getValue();
            assertEquals(1, list.size());
            var conditional = (net.minestom.server.item.enchant.ConditionalEffect<?>) list.getFirst();
            assertInstanceOf(net.minestom.server.item.enchant.ValueEffect.RemoveBinomial.class, conditional.effect());
            var removeBinomial = (net.minestom.server.item.enchant.ValueEffect.RemoveBinomial) conditional.effect();
            var chance = removeBinomial.chance();
            assertInstanceOf(LevelBasedValue.Linear.class, chance);
        }
    }

    @Test
    void decodesProviders(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("enchantment/sharpness.json", """
                        {"description": "S", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"]}
                        """)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment_provider/pick.json", """
                        {"type": "minecraft:single", "enchantment": "minecraft:sharpness", "level": 3}
                        """)
                .vanilla("enchantment_provider/raid/vindicator.json", """
                        {"type": "minecraft:single", "enchantment": "minecraft:sharpness", "level": 1}
                        """);
        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        try (var session = world.loader(process, enchantments, enchantmentTags, providers).start(EnchantWorld.createWorld())) {
            var data = providers.current();
            assertEquals(Set.of(Key.key("minecraft:pick"), Key.key("minecraft:raid/vindicator")),
                    data.providers().keySet());
            var pick = data.providers().get(Key.key("minecraft:pick"));
            assertEquals("single", pick.type());
            assertEquals("minecraft:sharpness", pick.definition().get("enchantment").getAsString());
            assertEquals(3, pick.definition().get("level").getAsInt());
        }
    }

    @Test
    void rejectsMissingEnchantmentReferenceInProvider(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("enchantment/sharpness.json", """
                        {"description": "S", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"]}
                        """)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment_provider/pick.json", """
                        {"type": "minecraft:single", "enchantment": "minecraft:smite", "level": 3}
                        """);
        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);

        var exception = assertInstanceOf(EnchantmentException.class,
                assertThrows(io.ib67.yuanban.datapack.LoadFailedException.class,
                        () -> world.loader(process, enchantments, enchantmentTags, providers).start(EnchantWorld.createWorld())).getCause());
        assertTrue(exception.getMessage().contains("Missing enchantment: 'minecraft:smite'"), exception.getMessage());
    }
}
