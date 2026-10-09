package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.trace.Redactor;
import io.ib67.yuanban.agent.trace.TraceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaseManagerTest {
    @TempDir
    Path dir;
    private TraceStore trace;
    private ScheduledExecutorService timer;
    private final List<LeaseManager.Revocation> revoked = new CopyOnWriteArrayList<>();
    private LeaseManager leases;

    @BeforeEach
    void setUp() {
        trace = TraceStore.open(dir.resolve("trace.jsonl"), "test", "bot", Redactor.none(), InstantSource.system(), new JsonObject());
        timer = Executors.newSingleThreadScheduledExecutor();
        leases = new LeaseManager(trace, InstantSource.system(), timer, revoked::add);
    }

    @AfterEach
    void tearDown() {
        timer.shutdownNow();
        trace.close();
    }

    @Test
    void foregroundPreemptsBackground() {
        assertNotNull(leases.tryAcquireBackground("bg", "look", Duration.ofSeconds(10)));
        leases.acquireForeground("primary", "bundle");
        assertEquals("primary", leases.owner());
        assertEquals(1, revoked.size());
        assertEquals("bg", revoked.getFirst().owner());
        assertNull(leases.tryAcquireBackground("bg", "look", Duration.ofSeconds(10)));
        assertThrows(IllegalStateException.class, () -> leases.acquireForeground("other", "bundle"));
    }

    @Test
    void delegationReturnsToTheDelegator() {
        leases.acquireForeground("primary", "bundle");
        leases.delegate("primary", "recovery", "recover");
        assertEquals(LeaseManager.Hold.HELD, leases.hold("recovery"));
        assertEquals(LeaseManager.Hold.NOT_HELD, leases.hold("primary"));
        leases.release("recovery", "done");
        assertEquals(LeaseManager.Hold.HELD, leases.hold("primary"));
        assertTrue(revoked.isEmpty());
    }

    @Test
    void releasingTheMiddleOfTheChainRevokesItsDelegates() {
        leases.acquireForeground("primary", "bundle");
        leases.delegate("primary", "nested", "nested bundle");
        leases.delegate("nested", "recovery", "recover");
        leases.release("nested", "nested ended");
        assertEquals("primary", leases.owner());
        assertEquals(1, revoked.size());
        assertEquals("recovery", revoked.getFirst().owner());
    }

    @Test
    void suspendedPlansWaitUntilResumed() {
        leases.acquireForeground("primary", "bundle");
        leases.suspend("primary", "paused");
        assertEquals(LeaseManager.Hold.SUSPENDED, leases.hold("primary"));
        var waiting = leases.awaitHeld("primary");
        assertFalse(waiting.isDone());
        leases.resume("primary");
        assertTrue(waiting.isDone());
        assertFalse(waiting.isCompletedExceptionally());
    }

    @Test
    void waitersFailWhenTheLeaseIsLost() {
        leases.acquireForeground("primary", "bundle");
        leases.suspend("primary", "paused");
        var waiting = leases.awaitHeld("primary");
        leases.revokeAll("STOPPED");
        assertTrue(waiting.isCompletedExceptionally());
    }

    @Test
    void backgroundLeasesExpire() throws InterruptedException {
        leases.tryAcquireBackground("bg", "look", Duration.ofMillis(100));
        Thread.sleep(400);
        assertNull(leases.owner());
        assertEquals("EXPIRED", revoked.getFirst().reason());
    }
}
