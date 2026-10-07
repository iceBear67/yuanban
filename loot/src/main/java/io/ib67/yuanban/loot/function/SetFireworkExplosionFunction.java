package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 原版 {@code set_firework_explosion}：写入烟花之星的爆炸效果。JSON 字段
 * {@code shape}、{@code colors}、{@code fade_colors}、{@code trail}、{@code twinkle}（均可缺省；
 * 原版颜色编码成整数列表，本模块承载为 int 列表）。
 */
public record SetFireworkExplosionFunction(List<LootCondition> conditions,
                                           Optional<FireworkExplosionShape> shape,
                                           Optional<List<Integer>> colors,
                                           Optional<List<Integer>> fadeColors,
                                           Optional<Boolean> trail,
                                           Optional<Boolean> twinkle) implements LootFunction {
    public SetFireworkExplosionFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        shape = shape == null ? Optional.empty() : shape;
        colors = colors == null ? Optional.empty() : colors.map(List::copyOf);
        fadeColors = fadeColors == null ? Optional.empty() : fadeColors.map(List::copyOf);
        trail = trail == null ? Optional.empty() : trail;
        twinkle = twinkle == null ? Optional.empty() : twinkle;
    }

    public SetFireworkExplosionFunction(List<LootCondition> conditions) {
        this(conditions, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
