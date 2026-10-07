package io.ib67.yuanban.loot.number;

import org.jetbrains.annotations.Nullable;

/**
 * 计分项持有者的名字来源：原版 {@code providers.score} 下的两种。
 * 固定名对应 {@code FixedScoreboardNameProvider}，上下文实体对应 {@code ContextScoreboardNameProvider}。
 * JSON 形态 {@code {type: "fixed", name} } 或 {@code {type: "context", target}} 的展开在 loot-table-parser 中完成。
 */
public sealed interface ScoreboardName {
    record Fixed(String name) implements ScoreboardName {
    }

    record Context(EntityTarget target) implements ScoreboardName {
    }
}
