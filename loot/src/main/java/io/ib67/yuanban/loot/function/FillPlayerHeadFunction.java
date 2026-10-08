package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.EntityTarget;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 原版 {@code fill_player_head}：把玩家的档案写入玩家头颅。JSON 字段 {@code entity}（必填）。
 */
public record FillPlayerHeadFunction(List<LootCondition> conditions,
                                     EntityTarget entity) implements LootFunction {
    public FillPlayerHeadFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(entity, "entity");
    }

    /**
     * 对应原版 {@code FillPlayerHead#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(entity.contextParam());
    }
}
