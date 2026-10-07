package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Map;

/**
 * 原版 {@code toggle_tooltips}：改写组件的 tooltip 显示。JSON 字段 {@code toggles}
 * （组件 ID 到布尔值的映射）。
 */
public record ToggleTooltipsFunction(List<LootCondition> conditions,
                                     Map<Key, Boolean> toggles) implements LootFunction {
    public ToggleTooltipsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        toggles = toggles == null ? Map.of() : Map.copyOf(toggles);
    }
}
