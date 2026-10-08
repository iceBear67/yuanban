package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.component.DataComponent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版 {@code toggle_tooltips}：设置各组件在物品提示中是否显示。JSON 字段 {@code toggles}
 * （组件类型 ID 到是否显示的映射，保持 JSON 中的顺序）。
 */
public record ToggleTooltipsFunction(List<LootCondition> conditions,
                                     Map<DataComponent<?>, Boolean> toggles) implements LootFunction {
    public ToggleTooltipsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        toggles = toggles == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(toggles));
    }
}
