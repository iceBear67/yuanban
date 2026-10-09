package io.ib67.yuanban.agent.view;

import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;

/**
 * 方块坐标。
 */
public record BlockPos(int x, int y, int z) {
    public static BlockPos of(Point point) {
        return new BlockPos(point.blockX(), point.blockY(), point.blockZ());
    }

    public Vec toVec() {
        return new Vec(x, y, z);
    }

    public int chunkX() {
        return CoordConversion.globalToChunk(x);
    }

    public int chunkZ() {
        return CoordConversion.globalToChunk(z);
    }

    /**
     * 方块顶面中心
     */
    public Vec3 topCenter() {
        return new Vec3(x + 0.5, y + 1, z + 0.5);
    }
}
