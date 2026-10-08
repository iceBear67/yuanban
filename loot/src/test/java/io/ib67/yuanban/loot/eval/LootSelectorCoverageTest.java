package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.condition.KilledByPlayerCondition;
import io.ib67.yuanban.loot.condition.RandomChanceCondition;
import io.ib67.yuanban.loot.context.LootContextArg;
import io.ib67.yuanban.loot.context.LootContexts;
import io.ib67.yuanban.loot.entry.AlternativesEntry;
import io.ib67.yuanban.loot.entry.GroupEntry;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.SequenceEntry;
import io.ib67.yuanban.loot.entry.SlotsEntry;
import io.ib67.yuanban.loot.function.FunctionReference;
import io.ib67.yuanban.loot.function.SetContainerContentsFunction;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.ScoreboardName;
import io.ib67.yuanban.loot.number.ScoreboardValue;
import io.ib67.yuanban.loot.number.UniformValue;
import io.ib67.yuanban.loot.slot.ContainerComponent;
import io.ib67.yuanban.loot.slot.SlotRange;
import io.ib67.yuanban.loot.slot.SlotSource;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static io.ib67.yuanban.loot.eval.EvalSupport.item;
import static io.ib67.yuanban.loot.eval.EvalSupport.pool;
import static io.ib67.yuanban.loot.eval.EvalSupport.selector;
import static io.ib67.yuanban.loot.eval.EvalSupport.single;
import static io.ib67.yuanban.loot.eval.EvalSupport.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 覆盖检查（DESIGN 8.4）：roll 入口先把表里所有数字提供器、条件、修饰器、槽位来源的参数需求
 * 和上下文做对照，缺失时立刻报错，不允许到一半再失败。
 */
@EnvTest
class LootSelectorCoverageTest {
    private static final String MISSING_PLAYER = "minecraft:last_damage_player is not provided in this context";

    private static void assertMissing(Env env, io.ib67.yuanban.loot.LootTable table, String message) {
        var environment = new InMemoryLootEnvironment(env.process());
        var exception = assertThrows(LootException.class, () ->
                selector(env, environment).roll(table, LootContexts.empty(null, environment), new Random(0L)));
        assertEquals(message, exception.getMessage());
    }

    @Test
    void missingParamOfPoolCondition(Env env) {
        assertMissing(env, table(new LootPool(List.of(KilledByPlayerCondition.INSTANCE), List.of(), new ConstantValue(1.0f),
                List.of(item(Material.STONE)))), MISSING_PLAYER);
    }

    @Test
    void nestedEntryMissingParamFailsBeforeDraw(Env env) {
        // alternatives 中不会被抽到的叶子也要检查，原版 validate 遍历全部条目
        var leaf = item(Material.STONE, List.of(KilledByPlayerCondition.INSTANCE));
        assertMissing(env, single(new AlternativesEntry(List.of(), List.of(item(Material.DIRT), leaf))), MISSING_PLAYER);
    }

    @Test
    void groupInsideGroupMissingParamPropagates(Env env) {
        var leaf = item(Material.STONE, List.of(KilledByPlayerCondition.INSTANCE));
        assertMissing(env, single(new GroupEntry(List.of(), List.of(new GroupEntry(List.of(), List.of(leaf))))), MISSING_PLAYER);
    }

    @Test
    void functionConditionMissingParam(Env env) {
        var conditional = new SetCountFunction(List.of(KilledByPlayerCondition.INSTANCE), new ConstantValue(2.0f), false);
        assertMissing(env, table(new LootPool(List.of(), List.of(conditional), new ConstantValue(1.0f), List.of(item(Material.STONE)))),
                MISSING_PLAYER);
    }

    @Test
    void nestedNumberProviderMissingParam(Env env) {
        // uniform 的下界是读取 this 实体分数的提供器
        var score = new ScoreboardValue(new ScoreboardName.Context(EntityTarget.THIS), "kills");
        var count = new SetCountFunction(List.of(), new UniformValue(score, new ConstantValue(3.0f)), false);
        assertMissing(env, single(item(Material.STONE, count)), "minecraft:this_entity is not provided in this context");
    }

    @Test
    void setContentsEntriesAreChecked(Env env) {
        var inner = item(Material.APPLE, List.of(KilledByPlayerCondition.INSTANCE));
        var contents = new SetContainerContentsFunction(List.of(), ContainerComponent.CONTAINER, List.of(inner));
        assertMissing(env, single(item(Material.SHULKER_BOX, contents)), MISSING_PLAYER);
    }

    @Test
    void slotSourceParamsAreChecked(Env env) {
        var range = SlotRange.VALUES.stream().filter(value -> value.name().equals("hotbar.*")).findFirst().orElseThrow();
        var source = new SlotSource.Filtered(new SlotSource.SlotRangeSource(new LootContextArg.Entity(EntityTarget.ATTACKER), range),
                new io.ib67.yuanban.loot.predicate.ItemPredicate(java.util.Optional.empty(), io.ib67.yuanban.codec.MinMaxBounds.ANY_INT,
                        io.ib67.yuanban.loot.predicate.ComponentMatchers.ANY));
        assertMissing(env, single(new SlotsEntry(List.of(), List.of(), 1, 0, source)),
                "minecraft:attacking_entity is not provided in this context");
    }

    @Test
    void missingFunctionReferenceFailsWhenApplied(Env env) {
        // 修饰器引用在求值时才查找，找不到时报错
        assertMissing(env, single(item(Material.STONE, new FunctionReference(List.of(), Key.key("test", "nothing")))),
                "Unknown function: test:nothing");
    }

    @Test
    void sequenceEntryStopsOnFirstFailure(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var yes = item(Material.STONE, List.of(new RandomChanceCondition(new ConstantValue(1.0f))));
        var no = item(Material.DIRT, List.of(new RandomChanceCondition(new ConstantValue(0.0f))));
        var after = item(Material.COBBLESTONE);
        var table = table(pool(new SequenceEntry(List.of(), List.of(yes, no, after))));
        // 原版 sequence：遇到第一个展开失败的子条目就停，前面展开的候选项保留，一次抽取命中其中一个
        var result = selector(env, environment).roll(table, LootContexts.empty(null, environment), new Random(0L));
        assertEquals(List.of(Material.STONE), result.stream().map(net.minestom.server.item.ItemStack::material).toList());
    }

    @Test
    void entriesWithoutConditionsAreDrawnOnce(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var result = selector(env, environment).roll(single(new ItemEntry(Material.STONE)), LootContexts.empty(null, environment), new Random(0L));
        assertEquals(1, result.size());
    }
}
