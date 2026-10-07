package io.ib67.yuanban.loot.number;

import java.util.List;

/**
 * 随魔咒等级计算的取值，对应原版 {@code net.minecraft.world.item.enchantment.LevelBasedValue}。
 * 子类型与原版 sealed 集合一一对应：{@code constant}、{@code linear}、{@code clamped}、{@code fraction}、
 * {@code levels_squared}、{@code lookup}、{@code exponent}。
 */
public sealed interface LevelBasedValue {
    record Constant(float value) implements LevelBasedValue {
    }

    record Linear(float base, float perLevelAboveFirst) implements LevelBasedValue {
    }

    record Clamped(LevelBasedValue value, float min, float max) implements LevelBasedValue {
    }

    record Fraction(LevelBasedValue numerator, LevelBasedValue denominator) implements LevelBasedValue {
    }

    record LevelsSquared(float added) implements LevelBasedValue {
    }

    record Lookup(List<Float> values, LevelBasedValue fallback) implements LevelBasedValue {
        public Lookup {
            values = List.copyOf(values);
        }
    }

    record Exponent(LevelBasedValue base, LevelBasedValue power) implements LevelBasedValue {
    }
}
