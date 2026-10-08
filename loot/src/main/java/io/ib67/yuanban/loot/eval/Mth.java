package io.ib67.yuanban.loot.eval;

import java.util.Random;

/**
 * 求值用到的原版 {@code net.minecraft.util.Mth} 方法，取值规则与原版逐个相同。
 */
final class Mth {
    /**
     * 原版 {@code Mth.SIN}：65536 项的正弦表。
     */
    private static final float[] SIN = createSinTable();

    private Mth() {
    }

    private static float[] createSinTable() {
        var table = new float[65536];
        for (int i = 0; i < table.length; i++) {
            table[i] = (float) Math.sin(i / 10430.378350470453);
        }
        return table;
    }

    /**
     * 原版 {@code Mth.sin(double)}：查表。
     */
    static float sin(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453) & 65535L)];
    }

    /**
     * 原版 {@code Mth.cos(double)}：查表。
     */
    static float cos(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453 + 16384.0) & 65535L)];
    }

    /**
     * 原版 {@code Mth.floor(double)}。
     */
    static int floor(double value) {
        return (int) Math.floor(value);
    }

    /**
     * 原版 {@code Mth.clamp(int, int, int)}：下界大于上界时结果是上界。
     */
    static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    /**
     * 原版 {@code Mth.clamp(float, float, float)}。
     */
    static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    /**
     * 原版 {@code Mth.nextInt(RandomSource, int, int)}：下界不小于上界时返回下界，否则在闭区间内取值。
     */
    static int nextInt(Random random, int minInclusive, int maxInclusive) {
        return minInclusive >= maxInclusive ? minInclusive : random.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
    }

    /**
     * 原版 {@code Mth.nextFloat(RandomSource, float, float)}：下界不小于上界时返回下界。
     */
    static float nextFloat(Random random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }

    /**
     * 原版 {@code Mth.lengthSquared(double, double, double)}。
     */
    static double lengthSquared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    /**
     * 原版 {@code Mth.lengthSquared(double, double)}。
     */
    static double lengthSquared(double x, double y) {
        return x * x + y * y;
    }
}
