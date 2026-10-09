package io.ib67.yuanban.agent.view;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;

/**
 * 坐标或向量。
 */
public record Vec3(double x, double y, double z) {
    public static Vec3 of(Point point) {
        return new Vec3(point.x(), point.y(), point.z());
    }

    public Vec toVec() {
        return new Vec(x, y, z);
    }

    public double distance(Vec3 other) {
        var dx = x - other.x;
        var dy = y - other.y;
        var dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double horizontalDistance(Vec3 other) {
        var dx = x - other.x;
        var dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public BlockPos block() {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
