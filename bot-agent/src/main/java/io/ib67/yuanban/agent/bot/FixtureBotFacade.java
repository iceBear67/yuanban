package io.ib67.yuanban.agent.bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.tool.Condition;
import io.ib67.yuanban.agent.tool.ObserverSpec;
import io.ib67.yuanban.agent.view.BlockView;
import io.ib67.yuanban.agent.view.GameView;
import io.ib67.yuanban.agent.view.Vec3;
import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.ChunkNotLoadedException;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 按 fixture 运行的 {@link BotFacade}，不连接服务器：状态来自 {@link FixtureWorld}，动作与等待的结果来自预设条目，信号按预设时间投递。
 * <p>
 * 动作条目 {@code {"tool": 工具名, "match": {...}, "outcome": "completed|cancelled|disconnected|failed|timeout",
 * "reason": 取消原因, "observation": observer 结论, "effects": 世界的变化, "delayMs": 执行时间}}：
 * 每次提交取第一个工具名相同、{@code match} 是动作描述（{@link ActionSpec} 的字段）子集的未使用条目。
 * 执行完毕且没有给出 effects 的 moveTo、jumpTo 把玩家移到目标；没有给出 observation 时，movementReached、blockBroken、blockAt
 * 由世界的变化算出结论。没有匹配的条目时动作以 FAILED 结束，并记入 {@link #unmatched()}。
 * <p>
 * 等待条目 {@code {"outcome": "met|timeout|disconnected", "effects": ..., "delayMs": ...}} 按顺序使用；没有条目时按当前世界检查条件，
 * 成立为 MET，否则立即 TIMEOUT（fixture 中的时间不流逝）。信号条目 {@code {"atMs": 毫秒, "topic": ..., "data": {...}}}。
 */
public final class FixtureBotFacade implements BotFacade {
    private final FixtureWorld world;
    private final List<JsonObject> actions = new ArrayList<>();
    private final List<JsonObject> waits = new ArrayList<>();
    private final List<JsonObject> signals = new ArrayList<>();
    private final List<String> unmatched = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Signal>> sinks = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("fixture-signals").factory());
    private final long startNanos = System.nanoTime();
    private final String username;

    public FixtureBotFacade(FixtureWorld world, @Nullable JsonArray actions, @Nullable JsonArray waits,
                            @Nullable JsonArray signals) {
        this.world = world;
        addAll(this.actions, actions);
        addAll(this.waits, waits);
        addAll(this.signals, signals);
        var player = world.player();
        this.username = player == null ? "fixture" : player.username();
    }

    public FixtureWorld world() {
        return world;
    }

    /**
     * 没有预设结果的动作
     */
    public List<String> unmatched() {
        return List.copyOf(unmatched);
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public CompletableFuture<Void> joined() {
        return world.status().inWorld() ? CompletableFuture.completedFuture(null) : new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<Void> loaded() {
        return world.status().loaded() ? CompletableFuture.completedFuture(null) : new CompletableFuture<>();
    }

    @Override
    public <T> T read(Function<GameView, T> reader) {
        return reader.apply(world);
    }

    @Override
    public PendingAction start(ActionSpec action, @Nullable ObserverSpec observer) {
        var description = Json.tree(action).getAsJsonObject();
        var entry = take(action.tool(), description);
        var spec = observer != null ? observer : action.defaultObserver();
        if (entry == null) {
            unmatched.add(action.tool() + " " + Json.write(description));
        }
        return new FixtureAction(action, spec, entry, description);
    }

    @Override
    public PendingWait startWait(Condition condition) {
        JsonObject entry;
        synchronized (waits) {
            entry = waits.isEmpty() ? null : waits.removeFirst();
        }
        var cancelled = new CountDownLatch(1);
        return new PendingWait() {
            @Override
            public WaitOutcome await(Duration timeout) {
                if (entry == null) {
                    return new WaitOutcome(condition.test(world) ? WaitOutcome.Status.MET : WaitOutcome.Status.TIMEOUT, null);
                }
                var delay = Duration.ofMillis(Json.longOr(entry, "delayMs", 0));
                if (sleep(cancelled, delay.compareTo(timeout) > 0 ? timeout : delay)) {
                    return new WaitOutcome(WaitOutcome.Status.CANCELLED, null);
                }
                world.apply(Json.objectOrNull(entry, "effects"));
                return switch (Json.requireString(entry, "outcome")) {
                    case "met" -> new WaitOutcome(WaitOutcome.Status.MET, null);
                    case "timeout" -> new WaitOutcome(WaitOutcome.Status.TIMEOUT, null);
                    case "disconnected" -> new WaitOutcome(WaitOutcome.Status.DISCONNECTED,
                            new BotDisconnectedException(Component.text("fixture disconnect")));
                    default -> throw new IllegalArgumentException("unknown wait outcome in fixture: " + entry);
                };
            }

            @Override
            public void cancel() {
                cancelled.countDown();
            }
        };
    }

    @Override
    public AutoCloseable subscribe(Consumer<Signal> sink) {
        sinks.add(sink);
        var elapsed = elapsedMillis();
        List<JsonObject> pending;
        synchronized (signals) {
            pending = List.copyOf(signals);
        }
        for (var signal : pending) {
            var at = Json.longOr(signal, "atMs", 0);
            if (at >= elapsed) {
                timer.schedule(() -> {
                    if (sinks.contains(sink)) {
                        var data = Json.objectOrNull(signal, "data");
                        sink.accept(new Signal(Json.requireString(signal, "topic"), data == null ? new JsonObject() : data, ticks()));
                    }
                }, at - elapsed, TimeUnit.MILLISECONDS);
            }
        }
        return () -> sinks.remove(sink);
    }

    @Override
    public long ticks() {
        return elapsedMillis() / 50;
    }

    @Override
    public boolean ended() {
        return "DISCONNECTED".equals(world.status().status());
    }

    @Override
    public void close() {
        timer.shutdownNow();
    }

    private long elapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private @Nullable JsonObject take(String tool, JsonObject description) {
        synchronized (actions) {
            var iterator = actions.iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (tool.equals(Json.string(entry, "tool")) && subset(Json.objectOrNull(entry, "match"), description)) {
                    iterator.remove();
                    return entry;
                }
            }
            return null;
        }
    }

    private static boolean subset(@Nullable JsonElement expected, @Nullable JsonElement actual) {
        if (expected == null || expected.isJsonNull()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (expected.isJsonObject()) {
            if (!actual.isJsonObject()) {
                return false;
            }
            for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject().entrySet()) {
                if (!subset(entry.getValue(), actual.getAsJsonObject().get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isJsonPrimitive() && expected.getAsJsonPrimitive().isNumber() && actual.isJsonPrimitive()
                && actual.getAsJsonPrimitive().isNumber()) {
            return expected.getAsDouble() == actual.getAsDouble();
        }
        return expected.equals(actual);
    }

    /**
     * @return 是否在等待期间被取消
     */
    private static boolean sleep(CountDownLatch cancelled, Duration delay) {
        try {
            return cancelled.await(delay.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static void addAll(List<JsonObject> target, @Nullable JsonArray source) {
        if (source != null) {
            source.forEach(element -> target.add(element.getAsJsonObject()));
        }
    }

    private final class FixtureAction implements PendingAction {
        private final ActionSpec action;
        private final @Nullable ObserverSpec observer;
        private final @Nullable JsonObject entry;
        private final JsonObject description;
        private final CountDownLatch cancelled = new CountDownLatch(1);

        private FixtureAction(ActionSpec action, @Nullable ObserverSpec observer, @Nullable JsonObject entry,
                              JsonObject description) {
            this.action = action;
            this.observer = observer;
            this.entry = entry;
            this.description = description;
        }

        @Override
        public ActionOutcome await(Duration timeout) {
            if (entry == null) {
                return new ActionOutcome(ActionOutcome.Status.FAILED, null,
                        new BotFailureException(new IllegalStateException("the fixture has no scripted result for "
                                + action.tool() + " " + Json.write(description))), null);
            }
            var delay = Duration.ofMillis(Json.longOr(entry, "delayMs", 0));
            if (delay.compareTo(timeout) > 0) {
                if (sleep(cancelled, timeout)) {
                    return new ActionOutcome(ActionOutcome.Status.CANCELLED, CancelReasons.BY_USER, null, null);
                }
                return new ActionOutcome(ActionOutcome.Status.TIMEOUT, null, null, null);
            }
            if (sleep(cancelled, delay)) {
                return new ActionOutcome(ActionOutcome.Status.CANCELLED, CancelReasons.BY_USER, null, null);
            }
            var outcome = Json.string(entry, "outcome");
            outcome = outcome == null ? "completed" : outcome;
            var before = blockBefore();
            var effects = Json.objectOrNull(entry, "effects");
            if (effects != null) {
                world.apply(effects);
            } else if (outcome.equals("completed")) {
                switch (action) {
                    case ActionSpec.MoveTo moveTo -> world.movePlayer(moveTo.target());
                    case ActionSpec.JumpTo jumpTo -> world.movePlayer(jumpTo.target().block().topCenter());
                    default -> {
                    }
                }
            }
            var observation = entry.has("observation") ? entry.get("observation") : conclude(before);
            return switch (outcome) {
                case "completed" -> new ActionOutcome(ActionOutcome.Status.COMPLETED, null, null, observation);
                case "cancelled" -> new ActionOutcome(ActionOutcome.Status.CANCELLED,
                        Json.requireString(entry, "reason"), null, observation);
                case "disconnected" -> new ActionOutcome(ActionOutcome.Status.DISCONNECTED, null,
                        new BotDisconnectedException(Component.text("fixture disconnect")), null);
                case "failed" -> new ActionOutcome(ActionOutcome.Status.FAILED, null,
                        new BotFailureException(new IllegalStateException("fixture failure")), null);
                case "timeout" -> new ActionOutcome(ActionOutcome.Status.TIMEOUT, null, null, observation);
                default -> throw new IllegalArgumentException("unknown action outcome in fixture: " + entry);
            };
        }

        @Override
        public void cancel() {
            cancelled.countDown();
        }

        private @Nullable BlockView blockBefore() {
            var pos = switch (observer) {
                case ObserverSpec.BlockBroken broken -> broken.pos();
                case ObserverSpec.BlockAt at -> at.pos();
                case null, default -> null;
            };
            if (pos == null) {
                return null;
            }
            try {
                return world.block(pos.block());
            } catch (ChunkNotLoadedException | IllegalStateException e) {
                return null;
            }
        }

        private @Nullable JsonElement conclude(@Nullable BlockView before) {
            return switch (observer) {
                case ObserverSpec.MovementReached reached -> {
                    var player = world.player();
                    if (player == null) {
                        yield Json.object("reached", false, "finalPosition", null, "distance", null, "minDistance", null);
                    }
                    var distance = player.position().distance(reached.target());
                    yield Json.object("reached", distance <= reached.distance(), "finalPosition", player.position(),
                            "distance", distance, "minDistance", distance);
                }
                case ObserverSpec.BlockBroken broken -> {
                    var after = blockAt(broken.pos());
                    var changed = before != null && after != null && !before.name().equals(after.name());
                    yield Json.object("broken", changed, "original", block(before), "changedTo", changed ? block(after) : null);
                }
                case ObserverSpec.BlockAt at -> {
                    var after = blockAt(at.pos());
                    yield Json.object("met", after != null && at.names().contains(after.name()), "current", block(after),
                            "changes", new JsonArray());
                }
                case null, default -> null;
            };
        }

        private @Nullable BlockView blockAt(Vec3 pos) {
            try {
                return world.block(pos.block());
            } catch (ChunkNotLoadedException | IllegalStateException e) {
                return null;
            }
        }

        private static @Nullable JsonElement block(@Nullable BlockView block) {
            return block == null ? null : Json.object("name", block.name(), "properties", block.properties());
        }
    }
}
