package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.number.NumberProvider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 原版 {@code set_custom_model_data}：修改 {@code custom_model_data} 组件。JSON 字段
 * {@code floats}、{@code flags}、{@code strings}、{@code colors}（都可缺省；缺省的项保持物品原有的值）。
 * 每一项是原版 {@code ListOperation$StandAlone}：{@code values} 列表加上内联的列表操作。
 * {@code colors} 的元素是数字提供器，也可以是 RGB 颜色（整数或三个 [0, 1] 的浮点数），颜色读作常数提供器。
 */
public record SetCustomModelDataFunction(List<LootCondition> conditions,
                                         Optional<StandAloneOperation<NumberProvider>> floats,
                                         Optional<StandAloneOperation<Boolean>> flags,
                                         Optional<StandAloneOperation<String>> strings,
                                         Optional<StandAloneOperation<NumberProvider>> colors)
        implements LootFunction {
    public SetCustomModelDataFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(floats, "floats");
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(strings, "strings");
        Objects.requireNonNull(colors, "colors");
    }

    public static SetCustomModelDataFunction empty(List<LootCondition> conditions) {
        return new SetCustomModelDataFunction(conditions, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
