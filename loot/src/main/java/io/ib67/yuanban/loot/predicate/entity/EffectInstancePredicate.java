package io.ib67.yuanban.loot.predicate.entity;

import net.minestom.server.utils.Range;

import java.util.Objects;
import java.util.Optional;

/**
 * 状态效果实例谓词，对应原版 {@code MobEffectsPredicate$MobEffectInstancePredicate}。
 * 实体没有这个效果时不匹配。
 *
 * @param duration 剩余时长（刻）
 * @param visible  是否显示粒子
 */
public record EffectInstancePredicate(Range.Int amplifier, Range.Int duration, Optional<Boolean> ambient,
                                      Optional<Boolean> visible) {
    public EffectInstancePredicate {
        Objects.requireNonNull(amplifier, "amplifier");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(ambient, "ambient");
        Objects.requireNonNull(visible, "visible");
    }
}
