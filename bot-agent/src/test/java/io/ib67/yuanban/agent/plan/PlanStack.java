package io.ib67.yuanban.agent.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentProfile;
import io.ib67.yuanban.agent.bot.BotFacade;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.message.Envelope;
import io.ib67.yuanban.agent.message.MessageFactory;
import io.ib67.yuanban.agent.message.MessageType;
import io.ib67.yuanban.agent.message.ScriptEventBus;
import io.ib67.yuanban.agent.notify.NotifyBus;
import io.ib67.yuanban.agent.script.ScriptEngine;
import io.ib67.yuanban.agent.script.ScriptLimits;
import io.ib67.yuanban.agent.tool.AgentConfigurator;
import io.ib67.yuanban.agent.tool.BudgetLimits;
import io.ib67.yuanban.agent.tool.BudgetScope;
import io.ib67.yuanban.agent.tool.FacadeBackend;
import io.ib67.yuanban.agent.tool.LeaseManager;
import io.ib67.yuanban.agent.tool.ToolCatalog;
import io.ib67.yuanban.agent.tool.ToolGateway;
import io.ib67.yuanban.agent.trace.Redactor;
import io.ib67.yuanban.agent.trace.TraceStore;
import io.ib67.yuanban.agent.workspace.ModuleRef;
import io.ib67.yuanban.agent.workspace.ModuleTester;
import io.ib67.yuanban.agent.workspace.Workspace;

import java.nio.file.Path;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 测试用的计划层：真实的 trace、workspace（安装内置模块）、ToolGateway、lease、事件总线与 PlanManager，bot 由调用方给出
 * （fixture 外观或连接 Minestom 服务端的实时外观）。
 */
public final class PlanStack implements AutoCloseable {
    public final TraceStore trace;
    public final ScheduledExecutorService timer = Executors.newScheduledThreadPool(2, Thread.ofPlatform().daemon(true).factory());
    public final ScriptEngine engine = new ScriptEngine();
    public final Workspace workspace;
    public final NotifyBus notifications;
    public final LeaseManager leases;
    public final ToolGateway gateway;
    public final ScriptEventBus events;
    public final MessageFactory messages;
    public final PlanManager plans;
    public final BudgetScope budget;
    public final ModuleTester tester;
    public final List<Envelope> published = new CopyOnWriteArrayList<>();
    private final BotFacade facade;

    public PlanStack(Path dir, BotFacade facade, AgentProfile profile, ScriptLimits limits) {
        this.facade = facade;
        var clock = InstantSource.system();
        trace = TraceStore.open(dir.resolve("trace.jsonl"), "test", facade.username(), Redactor.none(), clock, new JsonObject());
        workspace = Workspace.open(dir.resolve("workspace"), trace, clock);
        workspace.installBuiltins();
        notifications = new NotifyBus(trace, clock);
        var gatewayRef = new AtomicReference<ToolGateway>();
        leases = new LeaseManager(trace, clock, timer, revocation -> gatewayRef.get()
                .cancelWorldChanging(revocation.owner(), "LEASE_REVOKED: " + revocation.reason()));
        gateway = new ToolGateway(ToolCatalog.standard(), new FacadeBackend(facade, new AgentConfigurator() {
            @Override
            public Set<String> keys() {
                return Set.of();
            }

            @Override
            public void set(String key, JsonElement value) {
                throw new IllegalArgumentException("no configuration in tests");
            }
        }), leases, notifications, trace, clock, profile.policy());
        gatewayRef.set(gateway);
        events = new ScriptEventBus(trace);
        events.subscribe(_ -> true, published::add);
        messages = new MessageFactory(clock);
        tester = new ModuleTester(workspace, engine, limits, timer, clock, profile);
        var services = new PlanServices("test", trace, messages, events, notifications, gateway, leases, workspace,
                engine, limits, timer, clock, profile, tester, true);
        plans = new PlanManager(services, "");
        budget = BudgetScope.root("test", BudgetLimits.UNLIMITED, clock, facade::ticks, _ -> {
        });
    }

    /**
     * 保存一个 draft 模块并返回它的引用
     */
    public ModuleRef draft(String name, String source, String manifest) {
        return workspace.saveDraft(name, source, Json.parseObject(manifest), List.of(), "test").ref();
    }

    /**
     * 保存一个带 fixture 的 draft 模块，运行 module.test 并返回报告
     *
     * @param fixtures fixture 的 JSON 数组
     */
    public JsonObject test(String name, String source, String manifest, String fixtures) {
        var list = new ArrayList<JsonObject>();
        Json.parse(fixtures).getAsJsonArray().forEach(element -> list.add(element.getAsJsonObject()));
        var ref = workspace.saveDraft(name, source, Json.parseObject(manifest), list, "test").ref();
        return tester.testAndRecord(ref);
    }

    public PlanBundle bundle(String spec) {
        return plans.startBundle(BundleSpec.fromJson(Json.parseObject(spec), null), "run-test", null, budget, "foreground");
    }

    public List<Envelope> published(MessageType type) {
        return published.stream().filter(event -> event.type() == type).toList();
    }

    @Override
    public void close() throws Exception {
        for (var bundle : plans.bundles()) {
            bundle.cancel("test ended");
            bundle.done().join();
        }
        for (var plan : plans.plans()) {
            plan.kill("test ended");
            plan.result().join();
        }
        gateway.close();
        facade.close();
        timer.shutdownNow();
        engine.close();
        trace.close();
    }
}
