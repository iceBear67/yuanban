package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;
import java.util.Optional;

/**
 * 原版 {@code set_custom_model_data}：写入 {@code custom_model_data} 组件。JSON 字段
 * {@code floats}、{@code flags}、{@code strings}、{@code colors}（都可缺省；缺省意味不修改该项）。
 * {@code colors} 与原版的类型一致为 {@code ListOperation$StandAlone<NumberProvider>}（编码为颜色整数值）。
 */
public record SetCustomModelDataFunction(List<LootCondition> conditions,
                                         Optional<StandAloneOperation<io.ib67.yuanban.loot.number.NumberProvider>> floats,
                                         Optional<StandAloneOperation<Boolean>> flags,
                                         Optional<StandAloneOperation<String>> strings,
                                         Optional<StandAloneOperation<io.ib67.yuanban.loot.number.NumberProvider>> colors)
        implements LootFunction {
    public SetCustomModelDataFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        floats = floats == null ? Optional.empty() : floats;
        flags = flags == null ? Optional.empty() : flags;
        strings = strings == null ? Optional.empty() : strings;
        colors = colors == null ? Optional.empty() : colors;
    }

    public static SetCustomModelDataFunction empty(List<LootCondition> conditions) {
        return new SetCustomModelDataFunction(conditions, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
