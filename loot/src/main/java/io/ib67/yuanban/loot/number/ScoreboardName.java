package io.ib67.yuanban.loot.number;

import java.util.Objects;

/**
 * 计分项持有者的名字来源：原版 {@code providers.score} 下的两种。
 * 固定名对应 {@code FixedScoreboardNameProvider}，上下文实体对应 {@code ContextScoreboardNameProvider}。
 * JSON 形态是实体目标的名字（上下文实体），或者 {@code {type: "fixed", name}}、{@code {type: "context", target}}。
 */
public sealed interface ScoreboardName {
    record Fixed(String name) implements ScoreboardName {
        public Fixed {
            Objects.requireNonNull(name, "name");
        }
    }

    record Context(EntityTarget target) implements ScoreboardName {
        public Context {
            Objects.requireNonNull(target, "target");
        }
    }
}
