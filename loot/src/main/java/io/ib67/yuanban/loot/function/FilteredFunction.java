package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.predicate.ItemPredicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 原版 {@code filtered}：物品满足 {@code item_filter} 时应用 {@code on_pass}，不满足时应用
 * {@code on_fail}（都可缺省）。JSON 字段 {@code item_filter}、{@code on_pass}、{@code on_fail}。
 */
public record FilteredFunction(List<LootCondition> conditions,
                               ItemPredicate itemFilter,
                               Optional<LootFunction> onPass,
                               Optional<LootFunction> onFail) implements LootFunction {
    public FilteredFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(itemFilter, "itemFilter");
        Objects.requireNonNull(onPass, "onPass");
        Objects.requireNonNull(onFail, "onFail");
    }

    @Override
    public List<LootFunction> children() {
        var result = new ArrayList<LootFunction>(2);
        onPass.ifPresent(result::add);
        onFail.ifPresent(result::add);
        return List.copyOf(result);
    }
}
