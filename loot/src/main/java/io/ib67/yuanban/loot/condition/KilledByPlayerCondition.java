package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;

import java.util.Set;

/**
 * 原版 {@code killed_by_player}：上下文自带玩家击杀信息。原版是单例，JSON 字段为空对象。
 */
public record KilledByPlayerCondition() implements LootCondition {
    public static final KilledByPlayerCondition INSTANCE = new KilledByPlayerCondition();

    /**
     * 对应原版 {@code LootItemKilledByPlayerCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.LAST_DAMAGE_PLAYER);
    }
}
