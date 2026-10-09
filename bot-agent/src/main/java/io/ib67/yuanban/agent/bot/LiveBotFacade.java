package io.ib67.yuanban.agent.bot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.tool.Condition;
import io.ib67.yuanban.agent.tool.ObserverSpec;
import io.ib67.yuanban.agent.view.GameView;
import io.ib67.yuanban.agent.view.LiveGameView;
import io.ib67.yuanban.agent.view.Vec3;
import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.BotTimeoutException;
import io.ib67.yuanban.bot.ObservedAction;
import io.ib67.yuanban.bot.ObserverFailureException;
import io.ib67.yuanban.bot.observe.CloseReason;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.ObserverHandle;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.builtin.ConditionObserver;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 包装真实 {@link Bot} 的 {@link BotFacade}。
 * <p>
 * observer 的回调（包括条件求值）抛出异常时只有这个 observer 关闭，bot 不受影响（{@link ObserverFailureException}），
 * 对应的动作或等待以 FAILED 结束。
 */
public final class LiveBotFacade implements BotFacade {
    /**
     * 两次 moved 信号之间至少移动的距离
     */
    private static final double MOVE_SIGNAL_DISTANCE = 1.0;
    /**
     * 持续移动时两次 moved 信号之间最多相隔的 tick 数
     */
    private static final long MOVE_SIGNAL_TICKS = 20;

    private final Bot bot;
    private final CopyOnWriteArrayList<Consumer<Signal>> sinks = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Void> joined;
    private final CompletableFuture<Void> loaded;
    private final ObserverHandle<SignalObserver> signals;
    private volatile long ticks;

    /**
     * @throws BotDisconnectedException bot 已经断开
     * @throws BotFailureException      bot 因异常结束
     */
    public LiveBotFacade(Bot bot) {
        this.bot = bot;
        this.joined = bot.joined().future();
        this.loaded = bot.loaded().future();
        this.signals = bot.observe(new SignalObserver());
    }

    public Bot bot() {
        return bot;
    }

    @Override
    public String username() {
        return bot.username();
    }

    @Override
    public CompletableFuture<Void> joined() {
        return joined.copy();
    }

    @Override
    public CompletableFuture<Void> loaded() {
        return loaded.copy();
    }

    @Override
    public <T> T read(Function<GameView, T> reader) {
        return bot.inspect(state -> reader.apply(new LiveGameView(bot, state)));
    }

    @Override
    public PendingAction start(ActionSpec action, @Nullable ObserverSpec observer) {
        var spec = observer != null ? observer : action.defaultObserver();
        if (spec == null) {
            return new Plain(action.submit(bot));
        }
        var binding = LiveObservers.bind(spec);
        ObservedAction<Observer> observed;
        try {
            observed = bot.observeDuring(binding.observer(), action::submit);
        } catch (ObserverFailureException e) {
            // 注册时 observer 的回调抛出异常，动作没有提交
            var outcome = new ActionOutcome(ActionOutcome.Status.FAILED, null, e, null);
            return new PendingAction() {
                @Override
                public ActionOutcome await(Duration timeout) {
                    return outcome;
                }

                @Override
                public void cancel() {
                }
            };
        }
        return new Observed(observed, binding);
    }

    @Override
    public PendingWait startWait(Condition condition) {
        ConditionObserver observer = Observers.condition(state -> condition.test(new LiveGameView(bot, state)));
        ObserverHandle<ConditionObserver> handle;
        try {
            handle = bot.observe(observer);
        } catch (ObserverFailureException e) {
            // 注册时先求值一次条件，这时抛出异常
            var outcome = new WaitOutcome(WaitOutcome.Status.FAILED, e.getCause());
            return new PendingWait() {
                @Override
                public WaitOutcome await(Duration timeout) {
                    return outcome;
                }

                @Override
                public void cancel() {
                }
            };
        }
        return new PendingWait() {
            @Override
            public WaitOutcome await(Duration timeout) {
                CloseReason reason;
                try {
                    reason = handle.awaitClose(timeout);
                } catch (BotTimeoutException e) {
                    handle.close();
                    return new WaitOutcome(WaitOutcome.Status.TIMEOUT, null);
                } catch (ObserverFailureException e) {
                    // 条件求值抛出异常：只结束这次等待
                    return new WaitOutcome(WaitOutcome.Status.FAILED, e.getCause());
                }
                return switch (reason) {
                    case BY_OBSERVER -> new WaitOutcome(WaitOutcome.Status.MET, null);
                    case BY_HANDLE, ACTION_COMPLETED -> new WaitOutcome(WaitOutcome.Status.CANCELLED, null);
                    case DISCONNECTED -> endedWait();
                    case FAILED -> new WaitOutcome(WaitOutcome.Status.FAILED, handle.failure());
                };
            }

            @Override
            public void cancel() {
                handle.close();
            }
        };
    }

    @Override
    public AutoCloseable subscribe(Consumer<Signal> sink) {
        sinks.add(sink);
        return () -> sinks.remove(sink);
    }

    @Override
    public long ticks() {
        return ticks;
    }

    @Override
    public boolean ended() {
        return bot.status() == BotStatus.DISCONNECTED;
    }

    @Override
    public void close() {
        if (!ended()) {
            bot.disconnect();
        }
        signals.close();
    }

    private WaitOutcome endedWait() {
        var failure = bot.failure();
        if (failure != null) {
            return new WaitOutcome(WaitOutcome.Status.FAILED, new BotFailureException(failure));
        }
        var reason = bot.disconnectReason();
        return new WaitOutcome(WaitOutcome.Status.DISCONNECTED, reason == null ? null : new BotDisconnectedException(reason));
    }

    /**
     * @return 取消原因的名称；动作正常完成时为 null
     */
    private static @Nullable String cancelReason(Action action) {
        var error = action.future().handle((_, t) -> t).join();
        if (error == null) {
            return null;
        }
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        if (error instanceof ActionCancelledException cancelled) {
            return CancelReasons.name(cancelled);
        }
        throw new IllegalStateException("action ended with an unexpected error", error);
    }

    private static ActionOutcome endedOutcome(RuntimeException error) {
        return switch (error) {
            case BotDisconnectedException disconnected -> new ActionOutcome(ActionOutcome.Status.DISCONNECTED, null, disconnected, null);
            case BotFailureException failure -> new ActionOutcome(ActionOutcome.Status.FAILED, null, failure, null);
            default -> throw error;
        };
    }

    private record Plain(Action action) implements PendingAction {
        @Override
        public ActionOutcome await(Duration timeout) {
            try {
                if (action.await(timeout)) {
                    return new ActionOutcome(ActionOutcome.Status.COMPLETED, null, null, null);
                }
                return new ActionOutcome(ActionOutcome.Status.CANCELLED, cancelReason(action), null, null);
            } catch (BotTimeoutException e) {
                action.cancel();
                return new ActionOutcome(ActionOutcome.Status.TIMEOUT, null, e, null);
            } catch (BotDisconnectedException | BotFailureException e) {
                return endedOutcome(e);
            }
        }

        @Override
        public void cancel() {
            action.cancel();
        }
    }

    private record Observed(ObservedAction<Observer> observed, LiveObservers.Binding binding) implements PendingAction {
        @Override
        public ActionOutcome await(Duration timeout) {
            try {
                observed.await(timeout);
            } catch (BotTimeoutException e) {
                observed.action().cancel();
                observed.handle().close();
                return new ActionOutcome(ActionOutcome.Status.TIMEOUT, null, e, conclusion());
            } catch (ObserverFailureException e) {
                // observer 的回调抛出异常：observer 已经关闭，动作仍在执行，取消它
                observed.action().cancel();
                return new ActionOutcome(ActionOutcome.Status.FAILED, null, e, null);
            } catch (BotDisconnectedException | BotFailureException e) {
                return endedOutcome(e);
            }
            var reason = cancelReason(observed.action());
            return new ActionOutcome(reason == null ? ActionOutcome.Status.COMPLETED : ActionOutcome.Status.CANCELLED,
                    reason, null, conclusion());
        }

        private JsonElement conclusion() {
            return binding.conclude().apply(binding.observer());
        }

        @Override
        public void cancel() {
            observed.action().cancel();
        }
    }

    /**
     * 把观察记录转换为信号交给订阅者，并累计 tick 数。只在调度线程上运行，不阻塞
     */
    private final class SignalObserver implements Observer {
        private @Nullable Vec3 lastMoved;
        private long lastMovedTick;
        private int chunksLoaded;
        private int chunksUnloaded;

        @Override
        public Subscription subscription() {
            return Subscription.of(Observation.PlayerMoved.class, Observation.PlayerTeleported.class,
                    Observation.HealthChanged.class, Observation.EntityDamaged.class, Observation.ChatReceived.class,
                    Observation.WorldEntered.class, Observation.PlayerDied.class, Observation.ChunkLoaded.class,
                    Observation.ChunkUnloaded.class, Observation.PlayerLoaded.class, Observation.BotTicked.class);
        }

        @Override
        public void onObservation(Observation observation, ObserverContext context) {
            switch (observation) {
                case Observation.BotTicked(long tick) -> {
                    ticks = tick;
                    if (chunksLoaded > 0 || chunksUnloaded > 0) {
                        emit("chunks", Json.object("loaded", chunksLoaded, "unloaded", chunksUnloaded));
                        chunksLoaded = 0;
                        chunksUnloaded = 0;
                    }
                }
                case Observation.PlayerMoved moved -> {
                    var to = Vec3.of(moved.to());
                    if (lastMoved == null || lastMoved.distance(to) >= MOVE_SIGNAL_DISTANCE
                            || ticks - lastMovedTick >= MOVE_SIGNAL_TICKS) {
                        lastMoved = to;
                        lastMovedTick = ticks;
                        emit("moved", Json.object("position", to, "onGround", moved.onGround()));
                    }
                }
                case Observation.PlayerTeleported teleported -> {
                    lastMoved = Vec3.of(teleported.to());
                    lastMovedTick = ticks;
                    emit("teleported", Json.object("from", Vec3.of(teleported.from()), "to", lastMoved));
                }
                case Observation.HealthChanged health -> emit("health", Json.object("oldHealth", health.oldHealth(),
                        "health", health.health(), "food", health.food()));
                case Observation.EntityDamaged damaged -> {
                    if (context.world() != null && damaged.entityId() == context.state().player().entityId()) {
                        emit("damage", Json.object("type", damaged.type().asString(), "sourceId", damaged.sourceId(),
                                "sourcePos", damaged.sourcePos() == null ? null : Vec3.of(damaged.sourcePos())));
                    }
                }
                case Observation.ChatReceived(var line) -> emit("chat", Json.tree(LiveGameView.chatView(line)).getAsJsonObject());
                case Observation.WorldEntered(var world) -> emit("world_entered", Json.object("dimension",
                        world.dimension().asString(), "epoch", world.epoch(), "cause", world.cause().name()));
                case Observation.PlayerDied died -> emit("player_died", Json.object("message",
                        PlainTextComponentSerializer.plainText().serialize(died.message())));
                case Observation.ChunkLoaded _ -> chunksLoaded++;
                case Observation.ChunkUnloaded _ -> chunksUnloaded++;
                case Observation.PlayerLoaded _ -> emit("loaded", Json.object());
                default -> {
                }
            }
        }

        @Override
        public void onClose(CloseReason reason, ObserverContext context) {
            var disconnect = bot.disconnectReason();
            var failure = bot.failure();
            emit("disconnected", Json.object("reason", disconnect == null ? null
                            : PlainTextComponentSerializer.plainText().serialize(disconnect),
                    "failure", failure == null ? null : failure.toString(), "closeReason", reason.name()));
        }

        private void emit(String topic, JsonObject data) {
            var signal = new Signal(topic, data, ticks);
            for (var sink : sinks) {
                sink.accept(signal);
            }
        }
    }
}
