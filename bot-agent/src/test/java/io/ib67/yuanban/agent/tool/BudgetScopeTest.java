package io.ib67.yuanban.agent.tool;

import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.SchemaViolationException;
import org.junit.jupiter.api.Test;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BudgetScopeTest {
    @Test
    void chargesPropagateToParentsAndStopAtTheTightestLimit() {
        var exhausted = new ArrayList<BudgetScope.Exhaustion>();
        var root = BudgetScope.root("agent", new BudgetLimits(3L, null, null, null, null), InstantSource.system(), () -> 0, exhausted::add);
        var child = root.child("bundle", new BudgetLimits(10L, null, null, null, null), exhausted::add);
        assertNull(child.charge(BudgetScope.Kind.ACTIONS, 2));
        assertNull(child.charge(BudgetScope.Kind.ACTIONS, 1));
        var exhaustion = child.charge(BudgetScope.Kind.ACTIONS, 1);
        assertNotNull(exhaustion);
        assertEquals("agent", exhaustion.scope());
        assertEquals(1, exhausted.size());
        // 再次耗尽不重复通知
        child.charge(BudgetScope.Kind.ACTIONS, 1);
        assertEquals(1, exhausted.size());
        assertEquals(0, root.remaining().get("ACTIONS").getAsLong());
    }

    @Test
    void ticksAreMeasured() {
        var ticks = new AtomicLong();
        var root = BudgetScope.root("agent", new BudgetLimits(null, 20L, null, null, null), InstantSource.system(), ticks::get, _ -> {
        });
        assertNull(root.check());
        ticks.set(20);
        assertEquals(BudgetScope.Kind.TICKS, root.check().kind());
    }

    @Test
    void narrowingNeverWidens() {
        var root = BudgetScope.root("agent", new BudgetLimits(5L, null, null, null, null), InstantSource.system(), () -> 0, _ -> {
        });
        var script = root.child("script", BudgetLimits.UNLIMITED, _ -> {
        });
        script.narrow(new BudgetLimits(2L, null, null, null, null));
        assertEquals(2, script.remaining().get("ACTIONS").getAsLong());
        script.narrow(new BudgetLimits(100L, null, null, null, null));
        assertEquals(2, script.remaining().get("ACTIONS").getAsLong());
    }

    @Test
    void negativeLimitsAreRejected() {
        assertThrows(SchemaViolationException.class, () -> BudgetLimits.fromJson(Json.object("actions", -1)));
    }
}
