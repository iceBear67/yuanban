package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceStore;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 改变世界所需的 capability lease：同一时间只有一个持有者。
 * <ul>
 *     <li>前台 bundle 的 primary 以 {@link Priority#FOREGROUND} 取得，取得时收回后台持有的 lease；</li>
 *     <li>background plan 只能在没有任何 lease 时以 {@link Priority#BACKGROUND} 取得，带过期时间；</li>
 *     <li>持有者可以把 lease 委托给子计划（嵌套 bundle 的 primary、recovery plan），形成持有链，链的最后一个计划是当前持有者；</li>
 *     <li>暂停按计划记录：被暂停的计划仍在链上，但它改变世界的调用等到恢复。</li>
 * </ul>
 * 每次变化写入 trace。收回时在锁外调用 {@code onRevoke}，由网关取消失去 lease 的计划正在执行的调用并通知它。
 */
public final class LeaseManager {
    public enum Priority {
        BACKGROUND,
        FOREGROUND
    }

    public enum Hold {
        HELD,
        SUSPENDED,
        NOT_HELD
    }

    /**
     * @param baseOwner 取得 lease 的计划
     * @param expiresAt 过期时间（epoch 毫秒）；不过期时为 null
     */
    public record Lease(String id, String baseOwner, Priority priority, String purpose, @Nullable Long expiresAt) {
    }

    /**
     * owner 失去了 lease
     */
    public record Revocation(Lease lease, String owner, String reason) {
    }

    /**
     * 等待 lease 的计划已经不在持有链上
     */
    public static final class LeaseLostException extends RuntimeException {
        public LeaseLostException(String message) {
            super(message);
        }
    }

    private final TraceStore trace;
    private final InstantSource clock;
    private final ScheduledExecutorService timer;
    private final Consumer<Revocation> onRevoke;
    private final List<String> chain = new ArrayList<>();
    private final Map<String, String> suspended = new HashMap<>();
    private final List<Waiter> waiters = new ArrayList<>();
    private @Nullable Lease active;
    private @Nullable ScheduledFuture<?> expiry;
    private long counter;

    public LeaseManager(TraceStore trace, InstantSource clock, ScheduledExecutorService timer, Consumer<Revocation> onRevoke) {
        this.trace = trace;
        this.clock = clock;
        this.timer = timer;
        this.onRevoke = onRevoke;
    }

    /**
     * @throws IllegalStateException 另一个前台计划持有 lease
     */
    public Lease acquireForeground(String plan, String purpose) {
        Revocation revoked = null;
        Lease lease;
        synchronized (this) {
            if (active != null) {
                if (active.priority() == Priority.FOREGROUND) {
                    throw new IllegalStateException("the foreground lease is held by " + chain);
                }
                revoked = revokeLocked("PREEMPTED_BY_FOREGROUND");
            }
            lease = grantLocked(plan, Priority.FOREGROUND, purpose, null);
        }
        if (revoked != null) {
            onRevoke.accept(revoked);
        }
        return lease;
    }

    /**
     * @return 已经有 lease 时为 null
     */
    public synchronized @Nullable Lease tryAcquireBackground(String plan, String purpose, Duration duration) {
        if (active != null) {
            return null;
        }
        var lease = grantLocked(plan, Priority.BACKGROUND, purpose, clock.millis() + duration.toMillis());
        expiry = timer.schedule(() -> expire(lease), duration.toMillis(), TimeUnit.MILLISECONDS);
        return lease;
    }

    /**
     * 当前持有者 from 把 lease 委托给 to
     *
     * @throws IllegalStateException from 不是当前持有者
     */
    public synchronized void delegate(String from, String to, String purpose) {
        if (active == null || !from.equals(ownerLocked())) {
            throw new IllegalStateException(from + " does not hold the lease (chain: " + chain + ")");
        }
        chain.add(to);
        record("delegated", Json.object("from", from, "to", to, "purpose", purpose));
        wakeLocked();
    }

    /**
     * 暂停计划：它保留在持有链上，改变世界的调用等到恢复
     */
    public synchronized void suspend(String plan, String reason) {
        suspended.put(plan, reason);
        record("suspended", Json.object("plan", plan, "reason", reason));
    }

    public synchronized void resume(String plan) {
        if (suspended.remove(plan) != null) {
            record("resumed", Json.object("plan", plan));
            wakeLocked();
        }
    }

    public synchronized boolean suspended(String plan) {
        return suspended.containsKey(plan);
    }

    /**
     * 计划在持有链上：持有 lease，或者把 lease 委托了出去，委托结束后会回到它
     */
    public synchronized boolean inChain(String plan) {
        return active != null && chain.contains(plan);
    }

    /**
     * 计划结束或放弃 lease：它和链上在它之后的计划离开持有链，lease 回到它的委托者；它是最初的持有者时 lease 被释放。
     * 链上在它之后的计划因此失去 lease 时调用 {@code onRevoke}
     */
    public void release(String plan, String reason) {
        Revocation revoked = null;
        synchronized (this) {
            suspended.remove(plan);
            var index = chain.indexOf(plan);
            if (active == null || index < 0) {
                return;
            }
            var previousOwner = ownerLocked();
            var lease = active;
            while (chain.size() > index) {
                chain.removeLast();
            }
            if (!plan.equals(previousOwner)) {
                revoked = new Revocation(lease, previousOwner, "RELEASED_BY_" + plan);
            }
            if (chain.isEmpty()) {
                record("released", Json.object("plan", plan, "reason", reason));
                clearLocked();
            } else {
                record("returned", Json.object("from", plan, "to", ownerLocked(), "reason", reason));
                wakeLocked();
            }
        }
        if (revoked != null) {
            onRevoke.accept(revoked);
        }
    }

    /**
     * 收回 lease（停止、安全事件、预算耗尽、断线）
     */
    public void revokeAll(String reason) {
        Revocation revoked;
        synchronized (this) {
            if (active == null) {
                return;
            }
            revoked = revokeLocked(reason);
        }
        onRevoke.accept(revoked);
    }

    public synchronized Hold hold(String plan) {
        if (active == null || !plan.equals(ownerLocked())) {
            return Hold.NOT_HELD;
        }
        return suspended.containsKey(plan) ? Hold.SUSPENDED : Hold.HELD;
    }

    /**
     * 计划持有 lease 并且没有被暂停时完成；计划不在持有链上时以 {@link LeaseLostException} 完成
     */
    public synchronized CompletableFuture<Void> awaitHeld(String plan) {
        var future = new CompletableFuture<Void>();
        waiters.add(new Waiter(plan, future));
        wakeLocked();
        return future;
    }

    public synchronized @Nullable Lease active() {
        return active;
    }

    /**
     * 当前持有者；没有 lease 时为 null
     */
    public synchronized @Nullable String owner() {
        return active == null ? null : ownerLocked();
    }

    public synchronized JsonObject describe() {
        return Json.object("lease", active, "chain", List.copyOf(chain), "suspended", Map.copyOf(suspended));
    }

    private String ownerLocked() {
        return chain.getLast();
    }

    private Lease grantLocked(String plan, Priority priority, String purpose, @Nullable Long expiresAt) {
        var lease = new Lease("lease-" + ++counter, plan, priority, purpose, expiresAt);
        active = lease;
        chain.clear();
        chain.add(plan);
        record("granted", Json.object());
        wakeLocked();
        return lease;
    }

    private void expire(Lease lease) {
        Revocation revoked = null;
        synchronized (this) {
            if (active == lease) {
                revoked = revokeLocked("EXPIRED");
            }
        }
        if (revoked != null) {
            onRevoke.accept(revoked);
        }
    }

    private Revocation revokeLocked(String reason) {
        var revoked = new Revocation(Objects.requireNonNull(active), ownerLocked(), reason);
        record("revoked", Json.object("reason", reason));
        clearLocked();
        return revoked;
    }

    private void clearLocked() {
        active = null;
        chain.clear();
        if (expiry != null) {
            expiry.cancel(false);
            expiry = null;
        }
        wakeLocked();
    }

    private void wakeLocked() {
        var iterator = waiters.iterator();
        while (iterator.hasNext()) {
            var waiter = iterator.next();
            if (waiter.future.isDone()) {
                iterator.remove();
            } else if (active != null && waiter.plan.equals(ownerLocked()) && !suspended.containsKey(waiter.plan)) {
                iterator.remove();
                waiter.future.complete(null);
            } else if (active == null || !chain.contains(waiter.plan)) {
                iterator.remove();
                waiter.future.completeExceptionally(new LeaseLostException(waiter.plan + " no longer holds the lease"));
            }
        }
    }

    private void record(String event, JsonObject details) {
        details.addProperty("event", event);
        details.add("lease", Json.tree(active));
        details.add("chain", Json.tree(List.copyOf(chain)));
        trace.append(TraceKind.LEASE, details);
    }

    private record Waiter(String plan, CompletableFuture<Void> future) {
    }
}
