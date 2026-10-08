package io.ib67.yuanban.bot.protocol;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;

/**
 * MCProtocolLib 数据包中的向量与本库使用的 Minestom 坐标之间的转换
 */
public final class Vectors {
    private Vectors() {
    }

    public static Vec vec(Vector3d vector) {
        return new Vec(vector.getX(), vector.getY(), vector.getZ());
    }

    public static Vec vec(Vector3i vector) {
        return new Vec(vector.getX(), vector.getY(), vector.getZ());
    }

    /**
     * 点所在的方块坐标
     */
    public static Vector3i blockPos(Point point) {
        return Vector3i.from(point.blockX(), point.blockY(), point.blockZ());
    }

    public static Vector3d vector(Point point) {
        return Vector3d.from(point.x(), point.y(), point.z());
    }
}
