package io.ib67.yuanban.agent.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.message.MessageType;
import io.ib67.yuanban.agent.tool.BudgetScope;
import io.ib67.yuanban.agent.workspace.Checkpoint;
import io.ib67.yuanban.agent.workspace.Module;
import io.ib67.yuanban.agent.workspace.ModuleRef;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 创建计划与 bundle，分配编号与重放用的 planKey，记录正在运行的计划，处理脚本发起的 bundle 操作。
 */
public final class PlanManager {
    /**
     * supervisor 发给 bundle 中其它计划的消息类型
     */
    private static final Set<MessageType> SENDABLE = Set.of(MessageType.GOAL, MessageType.FOCUS, MessageType.POLICY,
            MessageType.USER_REPLY);

    private final PlanServices services;
    private final String idPrefix;
    private final Map<String, PlanActor> plans = new ConcurrentHashMap<>();
    private final Map<String, PlanBundle> bundles = new ConcurrentHashMap<>();
    private final AtomicLong planIds = new AtomicLong();
    private final AtomicLong bundleIds = new AtomicLong();
    private final Map<String, AtomicLong> keyOrdinals = new ConcurrentHashMap<>();

    /**
     * @param idPrefix 计划与 bundle 编号的前缀：agent 为空字符串，模块测试为 {@code t<n>-}，使测试的检查点与账本不与真实计划混在一起
     */
    public PlanManager(PlanServices services, String idPrefix) {
        this.services = services;
        this.idPrefix = idPrefix;
    }

    public PlanServices services() {
        return services;
    }

    /**
     * 创建计划（不启动）
     *
     * @param originKind {@code foreground} 或 {@code background}
     * @param lineage    幂等账本的归属；为 null 时使用新计划的编号
     * @throws IllegalArgumentException 模块不能以这个角色启动，或者输入不符合 inputSchema
     */
    public PlanActor create(Module module, PlanRole role, String run, String originKind, JsonElement input,
                            BudgetScope parentBudget, @Nullable PlanBundle bundle, @Nullable Checkpoint restore,
                            @Nullable String lineage) {
        var id = idPrefix + "plan-" + planIds.incrementAndGet();
        var keyBase = module.ref() + "/" + role.wire();
        var planKey = keyBase + "#" + keyOrdinals.computeIfAbsent(keyBase, _ -> new AtomicLong()).incrementAndGet();
        return new PlanActor(this, id, module, role, run, originKind, planKey, lineage == null ? id : lineage,
                input, bundle, restore, parentBudget);
    }

    /**
     * 计划启动时登记，结束时移除
     */
    void started(PlanActor actor) {
        plans.put(actor.id(), actor);
        actor.result().whenComplete((_, _) -> plans.remove(actor.id()));
    }

    /**
     * 创建并启动一个 bundle
     *
     * @param owner 嵌套 bundle 的上层计划（委托 lease）；前台 bundle 为 null
     */
    public PlanBundle startBundle(BundleSpec spec, String run, @Nullable PlanActor owner, BudgetScope parentBudget,
                                  String originKind) {
        var bundle = new PlanBundle(this, idPrefix + "bundle-" + bundleIds.incrementAndGet(), spec, run, owner, parentBudget, originKind);
        bundles.put(bundle.id(), bundle);
        try {
            bundle.start();
        } catch (RuntimeException e) {
            bundles.remove(bundle.id());
            throw e;
        }
        return bundle;
    }

    public Module load(ModuleRef ref) {
        return services.workspace().load(ref);
    }

    public @Nullable PlanActor plan(String id) {
        return plans.get(id);
    }

    public Collection<PlanActor> plans() {
        return List.copyOf(plans.values());
    }

    public @Nullable PlanBundle bundle(String id) {
        return bundles.get(id);
    }

    public Collection<PlanBundle> bundles() {
        return List.copyOf(bundles.values());
    }

    void bundleEnded(PlanBundle bundle) {
        bundles.remove(bundle.id());
    }

    /**
     * 脚本的 {@code api.bundle.*}
     */
    CompletableFuture<JsonElement> bundleOp(PlanActor caller, String op, JsonObject args) {
        try {
            if (op.equals("run")) {
                if (caller.role() != PlanRole.PRIMARY && caller.role() != PlanRole.RECOVERY) {
                    throw new IllegalStateException("only primary and recovery plans can start bundles, " + caller.id()
                            + " is " + caller.role().wire());
                }
                var bundle = startBundle(BundleSpec.fromJson(args, null), caller.run(), caller, caller.budget(), "foreground");
                return bundle.done().thenApply(BundleResult::toJson);
            }
            var bundle = caller.bundle();
            if (bundle == null) {
                throw new IllegalStateException(caller.id() + " does not belong to a bundle");
            }
            if (op.equals("status")) {
                return CompletableFuture.completedFuture(bundle.describe());
            }
            if (op.equals("send")) {
                var type = MessageType.fromWire(Json.requireString(args, "type"));
                if (!SENDABLE.contains(type)) {
                    throw new IllegalArgumentException("scripts can only send " + SENDABLE + ", got " + type);
                }
                var payload = Json.objectOrNull(args, "payload");
                var offer = bundle.send(Json.requireString(args, "target"), type, payload == null ? new JsonObject() : payload,
                        Json.string(args, "dedupeKey"), Json.string(args, "replyTo"), caller.source());
                return CompletableFuture.completedFuture(Json.object("delivery", offer.name()));
            }
            if (caller.role() != PlanRole.SUPERVISOR || bundle.supervisor() != caller) {
                throw new IllegalStateException(op + " can only be used by the supervisor of " + bundle.id());
            }
            return switch (op) {
                case "pausePrimary" -> {
                    bundle.pausePrimary(reason(args, "paused by supervisor"));
                    yield CompletableFuture.completedFuture(Json.object("paused", true));
                }
                case "resumePrimary" -> {
                    bundle.resumePrimary();
                    yield CompletableFuture.completedFuture(Json.object("resumed", true));
                }
                case "preflight" -> {
                    var module = Json.string(args, "module");
                    var ref = module != null ? ModuleRef.parse(module) : services.profile().preflightModule();
                    if (ref == null) {
                        throw new IllegalArgumentException("no preflight module given and the profile has none");
                    }
                    yield bundle.preflight(ref, input(args)).thenApply(PlanResult::toJson);
                }
                case "recover" -> bundle.recover(ModuleRef.parse(Json.requireString(args, "module")), input(args))
                        .thenApply(PlanResult::toJson);
                case "replan" -> {
                    bundle.replan(reason(args, "replan requested by supervisor"), args.get("data"));
                    yield CompletableFuture.completedFuture(Json.object("replanning", true));
                }
                case "cancel" -> {
                    bundle.cancel(reason(args, "cancelled by supervisor"));
                    yield CompletableFuture.completedFuture(Json.object("cancelled", true));
                }
                default -> throw new IllegalArgumentException("unknown bundle operation " + op);
            };
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static String reason(JsonObject args, String fallback) {
        var reason = Json.string(args, "reason");
        return reason == null ? fallback : reason;
    }

    private static JsonElement input(JsonObject args) {
        var input = args.get("input");
        return input == null ? JsonNull.INSTANCE : input;
    }
}
