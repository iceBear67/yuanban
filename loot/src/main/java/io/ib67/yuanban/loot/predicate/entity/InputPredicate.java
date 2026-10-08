package io.ib67.yuanban.loot.predicate.entity;

import java.util.Objects;
import java.util.Optional;

/**
 * 玩家输入谓词，对应原版 {@code net.minecraft.advancements.predicates.InputPredicate}。
 * 各项与客户端最近一次发送的输入比较，{@code sneak} 对应潜行键。
 */
public record InputPredicate(Optional<Boolean> forward, Optional<Boolean> backward, Optional<Boolean> left,
                             Optional<Boolean> right, Optional<Boolean> jump, Optional<Boolean> sneak,
                             Optional<Boolean> sprint) {
    public InputPredicate {
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(backward, "backward");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(jump, "jump");
        Objects.requireNonNull(sneak, "sneak");
        Objects.requireNonNull(sprint, "sprint");
    }
}
