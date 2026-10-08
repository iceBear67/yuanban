package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.condition.MatchToolCondition;
import io.ib67.yuanban.loot.condition.RandomChanceCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContexts;
import io.ib67.yuanban.loot.entry.AlternativesEntry;
import io.ib67.yuanban.loot.entry.EmptyEntry;
import io.ib67.yuanban.loot.entry.GroupEntry;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.SequenceEntry;
import io.ib67.yuanban.loot.entry.TagEntry;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.registry.RegistryKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static io.ib67.yuanban.loot.eval.EvalSupport.context;
import static io.ib67.yuanban.loot.eval.EvalSupport.item;
import static io.ib67.yuanban.loot.eval.EvalSupport.pool;
import static io.ib67.yuanban.loot.eval.EvalSupport.selector;
import static io.ib67.yuanban.loot.eval.EvalSupport.single;
import static io.ib67.yuanban.loot.eval.EvalSupport.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 求值器主流程：固定种子的 Random 同一输入得到同一产出；结构化的 {@code grass_block} 池复现了
 * 原版 {@code blocks/grass_block} 的经典 "剪刀 → 草方块，否则泥土" 模式；复合条目与权重按原版展开。
 */
@EnvTest
class LootSelectorRollsTest {

    private static LootTable grassBlock() {
        var shears = new ItemPredicate(Optional.of(HolderSet.direct(List.of(RegistryKey.<Material>of(Key.key("shears"))))),
                MinMaxBounds.ANY_INT, ComponentMatchers.ANY);
        var silk = item(Material.GRASS_BLOCK, List.of(new MatchToolCondition(Optional.of(shears))));
        var fallback = item(Material.DIRT);
        return new LootTable(Key.key("block"), List.of(), List.of(pool(new AlternativesEntry(List.of(), List.of(silk, fallback)))),
                Optional.empty());
    }

    private static io.ib67.yuanban.loot.context.LootContext blockContext(InMemoryLootEnvironment environment, ItemStack tool) {
        return LootContexts.block(Block.GRASS_BLOCK, Vec.ZERO, tool, null, null, null, null, environment);
    }

    @Test
    void sameSeedSameOutput(Env env) {
        var environment = new InMemoryLootEnvironment(env.process(), 42L);
        var selector = selector(env, environment);
        var context = blockContext(environment, ItemStack.of(Material.SHEARS));
        var first = selector.roll(grassBlock(), context, new Random(42L));
        var second = selector.roll(grassBlock(), context, new Random(42L));
        assertEquals(first, second);
    }

    @Test
    void shearsYieldsGrassBlock(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var result = selector(env, environment).roll(grassBlock(), blockContext(environment, ItemStack.of(Material.SHEARS)), new Random(42L));
        assertEquals(List.of(ItemStack.of(Material.GRASS_BLOCK)), result);
    }

    @Test
    void otherToolYieldsDirt(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var result = selector(env, environment).roll(grassBlock(), blockContext(environment, ItemStack.of(Material.DIAMOND_SHOVEL)), new Random(42L));
        assertEquals(List.of(ItemStack.of(Material.DIRT)), result);
    }

    @Test
    void randomChanceZeroDropsNothing(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var pool = new LootPool(List.of(new RandomChanceCondition(new ConstantValue(0.0f))),
                List.of(), new ConstantValue(1.0f), List.of(item(Material.STONE)));
        assertTrue(selector(env, environment).roll(table(pool), LootContexts.empty(null, environment), new Random(42L)).isEmpty());
    }

    @Test
    void randomChanceOneAlwaysPass(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = selector(env, environment);
        var pool = new LootPool(List.of(new RandomChanceCondition(new ConstantValue(1.0f))),
                List.of(), new ConstantValue(1.0f), List.of(item(Material.STONE)));
        // 100 个不同种子都通过（原版 LootItemRandomChanceCondition 是 nextFloat < chance）
        for (long seed = 0L; seed < 100L; seed++) {
            assertEquals(1, selector.roll(table(pool), LootContexts.empty(null, environment), new Random(seed)).size());
        }
    }

    @Test
    void randomSequenceIgnoresTheCallerRandom(Env env) {
        var environment = new InMemoryLootEnvironment(env.process(), 99L);
        var count = new SetCountFunction(List.of(), new io.ib67.yuanban.loot.number.UniformValue(new ConstantValue(1.0f),
                new ConstantValue(64.0f)), false);
        var table = new LootTable(LootTable.DEFAULT_TYPE, List.of(), List.of(pool(item(Material.STONE, count))),
                Optional.of(Key.key("example", "sequence")));
        var selector = selector(env, environment);
        // 表声明了 random_sequence 时按世界种子与序列 ID 派生随机数，调用者传入的随机数源不影响结果
        var first = selector.roll(table, LootContexts.empty(null, environment), new Random(1L));
        var second = selector.roll(table, LootContexts.empty(null, environment), new Random(2L));
        assertEquals(first, second);
    }

    @Test
    void missingTableByIdFailsFast(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertThrows(LootException.class,
                () -> selector(env, environment).roll(Key.key("test", "missing"), LootContexts.empty(null, environment), new Random()));
    }

    @Test
    void 权重为零的候选不参与抽取只剩一个候选时不消耗随机数(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var zero = new ItemEntry(List.of(), List.of(), 0, 0, Material.DIRT);
        var table = table(pool(zero, item(Material.STONE)));
        // 原版 LootPool#addRandomItem：权重为 0 的候选不进入列表，只剩一个候选时直接产出，不调用 nextInt
        var random = new Random(7L);
        var expectedNext = new Random(7L).nextInt();
        var context = new io.ib67.yuanban.loot.context.LootContextImpl(environment, null, Key.key("empty"),
                java.util.Map.of(), null, null, random);
        assertEquals(List.of(ItemStack.of(Material.STONE)), selector(env, environment).roll(table, context, random));
        assertEquals(expectedNext, random.nextInt());
    }

    @Test
    void 品质随运气改变权重(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.setLuck(-10.0f);
        // 原版 EntryBase#getWeight：max(floor(weight + quality * luck), 0)，运气为负时 quality 为正的候选权重降为 0
        var lucky = new ItemEntry(List.of(), List.of(), 1, 1, Material.DIAMOND);
        var table = table(pool(lucky, item(Material.STONE)));
        assertEquals(List.of(ItemStack.of(Material.STONE)),
                selector(env, environment).roll(table, LootContexts.empty(null, environment), new Random(0L)));
    }

    @Test
    void 替代条目在没有子条目时展开失败(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        // 原版 AlternativesEntry 没有子条目时为 ALWAYS_FALSE，外层 alternatives 继续尝试下一个子条目
        var outer = new AlternativesEntry(List.of(), List.of(new AlternativesEntry(List.of(), List.of()), item(Material.STONE)));
        assertEquals(List.of(ItemStack.of(Material.STONE)),
                selector(env, environment).roll(single(outer), LootContexts.empty(null, environment), new Random(0L)));
    }

    @Test
    void 序列条目在没有子条目时展开成功(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        // 原版 SequentialEntry 没有子条目时为 ALWAYS_TRUE，外层 alternatives 在这里停下
        var outer = new AlternativesEntry(List.of(), List.of(new SequenceEntry(List.of(), List.of()), item(Material.STONE)));
        assertTrue(selector(env, environment).roll(single(outer), LootContexts.empty(null, environment), new Random(0L)).isEmpty());
    }

    @Test
    void 组条目展开全部子条目(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var group = new GroupEntry(List.of(), List.of(item(Material.STONE), new EmptyEntry()));
        var rolls = new LootPool(List.of(), List.of(), new ConstantValue(50.0f), List.of(group));
        var result = selector(env, environment).roll(table(rolls), LootContexts.empty(null, environment), new Random(3L));
        assertTrue(result.stream().allMatch(stack -> stack.material() == Material.STONE));
        assertTrue(result.stream().mapToInt(ItemStack::amount).sum() > 0);
    }

    @Test
    void 展开的标签物品不经过条目的修饰器(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var planks = Key.key("minecraft", "planks");
        var members = new java.util.ArrayList<Material>();
        env.process().material().getTag(net.minestom.server.registry.TagKey.of(planks))
                .forEach(key -> members.add(env.process().material().get(key)));
        var setFive = List.<io.ib67.yuanban.loot.function.LootFunction>of(new SetCountFunction(List.of(), new ConstantValue(5)));
        var selector = selector(env, environment);
        var context = LootContexts.empty(null, environment);

        // 原版 TagEntry#expandTag 的候选项直接产出数量为 1 的物品，条目的修饰器不生效；每次抽取只命中一个物品
        var expanded = selector.roll(single(new TagEntry(List.of(), setFive, 1, 0, planks, true)), context, new Random(0L));
        assertEquals(1, expanded.size());
        assertEquals(1, expanded.getFirst().amount());
        assertTrue(members.contains(expanded.getFirst().material()));

        // 不展开时标签的每个物品各产出一次，并经过条目的修饰器
        var all = selector.roll(single(new TagEntry(List.of(), setFive, 1, 0, planks, false)), context, new Random(0L));
        assertEquals(members, all.stream().map(ItemStack::material).toList());
        assertTrue(all.stream().allMatch(stack -> stack.amount() == 5));
    }

    @Test
    void 不存在的标签按空标签处理(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var entry = new TagEntry(List.of(), List.of(), 1, 0, Key.key("example", "no_such_tag"), false);
        assertTrue(selector(env, environment).roll(single(entry), LootContexts.empty(null, environment), new Random(0L)).isEmpty());
    }

    @Test
    void 上下文参数按位置读取(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var context = context(environment, null, ContextKey.ORIGIN, new Vec(1, 2, 3));
        assertEquals(new Vec(1, 2, 3), LootEvaluation.origin(context));
    }
}
