package io.ib67.yuanban.loot;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.condition.MatchToolCondition;
import io.ib67.yuanban.loot.entry.CompositeEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.entry.NestedTableEntry;
import io.ib67.yuanban.loot.entry.SingletonEntry;
import io.ib67.yuanban.loot.entry.TagEntry;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.function.SetContainerLootTableFunction;
import net.kyori.adventure.key.Key;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 一张战利品表引用到的内容：物品标签、其他战利品表的 ID、嵌套的内联战利品表。
 * {@link #inspect(LootTable)} 沿条目和修饰器深度优先走查，只看 record 的字段，不做解析。
 * <p>
 * {@code tag} 条目的 {@code name} 和 {@code match_tool} 条件里 {@code #id} 形式的物品谓词进入 {@link #itemTags()}
 * （标签是否存在不在本模块校验，由 loot-table-parser 按当次加载的 {@code ItemTags} 核对）；
 * {@code loot_table} 条目的 ID 形式和 {@code set_container_loot_table} 修饰器的 {@code name} 进入 {@link #tables()}；
 * {@code loot_table} 条目的内联表形式按出现顺序进入 {@link #nested()}，内联表自身继续按同一规则走查。
 */
public record LootContents(Set<Key> itemTags, Set<Key> tables, List<LootTable> nested) {
    public LootContents {
        itemTags = Set.copyOf(itemTags);
        tables = Set.copyOf(tables);
        nested = List.copyOf(nested);
    }

    public static LootContents inspect(LootTable table) {
        Objects.requireNonNull(table, "table");
        var collector = new Collector();
        collector.table(table);
        return new LootContents(collector.itemTags, collector.tables, collector.nested);
    }

    /**
     * 修饰器列表里全部 {@code set_container_loot_table} 的表 ID，含 {@code sequence}、{@code filtered} 等嵌套的修饰器。
     */
    static void collectFunctionTableRefs(List<LootFunction> functions, Set<Key> tables) {
        for (var function : functions) {
            if (function instanceof SetContainerLootTableFunction containerLootTable) {
                tables.add(containerLootTable.name());
            }
            collectFunctionTableRefs(function.children(), tables);
        }
    }

    private static final class Collector {
        private final Set<Key> itemTags = new LinkedHashSet<>();
        private final Set<Key> tables = new LinkedHashSet<>();
        private final List<LootTable> nested = new ArrayList<>();

        private void table(LootTable table) {
            collectFunctionTableRefs(table.functions(), tables);
            for (var pool : table.pools()) {
                conditions(pool.conditions());
                collectFunctionTableRefs(pool.functions(), tables);
                pool.entries().forEach(this::entry);
            }
        }

        private void entry(LootEntry entry) {
            entryConditions(entry);
            if (entry instanceof TagEntry tag) {
                itemTags.add(tag.name());
            }
            if (entry instanceof NestedTableEntry nestedTable) {
                nestedTable.value().ifLeft(tables::add).ifRight(inline -> {
                    nested.add(inline);
                    table(inline);
                });
            }
            if (entry instanceof CompositeEntry composite) {
                composite.children().forEach(this::entry);
            }
        }

        private void entryConditions(LootEntry entry) {
            switch (entry) {
                case TagEntry tag -> {
                    conditions(tag.conditions());
                    collectFunctionTableRefs(tag.functions(), tables);
                }
                case SingletonEntry singleton -> {
                    conditions(singleton.conditions());
                    collectFunctionTableRefs(singleton.functions(), tables);
                }
                case CompositeEntry composite -> conditions(composite.conditions());
            }
        }

        private void conditions(List<LootCondition> conditions) {
            for (var condition : conditions) {
                if (condition instanceof MatchToolCondition matchTool) {
                    itemTags.addAll(matchTool.referencedItemTags());
                }
            }
        }
    }
}
