package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.condition.EntityPropertiesCondition;
import io.ib67.yuanban.loot.condition.LocationCheckCondition;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.condition.MatchToolCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContext;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.predicate.CollectionPredicate;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.predicate.LocationPredicate;
import io.ib67.yuanban.loot.predicate.StatePropertiesPredicate;
import io.ib67.yuanban.loot.predicate.component.ComponentPredicate;
import io.ib67.yuanban.loot.predicate.component.EnchantmentPredicate;
import io.ib67.yuanban.loot.predicate.entity.DistancePredicate;
import io.ib67.yuanban.loot.predicate.entity.EffectInstancePredicate;
import io.ib67.yuanban.loot.predicate.entity.EntityPredicate;
import io.ib67.yuanban.loot.predicate.entity.EntitySubPredicate;
import io.ib67.yuanban.loot.predicate.entity.FoodPredicate;
import io.ib67.yuanban.loot.predicate.entity.StatMatcher;
import io.ib67.yuanban.loot.slot.SlotRange;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.animal.SheepMeta;
import net.minestom.server.entity.metadata.cube.SlimeMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.PotionType;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.statistic.PlayerStatistic;
import net.minestom.server.statistic.StatisticCategory;
import net.minestom.server.utils.Range;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static io.ib67.yuanban.loot.eval.EvalSupport.context;
import static io.ib67.yuanban.loot.eval.EvalSupport.item;
import static io.ib67.yuanban.loot.eval.EvalSupport.selector;
import static io.ib67.yuanban.loot.eval.EvalSupport.table;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 谓词的求值：物品、组件、实体、位置、方块状态与流体。谓词经过使用它们的条件求值，与原版的调用方式相同。
 */
@EnvTest
class PredicateEvaluationTest {
    private static final Range.Int ANY_INT = MinMaxBounds.ANY_INT;
    private static final Range.Double ANY_DOUBLE = MinMaxBounds.ANY_DOUBLE;
    private static final LocationPredicate NO_LOCATION = new LocationPredicate(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    private static boolean passes(Env env, InMemoryLootEnvironment environment, LootContext context, LootCondition condition) {
        var table = table(new LootPool(List.of(condition), List.of(), new ConstantValue(1.0f), List.of(item(Material.STONE))));
        return !selector(env, environment).roll(table, context, new Random(0L)).isEmpty();
    }

    // ------------------------------------------------------------------
    // 物品与组件
    // ------------------------------------------------------------------

    private static boolean toolMatches(Env env, ItemStack tool, ItemPredicate predicate) {
        var environment = new InMemoryLootEnvironment(env.process());
        return passes(env, environment, context(environment, null, ContextKey.TOOL, tool), new MatchToolCondition(Optional.of(predicate)));
    }

    private static ItemPredicate components(ComponentPredicate... predicates) {
        return new ItemPredicate(Optional.empty(), ANY_INT, new ComponentMatchers(DataComponentMap.EMPTY, List.of(predicates)));
    }

    @Test
    void itemsAndCount(Env env) {
        var pickaxes = new ItemPredicate(Optional.of(HolderSet.named(Key.key("pickaxes"))), new Range.Int(2, null), ComponentMatchers.ANY);
        assertTrue(toolMatches(env, ItemStack.of(Material.DIAMOND_PICKAXE, 2), pickaxes));
        assertFalse(toolMatches(env, ItemStack.of(Material.DIAMOND_PICKAXE, 1), pickaxes));
        assertFalse(toolMatches(env, ItemStack.of(Material.STONE, 2), pickaxes));
    }

    @Test
    void exactComponents(Env env) {
        var named = new ItemPredicate(Optional.empty(), ANY_INT, new ComponentMatchers(
                DataComponentMap.builder().set(DataComponents.CUSTOM_NAME, Component.text("x")).build(), List.of()));
        assertTrue(toolMatches(env, ItemStack.of(Material.STONE).with(DataComponents.CUSTOM_NAME, Component.text("x")), named));
        assertFalse(toolMatches(env, ItemStack.of(Material.STONE), named));
    }

    @Test
    void damagePredicate(Env env) {
        var damaged = components(new ComponentPredicate.Damage(ANY_INT, new Range.Int(1, null)));
        assertTrue(toolMatches(env, ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.DAMAGE, 5), damaged));
        assertFalse(toolMatches(env, ItemStack.of(Material.DIAMOND_SWORD), damaged));
        // 没有 damage 组件时不通过
        assertFalse(toolMatches(env, ItemStack.of(Material.STONE), components(new ComponentPredicate.Damage(ANY_INT, ANY_INT))));
    }

    @Test
    void enchantmentsPredicate(Env env) {
        var sword = ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.ENCHANTMENTS, new EnchantmentList(Enchantment.SHARPNESS, 3));
        var sharp = components(new ComponentPredicate.Enchantments(List.of(new EnchantmentPredicate(
                Optional.of(HolderSet.direct(List.of(Enchantment.SHARPNESS))), new Range.Int(2, null)))));
        assertTrue(toolMatches(env, sword, sharp));
        // 没有附魔集合、范围为缺省值时只要求有任意附魔
        var anyEnchantment = components(new ComponentPredicate.Enchantments(List.of(new EnchantmentPredicate(Optional.empty(), ANY_INT))));
        assertTrue(toolMatches(env, sword, anyEnchantment));
        assertFalse(toolMatches(env, ItemStack.of(Material.DIAMOND_SWORD), anyEnchantment));
        // 显式写出的空范围与缺省值不是同一个对象，原版走按等级比较的分支
        var explicitRange = components(new ComponentPredicate.Enchantments(List.of(new EnchantmentPredicate(Optional.empty(),
                new Range.Int(null, null)))));
        assertTrue(toolMatches(env, sword, explicitRange));
        assertFalse(toolMatches(env, ItemStack.of(Material.DIAMOND_SWORD), explicitRange));
    }

    @Test
    void customDataPredicateComparesWithEmptyWhenAbsent(Env env) {
        var empty = components(new ComponentPredicate.CustomData(CompoundBinaryTag.empty()));
        // 原版 NbtPredicate#matches(DataComponentGetter)：组件不存在时按空的复合标签比较
        assertTrue(toolMatches(env, ItemStack.of(Material.STONE), empty));
        var withA = components(new ComponentPredicate.CustomData(CompoundBinaryTag.builder().putInt("a", 1).build()));
        assertTrue(toolMatches(env, ItemStack.of(Material.STONE).with(DataComponents.CUSTOM_DATA,
                new CustomData(CompoundBinaryTag.builder().putInt("a", 1).putInt("b", 2).build())), withA));
        assertFalse(toolMatches(env, ItemStack.of(Material.STONE), withA));
    }

    @Test
    void containerPredicate(Env env) {
        var box = ItemStack.of(Material.SHULKER_BOX).with(DataComponents.CONTAINER,
                List.of(ItemStack.AIR, ItemStack.of(Material.APPLE), ItemStack.of(Material.DIAMOND)));
        var apple = new ItemPredicate(Optional.of(HolderSet.direct(List.of(RegistryKey.<Material>of(Key.key("apple"))))), ANY_INT,
                ComponentMatchers.ANY);
        var containsApple = components(new ComponentPredicate.Container(Optional.of(new CollectionPredicate<>(
                Optional.of(List.of(apple)), Optional.empty(), Optional.of(new Range.Int(2, 2))))));
        // 空槽位不计入数量
        assertTrue(toolMatches(env, box, containsApple));
        assertFalse(toolMatches(env, ItemStack.of(Material.SHULKER_BOX).with(DataComponents.CONTAINER, List.of(ItemStack.of(Material.DIAMOND))),
                containsApple));
    }

    @Test
    void collectionCountsEntries(Env env) {
        var box = ItemStack.of(Material.SHULKER_BOX).with(DataComponents.CONTAINER,
                List.of(ItemStack.of(Material.APPLE), ItemStack.of(Material.APPLE), ItemStack.of(Material.DIAMOND)));
        var apple = new ItemPredicate(Optional.of(HolderSet.direct(List.of(RegistryKey.<Material>of(Key.key("apple"))))), ANY_INT,
                ComponentMatchers.ANY);
        var twoApples = components(new ComponentPredicate.Container(Optional.of(new CollectionPredicate<>(Optional.empty(),
                Optional.of(List.of(new CollectionPredicate.CountEntry<>(apple, new Range.Int(2, 2)))), Optional.empty()))));
        assertTrue(toolMatches(env, box, twoApples));
    }

    @Test
    void potionsAndAnyValue(Env env) {
        var potion = ItemStack.of(Material.POTION).with(DataComponents.POTION_CONTENTS,
                new net.minestom.server.item.component.PotionContents(PotionType.SWIFTNESS));
        assertTrue(toolMatches(env, potion, components(new ComponentPredicate.Potions(
                HolderSet.direct(List.of(RegistryKey.<PotionType>of(Key.key("swiftness"))))))));
        assertFalse(toolMatches(env, potion, components(new ComponentPredicate.Potions(
                HolderSet.direct(List.of(RegistryKey.<PotionType>of(Key.key("healing"))))))));
        assertTrue(toolMatches(env, potion, components(new ComponentPredicate.AnyValue(DataComponents.POTION_CONTENTS))));
        assertFalse(toolMatches(env, ItemStack.of(Material.STONE), components(new ComponentPredicate.AnyValue(DataComponents.POTION_CONTENTS))));
    }

    // ------------------------------------------------------------------
    // 实体
    // ------------------------------------------------------------------

    private static boolean entityMatches(Env env, Entity entity, Vec origin, EntitySubPredicate... parts) {
        var environment = new InMemoryLootEnvironment(env.process());
        var context = context(environment, entity.getInstance(), ContextKey.THIS_ENTITY, entity, ContextKey.ORIGIN, origin);
        return passes(env, environment, context, new EntityPropertiesCondition(Optional.of(new EntityPredicate(List.of(parts))), EntityTarget.THIS));
    }

    private static Entity spawn(Instance instance, Entity entity) {
        entity.setInstance(instance, new Pos(0, 42, 0)).join();
        return entity;
    }

    @Test
    void flagsAndBaby(Env env) {
        var instance = env.createFlatInstance();
        var pig = spawn(instance, new LivingEntity(EntityType.PIG));
        pig.setPose(EntityPose.SNEAKING);
        var sneaking = new EntitySubPredicate.Flags(Optional.empty(), Optional.empty(), Optional.of(true), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertTrue(entityMatches(env, pig, Vec.ZERO, sneaking));
        var baby = new EntitySubPredicate.Flags(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(true), Optional.empty(), Optional.empty());
        assertFalse(entityMatches(env, pig, Vec.ZERO, baby));
        ((net.minestom.server.entity.metadata.AgeableMobMeta) pig.getEntityMeta()).setBaby(true);
        assertTrue(entityMatches(env, pig, Vec.ZERO, baby));
    }

    @Test
    void equipmentAndEffects(Env env) {
        var instance = env.createFlatInstance();
        var zombie = (LivingEntity) spawn(instance, new LivingEntity(EntityType.ZOMBIE));
        zombie.setEquipment(EquipmentSlot.HELMET, ItemStack.of(Material.DIAMOND_HELMET));
        var helmet = new ItemPredicate(Optional.of(HolderSet.direct(List.of(RegistryKey.<Material>of(Key.key("diamond_helmet"))))),
                ANY_INT, ComponentMatchers.ANY);
        assertTrue(entityMatches(env, zombie, Vec.ZERO, new EntitySubPredicate.Equipment(Optional.of(helmet), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())));
        var effects = new LinkedHashMap<PotionEffect, EffectInstancePredicate>();
        effects.put(PotionEffect.SPEED, new EffectInstancePredicate(new Range.Int(1, 1), ANY_INT, Optional.empty(), Optional.empty()));
        assertFalse(entityMatches(env, zombie, Vec.ZERO, new EntitySubPredicate.Effects(effects)));
        zombie.addEffect(new Potion(PotionEffect.SPEED, 1, 200));
        assertTrue(entityMatches(env, zombie, Vec.ZERO, new EntitySubPredicate.Effects(effects)));
    }

    @Test
    void distanceUsesOrigin(Env env) {
        var instance = env.createFlatInstance();
        var pig = spawn(instance, new Entity(EntityType.PIG));
        var near = new EntitySubPredicate.Distance(new DistancePredicate(ANY_DOUBLE, ANY_DOUBLE, ANY_DOUBLE, ANY_DOUBLE,
                new Range.Double(null, 5.0)));
        assertTrue(entityMatches(env, pig, new Vec(0, 42, 3), near));
        assertFalse(entityMatches(env, pig, new Vec(0, 42, 30), near));
    }

    @Test
    void vehiclePassengerAndTeam(Env env) {
        var instance = env.createFlatInstance();
        var horse = spawn(instance, new LivingEntity(EntityType.HORSE));
        var rider = spawn(instance, new LivingEntity(EntityType.ZOMBIE));
        horse.addPassenger(rider);
        var horses = new EntityPredicate(List.of(new EntitySubPredicate.EntityTypes(
                HolderSet.direct(List.of(RegistryKey.<EntityType>of(Key.key("horse")))))));
        var zombies = new EntityPredicate(List.of(new EntitySubPredicate.EntityTypes(
                HolderSet.direct(List.of(RegistryKey.<EntityType>of(Key.key("zombie")))))));
        assertTrue(entityMatches(env, rider, Vec.ZERO, new EntitySubPredicate.Vehicle(horses)));
        assertTrue(entityMatches(env, horse, Vec.ZERO, new EntitySubPredicate.Passenger(zombies)));
        var team = env.process().team().createTeam("loot_test_red");
        ((LivingEntity) rider).setTeam(team);
        assertTrue(entityMatches(env, rider, Vec.ZERO, new EntitySubPredicate.Team("loot_test_red")));
        assertFalse(entityMatches(env, horse, Vec.ZERO, new EntitySubPredicate.Team("loot_test_red")));
    }

    @Test
    void cubeMobAndSheep(Env env) {
        var instance = env.createFlatInstance();
        var slime = spawn(instance, new LivingEntity(EntityType.SLIME));
        ((SlimeMeta) slime.getEntityMeta()).setSize(3);
        assertTrue(entityMatches(env, slime, Vec.ZERO, new EntitySubPredicate.CubeMob(new Range.Int(3, 3))));
        var sheep = spawn(instance, new LivingEntity(EntityType.SHEEP));
        ((SheepMeta) sheep.getEntityMeta()).setSheared(true);
        assertTrue(entityMatches(env, sheep, Vec.ZERO, new EntitySubPredicate.Sheep(Optional.of(true))));
        assertFalse(entityMatches(env, slime, Vec.ZERO, new EntitySubPredicate.Sheep(Optional.empty())));
    }

    @Test
    void entityTypePredicateRunsFirst(Env env) {
        var instance = env.createFlatInstance();
        var pig = spawn(instance, new Entity(EntityType.PIG));
        // 原版按 PREDICATE_TYPE_ORDER 先检查实体类型；类型不符时后面的 NBT 子谓词不会被求值
        var cows = new EntitySubPredicate.EntityTypes(HolderSet.direct(List.of(RegistryKey.<EntityType>of(Key.key("cow")))));
        assertFalse(entityMatches(env, pig, Vec.ZERO, new EntitySubPredicate.Nbt(CompoundBinaryTag.empty()), cows));
    }

    @Test
    void lightningAndFishingHookWithoutConditions(Env env) {
        var instance = env.createFlatInstance();
        var bolt = spawn(instance, new Entity(EntityType.LIGHTNING_BOLT));
        var pig = spawn(instance, new Entity(EntityType.PIG));
        var anyLightning = new EntitySubPredicate.Lightning(ANY_INT, Optional.empty());
        assertTrue(entityMatches(env, bolt, Vec.ZERO, anyLightning));
        assertFalse(entityMatches(env, pig, Vec.ZERO, anyLightning));
        // 不检查开阔水域时任何实体都通过
        assertTrue(entityMatches(env, pig, Vec.ZERO, new EntitySubPredicate.FishingHook(Optional.empty())));
        assertFalse(entityMatches(env, pig, Vec.ZERO, new EntitySubPredicate.FishingHook(Optional.of(true))));
        assertFalse(entityMatches(env, pig, Vec.ZERO, new EntitySubPredicate.Raider(false, false)));
    }

    @Test
    void playerPredicate(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 42, 0));
        player.setLevel(10);
        player.setGameMode(GameMode.CREATIVE);
        player.getStatisticValueMap().put(new PlayerStatistic(StatisticCategory.MINED, Block.STONE.id()), 7);
        var predicate = new EntitySubPredicate.Player(new Range.Int(5, null), FoodPredicate.ANY, List.of(GameMode.CREATIVE),
                List.of(new StatMatcher(StatisticCategory.MINED, Key.key("stone"), new Range.Int(7, 7))),
                Map.of(), Map.of(), Optional.empty(), Optional.empty());
        assertTrue(entityMatches(env, player, Vec.ZERO, predicate));
        var survival = new EntitySubPredicate.Player(ANY_INT, FoodPredicate.ANY, List.of(GameMode.SURVIVAL), List.of(),
                Map.of(), Map.of(), Optional.empty(), Optional.empty());
        assertFalse(entityMatches(env, player, Vec.ZERO, survival));
        var pig = spawn(instance, new Entity(EntityType.PIG));
        assertFalse(entityMatches(env, pig, Vec.ZERO, survival));
    }

    @Test
    void slotsOfPlayer(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 42, 0));
        player.getInventory().setItemStack(3, ItemStack.of(Material.EMERALD));
        var hotbar = SlotRange.VALUES.stream().filter(range -> range.name().equals("hotbar.*")).findFirst().orElseThrow();
        var emerald = new ItemPredicate(Optional.of(HolderSet.direct(List.of(RegistryKey.<Material>of(Key.key("emerald"))))), ANY_INT,
                ComponentMatchers.ANY);
        assertTrue(entityMatches(env, player, Vec.ZERO, new EntitySubPredicate.Slots(Map.of(hotbar, emerald))));
        var armor = SlotRange.VALUES.stream().filter(range -> range.name().equals("armor.*")).findFirst().orElseThrow();
        assertFalse(entityMatches(env, player, Vec.ZERO, new EntitySubPredicate.Slots(Map.of(armor, emerald))));
    }

    @Test
    void entityComponents(Env env) {
        var instance = env.createFlatInstance();
        var pig = spawn(instance, new Entity(EntityType.PIG));
        pig.set(DataComponents.CUSTOM_NAME, Component.text("Babe"));
        assertTrue(entityMatches(env, pig, Vec.ZERO, new EntitySubPredicate.Components(
                DataComponentMap.builder().set(DataComponents.CUSTOM_NAME, Component.text("Babe")).build())));
        assertTrue(entityMatches(env, pig, Vec.ZERO, new EntitySubPredicate.Predicates(
                List.of(new ComponentPredicate.AnyValue(DataComponents.CUSTOM_NAME)))));
    }

    // ------------------------------------------------------------------
    // 位置、方块、流体
    // ------------------------------------------------------------------

    private static boolean locationMatches(Env env, Instance instance, Vec origin, LocationPredicate predicate) {
        var environment = new InMemoryLootEnvironment(env.process());
        return passes(env, environment, context(environment, instance, ContextKey.ORIGIN, origin),
                new LocationCheckCondition(Optional.of(predicate), 0, 0, 0));
    }

    private static LocationPredicate block(LocationPredicate.BlockPredicate block) {
        return new LocationPredicate(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(block), Optional.empty(), Optional.empty());
    }

    @Test
    void positionAndDimension(Env env) {
        var instance = env.createFlatInstance();
        var high = new LocationPredicate(Optional.of(new LocationPredicate.PositionPredicate(ANY_DOUBLE, new Range.Double(100.0, null), ANY_DOUBLE)),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());
        assertTrue(locationMatches(env, instance, new Vec(0, 120, 0), high));
        assertFalse(locationMatches(env, instance, new Vec(0, 50, 0), high));
        var overworld = new LocationPredicate(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(Key.key(instance.getDimensionName())),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertTrue(locationMatches(env, instance, Vec.ZERO, overworld));
    }

    @Test
    void biomeOfLoadedChunk(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var biome = instance.getBiome(8, 40, 8).key();
        var predicate = new LocationPredicate(Optional.empty(), Optional.of(HolderSet.direct(List.of(RegistryKey.of(biome)))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        // 方块 (8, 40, 8) 的生物群系取样点都在已加载的区块中
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                instance.loadChunk(dx, dz).join();
            }
        }
        assertTrue(locationMatches(env, instance, new Vec(8.5, 40, 8.5), predicate));
    }

    @Test
    void blockStateNbtAndUnloaded(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        instance.setBlock(2, 45, 2, Block.FURNACE.withProperty("lit", "true"));
        var litFurnace = new LocationPredicate.BlockPredicate(
                Optional.of(HolderSet.direct(List.of(RegistryKey.<Block>of(Key.key("furnace"))))),
                Optional.of(new StatePropertiesPredicate(List.of(new StatePropertiesPredicate.PropertyMatcher("lit",
                        new StatePropertiesPredicate.Exact("true"))))),
                Optional.empty(), ComponentMatchers.ANY);
        assertTrue(locationMatches(env, instance, new Vec(2.5, 45.5, 2.5), block(litFurnace)));
        // 枚举属性上的精确匹配
        var facing = new LocationPredicate.BlockPredicate(Optional.empty(), Optional.of(new StatePropertiesPredicate(List.of(
                new StatePropertiesPredicate.PropertyMatcher("facing", new StatePropertiesPredicate.Exact(
                        instance.getBlock(2, 45, 2).getProperty("facing")))))), Optional.empty(), ComponentMatchers.ANY);
        assertTrue(locationMatches(env, instance, new Vec(2.5, 45.5, 2.5), block(facing)));
        // 方块实体的 NBT 带有 id 与坐标
        var furnaceNbt = new LocationPredicate.BlockPredicate(Optional.empty(), Optional.empty(),
                Optional.of(CompoundBinaryTag.builder().putString("id", "minecraft:furnace").putInt("x", 2).build()), ComponentMatchers.ANY);
        assertTrue(locationMatches(env, instance, new Vec(2.5, 45.5, 2.5), block(furnaceNbt)));
        // 区块未加载时方块谓词不通过
        assertFalse(locationMatches(env, instance, new Vec(5000.5, 45.5, 5000.5), block(litFurnace)));
    }

    @Test
    void fluidOfWaterloggedBlock(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        instance.setBlock(3, 45, 3, Block.OAK_SLAB.withProperty("waterlogged", "true"));
        instance.setBlock(4, 45, 4, Block.WATER.withProperty("level", "3"));
        var water = new LocationPredicate.FluidPredicate(Optional.of(HolderSet.direct(List.of(
                RegistryKey.<net.minestom.server.instance.fluid.Fluid>of(Key.key("water"))))), Optional.empty());
        var fluidLocation = new LocationPredicate(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(water), Optional.empty());
        assertTrue(locationMatches(env, instance, new Vec(3.5, 45.5, 3.5), fluidLocation));
        // level 3 的水方块是流动的水，流体等级为 5
        var flowing = new LocationPredicate.FluidPredicate(Optional.of(HolderSet.direct(List.of(
                RegistryKey.<net.minestom.server.instance.fluid.Fluid>of(Key.key("flowing_water"))))),
                Optional.of(new StatePropertiesPredicate(List.of(new StatePropertiesPredicate.PropertyMatcher("level",
                        new StatePropertiesPredicate.Exact("5"))))));
        var flowingLocation = new LocationPredicate(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(flowing), Optional.empty());
        assertTrue(locationMatches(env, instance, new Vec(4.5, 45.5, 4.5), flowingLocation));
        assertFalse(locationMatches(env, instance, new Vec(3.5, 45.5, 3.5), flowingLocation));
    }

    @Test
    void smokeyAboveLitCampfire(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        instance.setBlock(6, 45, 6, Block.CAMPFIRE.withProperty("lit", "true"));
        var smokey = new LocationPredicate(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(true),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertTrue(locationMatches(env, instance, new Vec(6.5, 48.5, 6.5), smokey));
        // 原版遇到挡住烟柱的方块时只看它正下方是不是点燃的营火：紧贴营火的方块不挡烟
        instance.setBlock(6, 46, 6, Block.STONE);
        assertTrue(locationMatches(env, instance, new Vec(6.5, 48.5, 6.5), smokey));
        // 挡烟的方块与营火之间隔着空气时看不到烟
        instance.setBlock(6, 46, 6, Block.AIR);
        instance.setBlock(6, 47, 6, Block.STONE);
        assertFalse(locationMatches(env, instance, new Vec(6.5, 48.5, 6.5), smokey));
    }

    @Test
    void lightWithoutBoundsOnlyNeedsLoadedChunk(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var anyLight = new LocationPredicate(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new LocationPredicate.LightPredicate(ANY_INT)), Optional.empty(), Optional.empty(), Optional.empty());
        assertTrue(locationMatches(env, instance, new Vec(1, 45, 1), anyLight));
        assertTrue(locationMatches(env, instance, new Vec(1, 45, 1), NO_LOCATION));
    }
}
