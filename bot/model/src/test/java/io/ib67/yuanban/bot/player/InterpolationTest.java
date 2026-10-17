package io.ib67.yuanban.bot.player;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原版 {@code InterpolationHandler} 的 3 步插值：每一步前进剩余步数分之一，朝向取较近的方向。
 */
class InterpolationTest {
    private static final Pos START = new Pos(0.5, 0, 0.5, 0, 0);
    private static final Pos TARGET = new Pos(4.5, 0, 0.5, 90, 45);

    /**
     * 没有任何碰撞
     */
    private static boolean free(Vec position) {
        return true;
    }

    @Test
    void eachStepMovesAFractionOfTheRemainingDistance() {
        var interpolation = Interpolation.start(null, START, TARGET);
        assertEquals(Interpolation.STEPS, interpolation.steps());
        var first = interpolation.step(START, InterpolationTest::free);
        assertEquals(0.5 + 4.0 / 3.0, first.position().x(), 1e-9);
        assertEquals(0.0, first.position().y(), 1e-9);
        assertEquals(30.0, first.position().yaw(), 1e-9);
        assertEquals(15.0, first.position().pitch(), 1e-9);

        var second = interpolation.step(START, InterpolationTest::free);
        // 每一步都从当前的位置向目标前进剩余步数中的一步
        var step2 = first.next().step(first.position(), InterpolationTest::free);
        assertEquals(0.5 + 8.0 / 3.0, step2.position().x(), 1e-9);
        assertEquals(60.0, step2.position().yaw(), 1e-9);

        var last = step2.next().step(step2.position(), InterpolationTest::free);
        assertEquals(TARGET.x(), last.position().x(), 1e-9);
        assertEquals(TARGET.yaw(), last.position().yaw(), 1e-9);
        assertEquals(TARGET.pitch(), last.position().pitch(), 1e-9);
        assertNull(last.next());
        // 与 first 无关，只是用来说明每一步的参考位置
        assertTrue(second.next() != null);
    }

    @Test
    void theYawTakesTheShorterDirection() {
        // 从 170 度转到 -170 度：较近的方向是 +20 度（经过 180），不是 -340 度
        var start = new Pos(0, 0, 0, 170, 0);
        var target = new Pos(0, 0, 0, -170, 0);
        var step = Interpolation.start(null, start, target).step(start, InterpolationTest::free);
        assertEquals(170 + 20.0 / 3.0, step.position().yaw(), 1e-4);
    }

    @Test
    void thePitchStaysWithinTheEntityRange() {
        var start = new Pos(0, 0, 0, 0, 80);
        var target = new Pos(0, 0, 0, 0, -80);
        var step = Interpolation.start(null, start, target).step(start, InterpolationTest::free);
        assertTrue(step.position().pitch() <= 90);
        assertTrue(step.position().pitch() >= -90);
    }

    @Test
    void localMovementAfterTheStartIsAddedToTheTarget() {
        var start = new Pos(0, 0, 0, 0, 0);
        var target = new Pos(3, 0, 0, 0, 0);
        var interpolation = Interpolation.start(null, start, target);
        // 上一步之后本地向前走了 1.5 格：这一步的目标位置加上 1.5
        var current = new Pos(1.5, 0, 0, 0, 0);
        var step = interpolation.step(current, InterpolationTest::free);
        // 目标变成 4.5，前进剩余步数（3）分之一
        assertEquals(1.5 + 3.0 / 3.0, step.position().x(), 1e-9);
    }

    @Test
    void theAddedMovementIsRejectedWhenItWouldCollide() {
        var start = new Pos(0, 0, 0, 0, 0);
        var target = new Pos(3, 0, 0, 0, 0);
        var interpolation = Interpolation.start(null, start, target);
        var current = new Pos(1.5, 0, 0, 0, 0);
        // 加了本地移动之后的包围盒碰撞：目标位置保持原样
        var step = interpolation.step(current, _ -> false);
        assertEquals(1.5 + 1.5 / 3.0, step.position().x(), 1e-9);
    }

    @Test
    void startingAgainWithTheSameTargetKeepsTheRunningInterpolation() {
        var interpolation = Interpolation.start(null, START, TARGET);
        // 目标相同：继续当前的插值，不重新开始
        assertSame(interpolation, Interpolation.start(interpolation, START, TARGET));
        // 目标不同：从当前的位置与朝向开始新的插值
        var other = new Pos(1, 1, 1, 0, 0);
        var restarted = Interpolation.start(interpolation, START, other);
        assertFalse(restarted == interpolation);
        assertEquals(Interpolation.STEPS, restarted.steps());
        assertEquals(other, restarted.target());
        assertEquals(START, restarted.previous());
    }
}
