package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;

import java.util.Set;

/**
 * 原版 {@code survives_explosion}：判定带爆炸半径时是否存活。原版是单例，JSON 字段为空对象。
 */
public record SurvivesExplosionCondition() implements LootCondition {
    public static final SurvivesExplosionCondition INSTANCE = new SurvivesExplosionCondition();

    /**
     * 对应原版 {@code ExplosionCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.EXPLOSION_RADIUS);
    }
}
