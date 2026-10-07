package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Set;

/**
 * 原版 {@code table_bonus}：按魔咒等级在表中查概率。JSON 字段 {@code enchantment}、{@code chances}。
 */
public record TableBonusCondition(Key enchantment, List<Float> values) implements LootCondition {
    public TableBonusCondition {
        values = List.copyOf(values);
    }

    /**
     * 对应原版 {@code BonusLevelTableCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.TOOL);
    }
}
