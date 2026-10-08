package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code set_stew_effect}：给谜之炖菜随机添加一种状态效果。JSON 字段 {@code effects}
 * （可选，{@link StewEffectEntry} 列表，缺省为空，同一种效果不能出现两次）。
 */
public record SetStewEffectFunction(List<LootCondition> conditions,
                                    List<StewEffectEntry> effects) implements LootFunction {
    public SetStewEffectFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
}
