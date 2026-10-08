package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContext;
import io.ib67.yuanban.loot.context.LootContextImpl;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * 求值测试共用的构造方法：真实的 Minestom 进程、内存中的求值环境与按参数组装的上下文。
 */
final class EvalSupport {
    private EvalSupport() {
    }

    static LootSelector selector(Env env, InMemoryLootEnvironment environment) {
        return new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()), LootTypeRegistry.vanilla(), environment);
    }

    static ItemEntry item(Material material, LootFunction... functions) {
        return new ItemEntry(List.of(), List.of(functions), 1, 0, material);
    }

    static ItemEntry item(Material material, List<LootCondition> conditions) {
        return new ItemEntry(conditions, List.of(), 1, 0, material);
    }

    static LootPool pool(LootEntry... entries) {
        return new LootPool(List.of(), List.of(), new ConstantValue(1.0f), List.of(entries));
    }

    static LootTable table(LootPool... pools) {
        return new LootTable(LootTable.DEFAULT_TYPE, List.of(), List.of(pools), Optional.empty());
    }

    /**
     * 只有一个条目的表。
     */
    static LootTable single(LootEntry entry) {
        return table(pool(entry));
    }

    /**
     * {@code generic} 类型、按给出参数组装的上下文。
     */
    static LootContext context(InMemoryLootEnvironment environment, @Nullable Instance level, Map<ContextKey<?>, Object> params,
                               long seed) {
        return new LootContextImpl(environment, level, Key.key("generic"), params, null, null, new Random(seed));
    }

    static LootContext context(InMemoryLootEnvironment environment, @Nullable Instance level, Object... pairs) {
        var params = new HashMap<ContextKey<?>, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            params.put((ContextKey<?>) pairs[i], pairs[i + 1]);
        }
        return context(environment, level, params, 0L);
    }
}
