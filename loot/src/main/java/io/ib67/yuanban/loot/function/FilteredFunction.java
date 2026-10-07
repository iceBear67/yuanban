package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.ItemPredicate;
import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;
import java.util.Optional;

/**
 * 原版 {@code filtered}：物品满足 {@code item_filter} 时应用 {@code on_match}，不满足时应用
 * {@code on_fail}（均可缺省）。JSON 字段 {@code item_filter}、{@code on_match}、{@code on_fail}。
 */
public record FilteredFunction(List<LootCondition> conditions,
                               ItemPredicate itemFilter,
                               Optional<LootFunction> onMatch,
                               Optional<LootFunction> onFail) implements LootFunction {
    public FilteredFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        onMatch = onMatch == null ? Optional.empty() : onMatch;
        onFail = onFail == null ? Optional.empty() : onFail;
    }

    @Override
    public List<LootFunction> children() {
        var result = new java.util.ArrayList<LootFunction>(2);
        onMatch.ifPresent(result::add);
        onFail.ifPresent(result::add);
        return List.copyOf(result);
    }
}
