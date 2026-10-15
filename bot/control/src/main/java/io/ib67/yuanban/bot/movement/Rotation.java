package io.ib67.yuanban.bot.movement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;

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

    /**
     * 视线方向的单位向量（原版 {@code Entity.calculateViewVector}：以 float 计算，正弦与余弦取自 {@link SineTable}）
     */
    public Vec viewVector() {
        var pitchRadians = pitch * (float) (Math.PI / 180.0);
        var yawRadians = -yaw * (float) (Math.PI / 180.0);
        var yawCos = SineTable.cos(yawRadians);
        var yawSin = SineTable.sin(yawRadians);
        var pitchCos = SineTable.cos(pitchRadians);
        var pitchSin = SineTable.sin(pitchRadians);
        return new Vec(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
    }
}
