package io.ib67.yuanban.bot;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.Nullable;

/**
 * 移动动作的参数。
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class MoveOptions {
    public static final MoveOptions DEFAULT = builder().build();

    /**
     * 移动方式。未指定时 {@code moveTo}、{@code moveForward} 使用 {@link MovementMode#WALK}，
     * {@code jumpTo} 按目标的水平距离选择
     */
    private final @Nullable MovementMode mode;
    /**
     * {@code moveTo} 判断到达的水平距离
     */
    @Builder.Default
    private final double closeDistance = 0.5;
    /**
     * {@code moveTo} 寻路的最大距离，也是寻路使用的世界快照的半径
     */
    @Builder.Default
    private final double maxDistance = 50;
    /**
     * {@code moveForward} 的移动方向，只使用水平分量；未指定时取开始时的水平朝向
     */
    private final @Nullable Vec direction;
}
