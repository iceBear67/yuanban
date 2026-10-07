package io.ib67.yuanban.bot.observe;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;

/**
 * 轴对齐的长方体区域，两端都包含在内。
 */
public record Region(Point min, Point max) {
    public Region {
        var lower = new Vec(Math.min(min.x(), max.x()), Math.min(min.y(), max.y()), Math.min(min.z(), max.z()));
        var upper = new Vec(Math.max(min.x(), max.x()), Math.max(min.y(), max.y()), Math.max(min.z(), max.z()));
        min = lower;
        max = upper;
    }

    /**
     * 以 center 为中心、各方向延伸 radius 的区域
     */
    public static Region around(Point center, double radius) {
        return new Region(center.sub(radius), center.add(radius));
    }

    public boolean contains(Point point) {
        return point.x() >= min.x() && point.x() <= max.x()
                && point.y() >= min.y() && point.y() <= max.y()
                && point.z() >= min.z() && point.z() <= max.z();
    }
}
