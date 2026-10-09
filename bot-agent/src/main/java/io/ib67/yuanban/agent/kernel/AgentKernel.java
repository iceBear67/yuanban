package io.ib67.yuanban.agent.kernel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.ib67.yuanban.agent.AgentEvent;
import io.ib67.yuanban.agent.AgentIntent;
import io.ib67.yuanban.agent.AgentRun;
import io.ib67.yuanban.agent.AgentStatus;
import io.ib67.yuanban.agent.bot.Signal;
import io.ib67.yuanban.agent.ingress.UserCommand;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.SchemaViolationException;
import io.ib67.yuanban.agent.message.Envelope;
import io.ib67.yuanban.agent.message.MessageType;
import io.ib67.yuanban.agent.message.Priority;
import io.ib67.yuanban.agent.model.ModelMessage;
import io.ib67.yuanban.agent.model.ModelRequest;
import io.ib67.yuanban.agent.model.ToolDefinition;
import io.ib67.yuanban.agent.notify.NotifyKind;
import io.ib67.yuanban.agent.plan.BackgroundScheduler;
import io.ib67.yuanban.agent.plan.BundleResult;
import io.ib67.yuanban.agent.plan.BundleSpec;
import io.ib67.yuanban.agent.plan.PlanActor;
import io.ib67.yuanban.agent.plan.PlanBundle;
import io.ib67.yuanban.agent.tool.BudgetScope;
import io.ib67.yuanban.agent.trace.TraceKind;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * AgentKernel：理解用户目标、选择与管理自治脚本、维护给模型的上下文与 compaction。它不是每个动作的调度器：
 * 只在用户消息、attention、fault、interest、bundle 结果、preflight 结果、用户回答与定期 heartbeat 时唤醒模型，
 * 模型的输出是一条结构化决策。
 * <p>
 * kernel 本身处理两种模式共用的部分：输入、模型回合与修正、预算、compaction、运行生命周期、前台 bundle、提问与说话。
 * 模型看到的内容、其余决策与 background plan 由 {@link KernelMode} 决定：{@link SupervisorMode}（完整模式：发送控制消息、
 * 启动 bundle、生成 draft、preflight、停止）或 {@link SimpleMode}（简单模式：直接编写并运行脚本、停止）。
 * <p>
 * 全部状态只在 kernel 线程上修改；其它线程通过输入队列交给它。模型回合与模块测试在虚拟线程上执行，结果回到输入队列。
 */
public final class AgentKernel {
    private static final int MAX_TURN_STEPS = 8;
    private static final int MAX_OUTPUT_TOKENS = 4096;
    private static final int MAX_REJECTED_DECISIONS = 2;
    private static final int MAX_MODEL_FAILURES = 3;
    private static final int RECENT_CHAT = 10;
    private static final int MAX_PROGRESS_PLANS = 20;
    private static final int STRING_LIMIT = 500;
    private static final int ARRAY_LIMIT = 20;
    /**
     * 进入上下文并唤醒模型的事件类型；suggestion 只在没有前台 bundle 时唤醒
     */
    private static final Set<MessageType> WAKING = Set.of(MessageType.ATTENTION, MessageType.FAULT, MessageType.INTEREST);
    /**
     * kernel 关心的 bot 信号
     */
    private static final Set<String> SIGNALS = Set.of("loaded", "disconnected", "player_died", "world_entered");
    /**
     * 参考文档中两种模式共用的工具 API 说明（{@code script-tools.md}）的位置
     */
    private static final String SCRIPT_TOOLS = "{{script-tools}}";

    private final KernelServices services;
    private final KernelMode mode;
    private final ContextManager context;
    private final SupervisorTools tools;
    private final List<ToolDefinition> toolDefinitions;
    private final LinkedBlockingQueue<Input> inbox = new LinkedBlockingQueue<>();
    private final Thread thread;
    private final List<AutoCloseable> subscriptions = new ArrayList<>();

    // 以下只在 kernel 线程上读写
    private AgentStatus status = AgentStatus.CREATED;
    private boolean joined;
    private boolean loaded;
    private boolean paused;
    private boolean budgetPaused;
    private @Nullable AgentStatus lastRunEnd;
    private @Nullable RunImpl run;
    private long runs;
    private @Nullable PlanBundle foreground;
    /**
     * 取消旧的前台 bundle 之后要启动的 bundle（start 带 replace）
     */
    private @Nullable BundleSpec queuedStart;
    private final LinkedHashMap<String, JsonObject> unacked = new LinkedHashMap<>();
    private final LinkedHashMap<String, Question> questions = new LinkedHashMap<>();
    /**
     * 每个计划的进度计数与最后一条进度，只保留最近的若干个计划
     */
    private final Map<String, JsonObject> progress = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, JsonObject> eldest) {
            return size() > MAX_PROGRESS_PLANS;
        }
    };
    private final ArrayDeque<JsonObject> recentChat = new ArrayDeque<>();
    private @Nullable String userGoal;
    private boolean wake;
    private boolean turnInFlight;
    private boolean discardTurn;
    private long retryAt;
    private long turns;
    private List<String> presented = List.of();
    private int rejectedDecisions;
    private int modelFailures;
    private long eventSeq;
    private boolean compacting;
    private long localIds;
    private @Nullable ScheduledFuture<?> heartbeat;
    private @Nullable String terminalReason;
    private volatile JsonObject snapshot = new JsonObject();
    private volatile AgentStatus publicStatus = AgentStatus.CREATED;

    private AgentKernel(KernelServices services, Function<KernelCore, KernelMode> mode) {
        this.services = services;
        this.mode = mode.apply(new Core());
        this.tools = new SupervisorTools(services.workspace(), services.tester(), services.gateway().catalog(),
                services.trace().file(), () -> snapshot);
        this.toolDefinitions = this.mode.tools();
        this.context = new ContextManager(services.model().contextWindow(), this.mode.pinned(), toolDefinitions);
        this.context.catalog(this.mode.catalog());
        this.thread = Thread.ofPlatform().daemon(true).name("agent-kernel-" + services.agentId()).unstarted(this::loop);
    }

    /**
     * 完整模式：模型作为 supervisor 管理 PlanBundle，人格驱动 background plan
     */
    public static AgentKernel supervisor(KernelServices services, BackgroundScheduler background) {
        return new AgentKernel(services, core -> new SupervisorMode(core, background));
    }

    /**
     * 简单模式：模型为每条指令直接编写脚本并运行，没有 supervisor 与 background plan
     */
    public static AgentKernel simple(KernelServices services) {
        return new AgentKernel(services, SimpleMode::new);
    }

    public void start() {
        subscriptions.add(services.events().subscribe(_ -> true, event -> inbox.add(new EventInput(event))));
        subscriptions.add(services.gateway().subscribeSignals(signal -> {
            if (SIGNALS.contains(signal.topic())) {
                inbox.add(new SignalInput(signal));
            }
        }));
        services.trace().append(TraceKind.STATE, Json.object("event", "kernel_started", "model", services.model().name(),
                "contextWindow", services.model().contextWindow()));
        transition(AgentStatus.CONNECTING);
        thread.start();
    }

    // 线程安全的入口

    public CompletableFuture<AgentRun> submit(AgentIntent intent) {
        var accepted = new CompletableFuture<AgentRun>();
        inbox.add(new IntentInput(intent, accepted));
        return accepted;
    }

    public void command(UserCommand command) {
        inbox.add(new CommandInput(command));
    }

    /**
     * 服务端发来的聊天（非命令），按文本去重之后进入上下文，不唤醒模型
     */
    public void serverChat(JsonObject line) {
        inbox.add(new ChatInput(line));
    }

    /**
     * bot 进入了世界
     */
    public void joined() {
        inbox.add(new LifecycleInput(Lifecycle.JOINED));
    }

    /**
     * bot 发送了 Player Loaded（{@code bot.loaded()} 完成；不依赖信号 observer 注册的时机）
     */
    public void loaded() {
        inbox.add(new LifecycleInput(Lifecycle.LOADED));
    }

    /**
     * 重新连接之后的新 bot 已经替换旧的
     */
    public void reconnected() {
        inbox.add(new LifecycleInput(Lifecycle.RECONNECTED));
    }

    /**
     * agent 级预算耗尽（由预算的耗尽监听调用）
     */
    public void budgetExhausted(BudgetScope.Exhaustion exhaustion) {
        inbox.add(new BudgetInput(exhaustion));
    }

    /**
     * workspace 中的模块变化之后刷新上下文中的模块目录
     */
    public void refreshCatalog() {
        inbox.add(new CatalogInput());
    }

    public AgentStatus status() {
        return publicStatus;
    }

    /**
     * 最近一次的完整状态（kernel 线程更新）
     */
    public JsonObject snapshot() {
        return snapshot.deepCopy();
    }

    /**
     * 停止全部计划并结束 kernel 线程
     */
    public void shutdown(String reason) {
        if (!thread.isAlive()) {
            return;
        }
        var done = new CompletableFuture<Void>();
        inbox.add(new ShutdownInput(reason, done));
        done.join();
    }

    // kernel 线程

    private void loop() {
        while (true) {
            Input input;
            try {
                input = inbox.take();
            } catch (InterruptedException e) {
                return;
            }
            try {
                if (input instanceof ShutdownInput(var reason, var done)) {
                    handleShutdown(reason);
                    done.complete(null);
                    return;
                }
                handle(input);
                maybeCompact();
                maybeStartTurn();
                refreshSnapshot();
            } catch (RuntimeException e) {
                // kernel 自身的程序错误：记录并让 agent 失败
                var stack = services.trace().stack(e);
                terminalReason = "kernel error: " + e + " (" + stack + ")";
                stopEverything("kernel error");
                endRun(AgentRun.Outcome.Status.FAILED, terminalReason, null);
                transition(AgentStatus.FAILED);
                services.publish().accept(new AgentEvent.Terminated(AgentStatus.FAILED, terminalReason));
                refreshSnapshot();
                drainAfterFailure();
                return;
            }
        }
    }

    /**
     * kernel 失败之后只处理 shutdown，其余输入丢弃
     */
    private void drainAfterFailure() {
        while (true) {
            try {
                if (inbox.take() instanceof ShutdownInput(var reason, var done)) {
                    handleShutdown(reason);
                    done.complete(null);
                    return;
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void handle(Input input) {
        switch (input) {
            case IntentInput(var intent, var accepted) -> handleIntent(intent, accepted);
            case CommandInput(var command) -> handleCommand(command);
            case EventInput(var event) -> handleEvent(event);
            case SignalInput(var signal) -> handleSignal(signal);
            case ChatInput(var line) -> handleChat(line);
            case LifecycleInput(var what) -> handleLifecycle(what);
            case TurnDone done -> handleTurnDone(done);
            case CompactionReady(var candidate) -> handleCompaction(candidate);
            case HeartbeatInput _ -> {
                if (foreground != null && !paused) {
                    addHot("heartbeat-" + ++localIds, Json.object("type", "heartbeat", "at", services.clock().millis()), true);
                }
            }
            case RetryInput _ -> {
                // 只触发 maybeStartTurn
            }
            case Deferred(var task) -> task.run();
            case BundleDone(var bundle, var result) -> handleBundleDone(bundle, result);
            case BudgetInput(var exhaustion) -> handleBudget(exhaustion);
            case CatalogInput _ -> context.catalog(mode.catalog());
            case ShutdownInput _ -> throw new IllegalStateException("shutdown is handled by the loop");
        }
    }

    private void handleIntent(AgentIntent intent, CompletableFuture<AgentRun> accepted) {
        services.trace().append(TraceKind.INTENT, Json.object("text", intent.text(), "source", intent.source().name(),
                "sender", intent.sender()));
        if (run != null && !run.outcome.isDone()) {
            endRun(AgentRun.Outcome.Status.SUPERSEDED, "superseded by a new goal", null);
        }
        var current = new RunImpl("run-" + ++runs, intent);
        run = current;
        userGoal = intent.text();
        lastRunEnd = null;
        mode.goalSubmitted(intent);
        addHot("user-" + current.id, Json.object("type", "user_message", "run", current.id, "text", intent.text(),
                "source", intent.source().name(), "sender", intent.sender()), true);
        services.publish().accept(new AgentEvent.RunStarted(current.id, intent));
        accepted.complete(current);
        recomputeStatus();
    }

    private void handleCommand(UserCommand command) {
        services.trace().append(TraceKind.INTENT, Json.object("command", command.kind().name(), "id", command.id(),
                "text", command.text(), "source", command.source()));
        switch (command.kind()) {
            case STOP -> {
                // 用户 stop 的优先级高于模型输出：进行中的回合的决策被丢弃
                discardTurn = turnInFlight;
                wake = false;
                mode.hold(null);
                stopEverything("stopped by " + command.source());
                endRun(AgentRun.Outcome.Status.STOPPED, "stopped by the user", null);
                lastRunEnd = AgentStatus.STOPPED;
                paused = false;
                say("已停止。", NotifyKind.STATUS);
            }
            case PAUSE -> {
                paused = true;
                if (foreground != null) {
                    foreground.pausePrimary("paused by " + command.source());
                }
                mode.hold("agent paused");
                say("已暂停；/resume 继续。", NotifyKind.STATUS);
            }
            case RESUME -> {
                paused = false;
                if (budgetPaused) {
                    budgetPaused = false;
                    services.budget().reset();
                }
                rejectedDecisions = 0;
                modelFailures = 0;
                if (foreground != null) {
                    foreground.resumePrimary();
                }
                mode.resumed(foreground == null && loaded);
                say("已恢复。", NotifyKind.STATUS);
                if (!unacked.isEmpty()) {
                    wake = true;
                }
            }
            case STATUS -> say(statusLine(), NotifyKind.STATUS);
            case REPLY -> handleReply(Objects.requireNonNull(command.id()), Objects.requireNonNull(command.text()), command.source());
            case TRACE -> say("trace：" + services.trace().file().toAbsolutePath(), NotifyKind.STATUS);
        }
        recomputeStatus();
    }

    private void handleReply(String id, String text, String source) {
        var question = questions.remove(id);
        if (question == null) {
            say("没有等待回答的问题 " + id, NotifyKind.STATUS);
            return;
        }
        services.trace().append(TraceKind.ACK, Json.object("id", id, "by", source, "reply", text));
        if (question.plan() != null) {
            var plan = services.plans().plan(question.plan());
            if (plan != null && plan.running()) {
                plan.deliver(services.messages().create(question.run(), MessageType.USER_REPLY, Priority.HIGH, "user:" + source,
                        null, Json.object("text", text, "question", id), null, id));
            }
            unacked.remove(id);
            mode.responded(id);
        }
        addHot("reply-" + id, Json.object("type", "user_reply", "question", id, "text", text, "from", source,
                "plan", question.plan()), true);
    }

    private void handleEvent(Envelope event) {
        eventSeq++;
        switch (event.type()) {
            case PROGRESS -> {
                var plan = planOf(event.source());
                var entry = progress.computeIfAbsent(plan, _ -> Json.object("count", 0));
                entry.addProperty("count", entry.get("count").getAsLong() + 1);
                entry.add("last", truncate(event.payload()));
                services.notifications().publish(NotifyKind.PROGRESS, event.source(), "进度 " + plan + "：" + summary(event.payload()),
                        event.payload());
            }
            case OBSERVATION, ACTION_REQUEST -> {
                // observation 只写入 trace；action_request 由后台调度器处理
            }
            case CHECKPOINT -> {
                services.trace().append(TraceKind.ACK, Json.object("id", event.id(), "by", "kernel"));
                mode.checkpointed(planOf(event.source()));
            }
            case QUESTION -> {
                var plan = planOf(event.source());
                var text = Json.string(event.payload(), "text");
                questions.put(event.id(), new Question(event.id(), plan, event.run(), text));
                addHot(event.id(), hotItem(event), false);
                mode.expectResponse(event.id());
                services.notifications().publish(NotifyKind.NEED_USER, event.source(), "问题 " + event.id() + "：" + text
                        + "（/reply " + event.id() + " <回答>）", event.payload());
            }
            case SUGGESTION -> addHot(event.id(), hotItem(event), foreground == null);
            case NEED_PREFLIGHT -> addHot(event.id(), hotItem(event), false);
            case RESULT -> {
                addHot(event.id(), hotItem(event), true);
                services.notifications().publish(NotifyKind.RESULT, event.source(), "结果 " + event.topic() + "：" + summary(event.payload()),
                        event.payload());
            }
            default -> {
                if (!WAKING.contains(event.type())) {
                    return;
                }
                var existing = event.dedupeKey() == null ? null : findByDedupe(event.dedupeKey());
                if (existing != null) {
                    // 同一个 dedupeKey 的未确认事件合并为计数与最新内容
                    var item = unacked.get(existing);
                    item.addProperty("repeats", Json.longOr(item, "repeats", 1) + 1);
                    item.add("payload", truncate(event.payload()));
                } else {
                    addHot(event.id(), hotItem(event), true);
                    mode.expectResponse(event.id());
                }
                var kind = event.type() == MessageType.FAULT ? NotifyKind.FAULT : NotifyKind.ATTENTION;
                services.notifications().publish(kind, event.source(), event.type().wire() + " " + event.topic() + "："
                        + summary(event.payload()), event.payload());
            }
        }
    }

    private void handleSignal(Signal signal) {
        switch (signal.topic()) {
            case "loaded" -> markLoaded();
            case "disconnected" -> {
                var failure = Json.string(signal.data(), "failure");
                terminalReason = failure != null ? failure : Json.string(signal.data(), "reason");
                stopEverything("bot disconnected");
                endRun(AgentRun.Outcome.Status.FAILED, "bot disconnected: " + terminalReason, null);
                services.notifications().publish(NotifyKind.FAULT, "kernel", "bot 断开：" + terminalReason, signal.data());
                transition(failure != null ? AgentStatus.FAILED : AgentStatus.DISCONNECTED);
                services.publish().accept(new AgentEvent.Terminated(status, terminalReason));
            }
            case "player_died" -> {
                // 安全事件：取消全部 background plan，交给模型决定
                mode.hold("player died");
                addHot("died-" + ++localIds, Json.object("type", "player_died", "data", signal.data()), true);
            }
            case "world_entered" -> addHot("world-" + ++localIds, Json.object("type", "world_entered", "data", signal.data()), false);
            default -> {
            }
        }
        recomputeStatus();
    }

    /**
     * 第一次加载：交给模式（完整模式按人格启动 background plan），开始定期 heartbeat
     */
    private void markLoaded() {
        joined = true;
        if (loaded) {
            return;
        }
        loaded = true;
        mode.loaded();
        if (heartbeat == null && !services.profile().heartbeat().isZero()) {
            var period = services.profile().heartbeat().toMillis();
            heartbeat = services.timer().scheduleAtFixedRate(() -> inbox.add(new HeartbeatInput()), period, period,
                    TimeUnit.MILLISECONDS);
        }
    }

    private void handleLifecycle(Lifecycle what) {
        switch (what) {
            case JOINED -> joined = true;
            case LOADED -> markLoaded();
            case RECONNECTED -> {
                // 新的连接：旧连接上的计划都已经停止，从头开始等待进入世界
                joined = false;
                loaded = false;
                terminalReason = null;
                status = AgentStatus.CONNECTING;
                publicStatus = status;
                services.trace().append(TraceKind.STATE, Json.object("event", "reconnected"));
                services.publish().accept(new AgentEvent.StatusChanged(AgentStatus.DISCONNECTED, AgentStatus.CONNECTING));
            }
        }
        recomputeStatus();
    }

    private void handleChat(JsonObject line) {
        var text = Json.string(line, "text");
        recentChat.removeIf(existing -> Objects.equals(Json.string(existing, "text"), text));
        line.addProperty("source", "server");
        recentChat.addLast(line);
        while (recentChat.size() > RECENT_CHAT) {
            recentChat.removeFirst();
        }
        eventSeq++;
    }

    private void handleBundleDone(PlanBundle bundle, BundleResult result) {
        services.publish().accept(new AgentEvent.BundleEnded(bundle.id(), result.toJson()));
        if (bundle != foreground) {
            return;
        }
        foreground = null;
        if (run != null) {
            run.lastResult = result.toJson();
        }
        var queued = queuedStart;
        queuedStart = null;
        if (queued != null) {
            var error = startBundle(queued);
            if (error != null) {
                addHot("start-failed-" + ++localIds, Json.object("type", "decision_error", "error", error), true);
            }
        } else {
            mode.foregroundEnded();
        }
        recomputeStatus();
    }

    private void handleBudget(BudgetScope.Exhaustion exhaustion) {
        paused = true;
        budgetPaused = true;
        if (foreground != null) {
            foreground.pausePrimary("agent budget exhausted");
        }
        mode.hold("agent budget exhausted");
        services.notifications().publish(NotifyKind.NEED_USER, "kernel", "agent 预算耗尽（" + exhaustion.kind()
                + "），已暂停；/resume 重新开始计数", exhaustion.toJson());
        recomputeStatus();
    }

    // 模型回合

    private void maybeStartTurn() {
        if (!wake || turnInFlight || paused || status.terminal() || !loaded || services.clock().millis() < retryAt) {
            return;
        }
        // 脚本保存 draft、模块测试都会改变 workspace，每个回合开始时重新读取目录
        context.catalog(mode.catalog());
        var current = mode.context(KernelMode.ContextKind.TURN);
        var text = render(current);
        var messages = context.messages(text, false);
        var tokens = context.estimate(messages);
        var pressure = context.pressure(tokens);
        var emergency = false;
        if (pressure == ContextManager.Pressure.HARD || pressure == ContextManager.Pressure.EMERGENCY) {
            if (context.compactable() > 0) {
                startCompaction();
                if (pressure == ContextManager.Pressure.HARD) {
                    // hard：暂缓新的回合，等压缩提交；脚本与控制消息照常
                    return;
                }
            }
            if (pressure == ContextManager.Pressure.EMERGENCY) {
                // emergency：只保留 pinned、最近的 attention/fault/question、脚本版本与检查点
                emergency = true;
                current = mode.context(KernelMode.ContextKind.EMERGENCY);
                text = render(current);
                messages = context.messages(text, true);
            }
        }
        var exhausted = services.budget().charge(BudgetScope.Kind.MODEL_CALLS, 1);
        if (exhausted != null) {
            handleBudget(exhausted);
            return;
        }
        wake = false;
        turnInFlight = true;
        discardTurn = false;
        var turn = ++turns;
        presented = List.copyOf(unacked.keySet());
        var traceFrom = services.trace().append(TraceKind.MODEL_REQUEST_META, Json.object("turn", turn, "model", services.model().name(),
                "messages", messages.size(), "estimatedTokens", tokens, "pressure", pressure.name(), "emergency", emergency,
                "presented", presented, "contextHash", Json.sha256(Json.write(withoutVolatile(current))), "context", current));
        recomputeStatus();
        var request = messages;
        var userMessage = new ModelMessage.User(text);
        Thread.ofVirtual().name("agent-turn-" + turn).start(() -> inbox.add(runTurn(turn, request, userMessage, traceFrom)));
    }

    /**
     * 在虚拟线程上执行一个回合：查询函数在回合中执行，最后得到一条通过 schema 校验的决策；无效的决策可以修正一次
     */
    private TurnDone runTurn(long turn, List<ModelMessage> request, ModelMessage.User userMessage, long traceFrom) {
        var added = new ArrayList<ModelMessage>();
        long input = 0;
        long output = 0;
        var repairs = 0;
        List<String> errors = List.of("no decision");
        try {
            for (var step = 0; step < MAX_TURN_STEPS; step++) {
                var messages = new ArrayList<>(request);
                messages.addAll(added);
                var response = services.model().complete(new ModelRequest(messages, toolDefinitions, true, MAX_OUTPUT_TOKENS))
                        .toCompletableFuture().get();
                input += response.usage().inputTokens();
                output += response.usage().outputTokens();
                services.trace().append(TraceKind.MODEL_RESPONSE_META, Json.object("turn", turn, "step", step,
                        "usage", response.usage().toJson(), "finishReason", response.finishReason(), "response", response.toJson()));
                added.add(new ModelMessage.Assistant(response.content(), response.toolCalls()));
                var attempted = false;
                JsonObject decision = null;
                for (var call : response.toolCalls()) {
                    if (!call.name().equals(Decisions.TOOL_NAME)) {
                        added.add(new ModelMessage.ToolResult(call.id(), tools.execute(call)));
                        continue;
                    }
                    attempted = true;
                    var problems = Decisions.validate(call.arguments(), mode.decisionSchema());
                    if (problems.isEmpty() && decision == null) {
                        decision = Json.parseObject(call.arguments());
                        added.add(new ModelMessage.ToolResult(call.id(), "{\"accepted\": true}"));
                    } else {
                        errors = problems.isEmpty() ? List.of("only one decision per turn") : problems;
                        added.add(new ModelMessage.ToolResult(call.id(), Json.write(Json.object("accepted", false,
                                "errors", errors, "hint", "修正后重新调用 " + Decisions.TOOL_NAME))));
                    }
                }
                if (decision != null) {
                    return new TurnDone(turn, userMessage, decision, List.of(), added, input, output, traceFrom, null);
                }
                if (response.toolCalls().isEmpty()) {
                    attempted = true;
                    errors = List.of("the response called no function; call " + Decisions.TOOL_NAME);
                    added.add(new ModelMessage.User("必须调用 " + Decisions.TOOL_NAME + " 提交决策。"));
                }
                if (attempted && repairs++ >= 1) {
                    return new TurnDone(turn, userMessage, null, errors, added, input, output, traceFrom, null);
                }
            }
            return new TurnDone(turn, userMessage, null, List.of("no decision after " + MAX_TURN_STEPS + " steps"), added,
                    input, output, traceFrom, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TurnDone(turn, userMessage, null, List.of("interrupted"), added, input, output, traceFrom, e);
        } catch (ExecutionException e) {
            return new TurnDone(turn, userMessage, null, List.of(), added, input, output, traceFrom, e.getCause());
        } catch (RuntimeException e) {
            return new TurnDone(turn, userMessage, null, List.of(), added, input, output, traceFrom, e);
        }
    }

    private void handleTurnDone(TurnDone done) {
        turnInFlight = false;
        var tokens = services.budget().charge(BudgetScope.Kind.TOKENS, done.input() + done.output());
        var traceTo = services.trace().append(TraceKind.STATE, Json.object("event", "turn_done", "turn", done.turn(),
                "decision", done.decision(), "errors", done.errors(), "inputTokens", done.input(), "outputTokens", done.output(),
                "failure", done.failure() == null ? null : done.failure().toString()));
        var messages = new ArrayList<ModelMessage>();
        messages.add(done.userMessage());
        messages.addAll(done.added());
        context.append(new ContextManager.Turn(done.turn(), messages, context.estimate(messages), done.traceFrom(), traceTo));
        services.publish().accept(new AgentEvent.ModelTurn(done.turn(), done.decision(), done.input(), done.output()));
        if (tokens != null) {
            handleBudget(tokens);
        }
        if (done.failure() != null) {
            modelFailures++;
            services.trace().stack(done.failure());
            wake = true;
            if (modelFailures >= MAX_MODEL_FAILURES) {
                paused = true;
                services.notifications().publish(NotifyKind.NEED_USER, "kernel", "模型连续 " + modelFailures + " 次调用失败，已暂停："
                        + done.failure(), Json.object("error", done.failure().toString()));
            } else {
                var delay = 2_000L * modelFailures;
                retryAt = services.clock().millis() + delay;
                services.timer().schedule(() -> inbox.add(new RetryInput()), delay, TimeUnit.MILLISECONDS);
            }
            recomputeStatus();
            return;
        }
        modelFailures = 0;
        if (discardTurn) {
            services.trace().append(TraceKind.STATE, Json.object("event", "turn_discarded", "turn", done.turn()));
            recomputeStatus();
            return;
        }
        if (done.decision() == null) {
            // 回合中已经自动修正过一次，仍然不符合 schema：进入 PAUSED 等待用户
            paused = true;
            services.notifications().publish(NotifyKind.NEED_USER, "kernel", "模型输出修正一次之后仍然无效，已暂停："
                    + String.join("; ", done.errors()), Json.object("errors", done.errors()));
            recomputeStatus();
            return;
        }
        var error = apply(done.decision());
        if (error != null) {
            // 符合 schema 但不能执行的决策（模块不存在、目标计划已经结束等）交还给模型，连续两次之后暂停
            rejectedDecisions++;
            if (rejectedDecisions >= MAX_REJECTED_DECISIONS) {
                paused = true;
                services.notifications().publish(NotifyKind.NEED_USER, "kernel", "模型的决策连续不能执行，已暂停：" + error,
                        Json.object("error", error));
            } else {
                addHot("decision-error-" + done.turn(), Json.object("type", "decision_error", "decision",
                        Json.string(done.decision(), "decision"), "error", error), true);
            }
        } else {
            rejectedDecisions = 0;
            acknowledgePresented();
        }
        recomputeStatus();
    }

    /**
     * ask 与 say 在这里处理，其余决策交给模式
     *
     * @return 不能执行时的说明；执行时为 null
     */
    private @Nullable String apply(JsonObject decision) {
        var type = Json.requireString(decision, "decision");
        var reason = Json.requireString(decision, "reason");
        try {
            return switch (type) {
                case "ask" -> {
                    var ask = decision.getAsJsonObject("ask");
                    var id = "ask-" + ++localIds;
                    var text = Json.requireString(ask, "question");
                    questions.put(id, new Question(id, null, run == null ? "run-0" : run.id, text));
                    services.notifications().publish(NotifyKind.NEED_USER, "kernel", "问题 " + id + "：" + text
                            + (ask.has("choices") ? " " + ask.get("choices") : "") + "（/reply " + id + " <回答>）", ask);
                    yield null;
                }
                case "say" -> {
                    var say = decision.getAsJsonObject("say");
                    var text = Json.requireString(say, "text");
                    services.notifications().publish(NotifyKind.SAY, "kernel", text, say);
                    if (Json.booleanOr(say, "done", false)) {
                        finishGoal(text);
                    }
                    yield null;
                }
                default -> mode.apply(type, decision, reason);
            };
        } catch (IllegalArgumentException | IllegalStateException | SchemaViolationException e) {
            return e.getMessage();
        }
    }

    private @Nullable String startBundle(BundleSpec spec) {
        var runId = run == null ? "run-0" : run.id;
        var bundle = services.plans().startBundle(spec, runId, null, services.budget(), "foreground");
        foreground = bundle;
        mode.foregroundStarted(spec);
        services.publish().accept(new AgentEvent.BundleStarted(bundle.id(), spec.toJson()));
        bundle.done().thenAccept(result -> inbox.add(new BundleDone(bundle, result)));
        return null;
    }

    private void acknowledgePresented() {
        for (var id : presented) {
            var item = unacked.get(id);
            if (item == null || "question".equals(Json.string(item, "type"))) {
                continue;
            }
            unacked.remove(id);
            mode.responded(id);
            services.trace().append(TraceKind.ACK, Json.object("id", id, "by", "supervisor", "turn", turns));
        }
        presented = List.of();
    }

    // compaction

    private void maybeCompact() {
        if (compacting || turnInFlight || context.compactable() == 0) {
            return;
        }
        var tokens = context.estimate(context.messages(render(mode.context(KernelMode.ContextKind.TURN)), false));
        if (context.pressure(tokens).compareTo(ContextManager.Pressure.SOFT) >= 0) {
            startCompaction();
        }
    }

    /**
     * soft：后台生成候选摘要，脚本与控制消息完全不受影响；提交时按事件序号比较，期间有新输入则丢弃并重新生成
     */
    private void startCompaction() {
        if (compacting) {
            return;
        }
        compacting = true;
        var turnsToFold = context.compactable();
        var folded = context.turns().subList(0, turnsToFold);
        var summary = mode.context(KernelMode.ContextKind.SUMMARY);
        var refs = new ArrayList<String>();
        refs.add("trace:" + folded.getFirst().traceFrom() + "-" + folded.getLast().traceTo());
        if (foreground != null && foreground.primary() != null) {
            var primary = Objects.requireNonNull(foreground.primary()).module();
            refs.add("workspace:module/" + primary.name() + "/" + primary.version());
        }
        summary.add("coldRefs", Json.array(refs));
        var basedOn = eventSeq;
        recomputeStatus();
        Thread.ofVirtual().name("agent-compaction").start(() ->
                inbox.add(new CompactionReady(new ContextManager.Candidate(summary, turnsToFold, basedOn))));
    }

    private void handleCompaction(ContextManager.Candidate candidate) {
        compacting = false;
        var committed = context.commit(candidate, eventSeq);
        services.trace().append(TraceKind.COMPACTION, Json.object("committed", committed, "turns", candidate.turns(),
                "basedOnEventSeq", candidate.basedOnEventSeq(), "eventSeq", eventSeq,
                "summary", committed ? candidate.summary() : null));
        recomputeStatus();
    }

    // 上下文

    /**
     * 本回合的输入：标题为上下文的 schema（{@code supervisor-context/1} 或 {@code simple-context/1}）
     */
    private static String render(JsonObject context) {
        return Json.requireString(context, "schema") + "（当前状态与需要处理的输入）：\n" + Json.PRETTY.toJson(context);
    }

    /**
     * 上下文中的前台 bundle：编号、状态、primary 与 supervisor
     */
    static JsonObject scriptSummary(PlanBundle bundle) {
        var primary = bundle.primary();
        var supervisor = bundle.supervisor();
        return Json.object("bundle", bundle.id(), "status", bundle.status().name(),
                "primary", primary == null ? null : primary.describe(), "supervisor", supervisor == null ? null : supervisor.describe());
    }

    /**
     * 计算上下文 hash 时去掉每次都会变化的字段（bot 位置、预算、trace 序号）
     */
    private static JsonObject withoutVolatile(JsonObject context) {
        var copy = context.deepCopy();
        copy.remove("agent");
        copy.remove("budget");
        copy.remove("coldRefs");
        copy.remove("lease");
        return copy;
    }

    private JsonObject hotItem(Envelope event) {
        return Json.object("id", event.id(), "type", event.type().wire(), "topic", event.topic(), "source", event.source(),
                "sequence", event.sequence(), "dedupeKey", event.dedupeKey(), "payload", truncate(event.payload()), "at", event.at(),
                "ref", "trace:msg/" + event.id());
    }

    private void addHot(String id, JsonObject item, boolean wakeModel) {
        if (!item.has("id")) {
            item.addProperty("id", id);
        }
        unacked.put(id, item);
        eventSeq++;
        if (wakeModel) {
            wake = true;
        }
    }

    private @Nullable String findByDedupe(String dedupeKey) {
        for (var entry : unacked.entrySet()) {
            if (dedupeKey.equals(Json.string(entry.getValue(), "dedupeKey"))) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 截断过长的字符串与数组：被截断的值换成带 truncated: true 的对象
     */
    static JsonElement truncate(JsonElement element) {
        if (element.isJsonObject()) {
            var result = new JsonObject();
            for (var entry : element.getAsJsonObject().entrySet()) {
                result.add(entry.getKey(), truncate(entry.getValue()));
            }
            return result;
        }
        if (element.isJsonArray()) {
            var array = element.getAsJsonArray();
            var result = new JsonArray();
            for (var i = 0; i < Math.min(array.size(), ARRAY_LIMIT); i++) {
                result.add(truncate(array.get(i)));
            }
            return array.size() > ARRAY_LIMIT ? Json.object("items", result, "truncated", true, "total", array.size()) : result;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && element.getAsString().length() > STRING_LIMIT) {
            return Json.object("text", new JsonPrimitive(element.getAsString().substring(0, STRING_LIMIT)), "truncated", true,
                    "length", element.getAsString().length());
        }
        return element;
    }

    private static String summary(JsonObject payload) {
        var text = Json.write(payload);
        return text.length() > 160 ? text.substring(0, 160) + "…" : text;
    }

    // 状态

    private void recomputeStatus() {
        if (status.terminal()) {
            return;
        }
        AgentStatus next;
        if (paused) {
            next = AgentStatus.PAUSED;
        } else if (compacting) {
            next = AgentStatus.COMPACTING;
        } else if (turnInFlight) {
            next = AgentStatus.SUPERVISING;
        } else if (foreground != null) {
            next = AgentStatus.SCRIPT_RUNNING;
        } else if (lastRunEnd != null) {
            next = lastRunEnd;
        } else if (loaded) {
            next = AgentStatus.LOADED;
        } else if (joined) {
            next = AgentStatus.JOINED;
        } else {
            next = AgentStatus.CONNECTING;
        }
        transition(next);
    }

    private void transition(AgentStatus next) {
        if (next == status) {
            return;
        }
        var old = status;
        status = next;
        publicStatus = next;
        services.trace().append(TraceKind.STATE, Json.object("event", "status", "old", old.name(), "now", next.name()));
        services.publish().accept(new AgentEvent.StatusChanged(old, next));
    }

    private void refreshSnapshot() {
        snapshot = Json.object("status", status.name(), "bot", status.terminal() ? null : services.botStatus().get(),
                "userGoal", userGoal, "run", run == null ? null : run.id,
                "foreground", foreground == null ? null : foreground.describe(),
                "background", Json.array(mode.background().stream().map(PlanActor::describe).toList()),
                "lease", services.leases().describe(), "budget", services.budget().usage(),
                "pendingQuestions", Json.array(questions.values().stream().map(Question::toJson).toList()),
                "unacked", unacked.size(), "turns", turns, "terminalReason", terminalReason);
    }

    private String statusLine() {
        var bundle = foreground;
        var primary = bundle == null ? null : bundle.primary();
        return "状态 " + status + "；目标 " + (userGoal == null ? "无" : userGoal) + "；前台 "
                + (bundle == null ? "无" : bundle.id() + "（" + (primary == null ? "" : primary.module()) + "，" + bundle.status() + "）")
                + "；后台 " + mode.background().size() + " 个；待回答 " + questions.size();
    }

    // 停止与结束

    private void stopEverything(String reason) {
        queuedStart = null;
        if (foreground != null) {
            foreground.cancel(reason);
        }
        mode.stopping(reason);
        for (var plan : services.plans().plans()) {
            plan.stop(reason, "kernel");
        }
        services.leases().revokeAll("STOPPED");
    }

    private void handleShutdown(String reason) {
        stopEverything(reason);
        for (var plan : services.plans().plans()) {
            plan.result().join();
        }
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
        endRun(AgentRun.Outcome.Status.STOPPED, "agent shut down: " + reason, null);
        for (var subscription : subscriptions) {
            try {
                subscription.close();
            } catch (Exception e) {
                throw new IllegalStateException("failed to close a kernel subscription", e);
            }
        }
        if (!status.terminal()) {
            transition(AgentStatus.STOPPED);
        }
        services.trace().append(TraceKind.TERMINAL_STATE, Json.object("status", status.name(), "reason", reason,
                "terminalReason", terminalReason, "turns", turns));
        refreshSnapshot();
    }

    private void endRun(AgentRun.Outcome.Status outcomeStatus, @Nullable String summary, @Nullable JsonElement result) {
        var current = run;
        if (current == null || current.outcome.isDone()) {
            return;
        }
        var outcome = new AgentRun.Outcome(outcomeStatus, summary, result);
        services.trace().append(TraceKind.STATE, Json.object("event", "run_ended", "run", current.id, "outcome", outcome.toJson()));
        services.workspace().writeRun(current.id, Json.object("run", current.id, "intent", current.intent.text(),
                "outcome", outcome.toJson(), "trace", services.trace().file().toAbsolutePath().toString(),
                "endedAt", services.clock().millis()));
        services.publish().accept(new AgentEvent.RunEnded(current.id, outcome));
        current.outcome.complete(outcome);
    }

    /**
     * 以 COMPLETED 结束当前运行：模型的 say 或 stop 写了 done
     */
    private void finishGoal(String summary) {
        endRun(AgentRun.Outcome.Status.COMPLETED, summary, run == null ? null : run.lastResult);
        lastRunEnd = AgentStatus.COMPLETED;
    }

    private void say(String text, NotifyKind kind) {
        services.notifications().publish(kind, "kernel", text, Json.object("text", text));
    }

    /**
     * 从 {@code script:<模块>@<版本>#<计划>} 中取出计划编号；其它来源原样返回
     */
    static String planOf(String source) {
        var hash = source.lastIndexOf('#');
        return hash < 0 ? source : source.substring(hash + 1);
    }

    static String resource(String name) {
        try (InputStream stream = Objects.requireNonNull(AgentKernel.class.getResourceAsStream(name), () -> "missing resource " + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + name, e);
        }
    }

    /**
     * 把参考文档中的 {@code {{script-tools}}} 一行替换为两种模式共用的工具 API 说明
     */
    static String withScriptTools(String reference) {
        return reference.replace(SCRIPT_TOOLS + "\n", resource("script-tools.md"));
    }

    /**
     * 交给模式的状态与操作
     */
    private final class Core implements KernelCore {
        @Override
        public KernelServices services() {
            return services;
        }

        @Override
        public AgentStatus status() {
            return status;
        }

        @Override
        public @Nullable String run() {
            return run == null ? null : run.id;
        }

        @Override
        public @Nullable String userGoal() {
            return userGoal;
        }

        @Override
        public @Nullable PlanBundle foreground() {
            return foreground;
        }

        @Override
        public Map<String, JsonObject> unacked() {
            return Collections.unmodifiableMap(unacked);
        }

        @Override
        public List<JsonObject> questions() {
            return questions.values().stream().map(Question::toJson).toList();
        }

        @Override
        public Map<String, JsonObject> progress() {
            return Collections.unmodifiableMap(progress);
        }

        @Override
        public List<JsonObject> recentChat() {
            return List.copyOf(recentChat);
        }

        @Override
        public void addHot(String id, JsonObject item, boolean wakeModel) {
            AgentKernel.this.addHot(id, item, wakeModel);
        }

        @Override
        public void acknowledge(String id) {
            unacked.remove(id);
            mode.responded(id);
        }

        @Override
        public @Nullable String startForeground(BundleSpec spec) {
            return startBundle(spec);
        }

        @Override
        public void replaceForeground(BundleSpec spec, String reason) {
            var current = Objects.requireNonNull(foreground, "no foreground bundle to replace");
            queuedStart = spec;
            current.cancel(reason);
        }

        @Override
        public void finishGoal(String summary) {
            AgentKernel.this.finishGoal(summary);
        }

        @Override
        public void catalogChanged() {
            context.catalog(mode.catalog());
        }

        @Override
        public void post(Runnable task) {
            inbox.add(new Deferred(task));
        }
    }

    // 运行与问题

    private static final class RunImpl implements AgentRun {
        private final String id;
        private final AgentIntent intent;
        private final CompletableFuture<Outcome> outcome = new CompletableFuture<>();
        private @Nullable JsonElement lastResult;

        private RunImpl(String id, AgentIntent intent) {
            this.id = id;
            this.intent = intent;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public AgentIntent intent() {
            return intent;
        }

        @Override
        public CompletionStage<Outcome> outcome() {
            return outcome.copy();
        }
    }

    /**
     * @param plan 脚本的问题为提问的计划；模型的问题为 null
     */
    private record Question(String id, @Nullable String plan, String run, @Nullable String text) {
        JsonObject toJson() {
            return Json.object("id", id, "plan", plan, "type", "question", "text", text);
        }
    }

    // 输入

    private enum Lifecycle {
        JOINED,
        LOADED,
        RECONNECTED
    }

    private sealed interface Input {
    }

    private record IntentInput(AgentIntent intent, CompletableFuture<AgentRun> accepted) implements Input {
    }

    private record CommandInput(UserCommand command) implements Input {
    }

    private record EventInput(Envelope event) implements Input {
    }

    private record SignalInput(Signal signal) implements Input {
    }

    private record ChatInput(JsonObject line) implements Input {
    }

    private record LifecycleInput(Lifecycle what) implements Input {
    }

    private record TurnDone(long turn, ModelMessage.User userMessage, @Nullable JsonObject decision, List<String> errors,
                            List<ModelMessage> added, long input, long output, long traceFrom,
                            @Nullable Throwable failure) implements Input {
    }

    private record CompactionReady(ContextManager.Candidate candidate) implements Input {
    }

    private record HeartbeatInput() implements Input {
    }

    private record RetryInput() implements Input {
    }

    /**
     * 模式交回 kernel 线程执行的任务（fallback 到期、preflight 结果、模块测试报告等）
     */
    private record Deferred(Runnable task) implements Input {
    }

    private record BundleDone(PlanBundle bundle, BundleResult result) implements Input {
    }

    private record BudgetInput(BudgetScope.Exhaustion exhaustion) implements Input {
    }

    private record CatalogInput() implements Input {
    }

    private record ShutdownInput(String reason, CompletableFuture<Void> done) implements Input {
    }
}
