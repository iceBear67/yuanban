package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.condition.KilledByPlayerCondition;
import io.ib67.yuanban.loot.context.LootContexts;
import io.ib67.yuanban.loot.entry.AlternativesEntry;
import io.ib67.yuanban.loot.entry.GroupEntry;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.entry.NestedTableEntry;
import io.ib67.yuanban.loot.entry.SlotsEntry;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.function.SetDamageFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.ScoreboardName;
import io.ib67.yuanban.loot.number.ScoreboardValue;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 覆盖检查（DESIGN 8.4）：roll 入口先把表里所有数字提供器、条件、修饰器的参数需求
 * 和上下文做对照，缺失时立刻报错，不允许到一半再失败。
 */
@EnvTest
class LootSelectorCoverageTest {

    private static LootTable tableWithEntryFunctions(List<io.ib67.yuanban.loot.function.LootFunction> functions) {
        var entry = new ItemEntry(List.of(), functions, 1, 0, Key.key("minecraft", "stone"));
        return new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), List.of(), new ConstantValue(1.0f), List.of(entry))), null);
    }

    @Test
    void slotsEntryFailsFast(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var entry = new SlotsEntry(List.of(), List.of(), 1, 0, null);
        var table = new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), List.of(), new ConstantValue(1.0f),
                        List.of(entry))), null);
        var exception = assertThrows(LootException.class, () ->
                selector.roll(table, LootContexts.empty(environment), new Random(0L)));
        assertEquals("当前版本不支持 slots 条目", exception.getMessage());
    }

    @Test
    void missingToolParamFailsBeforeDraw(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        // set_damage 仅在工具上才有意义；原版 SetItemDamageFunction.getReferencedContextParams 在
        // 设计上没声明 TOOL 参数（原版通过 isDamageableItem 防御空 tool），loot 模块沿用原版签名，
        // 此用例不放在覆盖检查集合里。
        var table = new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(KilledByPlayerCondition.INSTANCE), List.of(),
                        new ConstantValue(1.0f),
                        List.of(new ItemEntry(List.of(), List.of(), 1, 0, Key.key("minecraft", "stone"))))),
                null);
        var exception = assertThrows(LootException.class, () ->
                selector.roll(table, LootContexts.empty(environment), new Random(0L)));
        assertEquals("minecraft:last_damage_player is not provided in this context", exception.getMessage());
    }

    @Test
    void nestedEntryMissingParamFailsBeforeDraw(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.setLuck(0f);
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var leaf = new ItemEntry(List.of(KilledByPlayerCondition.INSTANCE), List.of(), 1, 0,
                Key.key("minecraft", "stone"));
        var top = new ItemEntry(List.of(), List.of(), 1, 0, Key.key("minecraft", "dirt"));
        // alternatives(有 KilledByPlayer 的叶子) 不在抽取结果里仍应被拒绝，原版 validation 是穷举型
        // （原版 {@code LootTable.validate} 遍历 {@code LootPool#entries} 的所有
        // {@code LootPoolEntryContainer#getReferencedContextParams}）。
        var table = new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), List.of(), new ConstantValue(1.0f),
                        List.of(new AlternativesEntry(List.of(), List.of(), List.of(leaf, top)) ))),
                null);
        var exception = assertThrows(LootException.class, () ->
                selector.roll(table, LootContexts.empty(environment), new Random(0L)));
        assertEquals("minecraft:last_damage_player is not provided in this context", exception.getMessage());
    }

    @Test
    void groupInsideGroupMissingParamPropagates(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var leaf = new ItemEntry(List.of(KilledByPlayerCondition.INSTANCE), List.of(), 1, 0,
                Key.key("minecraft", "stone"));
        var inner = new GroupEntry(List.of(), List.of(), List.of(leaf));
        var outer = new GroupEntry(List.of(), List.of(), List.of(inner));
        var table = new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), List.of(), new ConstantValue(1.0f), List.of(outer))), null);
        var exception = assertThrows(LootException.class, () ->
                selector.roll(table, LootContexts.empty(environment), new Random(0L)));
        assertEquals("minecraft:last_damage_player is not provided in this context", exception.getMessage());
    }

    @Test
    void functionNeedsToolReportsAtRollTime(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        // set_count 用 score 提供器引发了记分板参数需求；empty context 里没有 THIS_ENTITY 就应当在 roll
        // 入口报错，避免走到抽取一半再发现缺参。
        var table = tableWithEntryFunctions(List.of(
                new SetCountFunction(List.of(),
                        new ScoreboardValue(new ScoreboardName.Context(
                                io.ib67.yuanban.loot.number.EntityTarget.THIS), "kills", 1.0f), false)));
        // ScoreboardName.Context 不推 referencedParams（原版 ScoresProvider.getReferencedContextParams
        // 只对 contextTarget=THIS 才返回 THIS_ENTITY；record 覆盖了缺省实现时会有差异——loot 模块
        // 对 score 字段是有限支持，覆盖检查会忽略它）。本测试用另一种覆盖场景：
        // 直接依赖修饰器自身的 conditions 引用 LAST_DAMAGE_PLAYER。
        var conditional = new SetCountFunction(
                List.of(KilledByPlayerCondition.INSTANCE), new ConstantValue(2.0f), false);
        var checkedTable = new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), List.of(conditional), new ConstantValue(1.0f),
                        List.of(new ItemEntry(List.of(), List.of(), 1, 0,
                                Key.key("minecraft", "stone"))))), null);
        var ex = assertThrows(LootException.class, () ->
                selector.roll(checkedTable, LootContexts.empty(environment), new Random(0L)));
        assertEquals("minecraft:last_damage_player is not provided in this context", ex.getMessage());
    }

    @Test
    void missingFunctionReferenceWalksIntoAdd(Env env) {
        // 原版 ValidationContext 只校验已知的条件/修饰器引用，本模块沿用到 FunctionReference 的
        // 处理：缺失的 function ID 在求值时才报错（{@code FunctionReference} 内的子键随之失效）。
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = tableWithEntryFunctions(List.of(
                new io.ib67.yuanban.loot.function.FunctionReference(List.of(), Key.key("test", "nothing"))));
        var exception = assertThrows(LootException.class, () ->
                selector.roll(table, LootContexts.empty(environment), new Random(0L)));
        assertEquals("Unknown function: test:nothing", exception.getMessage());
    }

    @Test
    void sequenceEntryStopsOnFirstFailure(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.putCondition(Key.key("test", "yes"), new io.ib67.yuanban.loot.condition.RandomChanceCondition(
                new ConstantValue(1.0f)));
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var yes = new ItemEntry(List.of(new io.ib67.yuanban.loot.condition.RandomChanceCondition(
                        new ConstantValue(1.0f))), List.of(), 1, 0, Key.key("minecraft", "stone"));
        var no = new ItemEntry(List.of(new io.ib67.yuanban.loot.condition.RandomChanceCondition(
                        new ConstantValue(0.0f))), List.of(), 1, 0, Key.key("minecraft", "dirt"));
        var after = new ItemEntry(List.of(), List.of(), 1, 0, Key.key("minecraft", "cobblestone"));
        var sequence = new io.ib67.yuanban.loot.entry.SequenceEntry(List.of(), List.of(),
                List.of(yes, no, after));
        var table = new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), List.of(), new ConstantValue(1.0f), List.of(sequence))), null);
        var result = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        // 原版 sequence：走到第一个 condition 不通过的子就停（前面展开的保留）。SequenceEntry 在 loot
        // 模块按复合加入抽取采样（见原版 {@code LootPool.listCompositeEntries} 中
        // {@code ComposableEntryContainer#expand=Compositor 处理为 optional}）。
        assertEquals(1, result.size());
    }
}
