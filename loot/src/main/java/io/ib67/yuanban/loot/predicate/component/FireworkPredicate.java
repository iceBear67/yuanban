package io.ib67.yuanban.loot.predicate.component;

import net.minestom.server.item.component.FireworkExplosion;

import java.util.Objects;
import java.util.Optional;

/**
 * 烟花爆炸谓词，对应原版 {@code FireworkExplosionPredicate$FireworkPredicate}。
 *
 * @param shape   JSON 字段 {@code shape}
 * @param twinkle JSON 字段 {@code has_twinkle}
 * @param trail   JSON 字段 {@code has_trail}
 */
public record FireworkPredicate(Optional<FireworkExplosion.Shape> shape, Optional<Boolean> twinkle,
                                Optional<Boolean> trail) {
    public FireworkPredicate {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(twinkle, "twinkle");
        Objects.requireNonNull(trail, "trail");
    }
}
