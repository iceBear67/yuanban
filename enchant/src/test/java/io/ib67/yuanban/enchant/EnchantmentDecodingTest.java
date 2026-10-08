package io.ib67.yuanban.enchant;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.datapack.LoadFailedException;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.item.enchant.LevelBasedValue;
import net.minestom.server.registry.RegistryKey;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
            assertEquals(HolderSet.named(Key.key("minecraft:enchantable/sword")), sharpness.supportedItems());
            assertNull(sharpness.primaryItems());
            // exclusive_set 缺失时为空的元素列表
            assertEquals(HolderSet.direct(List.of()), sharpness.exclusiveSet());

            // primary_items 缺失时构造器收到 null，注册表中的附魔不限制主要物品
            var registered = process.enchantment().get(Key.key("minecraft:sharpness"));
            assertNull(registered.primaryItems());
            assertEquals(0, registered.exclusiveSet().size());
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
                        """)
                .vanilla("enchantment/smite.json", """
                        {"description": "Smite", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 5, "max_level": 5,
                         "min_cost": {"base": 5, "per_level_above_first": 8},
                         "max_cost": {"base": 25, "per_level_above_first": 8},
                         "anvil_cost": 2, "slots": ["mainhand"],
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
            assertEquals(HolderSet.named(Key.key("minecraft:exclusive_set/damage")), sharpness.exclusiveSet());

            var registered = process.enchantment().get(Key.key("minecraft:sharpness"));
            assertEquals(Key.key("minecraft:exclusive_set/damage"), registered.exclusiveSet().key().key());
            assertTrue(registered.exclusiveSet().contains(RegistryKey.of("minecraft:smite")));
        }
    }

    @Test
    void decodesDirectListsAndSingleIds(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment/sharpness.json", """
                        {"description": "Sharpness", "supported_items": "minecraft:iron_sword",
                         "primary_items": ["minecraft:golden_sword", "minecraft:iron_sword"],
                         "weight": 10, "max_level": 5,
                         "min_cost": {"base": 1, "per_level_above_first": 11},
                         "max_cost": {"base": 21, "per_level_above_first": 11},
                         "anvil_cost": 1, "slots": ["mainhand", "saddle"],
                         "exclusive_set": ["minecraft:smite", "minecraft:bane_of_arthropods"]}
                        """)
                .vanilla("enchantment/smite.json", """
                        {"description": "Smite", "supported_items": ["minecraft:iron_sword"],
                         "primary_items": "#minecraft:enchantable/sword",
                         "weight": 5, "max_level": 5,
                         "min_cost": {"base": 5, "per_level_above_first": 8},
                         "max_cost": {"base": 25, "per_level_above_first": 8},
                         "anvil_cost": 2, "slots": ["mainhand"],
                         "exclusive_set": "minecraft:sharpness"}
                        """)
                .vanilla("enchantment/bane_of_arthropods.json", """
                        {"description": "Bane", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 5, "max_level": 5,
                         "min_cost": {"base": 5, "per_level_above_first": 8},
                         "max_cost": {"base": 25, "per_level_above_first": 8},
                         "anvil_cost": 2, "slots": ["mainhand"], "exclusive_set": []}
                        """);
        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        try (var session = world.loader(process, enchantments, enchantmentTags, providers).start(EnchantWorld.createWorld())) {
            var data = enchantments.current().enchantments();
            var sharpness = data.get(Key.key("minecraft:sharpness"));
            assertEquals(HolderSet.direct(List.of(RegistryKey.of("minecraft:iron_sword"))), sharpness.supportedItems());
            assertEquals(HolderSet.direct(List.of(RegistryKey.of("minecraft:golden_sword"), RegistryKey.of("minecraft:iron_sword"))),
                    sharpness.primaryItems());
            assertEquals(HolderSet.direct(List.of(RegistryKey.of("minecraft:smite"), RegistryKey.of("minecraft:bane_of_arthropods"))),
                    sharpness.exclusiveSet());
            assertEquals(List.of(net.minestom.server.entity.EquipmentSlotGroup.MAIN_HAND, net.minestom.server.entity.EquipmentSlotGroup.SADDLE),
                    sharpness.slots());
            var smite = data.get(Key.key("minecraft:smite"));
            assertEquals(HolderSet.direct(List.of(RegistryKey.of("minecraft:iron_sword"))), smite.supportedItems());
            assertEquals(HolderSet.named(Key.key("minecraft:enchantable/sword")), smite.primaryItems());
            assertEquals(HolderSet.direct(List.of(RegistryKey.of("minecraft:sharpness"))), smite.exclusiveSet());

            // 元素列表按原顺序成为直接标签
            var registered = process.enchantment().get(Key.key("minecraft:sharpness"));
            assertNull(registered.supportedItems().key());
            assertEquals(1, registered.supportedItems().size());
            assertTrue(registered.supportedItems().contains(RegistryKey.of("minecraft:iron_sword")));
            assertEquals(List.of(Key.key("minecraft:golden_sword"), Key.key("minecraft:iron_sword")), keys(registered.primaryItems()));
            assertEquals(List.of(Key.key("minecraft:smite"), Key.key("minecraft:bane_of_arthropods")), keys(registered.exclusiveSet()));
            var registeredSmite = process.enchantment().get(Key.key("minecraft:smite"));
            assertEquals(Key.key("minecraft:enchantable/sword"), registeredSmite.primaryItems().key().key());
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
            var pick = assertInstanceOf(ProviderEntry.Single.class, data.providers().get(Key.key("minecraft:pick")));
            assertEquals(Key.key("minecraft:sharpness"), pick.enchantment());
            var level = assertInstanceOf(ProviderEntry.IntProvider.ConstantInt.class, pick.level());
            assertEquals(3, level.value());
            assertEquals(3, level.sample());
            var vindicator = assertInstanceOf(ProviderEntry.Single.class, data.providers().get(Key.key("minecraft:raid/vindicator")));
            assertEquals(Key.key("minecraft:sharpness"), vindicator.enchantment());
        }
    }

    @Test
    void decodesAllProviderTypes(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("enchantment/sharpness.json", """
                        {"description": "S", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"]}
                        """)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("tags/enchantment/mob_equipment.json", """
                        {"values": ["minecraft:sharpness"]}
                        """)
                .vanilla("enchantment_provider/single_fixed.json", """
                        {"type": "minecraft:single", "enchantment": "minecraft:sharpness", "level": 2}
                        """)
                .vanilla("enchantment_provider/single_uniform.json", """
                        {"type": "minecraft:single", "enchantment": "minecraft:sharpness",
                         "level": {"type": "minecraft:uniform", "min_inclusive": 5, "max_inclusive": 10}}
                        """)
                .vanilla("enchantment_provider/single_weighted.json", """
                        {"type": "single", "enchantment": "sharpness",
                         "level": {"type": "minecraft:weighted_list", "distribution": [
                           {"data": {"type": "minecraft:clamped", "source": 9, "min_inclusive": 1, "max_inclusive": 4}, "weight": 1},
                           {"data": {"type": "minecraft:trapezoid", "min": 1, "max": 3, "plateau": 0}, "weight": 2}]}}
                        """)
                .vanilla("enchantment_provider/by_cost_uniform.json", """
                        {"type": "minecraft:by_cost", "enchantments": "#minecraft:mob_equipment",
                         "cost": {"type": "minecraft:uniform", "min_inclusive": 5, "max_inclusive": 10}}
                        """)
                .vanilla("enchantment_provider/by_cost_flat.json", """
                        {"type": "minecraft:by_cost", "enchantments": "#minecraft:mob_equipment", "cost": 7}
                        """)
                .vanilla("enchantment_provider/by_cost_list.json", """
                        {"type": "minecraft:by_cost", "enchantments": ["minecraft:sharpness"], "cost": 7}
                        """)
                .vanilla("enchantment_provider/by_cost_difficulty.json", """
                        {"type": "minecraft:by_cost_with_difficulty", "enchantments": "#minecraft:mob_equipment",
                         "min_cost": 5, "max_cost_span": 17}
                        """)
                .vanilla("enchantment_provider/by_cost_difficulty_single.json", """
                        {"type": "minecraft:by_cost_with_difficulty", "enchantments": "minecraft:sharpness",
                         "min_cost": 1, "max_cost_span": 0}
                        """);
        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        try (var session = world.loader(process, enchantments, enchantmentTags, providers).start(EnchantWorld.createWorld())) {
            var data = providers.current();
            assertEquals(8, data.providers().size());

            var singleFixed = assertInstanceOf(ProviderEntry.Single.class,
                    data.providers().get(Key.key("minecraft:single_fixed")));
            assertEquals(Key.key("minecraft:sharpness"), singleFixed.enchantment());
            var singleFixedLevel = assertInstanceOf(ProviderEntry.IntProvider.ConstantInt.class, singleFixed.level());
            assertEquals(2, singleFixedLevel.value());
            assertEquals(2, singleFixedLevel.sample());

            var singleUniform = assertInstanceOf(ProviderEntry.Single.class,
                    data.providers().get(Key.key("minecraft:single_uniform")));
            assertEquals(Key.key("minecraft:sharpness"), singleUniform.enchantment());
            var singleUniformLevel = assertInstanceOf(ProviderEntry.IntProvider.UniformInt.class, singleUniform.level());
            assertEquals(5, singleUniformLevel.minInclusive());
            assertEquals(10, singleUniformLevel.maxInclusive());
            for (var i = 0; i < 16; i++) {
                var value = singleUniformLevel.sample();
                assertTrue(value >= 5 && value <= 10, "sample: " + value);
            }

            var singleWeighted = assertInstanceOf(ProviderEntry.Single.class,
                    data.providers().get(Key.key("minecraft:single_weighted")));
            var weighted = assertInstanceOf(ProviderEntry.IntProvider.WeightedListInt.class, singleWeighted.level());
            assertEquals(List.of(
                    new ProviderEntry.IntProvider.WeightedListInt.Weighted(
                            new ProviderEntry.IntProvider.ClampedInt(new ProviderEntry.IntProvider.ConstantInt(9), 1, 4), 1),
                    new ProviderEntry.IntProvider.WeightedListInt.Weighted(
                            new ProviderEntry.IntProvider.TrapezoidInt(1, 3, 0), 2)), weighted.distribution());

            var byCost = assertInstanceOf(ProviderEntry.ByCost.class,
                    data.providers().get(Key.key("minecraft:by_cost_uniform")));
            assertEquals(HolderSet.named(Key.key("minecraft:mob_equipment")), byCost.enchantments());
            assertEquals(Set.of(Key.key("minecraft:mob_equipment")), byCost.referencedEnchantmentTags());
            assertEquals(Set.of(), byCost.referencedEnchantments());
            var byCostCost = assertInstanceOf(ProviderEntry.IntProvider.UniformInt.class, byCost.cost());
            assertEquals(5, byCostCost.minInclusive());
            assertEquals(10, byCostCost.maxInclusive());

            var byCostFlat = assertInstanceOf(ProviderEntry.ByCost.class,
                    data.providers().get(Key.key("minecraft:by_cost_flat")));
            assertEquals(new ProviderEntry.IntProvider.ConstantInt(7), byCostFlat.cost());

            var byCostList = assertInstanceOf(ProviderEntry.ByCost.class,
                    data.providers().get(Key.key("minecraft:by_cost_list")));
            assertEquals(HolderSet.direct(List.of(RegistryKey.of("minecraft:sharpness"))), byCostList.enchantments());
            assertEquals(Set.of(Key.key("minecraft:sharpness")), byCostList.referencedEnchantments());
            assertEquals(Set.of(), byCostList.referencedEnchantmentTags());

            var byCostDifficulty = assertInstanceOf(ProviderEntry.ByCostWithDifficulty.class,
                    data.providers().get(Key.key("minecraft:by_cost_difficulty")));
            assertEquals(HolderSet.named(Key.key("minecraft:mob_equipment")), byCostDifficulty.enchantments());
            assertEquals(5, byCostDifficulty.minCost());
            assertEquals(17, byCostDifficulty.maxCostSpan());

            var byCostDifficultySingle = assertInstanceOf(ProviderEntry.ByCostWithDifficulty.class,
                    data.providers().get(Key.key("minecraft:by_cost_difficulty_single")));
            assertEquals(HolderSet.direct(List.of(RegistryKey.of("minecraft:sharpness"))), byCostDifficultySingle.enchantments());
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
                        """)
                .vanilla("enchantment_provider/list.json", """
                        {"type": "minecraft:by_cost", "enchantments": ["minecraft:sharpness", "minecraft:bane_of_arthropods"], "cost": 3}
                        """);
        var exception = loadFailure(env, world);
        assertEquals("Unbound values in registry ResourceKey[minecraft:root / minecraft:enchantment]: "
                + "[minecraft:bane_of_arthropods, minecraft:smite]", exception.getMessage());
    }

    @Test
    void rejectsMissingEnchantmentTagInProvider(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("enchantment_provider/by_cost.json", """
                        {"type": "minecraft:by_cost", "enchantments": "#minecraft:nope", "cost": 3}
                        """);
        var exception = loadFailure(env, world);
        assertEquals("Unbound tags in registry ResourceKey[minecraft:root / minecraft:enchantment]: [minecraft:nope]",
                exception.getMessage());
    }

    @Test
    void providerTypeMatchesFullId(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("enchantment/sharpness.json", """
                        {"description": "S", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 1, "max_level": 1, "min_cost": {"base": 1, "per_level_above_first": 1},
                         "max_cost": {"base": 1, "per_level_above_first": 1}, "anvil_cost": 1, "slots": ["mainhand"]}
                        """)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment_provider/foreign.json", """
                        {"type": "example:single", "enchantment": "minecraft:sharpness", "level": 1}
                        """)
                .vanilla("enchantment_provider/untyped.json", """
                        {"enchantment": "minecraft:sharpness", "level": 1}
                        """);
        var lines = loadFailure(env, world).getMessage().split("\n");
        assertEquals(2, lines.length);
        assertEquals("Couldn't parse data file 'minecraft:foreign' from [vanilla] data/minecraft/enchantment_provider/foreign.json: "
                + "Unknown registry key in ResourceKey[minecraft:root / minecraft:enchantment_provider_type]: example:single", lines[0]);
        assertTrue(lines[1].startsWith("Couldn't parse data file 'minecraft:untyped' from [vanilla] data/minecraft/enchantment_provider/untyped.json: "
                + "No key type in MapLike["), lines[1]);
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

    private static <T> List<Key> keys(net.minestom.server.registry.RegistryTag<T> tag) {
        var keys = new java.util.ArrayList<Key>();
        tag.forEach(key -> keys.add(key.key()));
        return keys;
    }
}
