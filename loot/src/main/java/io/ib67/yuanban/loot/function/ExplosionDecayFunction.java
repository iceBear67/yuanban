package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code explosion_decay}：在有爆炸半径的上下文里按概率丢弃。原版 JSON 无字段。
 */
public record ExplosionDecayFunction(List<LootCondition> conditions) implements LootFunction {
    public ExplosionDecayFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public ExplosionDecayFunction() {
        this(List.of());
    }
}
