package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Region;
import net.minestom.server.coordinate.Point;
import org.jetbrains.annotations.Nullable;

/**
 * {@code movementOf} 的关闭条件。由 {@link #create()} 开始，至少设置 {@link #atLeast}、{@link #towards}、{@link #into}
 * 中的一项，否则构造 observer 时抛出异常。各项条件同时满足时，这个实体满足条件。
 */
public final class MovementCriteria {
    private final double minDistance;
    private final @Nullable Point towards;
    private final double maxAngleDegrees;
    private final @Nullable Region area;
    private final int entities;

    private MovementCriteria(double minDistance, @Nullable Point towards, double maxAngleDegrees, @Nullable Region area,
                             int entities) {
        this.minDistance = minDistance;
        this.towards = towards;
        this.maxAngleDegrees = maxAngleDegrees;
        this.area = area;
        this.entities = entities;
    }

    public static MovementCriteria create() {
        return new MovementCriteria(-1, null, 0, null, 1);
    }

    /**
     * 位移长度不小于 blocks
     */
    public MovementCriteria atLeast(double blocks) {
        return new MovementCriteria(blocks, towards, maxAngleDegrees, area, entities);
    }

    /**
     * 位移方向与「起点指向 target」的方向夹角不超过 maxAngleDegrees
     */
    public MovementCriteria towards(Point target, double maxAngleDegrees) {
        return new MovementCriteria(minDistance, target, maxAngleDegrees, area, entities);
    }

    /**
     * 实体当前位置在 area 内
     */
    public MovementCriteria into(Region area) {
        return new MovementCriteria(minDistance, towards, maxAngleDegrees, area, entities);
    }

    /**
     * 满足条件的实体数达到 count 时关闭，默认 1
     */
    public MovementCriteria entities(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("entity count must be positive, got " + count);
        }
        return new MovementCriteria(minDistance, towards, maxAngleDegrees, area, count);
    }

    int entities() {
        return entities;
    }

    void validate() {
        if (minDistance < 0 && towards == null && area == null) {
            throw new IllegalArgumentException("movement criteria require atLeast, towards or into");
        }
    }

    boolean test(MovementTrack track) {
        var displacement = track.displacement();
        if (minDistance >= 0 && displacement.length() < minDistance) {
            return false;
        }
        if (towards != null) {
            var expected = towards.asVec().sub(track.start());
            if (displacement.isZero() || expected.isZero()) {
                return false;
            }
            var angle = Math.toDegrees(displacement.angle(expected));
            if (angle > maxAngleDegrees) {
                return false;
            }
        }
        return area == null || area.contains(track.current());
    }
}
