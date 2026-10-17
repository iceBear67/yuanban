package io.ib67.yuanban.bot.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Set Ticking State 与 Step Tick 的规则（原版 {@code TickRateManager}）。
 */
class TickStateTest {
    @Test
    void aFreshStateRunsEveryTick() {
        var state = new TickState();
        assertEquals(TickState.DEFAULT_RATE, state.tickRate());
        assertFalse(state.frozen());
        assertEquals(0, state.stepTicks());
        // 第一次 tick 之前就按正常运行考虑
        assertTrue(state.runsNormally());
        state.tick();
        assertTrue(state.runsNormally());
    }

    @Test
    void aFrozenWorldOnlyRunsTheStepsGivenByStepTick() {
        var state = new TickState();
        state.apply(20, true);
        assertTrue(state.frozen());
        state.tick();
        assertFalse(state.runsNormally());
        state.step(2);
        assertEquals(2, state.stepTicks());
        state.tick();
        assertTrue(state.runsNormally());
        assertEquals(1, state.stepTicks());
        state.tick();
        assertTrue(state.runsNormally());
        assertEquals(0, state.stepTicks());
        state.tick();
        assertFalse(state.runsNormally());
    }

    @Test
    void unfreezingRunsEveryTickAgain() {
        var state = new TickState();
        state.apply(20, true);
        state.tick();
        assertFalse(state.runsNormally());
        state.apply(20, false);
        state.tick();
        assertTrue(state.runsNormally());
        // 解冻之后剩下的步进数照常递减，不影响运行
        state.apply(20, true);
        state.step(2);
        state.apply(20, false);
        state.tick();
        assertTrue(state.runsNormally());
        assertEquals(1, state.stepTicks());
    }

    @Test
    void theTickRateIsKeptAsGiven() {
        var state = new TickState();
        state.apply(5.5f, false);
        assertEquals(5.5f, state.tickRate());
        state.apply(0, true);
        assertEquals(0, state.tickRate());
    }
}
