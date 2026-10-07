package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.condition.ItemPredicate;
import io.ib67.yuanban.loot.condition.MatchToolCondition;
import io.ib67.yuanban.loot.condition.RandomChanceCondition;
import io.ib67.yuanban.loot.context.LootContexts;
import io.ib67.yuanban.loot.entry.AlternativesEntry;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 求值器主流程：固定种子的 Random 同一输入得到同一产出；结构化的 {@code grass_block} 池复现了
 * 原版 {@code blocks/grass_block} 的经典 "silk touch → 草方块，否则泥土" 模式。
 */
@EnvTest
class LootSelectorRollsTest {

    private static LootTable grassBlock() {
        var silk = new ItemEntry(List.of(new MatchToolCondition(
                        new ItemPredicate(List.of(Key.key("minecraft", "shears")), null))),
                List.of(), 1, 0, Key.key("minecraft:grass_block"));
        var fallback = new ItemEntry(List.of(), List.of(), 1, 0, Key.key("minecraft:dirt"));
        return new LootTable(Key.key("block"), List.of(), List.of(
                new LootPool(List.of(), List.of(), new ConstantValue(1.0f), List.of(
                        new AlternativesEntry(List.of(), List.of(), List.of(silk, fallback))))), null);
    }

    @Test
    void sameSeedSameOutput(Env env) {
        var environment = new InMemoryLootEnvironment(env.process(), 42L);
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var tool = ItemStack.of(Material.SHEARS);
        var context = LootContexts.block(null, null, tool, null, null, null, environment);

        var first = selector.roll(grassBlock(), context, new Random(42L));
        var second = selector.roll(grassBlock(), context, new Random(42L));
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).material(), second.get(i).material());
            assertEquals(first.get(i).amount(), second.get(i).amount());
        }
    }

    @Test
    void shearsYieldsGrassBlock(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var tool = ItemStack.of(Material.SHEARS);
        var context = LootContexts.block(null, null, tool, null, null, null, environment);
        var result = selector.roll(grassBlock(), context, new Random(42L));
        assertEquals(1, result.size());
        assertEquals(Material.GRASS_BLOCK, result.getFirst().material());
    }

    @Test
    void otherToolYieldsDirt(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var tool = ItemStack.of(Material.DIAMOND_SHOVEL);
        var context = LootContexts.block(null, null, tool, null, null, null, environment);
        var result = selector.roll(grassBlock(), context, new Random(42L));
        assertEquals(1, result.size());
        assertEquals(Material.DIRT, result.getFirst().material());
    }

    @Test
    void randomChanceZeroDropsNothing(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var entry = new ItemEntry(List.of(), List.of(), 1, 0, Key.key("minecraft:stone"));
        var pool = new LootPool(List.of(new RandomChanceCondition(new ConstantValue(0.0f))),
                List.of(), new ConstantValue(1.0f), List.of(entry));
        var table = new LootTable(Key.key("empty"), List.of(), List.of(pool), null);
        var context = LootContexts.empty(environment);
        var result = selector.roll(table, context, new Random(42L));
        assertTrue(result.isEmpty());
    }

    @Test
    void randomChanceOneAlwaysPass(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var entry = new ItemEntry(List.of(), List.of(), 1, 0, Key.key("minecraft:stone"));
        var pool = new LootPool(List.of(new RandomChanceCondition(new ConstantValue(1.0f))),
                List.of(), new ConstantValue(1.0f), List.of(entry));
        var table = new LootTable(Key.key("empty"), List.of(), List.of(pool), null);
        var context = LootContexts.empty(environment);
        // 100 个不同种子都通过（原版 LootItemRandomChanceCondition 是 nextFloat < chance）
        for (long seed = 0L; seed < 100L; seed++) {
            assertEquals(1, selector.roll(table, context, new Random(seed)).size());
        }
    }

    @Test
    void missingTableByIdFailsFast(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var context = LootContexts.empty(environment);
        assertThrows(io.ib67.yuanban.loot.LootException.class,
                () -> selector.roll(Key.key("test", "missing"), context, new Random()));
    }
}
