package io.ib67.yuanban.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.bot.LiveBotFacade;
import io.ib67.yuanban.agent.bot.Signal;
import io.ib67.yuanban.agent.ingress.GameChatIngress;
import io.ib67.yuanban.agent.ingress.GameChatNotifier;
import io.ib67.yuanban.agent.ingress.UserCommand;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.kernel.AgentKernel;
import io.ib67.yuanban.agent.kernel.KernelServices;
import io.ib67.yuanban.agent.model.ModelProvider;
import io.ib67.yuanban.agent.message.MessageFactory;
import io.ib67.yuanban.agent.message.MessageType;
import io.ib67.yuanban.agent.message.Priority;
import io.ib67.yuanban.agent.message.ScriptEventBus;
import io.ib67.yuanban.agent.notify.NotifyBus;
import io.ib67.yuanban.agent.notify.NotifyKind;
import io.ib67.yuanban.agent.plan.BackgroundScheduler;
import io.ib67.yuanban.agent.plan.PlanManager;
import io.ib67.yuanban.agent.plan.PlanServices;
import io.ib67.yuanban.agent.tool.AgentConfigurator;
import io.ib67.yuanban.agent.tool.BudgetScope;
import io.ib67.yuanban.agent.tool.FacadeBackend;
import io.ib67.yuanban.agent.tool.LeaseManager;
import io.ib67.yuanban.agent.tool.ToolBackend;
import io.ib67.yuanban.agent.tool.ToolCatalog;
import io.ib67.yuanban.agent.tool.ToolGateway;
import io.ib67.yuanban.agent.trace.Redactor;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceStore;
import io.ib67.yuanban.agent.workspace.ModuleTester;
import io.ib67.yuanban.agent.workspace.Workspace;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 组装一个 agent：trace、workspace、ToolGateway、lease、计划、后台调度（只在完整模式中）、AgentKernel 与游戏内入口。
 * 实时模式连接 bot；重放模式（{@link io.ib67.yuanban.agent.replay.TraceReplay}）使用记录的工具结果，不连接服务器。
 */
final class AgentImpl implements Agent {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final String id;
    private final AgentRuntime runtime;
    private final AgentOptions options;
    private final TraceStore trace;
    private final @Nullable FacadeBackend live;
    private final NotifyBus notifications;
    private final ToolGateway gateway;
    private final PlanManager plans;
    /**
     * 人格驱动的 background plan；简单模式中没有
     */
    private final @Nullable BackgroundScheduler background;
    private final AgentKernel kernel;
    private final @Nullable GameChatNotifier notifier;
    private final @Nullable GameChatIngress ingress;
    private final SubmissionPublisher<AgentEvent> publisher = new SubmissionPublisher<>(Executors.newVirtualThreadPerTaskExecutor(), 1024);
    private final List<AutoCloseable> closeables = new ArrayList<>();
    private final AtomicInteger reconnects = new AtomicInteger();
    private volatile boolean inGameNotify = true;
    private volatile boolean closing;

    private AgentImpl(AgentRuntime runtime, AgentOptions options, @Nullable LiveBotFacade facade, @Nullable ToolBackend recorded) {
        this.runtime = runtime;
        this.options = options;
        var username = options.bot().username();
        var clock = options.clock();
        this.id = "agent-" + username + "-" + FILE_TIME.format(clock.instant());
        var secrets = new HashSet<>(options.secrets());
        var redactor = new Redactor(secrets, options.redactPatterns());
        this.trace = TraceStore.open(options.traceDirectory().resolve(id + ".jsonl"), id, username, redactor, clock,
                Json.object("agentId", id, "mode", facade == null ? "replay" : "live",
                        "model", Json.object("name", options.model().name(), "contextWindow", options.model().contextWindow()),
                        "profile", options.profile().raw(), "budget", options.budget().toJson(),
                        "workspace", options.workspace().toAbsolutePath().toString()));
        var timer = runtime.timer();
        var configurator = new Configurator();
        ToolBackend backend;
        if (facade != null) {
            var liveBackend = new FacadeBackend(facade, configurator);
            this.live = liveBackend;
            backend = liveBackend;
        } else {
            this.live = null;
            backend = Objects.requireNonNull(recorded);
        }
        this.notifications = new NotifyBus(trace, clock);
        closeables.add(notifications.subscribe(notification -> publish(new AgentEvent.Notified(notification))));
        var gatewayRef = new AtomicReference<ToolGateway>();
        var plansRef = new AtomicReference<PlanManager>();
        var messages = new MessageFactory(clock);
        var leases = new LeaseManager(trace, clock, timer, revocation -> {
            gatewayRef.get().cancelWorldChanging(revocation.owner(), "LEASE_REVOKED: " + revocation.reason());
            var plan = plansRef.get().plan(revocation.owner());
            if (plan != null) {
                plan.deliver(messages.create(plan.run(), MessageType.LEASE, Priority.HIGH, "leases", null,
                        Json.object("revoked", true, "reason", revocation.reason(), "leaseId", revocation.lease().id()), null, null));
            }
            notifications.publish(NotifyKind.LEASE, "leases", "lease " + revocation.lease().id() + " 从 " + revocation.owner()
                    + " 收回：" + revocation.reason(), Json.object("lease", Json.tree(revocation.lease()), "owner", revocation.owner()));
        });
        this.gateway = new ToolGateway(ToolCatalog.standard(), backend, leases, notifications, trace, clock, options.profile().policy());
        gatewayRef.set(gateway);
        var events = new ScriptEventBus(trace);
        var workspace = Workspace.open(options.workspace(), trace, clock);
        workspace.installBuiltins();
        var tester = new ModuleTester(workspace, runtime.engine(), options.scriptLimits(), timer, clock, options.profile());
        var simple = options.profile().simple();
        // 简单模式运行模型直接写下的 draft 脚本
        var planServices = new PlanServices(id, trace, messages, events, notifications, gateway, leases, workspace,
                runtime.engine(), options.scriptLimits(), timer, clock, options.profile(), tester, simple);
        this.plans = new PlanManager(planServices, "");
        plansRef.set(plans);
        var kernelRef = new AtomicReference<AgentKernel>();
        var budget = BudgetScope.root("agent", options.budget(), clock, backend::ticks, exhaustion -> {
            var current = kernelRef.get();
            if (current != null) {
                current.budgetExhausted(exhaustion);
            }
        });
        var kernelServices = new KernelServices(id, plans, gateway, leases, notifications, events,
                messages, workspace, tester, trace, options.model(), options.profile(), budget, clock, timer,
                this::botStatus, this::publish);
        if (simple) {
            this.background = null;
            this.kernel = AgentKernel.simple(kernelServices);
        } else {
            var scheduler = new BackgroundScheduler(plans, budget);
            scheduler.validate();
            this.background = scheduler;
            this.kernel = AgentKernel.supervisor(kernelServices, scheduler);
        }
        kernelRef.set(kernel);
        if (facade != null && options.chat().enabled()) {
            this.notifier = new GameChatNotifier(options.chat(), Set.copyOf(options.profile().inGameNotify()), notifications,
                    text -> {
                        var current = Objects.requireNonNull(live).facade();
                        if (inGameNotify && current instanceof LiveBotFacade bot && !bot.ended()) {
                            bot.bot().chat(text);
                        }
                    });
            this.ingress = new GameChatIngress(options.chat(), gateway, kernel, notifier, notifications, trace, clock, this::selfUuid);
        } else {
            this.notifier = null;
            this.ingress = null;
        }
        closeables.add(gateway.subscribeSignals(this::onSignal));
        kernel.start();
        if (facade != null) {
            facade.joined().thenRun(kernel::joined);
        }
        backend.loaded().thenRun(kernel::loaded);
    }

    static AgentImpl live(AgentRuntime runtime, AgentOptions options) {
        var bot = runtime.botRuntime().connect(options.bot());
        return new AgentImpl(runtime, options, new LiveBotFacade(bot), null);
    }

    static AgentImpl replay(AgentRuntime runtime, AgentOptions options, ToolBackend recorded) {
        return new AgentImpl(runtime, options, null, recorded);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public AgentStatus status() {
        return kernel.status();
    }

    @Override
    public CompletionStage<AgentRun> submit(AgentIntent intent) {
        return kernel.submit(intent);
    }

    @Override
    public void stop() {
        kernel.command(new UserCommand(UserCommand.Kind.STOP, null, null, "api"));
    }

    @Override
    public void command(UserCommand command) {
        kernel.command(command);
    }

    @Override
    public Flow.Publisher<AgentEvent> events() {
        return publisher;
    }

    @Override
    public JsonObject snapshot() {
        var snapshot = kernel.snapshot();
        snapshot.addProperty("agent", id);
        snapshot.addProperty("trace", trace.file().toAbsolutePath().toString());
        return snapshot;
    }

    @Override
    public Path traceFile() {
        return trace.file();
    }

    PlanManager plans() {
        return plans;
    }

    ModelProvider model() {
        return options.model();
    }

    TraceStore trace() {
        return trace;
    }

    /**
     * 停止 agent：停止 kernel（取消脚本与动作）、关闭游戏内入口与订阅、断开 bot、关闭 trace
     */
    void close() {
        closing = true;
        kernel.shutdown("runtime closing");
        for (var closeable : closeables.reversed()) {
            closeQuietly(closeable);
        }
        if (ingress != null) {
            closeQuietly(ingress);
        }
        if (notifier != null) {
            closeQuietly(notifier);
        }
        if (background != null) {
            closeQuietly(background);
        }
        closeQuietly(gateway);
        if (live != null) {
            live.facade().close();
        }
        publisher.close();
        trace.close();
    }

    private void onSignal(Signal signal) {
        if (!signal.topic().equals("disconnected") || closing || live == null) {
            return;
        }
        var reconnect = options.profile().reconnect();
        if (!reconnect.enabled() || Json.string(signal.data(), "failure") != null || reconnects.get() >= reconnect.maxAttempts()) {
            return;
        }
        runtime.timer().schedule(this::reconnect, reconnect.delay().toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 断线之后的重新连接：只重新建立连接，计划不会自动恢复；新的连接加载之后 background plan 按人格重新启动
     */
    private void reconnect() {
        if (closing) {
            return;
        }
        var attempt = reconnects.incrementAndGet();
        trace.append(TraceKind.BOT, Json.object("event", "reconnecting", "attempt", attempt));
        try {
            var facade = new LiveBotFacade(runtime.botRuntime().connect(options.bot()));
            Objects.requireNonNull(live).replace(facade);
            kernel.reconnected();
            facade.joined().thenRun(kernel::joined);
            facade.loaded().thenRun(kernel::loaded);
            trace.append(TraceKind.BOT, Json.object("event", "reconnected", "attempt", attempt));
        } catch (RuntimeException e) {
            trace.append(TraceKind.BOT, Json.object("event", "reconnect_failed", "attempt", attempt, "error", e.toString(),
                    "stackRef", trace.stack(e)));
            var reconnect = options.profile().reconnect();
            if (attempt < reconnect.maxAttempts()) {
                runtime.timer().schedule(this::reconnect, reconnect.delay().toMillis(), TimeUnit.MILLISECONDS);
            } else {
                notifications.publish(NotifyKind.NEED_USER, "agent", "重新连接失败 " + attempt + " 次，放弃", Json.object("error", e.toString()));
            }
        }
    }

    private JsonObject botStatus() {
        if (live == null) {
            return Json.object("replay", true);
        }
        var facade = live.facade();
        if (facade.ended()) {
            return Json.object("username", facade.username(), "status", BotStatus.DISCONNECTED.name());
        }
        try {
            return facade.read(view -> {
                var status = view.status();
                var player = view.player();
                var world = view.world();
                return Json.object("username", facade.username(), "status", status.status(), "loaded", status.loaded(),
                        "position", player == null ? null : player.position(), "health", player == null ? null : player.health(),
                        "food", player == null ? null : player.food(), "gameMode", player == null ? null : player.gameMode(),
                        "dimension", world == null ? null : world.dimension(), "ticks", status.ticks());
            });
        } catch (BotFailureException e) {
            // bot 刚刚因异常结束：disconnected 信号随后交给 kernel，这里如实给出状态
            return Json.object("username", facade.username(), "status", "FAILED", "failure", String.valueOf(e.getCause()));
        }
    }

    private @Nullable String selfUuid() {
        if (live == null || live.facade().ended()) {
            return null;
        }
        try {
            return live.facade().read(view -> {
                var player = view.player();
                return player == null ? null : player.uuid();
            });
        } catch (BotFailureException e) {
            return null;
        }
    }

    private void publish(AgentEvent event) {
        publisher.offer(event, (_, _) -> false);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            throw new IllegalStateException("failed to close " + closeable, e);
        }
    }

    /**
     * {@code external.configure} 可以修改的运行时配置；background.enabled 只在有 background plan 的完整模式中提供
     */
    private final class Configurator implements AgentConfigurator {
        @Override
        public Set<String> keys() {
            return background == null ? Set.of("notify.inGame", "dryRun") : Set.of("background.enabled", "notify.inGame", "dryRun");
        }

        @Override
        public void set(String key, JsonElement value) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException(key + " expects a boolean");
            }
            var enabled = value.getAsBoolean();
            switch (key) {
                case "background.enabled" -> Objects.requireNonNull(background, "simple mode has no background plans").enabled(enabled);
                case "notify.inGame" -> inGameNotify = enabled;
                case "dryRun" -> gateway.policy(gateway.policy().withDryRun(enabled));
                default -> throw new IllegalArgumentException("unknown configuration key " + key);
            }
            trace.append(TraceKind.STATE, Json.object("event", "configured", "key", key, "value", enabled));
        }
    }
}
