package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * 原版 {@code tag}：按物品标签取材。JSON 独有字段 {@code name}（物品标签 ID）、
 * {@code expand}。{@code expand=true} 时按标签展开成单例集合（复合语义），{@code false}
 * 时每个物品各一件。
 * <p>
 * 因 Java sealed permits 不能双向继承，本类同时承担 {@code expand=false} 的单例语义与
 * {@code expand=true} 的复合语义；求值器在 {@code expand=true} 时按 {@link #name()} 展开。
 */
public record TagEntry(List<LootCondition> conditions,
                       List<LootFunction> functions,
                       int weight,
                       int quality,
                       Key name,
                       boolean expand) implements SingletonEntry {
    public TagEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
    }
}
