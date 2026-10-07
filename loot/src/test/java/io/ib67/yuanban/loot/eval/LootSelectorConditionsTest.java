package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.condition.AllOfCondition;
import io.ib67.yuanban.loot.condition.InvertedCondition;
import io.ib67.yuanban.loot.condition.KilledByPlayerCondition;
import io.ib67.yuanban.loot.condition.ReferenceCondition;
import io.ib67.yuanban.loot.condition.RandomChanceCondition;
import io.ib67.yuanban.loot.condition.TableBonusCondition;
import io.ib67.yuanban.loot.context.LootContexts;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.number.ConstantValue;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 条件：原版 {@code net.minecraft.world.level.storage.loot.predicates} 同名类的覆盖。
 */
@EnvTest
class LootSelectorConditionsTest {

    private static LootTable tableWith(List<io.ib67.yuanban.loot.condition.LootCondition> poolConditions) {
        var entry = new ItemEntry(List.of(), List.of(), 1, 0, Key.key("minecraft", "stone"));
        return new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(poolConditions, List.of(), new ConstantValue(1.0f), List.of(entry))), null);
    }

    @Test
    void killedByPlayerWithoutParamReportsAtRollEntry(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = tableWith(List.of(KilledByPlayerCondition.INSTANCE));
        var exception = assertThrows(LootException.class, () ->
                selector.roll(table, LootContexts.empty(environment), new Random(0L)));
        assertEquals("minecraft:last_damage_player is not provided in this context", exception.getMessage());
    }

    @Test
    void killedByPlayerWithPresentParam(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = tableWith(List.of(KilledByPlayerCondition.INSTANCE));
        // 原版 killed_by_player 只判 LAST_DAMAGE_PLAYER 参数是否存在（LootItemKilledByPlayerCondition.test
        // 是 hasParameter），不读取实际玩家对象——loot 模块沿用相同语义。
        var context = LootContexts.entity(null, null, null, null, null,
                new Object() /* 测试布置：任意非 null 值即表示参数已设置 */, environment);
        var result = selector.roll(table, context, new Random(0L));
        assertEquals(1, result.size());
    }

    @Test
    void randomChanceConsistentWithSeed(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = tableWith(List.of(new RandomChanceCondition(new ConstantValue(0.5f))));
        var context = LootContexts.empty(environment);
        var first = selector.roll(table, context, new Random(17L));
        var second = selector.roll(table, context, new Random(17L));
        assertEquals(first.size(), second.size());
    }

    @Test
    void tableBonusBinnedByLevel(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var bonus = new TableBonusCondition(Key.key("minecraft", "fortune"), List.of(0.0f, 1.0f));
        var plainShovel = ItemStack.of(Material.DIAMOND_SHOVEL);
        var context = LootContexts.block(null, null, plainShovel, null, null, null, environment);
        var table = tableWith(List.of(bonus));
        // 没有指定魔咒，等级 0 → 概率 0.0 → 不产出
        assertTrue(selector.roll(table, context, new Random(0L)).isEmpty());
    }

    @Test
    void referenceConditionResolves(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.putCondition(Key.key("test", "always_true"), new RandomChanceCondition(new ConstantValue(1.0f)));
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = tableWith(List.of(new ReferenceCondition(Key.key("test", "always_true"))));
        var result = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        assertEquals(1, result.size());
    }

    @Test
    void referenceConditionMissingFailsFast(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = tableWith(List.of(new ReferenceCondition(Key.key("test", "missing"))));
        var exception = assertThrows(LootException.class, () ->
                selector.roll(table, LootContexts.empty(environment), new Random(0L)));
        assertEquals("Unknown condition: test:missing", exception.getMessage());
    }

    @Test
    void invertedNegates(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = tableWith(List.of(new InvertedCondition(new RandomChanceCondition(new ConstantValue(0.0f)))));
        var result = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        assertEquals(1, result.size());
    }

    @Test
    void allOfRequiresAll(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var zeroTable = tableWith(List.of(new AllOfCondition(List.of(
                new RandomChanceCondition(new ConstantValue(1.0f)),
                new RandomChanceCondition(new ConstantValue(0.0f))))));
        assertTrue(selector.roll(zeroTable, LootContexts.empty(environment), new Random(0L)).isEmpty());
        var oneTable = tableWith(List.of(new AllOfCondition(List.of(
                new RandomChanceCondition(new ConstantValue(1.0f)),
                new RandomChanceCondition(new ConstantValue(1.0f))))));
        assertEquals(1, selector.roll(oneTable, LootContexts.empty(environment), new Random(0L)).size());
    }
}
