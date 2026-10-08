package io.ib67.yuanban.loot.number;

import java.util.List;

/**
 * 随魔咒等级计算的取值，对应原版 {@code net.minecraft.world.item.enchantment.LevelBasedValue}。
 * JSON 是裸数字（{@link Constant}）或带 {@code type} 的对象，{@code type} 取原版
 * {@code enchantment_level_based_value_type} 注册表的 6 种：{@code clamped}、{@code fraction}、
 * {@code levels_squared}、{@code linear}、{@code exponent}、{@code lookup}。原版没有注册 {@code constant} 类型。
 */
public sealed interface LevelBasedValue {
    /**
     * 原版 {@code LevelBasedValue$Constant}：只能写成裸数字。
     */
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
