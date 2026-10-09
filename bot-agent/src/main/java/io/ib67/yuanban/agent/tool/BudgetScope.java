package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import org.jetbrains.annotations.Nullable;

import java.time.InstantSource;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 一层预算：agent、run（bundle）、脚本各一层，子层的消耗同时计入全部上层。任何一层耗尽时扣减失败，
 * 并对这一层的这一项调用一次耗尽监听（可恢复事件：上层可以扩大预算或重新开始）。线程安全。
 */
public final class BudgetScope {
    public enum Kind {
        ACTIONS,
        TICKS,
        WALL_TIME,
        MODEL_CALLS,
        TOKENS
    }

    /**
     * 一层预算的一项耗尽
     */
    public record Exhaustion(String scope, Kind kind, long limit, long used) {
        public JsonObject toJson() {
            return Json.object("scope", scope, "kind", kind.name(), "limit", limit, "used", used);
        }
    }

    private final String name;
    private final @Nullable BudgetScope parent;
    private final InstantSource clock;
    private final LongSupplier ticks;
    private final Consumer<Exhaustion> listener;
    private final Set<Kind> reported = EnumSet.noneOf(Kind.class);
    private BudgetLimits limits;
    private long startMillis;
    private long startTick;
    private long actions;
    private long modelCalls;
    private long tokens;

    private BudgetScope(String name, BudgetLimits limits, @Nullable BudgetScope parent, InstantSource clock,
                        LongSupplier ticks, Consumer<Exhaustion> listener) {
        this.name = name;
        this.limits = limits;
        this.parent = parent;
        this.clock = clock;
        this.ticks = ticks;
        this.listener = listener;
        this.startMillis = clock.millis();
        this.startTick = ticks.getAsLong();
    }

    public static BudgetScope root(String name, BudgetLimits limits, InstantSource clock, LongSupplier ticks,
                                   Consumer<Exhaustion> listener) {
        return new BudgetScope(name, limits, null, clock, ticks, listener);
    }

    public BudgetScope child(String name, BudgetLimits limits, Consumer<Exhaustion> listener) {
        return new BudgetScope(name, limits, this, clock, ticks, listener);
    }

    public String name() {
        return name;
    }

    /**
     * 检查时间与 tick 预算
     *
     * @return 第一个耗尽的项（从这一层向上）；没有时为 null
     */
    public @Nullable Exhaustion check() {
        for (var scope = this; scope != null; scope = scope.parent) {
            var exhausted = scope.checkOwn();
            if (exhausted != null) {
                scope.report(exhausted);
                return exhausted;
            }
        }
        return null;
    }

    /**
     * 扣减计数类的预算（ACTIONS、MODEL_CALLS、TOKENS）；任何一层不够时一层都不扣减
     *
     * @return 耗尽的项；扣减成功时为 null
     */
    public @Nullable Exhaustion charge(Kind kind, long amount) {
        if (kind == Kind.TICKS || kind == Kind.WALL_TIME) {
            throw new IllegalArgumentException(kind + " is not charged, it is measured");
        }
        var timed = check();
        if (timed != null) {
            return timed;
        }
        synchronized (rootLock()) {
            for (var scope = this; scope != null; scope = scope.parent) {
                var limit = scope.limit(kind);
                var used = scope.used(kind);
                if (limit != null && used + amount > limit) {
                    var exhausted = new Exhaustion(scope.name, kind, limit, used);
                    scope.report(exhausted);
                    return exhausted;
                }
            }
            for (var scope = this; scope != null; scope = scope.parent) {
                scope.add(kind, amount);
            }
        }
        return null;
    }

    /**
     * 缩小这一层的上限：新上限不能放宽已有的上限
     */
    public synchronized void narrow(BudgetLimits narrower) {
        limits = new BudgetLimits(min(limits.actions(), narrower.actions()), min(limits.ticks(), narrower.ticks()),
                min(limits.wallTimeMs(), narrower.wallTimeMs()), min(limits.modelCalls(), narrower.modelCalls()),
                min(limits.tokens(), narrower.tokens()));
    }

    /**
     * 重新开始计数（用户在 agent 预算耗尽之后恢复运行）
     */
    public synchronized void reset() {
        startMillis = clock.millis();
        startTick = ticks.getAsLong();
        actions = 0;
        modelCalls = 0;
        tokens = 0;
        reported.clear();
    }

    /**
     * 这一层与上层中最紧的剩余量
     */
    public JsonObject remaining() {
        var result = new JsonObject();
        for (var kind : Kind.values()) {
            Long remaining = null;
            for (var scope = this; scope != null; scope = scope.parent) {
                var limit = scope.limit(kind);
                if (limit != null) {
                    var left = Math.max(0, limit - scope.used(kind));
                    remaining = remaining == null ? left : Math.min(remaining, left);
                }
            }
            result.addProperty(kind.name(), remaining);
        }
        return result;
    }

    public synchronized JsonObject usage() {
        return Json.object("scope", name, "limits", limits.toJson(), "actions", actions, "ticks", used(Kind.TICKS),
                "wallTimeMs", used(Kind.WALL_TIME), "modelCalls", modelCalls, "tokens", tokens);
    }

    private @Nullable Exhaustion checkOwn() {
        for (var kind : new Kind[]{Kind.WALL_TIME, Kind.TICKS}) {
            var limit = limit(kind);
            var used = used(kind);
            if (limit != null && used >= limit) {
                return new Exhaustion(name, kind, limit, used);
            }
        }
        return null;
    }

    private synchronized @Nullable Long limit(Kind kind) {
        return switch (kind) {
            case ACTIONS -> limits.actions();
            case TICKS -> limits.ticks();
            case WALL_TIME -> limits.wallTimeMs();
            case MODEL_CALLS -> limits.modelCalls();
            case TOKENS -> limits.tokens();
        };
    }

    private synchronized long used(Kind kind) {
        return switch (kind) {
            case ACTIONS -> actions;
            case TICKS -> ticks.getAsLong() - startTick;
            case WALL_TIME -> clock.millis() - startMillis;
            case MODEL_CALLS -> modelCalls;
            case TOKENS -> tokens;
        };
    }

    private synchronized void add(Kind kind, long amount) {
        switch (kind) {
            case ACTIONS -> actions += amount;
            case MODEL_CALLS -> modelCalls += amount;
            case TOKENS -> tokens += amount;
            default -> throw new IllegalArgumentException(kind.name());
        }
    }

    private void report(Exhaustion exhausted) {
        boolean first;
        synchronized (this) {
            first = reported.add(exhausted.kind());
        }
        if (first) {
            listener.accept(exhausted);
        }
    }

    private Object rootLock() {
        var scope = this;
        while (scope.parent != null) {
            scope = scope.parent;
        }
        return scope;
    }

    private static @Nullable Long min(@Nullable Long a, @Nullable Long b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : Math.min(a, b);
    }
}
