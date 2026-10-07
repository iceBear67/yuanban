package io.ib67.yuanban.bot.movement;

import net.minestom.server.coordinate.Vec;

/**
 * 原版客户端 {@code Mth.sin}、{@code Mth.cos} 使用的 65536 项 float 正弦表，以及用它实现的 {@code Vec3.xRot}、{@code Vec3.yRot}。
 * 传送时旋转速度、计算骑乘位置时旋转挂点都用它，结果与原版客户端相同。
 * <p>
 * Minestom 的 {@code Vec.rotateAroundX(a)} 等于 {@code Vec3.xRot(-a)}，方向相反，并且使用 {@link Math#sin}，所以不能代替。
 */
public final class SineTable {
    private static final double SCALE = 10430.378350470453;
    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin(i / SCALE);
        }
    }

    private SineTable() {
    }

    public static float sin(double radians) {
        return SIN[(int) ((long) (radians * SCALE) & 65535L)];
    }

    public static float cos(double radians) {
        return SIN[(int) ((long) (radians * SCALE + 16384.0) & 65535L)];
    }

    /**
     * 原版 {@code Vec3.xRot}：绕 x 轴旋转
     */
    public static Vec xRot(Vec vector, float radians) {
        var cos = cos(radians);
        var sin = sin(radians);
        return new Vec(vector.x(), vector.y() * cos + vector.z() * sin, vector.z() * cos - vector.y() * sin);
    }

    /**
     * 原版 {@code Vec3.yRot}：绕 y 轴旋转
     */
    public static Vec yRot(Vec vector, float radians) {
        var cos = cos(radians);
        var sin = sin(radians);
        return new Vec(vector.x() * cos + vector.z() * sin, vector.y(), vector.z() * cos - vector.x() * sin);
    }
}
