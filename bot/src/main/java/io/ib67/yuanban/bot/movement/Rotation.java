package io.ib67.yuanban.bot.movement;

import net.minestom.server.coordinate.Point;

/**
 * 朝向，单位为度，取值含义见 wiki Set Player Rotation。
 */
public record Rotation(float yaw, float pitch) {
    /**
     * 由 from 指向 to 的朝向（wiki Set Player Rotation 的公式）
     */
    public static Rotation lookAt(Point from, Point to) {
        var dx = to.x() - from.x();
        var dy = to.y() - from.y();
        var dz = to.z() - from.z();
        var horizontal = Math.sqrt(dx * dx + dz * dz);
        var yaw = (float) -Math.toDegrees(Math.atan2(dx, dz));
        var pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new Rotation(yaw, pitch);
    }

    /**
     * 看向水平方向 (x, z)，俯仰角为 0
     */
    public static Rotation horizontal(double x, double z) {
        return new Rotation((float) -Math.toDegrees(Math.atan2(x, z)), 0);
    }
}
