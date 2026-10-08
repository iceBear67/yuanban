package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.condition.EntityPropertiesCondition;
import io.ib67.yuanban.loot.condition.EnvironmentAttributeCheckCondition;
import io.ib67.yuanban.loot.condition.LocationCheckCondition;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.condition.MatchToolCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContext;
import io.ib67.yuanban.loot.context.LootContextArg;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.SlotsEntry;
import io.ib67.yuanban.loot.function.AppendOperation;
import io.ib67.yuanban.loot.function.CopyComponentsFunction;
import io.ib67.yuanban.loot.function.CopyCustomDataFunction;
import io.ib67.yuanban.loot.function.CopyNameFunction;
import io.ib67.yuanban.loot.function.ExplorationMapFunction;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.function.NbtSource;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.function.SetLoreFunction;
import io.ib67.yuanban.loot.function.SetNameFunction;
import io.ib67.yuanban.loot.function.SetNameTarget;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.EnvironmentAttributeValue;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.predicate.LocationPredicate;
import io.ib67.yuanban.loot.predicate.StatePropertiesPredicate;
import io.ib67.yuanban.loot.predicate.component.AttributeEntryPredicate;
import io.ib67.yuanban.loot.predicate.component.ComponentPredicate;
import io.ib67.yuanban.loot.predicate.entity.AdvancementPredicate;
import io.ib67.yuanban.loot.predicate.entity.EntityPredicate;
import io.ib67.yuanban.loot.predicate.entity.EntitySubPredicate;
import io.ib67.yuanban.loot.predicate.entity.FoodPredicate;
import io.ib67.yuanban.loot.slot.SlotRange;
import io.ib67.yuanban.loot.slot.SlotSource;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.VillagerType;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.entity.metadata.animal.ChestedHorseMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.AttributeList;
import net.minestom.server.utils.Range;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static io.ib67.yuanban.loot.eval.EvalSupport.context;
import static io.ib67.yuanban.loot.eval.EvalSupport.item;
import static io.ib67.yuanban.loot.eval.EvalSupport.selector;
import static io.ib67.yuanban.loot.eval.EvalSupport.single;
import static io.ib67.yuanban.loot.eval.EvalSupport.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minestom 缺少原版求值所需数据的每一处都抛出 {@link LootException}，与源码中的待办注释一一对应。
 */
@EnvTest
class UnsupportedEvaluationTest {
    private static final Range.Int ANY_INT = MinMaxBounds.ANY_INT;
    private static final Range.Double ANY_DOUBLE = MinMaxBounds.ANY_DOUBLE;

    private static void assertUnsupported(String what, Runnable evaluation) {
        var exception = assertThrows(LootException.class, evaluation::run);
        assertEquals("Not supported on Minestom: " + what, exception.getMessage());
    }

    private static void assertUnsupportedPrefix(String what, Runnable evaluation) {
        var exception = assertThrows(LootException.class, evaluation::run);
        assertTrue(exception.getMessage().startsWith("Not supported on Minestom: " + what), exception.getMessage());
    }

    private static void rollCondition(Env env, LootContext context, InMemoryLootEnvironment environment, LootCondition condition) {
        selector(env, environment).roll(table(new LootPool(List.of(condition), List.of(), new ConstantValue(1.0f),
                List.of(item(Material.STONE)))), context, new Random(0L));
    }

    private static void rollFunction(Env env, LootContext context, InMemoryLootEnvironment environment, Material material,
                                     LootFunction function) {
        selector(env, environment).roll(single(new ItemEntry(List.of(), List.of(function), 1, 0, material)), context, new Random(0L));
    }

    private static void entityCheck(Env env, Entity entity, EntitySubPredicate part) {
        var environment = new InMemoryLootEnvironment(env.process());
        var context = context(environment, entity.getInstance(), ContextKey.THIS_ENTITY, entity, ContextKey.ORIGIN, Vec.ZERO);
        rollCondition(env, context, environment, new EntityPropertiesCondition(Optional.of(new EntityPredicate(List.of(part))), EntityTarget.THIS));
    }

    private static void locationCheck(Env env, Instance instance, Vec origin, LocationPredicate predicate) {
        var environment = new InMemoryLootEnvironment(env.process());
        rollCondition(env, context(environment, instance, ContextKey.ORIGIN, origin), environment,
                new LocationCheckCondition(Optional.of(predicate), 0, 0, 0));
    }

    private static LocationPredicate location(Optional<HolderSet<net.minestom.server.world.biome.Biome>> biomes,
                                              Optional<HolderSet<io.ib67.yuanban.codec.Structure>> structures,
                                              Optional<LocationPredicate.LightPredicate> light,
                                              Optional<LocationPredicate.BlockPredicate> block,
                                              Optional<Boolean> canSeeSky) {
        return new LocationPredicate(Optional.empty(), biomes, structures, Optional.empty(), Optional.empty(), light, block,
                Optional.empty(), canSeeSky);
    }

    private static Entity spawn(Instance instance, Entity entity) {
        entity.setInstance(instance, new Pos(0.5, 40, 0.5)).join();
        return entity;
    }

    private static List<ItemStack> slots(Env env, Entity entity, String range) {
        var environment = new InMemoryLootEnvironment(env.process());
        var slotRange = SlotRange.VALUES.stream().filter(value -> value.name().equals(range)).findFirst().orElseThrow();
        var entry = new SlotsEntry(List.of(), List.of(), 1, 0,
                new SlotSource.SlotRangeSource(new LootContextArg.Entity(EntityTarget.THIS), slotRange));
        return selector(env, environment).roll(single(entry), context(environment, null, ContextKey.THIS_ENTITY, entity), new Random(0L));
    }

    // ------------------------------------------------------------------
    // 位置
    // ------------------------------------------------------------------

    @Test
    void biomeOfUnloadedCorner(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var plains = Optional.<HolderSet<net.minestom.server.world.biome.Biome>>of(HolderSet.named(Key.key("is_overworld")));
        // 区块 (0, 0) 西北角的方块，取样点有一半以上落在未加载的相邻区块中；原版只读取按种子选中的那个取样点，
        // 这里逐个位置求值，直到选中的取样点落在未加载的区块中
        assertUnsupportedPrefix("biome of the unloaded chunk", () -> {
            for (int y = 40; y < 60; y++) {
                for (int x = 0; x < 2; x++) {
                    for (int z = 0; z < 2; z++) {
                        locationCheck(env, instance, new Vec(x + 0.5, y, z + 0.5),
                                location(plains, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
                    }
                }
            }
        });
    }

    @Test
    void structures(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        assertUnsupported("structures at a position", () -> locationCheck(env, instance, new Vec(8, 40, 8),
                location(Optional.empty(), Optional.of(HolderSet.named(Key.key("village"))), Optional.empty(), Optional.empty(), Optional.empty())));
    }

    @Test
    void canSeeSky(Env env) {
        var instance = env.createFlatInstance();
        assertUnsupported("whether a position can see the sky", () -> locationCheck(env, instance, new Vec(8, 40, 8),
                location(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(true))));
    }

    @Test
    void boundedLight(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        assertUnsupported("raw brightness at a position", () -> locationCheck(env, instance, new Vec(8, 40, 8),
                location(Optional.empty(), Optional.empty(), Optional.of(new LocationPredicate.LightPredicate(new Range.Int(0, 15))),
                        Optional.empty(), Optional.empty())));
    }

    @Test
    void blockEntityComponents(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        instance.setBlock(1, 41, 1, Block.CHEST);
        var withComponents = new LocationPredicate.BlockPredicate(Optional.empty(), Optional.empty(), Optional.empty(),
                new ComponentMatchers(net.minestom.server.component.DataComponentMap.EMPTY,
                        List.of(new ComponentPredicate.AnyValue(DataComponents.CUSTOM_NAME))));
        assertUnsupported("data components of the block entity minecraft:chest", () -> locationCheck(env, instance, new Vec(1.5, 41.5, 1.5),
                location(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(withComponents), Optional.empty())));
    }

    @Test
    void rangeOnEnumProperty(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var ranged = new StatePropertiesPredicate(List.of(new StatePropertiesPredicate.PropertyMatcher("facing",
                new StatePropertiesPredicate.Ranged(Optional.of("north"), Optional.empty()))));
        assertUnsupportedPrefix("range matching on the enum block state property with values",
                () -> rollCondition(env, context(environment, null, ContextKey.BLOCK_STATE, Block.FURNACE), environment,
                        new io.ib67.yuanban.loot.condition.BlockStatePropertyCondition(Block.FURNACE, Optional.of(ranged))));
    }

    // ------------------------------------------------------------------
    // 实体
    // ------------------------------------------------------------------

    @Test
    void steppingOnWhileOnGround(Env env) {
        var instance = env.createFlatInstance();
        var pig = spawn(instance, new LivingEntity(EntityType.PIG));
        env.tickWhile(() -> !pig.isOnGround(), Duration.ofSeconds(5));
        assertTrue(pig.isOnGround());
        var predicate = new EntitySubPredicate.SteppingOn(location(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty()));
        assertUnsupported("block supporting the entity minecraft:pig", () -> entityCheck(env, pig, predicate));
    }

    @Test
    void movementAffectedBy(Env env) {
        var pig = new LivingEntity(EntityType.PIG);
        assertUnsupported("block affecting the movement of the entity minecraft:pig", () -> entityCheck(env, pig,
                new EntitySubPredicate.MovementAffectedBy(location(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty()))));
    }

    @Test
    void entityNbt(Env env) {
        assertUnsupported("NBT of the entity minecraft:pig", () -> entityCheck(env, new Entity(EntityType.PIG),
                new EntitySubPredicate.Nbt(net.kyori.adventure.nbt.CompoundBinaryTag.empty())));
    }

    @Test
    void entityTags(Env env) {
        assertUnsupported("command tags of the entity minecraft:pig", () -> entityCheck(env, new Entity(EntityType.PIG),
                new EntitySubPredicate.EntityTags(Optional.of(List.of("a")), Optional.empty(), Optional.empty())));
    }

    @Test
    void lightningWithConditions(Env env) {
        assertUnsupported("blocks set on fire and entities struck by the lightning bolt", () -> entityCheck(env,
                new Entity(EntityType.LIGHTNING_BOLT), new EntitySubPredicate.Lightning(new Range.Int(1, null), Optional.empty())));
    }

    @Test
    void fishingHookInOpenWater(Env env) {
        assertUnsupported("whether the fishing hook is in open water", () -> entityCheck(env, new Entity(EntityType.FISHING_BOBBER),
                new EntitySubPredicate.FishingHook(Optional.of(true))));
    }

    @Test
    void raider(Env env) {
        assertUnsupported("raid and captain state of the raider minecraft:pillager", () -> entityCheck(env,
                new LivingEntity(EntityType.PILLAGER), new EntitySubPredicate.Raider(false, false)));
    }

    @Test
    void fallDistance(Env env) {
        var movement = new EntitySubPredicate.Movement(ANY_DOUBLE, ANY_DOUBLE, ANY_DOUBLE, ANY_DOUBLE, ANY_DOUBLE, ANY_DOUBLE,
                new Range.Double(1.0, null));
        assertUnsupported("fall distance of the entity minecraft:pig", () -> entityCheck(env, new Entity(EntityType.PIG), movement));
    }

    @Test
    void isInWater(Env env) {
        var flags = new EntitySubPredicate.Flags(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(true), Optional.empty());
        assertUnsupported("whether the entity minecraft:pig is in water", () -> entityCheck(env, new Entity(EntityType.PIG), flags));
    }

    @Test
    void playerRecipes(Env env) {
        var player = env.createPlayer(env.createFlatInstance(), new Pos(0, 42, 0));
        var recipes = new EntitySubPredicate.Player(ANY_INT, FoodPredicate.ANY, List.of(net.minestom.server.entity.GameMode.values()),
                List.of(), Map.of(Key.key("bread"), true), Map.of(), Optional.empty(), Optional.empty());
        assertUnsupported("recipe book of the player", () -> entityCheck(env, player, recipes));
    }

    @Test
    void playerAdvancements(Env env) {
        var player = env.createPlayer(env.createFlatInstance(), new Pos(0, 42, 0));
        var advancements = new EntitySubPredicate.Player(ANY_INT, FoodPredicate.ANY, List.of(net.minestom.server.entity.GameMode.values()),
                List.of(), Map.of(), Map.of(Key.key("story/root"), new AdvancementPredicate.Done(true)), Optional.empty(), Optional.empty());
        assertUnsupported("advancements of the player", () -> entityCheck(env, player, advancements));
    }

    // ------------------------------------------------------------------
    // 槽位
    // ------------------------------------------------------------------

    @Test
    void playerEnderChest(Env env) {
        var player = env.createPlayer(env.createFlatInstance(), new Pos(0, 42, 0));
        assertUnsupported("ender chest slot 200 of a player", () -> slots(env, player, "enderchest.*"));
    }

    @Test
    void arrowPickupItem(Env env) {
        assertUnsupported("pickup item of the arrow minecraft:arrow", () -> slots(env, new Entity(EntityType.ARROW), "contents"));
    }

    @Test
    void chestMinecartContents(Env env) {
        assertUnsupported("container slot 0 of minecraft:chest_minecart", () -> slots(env, new Entity(EntityType.CHEST_MINECART), "container.*"));
    }

    @Test
    void chestedHorseInventory(Env env) {
        var donkey = new LivingEntity(EntityType.DONKEY);
        ((ChestedHorseMeta) donkey.getEntityMeta()).setHasChest(true);
        assertEquals(List.of(ItemStack.of(Material.CHEST)), slots(env, donkey, "horse.chest"));
        assertUnsupported("chest slot 500 of minecraft:donkey", () -> slots(env, donkey, "horse.*"));
    }

    @Test
    void villagerInventory(Env env) {
        assertUnsupported("inventory slot 300 of minecraft:villager", () -> slots(env, new LivingEntity(EntityType.VILLAGER), "mob.inventory.*"));
    }

    @Test
    void blockEntityAsSlotSource(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var hotbar = SlotRange.VALUES.stream().filter(value -> value.name().equals("container.*")).findFirst().orElseThrow();
        var entry = new SlotsEntry(List.of(), List.of(), 1, 0, new SlotSource.SlotRangeSource(LootContextArg.BlockEntity.INSTANCE, hotbar));
        assertUnsupported("slots of the block entity minecraft:chest", () -> selector(env, environment).roll(single(entry),
                context(environment, null, ContextKey.BLOCK_ENTITY, Block.CHEST), new Random(0L)));
    }

    // ------------------------------------------------------------------
    // 组件与注册表
    // ------------------------------------------------------------------

    @Test
    void attributeTag(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var tool = ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.ATTRIBUTE_MODIFIERS, new AttributeList(new AttributeList.Modifier(
                Attribute.ATTACK_DAMAGE, new AttributeModifier(Key.key("example:a"), 1.0, AttributeOperation.ADD_VALUE), EquipmentSlotGroup.MAIN_HAND)));
        var predicate = new ComponentPredicate.AttributeModifiers(Optional.of(new io.ib67.yuanban.loot.predicate.CollectionPredicate<>(
                Optional.of(List.of(new AttributeEntryPredicate(Optional.of(HolderSet.named(Key.key("example:attributes"))), Optional.empty(),
                        ANY_DOUBLE, Optional.empty(), Optional.empty()))), Optional.empty(), Optional.empty())));
        var items = new ItemPredicate(Optional.empty(), ANY_INT, new ComponentMatchers(net.minestom.server.component.DataComponentMap.EMPTY,
                List.of(predicate)));
        assertUnsupported("tag #example:attributes of the registry minecraft:attribute", () -> rollCondition(env,
                context(environment, null, ContextKey.TOOL, tool), environment, new MatchToolCondition(Optional.of(items))));
    }

    @Test
    void villagerTypeTag(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var tool = ItemStack.of(Material.STONE).with(DataComponents.VILLAGER_VARIANT, VillagerType.PLAINS);
        var items = new ItemPredicate(Optional.empty(), ANY_INT, new ComponentMatchers(net.minestom.server.component.DataComponentMap.EMPTY,
                List.of(new ComponentPredicate.VillagerVariant(HolderSet.named(Key.key("example:types"))))));
        assertUnsupported("tag #example:types of the registry minecraft:villager_type", () -> rollCondition(env,
                context(environment, null, ContextKey.TOOL, tool), environment, new MatchToolCondition(Optional.of(items))));
    }

    @Test
    void environmentAttributeNumber(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertUnsupported("value of the environment attribute minecraft:gameplay/sky_light_level", () -> rollFunction(env,
                context(environment, null), environment, Material.STONE,
                new SetCountFunction(List.of(), new EnvironmentAttributeValue(EnvironmentAttribute.SKY_LIGHT_LEVEL), false)));
    }

    @Test
    void environmentAttributeCheck(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertUnsupported("value of the environment attribute minecraft:gameplay/fast_lava", () -> rollCondition(env,
                context(environment, null), environment, new EnvironmentAttributeCheckCondition<>(EnvironmentAttribute.FAST_LAVA, true)));
    }

    // ------------------------------------------------------------------
    // 修饰器
    // ------------------------------------------------------------------

    @Test
    void explorationMap(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var function = new ExplorationMapFunction(List.of(), ExplorationMapFunction.DEFAULT_DESTINATION,
                ExplorationMapFunction.DEFAULT_DECORATION, (byte) 2, 50, true);
        assertUnsupported("structure search and map data for the exploration map", () -> rollFunction(env,
                context(environment, null, ContextKey.ORIGIN, Vec.ZERO), environment, Material.MAP, function));
        // 不是空地图时原样返回
        assertEquals(List.of(ItemStack.of(Material.STONE)), selector(env, environment).roll(single(new ItemEntry(List.of(),
                List.of(function), 1, 0, Material.STONE)), context(environment, null, ContextKey.ORIGIN, Vec.ZERO), new Random(0L)));
    }

    @Test
    void copyNameFromBlockEntity(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertUnsupported("custom name of the block entity minecraft:chest", () -> rollFunction(env,
                context(environment, null, ContextKey.BLOCK_ENTITY, Block.CHEST), environment, Material.CHEST,
                new CopyNameFunction(List.of(), LootContextArg.BlockEntity.INSTANCE)));
    }

    @Test
    void resolveTextForEntity(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var context = context(environment, null, ContextKey.THIS_ENTITY, new Entity(EntityType.PIG));
        assertUnsupported("resolving text components for an entity", () -> rollFunction(env, context, environment, Material.STONE,
                new SetNameFunction(List.of(), Component.text("x"), EntityTarget.THIS, SetNameTarget.CUSTOM_NAME)));
        assertUnsupported("resolving text components for an entity", () -> rollFunction(env, context, environment, Material.STONE,
                new SetLoreFunction(List.of(), List.of(Component.text("x")), AppendOperation.INSTANCE, Optional.of(EntityTarget.THIS))));
    }

    @Test
    void copyCustomDataFromEntity(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertUnsupported("NBT of the entity minecraft:pig", () -> rollFunction(env,
                context(environment, null, ContextKey.THIS_ENTITY, new Entity(EntityType.PIG)), environment, Material.STONE,
                new CopyCustomDataFunction(List.of(), new NbtSource.Context(new LootContextArg.Entity(EntityTarget.THIS)), List.of())));
    }

    @Test
    void copyComponentsFromBlockEntity(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertUnsupported("data components of the block entity minecraft:chest", () -> rollFunction(env,
                context(environment, null, ContextKey.BLOCK_ENTITY, Block.CHEST), environment, Material.CHEST,
                new CopyComponentsFunction(List.of(), LootContextArg.BlockEntity.INSTANCE, Optional.empty(), Optional.empty())));
    }
}
