package io.ib67.yuanban.loot.number;

import io.ib67.yuanban.loot.context.ContextKey;

import java.util.Set;

/**
 * 计分项取值：原版 {@code providers.number.ScoreboardValue}，JSON 字段 {@code target}、{@code score}、
 * {@code scale}（原版缺省 {@code 1.0}；使用双参数构造器时按缺省填充）。
 */
public record ScoreboardValue(ScoreboardName target, String score, float scale) implements NumberProvider {
    public ScoreboardValue(ScoreboardName target, String score) {
        this(target, score, 1.0f);
    }

    /**
     * 对应原版 {@code ScoreboardValue#getReferencedContextParams}（固定名为空集，上下文实体
     * 对应原版 {@code ContextScoreboardNameProvider#getReferencedContextParams}）。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        if (target instanceof ScoreboardName.Context(var entity)) {
            return Set.of(entity.contextParam());
        }
        return Set.of();
    }
}
