package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code set_stew_effect}：给谜之炖菜添加状态效果。JSON 字段 {@code effects}
 * （{@link StewEffectEntry} 列表）。
 */
public record SetStewEffectFunction(List<LootCondition> conditions,
                                    List<StewEffectEntry> effects) implements LootFunction {
    public SetStewEffectFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
}
