package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.item.component.FireworkExplosion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 原版 {@code set_firework_explosion}：修改烟花之星的爆炸效果。JSON 字段 {@code shape}、{@code colors}、
 * {@code fade_colors}（整数颜色列表）、{@code trail}、{@code twinkle}，都可缺省；缺省的项保持物品原有的值，
 * 物品没有这个组件时从小型球状、无颜色、无拖尾、无闪烁开始。
 */
public record SetFireworkExplosionFunction(List<LootCondition> conditions,
                                           Optional<FireworkExplosion.Shape> shape,
                                           Optional<List<Integer>> colors,
                                           Optional<List<Integer>> fadeColors,
                                           Optional<Boolean> trail,
                                           Optional<Boolean> twinkle) implements LootFunction {
    public SetFireworkExplosionFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(shape, "shape");
        colors = colors.map(List::copyOf);
        fadeColors = fadeColors.map(List::copyOf);
        Objects.requireNonNull(trail, "trail");
        Objects.requireNonNull(twinkle, "twinkle");
    }

    public SetFireworkExplosionFunction(List<LootCondition> conditions) {
        this(conditions, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
