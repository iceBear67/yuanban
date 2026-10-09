package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.bot.ActionSpec;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.view.BlockPos;
import io.ib67.yuanban.agent.view.Vec3;
import io.ib67.yuanban.bot.MovementMode;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 脚本可以调用的全部 bot 工具。每个工具的参数都可以带 {@code timeoutMs}（不超过工具的上限）与
 * {@code idempotencyKey}（有副作用的调用记入幂等账本）。
 */
public final class ToolCatalog {
    /**
     * observe.blocks 一次最多读取的方块数
     */
    public static final int MAX_BLOCKS = 4096;
    /**
     * jumpTo 目标与玩家的最大水平距离
     */
    public static final double MAX_JUMP_DISTANCE = 6;

    private static final String POINT = "\"x\": {\"$ref\": \"#/$defs/horizontal\"}, \"y\": {\"$ref\": \"#/$defs/vertical\"}, \"z\": {\"$ref\": \"#/$defs/horizontal\"}";
    private static final String MODE = "\"mode\": {\"enum\": [\"WALK\", \"SPRINT\", \"SNEAK\"]}";
    private static final String FACE = "\"face\": {\"enum\": [\"DOWN\", \"UP\", \"NORTH\", \"SOUTH\", \"WEST\", \"EAST\"]}";
    private static final String HAND = "\"hand\": {\"enum\": [\"MAIN_HAND\", \"OFF_HAND\"]}";
    private static final String ENTITY = "\"entityId\": {\"type\": \"integer\"}";
    private static final String BOX = "{\"type\": \"object\", \"required\": [\"x\", \"y\", \"z\"], \"additionalProperties\": false, \"properties\": {" + POINT + "}}";
    /**
     * 每个工具都接受的参数
     */
    private static final String COMMON = "\"timeoutMs\": {\"type\": \"integer\", \"minimum\": 1}, \"idempotencyKey\": {\"type\": \"string\", \"minLength\": 1, \"maxLength\": 128}";

    private static final Duration OBSERVE = Duration.ofSeconds(10);
    private static final Duration OBSERVE_MAX = Duration.ofSeconds(60);
    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final Duration WAIT_MAX = Duration.ofMinutes(10);
    private static final Duration MOVE = Duration.ofSeconds(60);
    private static final Duration MOVE_MAX = Duration.ofMinutes(5);
    private static final Duration INTERACT = Duration.ofSeconds(30);
    private static final Duration INTERACT_MAX = Duration.ofMinutes(2);
    private static final Duration EXTERNAL = Duration.ofSeconds(10);
    private static final Duration EXTERNAL_MAX = Duration.ofSeconds(30);

    private final Map<String, ToolSpec> tools = new LinkedHashMap<>();

    private ToolCatalog() {
    }

    public static ToolCatalog standard() {
        var catalog = new ToolCatalog();
        catalog.observeTools();
        catalog.locomotionTools();
        catalog.interactionTools();
        catalog.externalTools();
        return catalog;
    }

    public @Nullable ToolSpec get(String name) {
        return tools.get(name);
    }

    public Collection<ToolSpec> all() {
        return tools.values();
    }

    /**
     * 给模型与 workspace 看的目录：名称、层、说明、参数 schema、是否改变世界
     */
    public JsonArray describe() {
        var array = new JsonArray();
        for (var tool : tools.values()) {
            array.add(Json.object("name", tool.name(), "layer", tool.layer().wire(), "description", tool.description(),
                    "worldChanging", tool.worldChanging(), "acceptsObserver", tool.acceptsObserver(),
                    "defaultTimeoutMs", tool.defaultTimeout().toMillis(), "maxTimeoutMs", tool.maxTimeout().toMillis(),
                    "args", tool.schema().document()));
        }
        return array;
    }

    private void observeTools() {
        add("observe.playerSnapshot", ToolLayer.OBSERVE, "本地玩家的位置、朝向、生命、饥饿、游戏模式、效果与维度",
                schema(""), false, false, OBSERVE, OBSERVE_MAX,
                context -> context.read(view -> {
                    var player = view.player();
                    return player == null ? ToolResult.of(ResultKind.NOT_IN_WORLD, "not in a world") : ToolResult.ok(Json.tree(player));
                }));
        add("observe.block", ToolLayer.OBSERVE, "一个方块；区块未加载为 CHUNK_NOT_LOADED",
                schema(POINT, "x", "y", "z"), false, false, OBSERVE, OBSERVE_MAX,
                context -> {
                    var pos = point(context.args()).block();
                    return context.readValue(view -> Json.tree(view.block(pos)));
                });
        add("observe.blocks", ToolLayer.OBSERVE, "一个长方体区域内的方块（最多 " + MAX_BLOCKS + " 个）；未加载区块中的方块不读取，列在 unknownChunks 中",
                schema("\"min\": " + BOX + ", \"max\": " + BOX + ", \"onlyNonAir\": {\"type\": \"boolean\"}", "min", "max"),
                false, false, OBSERVE, OBSERVE_MAX, ToolCatalog::blocks);
        add("observe.entities", ToolLayer.OBSERVE, "附近的实体，按距离从近到远；可以按类型与名称过滤",
                schema("\"radius\": {\"type\": \"number\", \"exclusiveMinimum\": 0, \"maximum\": 128}, \"type\": {\"$ref\": \"#/$defs/name\"},"
                        + " \"name\": {\"type\": \"string\", \"maxLength\": 64}, \"limit\": {\"type\": \"integer\", \"minimum\": 1, \"maximum\": 256}"),
                false, false, OBSERVE, OBSERVE_MAX, ToolCatalog::entities);
        add("observe.entity", ToolLayer.OBSERVE, "一个实体；不在模型中时值为 null",
                schema("\"id\": {\"type\": \"integer\"}", "id"), false, false, OBSERVE, OBSERVE_MAX,
                context -> {
                    var id = context.args().get("id").getAsInt();
                    return context.readValue(view -> Json.tree(view.entity(id)));
                });
        add("observe.chatLog", ToolLayer.OBSERVE, "最近的聊天（来源、发送者、文本、签名校验结果）",
                schema("\"limit\": {\"type\": \"integer\", \"minimum\": 1, \"maximum\": 256}"), false, false, OBSERVE, OBSERVE_MAX,
                context -> {
                    var limit = (int) Json.longOr(context.args(), "limit", 20);
                    return context.readValue(view -> Json.object("lines", Json.array(view.chat(limit))));
                });
        add("observe.inventory", ToolLayer.OBSERVE, "玩家物品栏、主手与副手、光标与打开的窗口",
                schema(""), false, false, OBSERVE, OBSERVE_MAX,
                context -> context.readValue(view -> Json.tree(view.inventory())));
        add("observe.world", ToolLayer.OBSERVE, "当前世界：维度、进入原因、时间、天气、难度",
                schema(""), false, false, OBSERVE, OBSERVE_MAX,
                context -> context.read(view -> {
                    var world = view.world();
                    return world == null ? ToolResult.of(ResultKind.NOT_IN_WORLD, "not in a world") : ToolResult.ok(Json.tree(world));
                }));
        add("observe.status", ToolLayer.OBSERVE, "连接与加载状态",
                schema(""), false, false, OBSERVE, OBSERVE_MAX,
                context -> context.readValue(view -> Json.tree(view.status())));
        add("observe.waitFor", ToolLayer.OBSERVE, "等到条件成立；超时为 WAIT_TIMEOUT。条件只能是声明式的 condition，不能是函数",
                schema("\"condition\": {\"$ref\": \"#/$defs/condition\"}", "condition"), false, false, WAIT, WAIT_MAX,
                context -> context.waitFor(Condition.parse(context.args().get("condition"))));
        add("observe.assertState", ToolLayer.OBSERVE, "只读断言；不成立为 ASSERTION_FAILED，值中列出不满足的条件",
                schema("\"condition\": {\"$ref\": \"#/$defs/condition\"}", "condition"), false, false, OBSERVE, OBSERVE_MAX,
                context -> {
                    var condition = Condition.parse(context.args().get("condition"));
                    return context.read(view -> {
                        var failures = condition.failures(view);
                        return failures.isEmpty()
                                ? ToolResult.ok(Json.object("met", true))
                                : ToolResult.of(ResultKind.ASSERTION_FAILED, String.join("; ", failures),
                                Json.object("met", false, "failures", failures));
                    });
                });
    }

    private void locomotionTools() {
        add("locomotion.moveTo", ToolLayer.LOCOMOTION, "寻路并沿路径移动到目标；只表示路径走完，到达与否用 movementReached observer 确认",
                schema(POINT + ", " + MODE + ", \"closeDistance\": {\"type\": \"number\", \"minimum\": 0.1, \"maximum\": 8},"
                        + " \"maxDistance\": {\"type\": \"number\", \"minimum\": 1, \"maximum\": 256}", "x", "y", "z"),
                true, true, MOVE, MOVE_MAX, context -> {
                    var args = context.args();
                    var target = point(args);
                    var maxDistance = Json.doubleOr(args, "maxDistance", Math.min(50, context.policy().maxMoveDistance()));
                    if (maxDistance > context.policy().maxMoveDistance()) {
                        return ToolResult.of(ResultKind.INVALID_ARGUMENT, "maxDistance " + maxDistance
                                + " exceeds the policy limit " + context.policy().maxMoveDistance());
                    }
                    var tooFar = checkDistance(context, target, context.policy().maxMoveDistance());
                    if (tooFar != null) {
                        return tooFar;
                    }
                    return context.action(new ActionSpec.MoveTo(target, mode(args), Json.doubleOr(args, "closeDistance", 0.5), maxDistance));
                });
        add("locomotion.moveForward", ToolLayer.LOCOMOTION, "沿直线移动，不寻路、不跳跃；方向默认取当前水平朝向",
                schema("\"blocks\": {\"type\": \"number\", \"exclusiveMinimum\": 0, \"maximum\": 256}, " + MODE + ","
                        + " \"direction\": {\"type\": \"object\", \"required\": [\"x\", \"z\"], \"additionalProperties\": false,"
                        + " \"properties\": {\"x\": {\"type\": \"number\", \"minimum\": -1, \"maximum\": 1}, \"z\": {\"type\": \"number\", \"minimum\": -1, \"maximum\": 1}}}",
                        "blocks"),
                true, true, MOVE, MOVE_MAX, context -> {
                    var args = context.args();
                    var blocks = args.get("blocks").getAsDouble();
                    if (blocks > context.policy().maxMoveDistance()) {
                        return ToolResult.of(ResultKind.INVALID_ARGUMENT, "blocks " + blocks + " exceeds the policy limit "
                                + context.policy().maxMoveDistance());
                    }
                    var direction = Json.objectOrNull(args, "direction");
                    return context.action(new ActionSpec.MoveForward(blocks, direction == null ? null
                            : new Vec3(direction.get("x").getAsDouble(), 0, direction.get("z").getAsDouble()), mode(args)));
                });
        add("locomotion.jumpTo", ToolLayer.LOCOMOTION, "跳向目标方块的顶面，不寻路；水平距离不超过 " + MAX_JUMP_DISTANCE,
                schema(POINT + ", " + MODE, "x", "y", "z"), true, true, MOVE, MOVE_MAX, context -> {
                    var target = point(context.args());
                    var tooFar = checkDistance(context, target, MAX_JUMP_DISTANCE);
                    return tooFar != null ? tooFar : context.action(new ActionSpec.JumpTo(target, mode(context.args())));
                });
        add("locomotion.rotate", ToolLayer.LOCOMOTION, "设置朝向（度）",
                schema("\"yaw\": {\"type\": \"number\", \"minimum\": -3600, \"maximum\": 3600}, \"pitch\": {\"type\": \"number\", \"minimum\": -90, \"maximum\": 90}",
                        "yaw", "pitch"), true, true, INTERACT, INTERACT_MAX,
                context -> context.action(new ActionSpec.Rotate(context.args().get("yaw").getAsFloat(), context.args().get("pitch").getAsFloat())));
        add("locomotion.lookAt", ToolLayer.LOCOMOTION, "看向一个点（x、y、z）或一个实体（entityId）",
                JsonSchema.of(Condition.withDefs(Json.parseObject("""
                        {"oneOf": [
                          {"type": "object", "required": ["x", "y", "z"], "additionalProperties": false, "properties": {%s, %s}},
                          {"type": "object", "required": ["entityId"], "additionalProperties": false, "properties": {%s, %s}}
                        ]}
                        """.formatted(POINT, COMMON, ENTITY, COMMON)))),
                true, true, INTERACT, INTERACT_MAX, context -> {
                    var args = context.args();
                    return context.action(args.has("entityId")
                            ? new ActionSpec.LookAtEntity(args.get("entityId").getAsInt())
                            : new ActionSpec.LookAtPoint(point(args)));
                });
    }

    private void interactionTools() {
        add("interaction.breakBlock", ToolLayer.INTERACTION, "挖掘一个方块；默认带 blockBroken observer，值为 {broken, original, changedTo}",
                schema(POINT + ", " + FACE, "x", "y", "z"), true, true, INTERACT, INTERACT_MAX,
                context -> context.action(new ActionSpec.BreakBlock(point(context.args()).block(), face(context.args()))));
        add("interaction.placeBlock", ToolLayer.INTERACTION, "对着 (x, y, z) 的 face 面放置手中的方块；放置结果用 blockAt observer 确认",
                schema(POINT + ", " + FACE + ", " + HAND, "x", "y", "z", "face"), true, true, INTERACT, INTERACT_MAX,
                context -> context.action(new ActionSpec.PlaceBlock(point(context.args()).block(),
                        Objects.requireNonNull(face(context.args())), hand(context.args()))));
        add("interaction.useItemOn", ToolLayer.INTERACTION, "对方块使用手中的物品",
                schema(POINT + ", " + FACE + ", " + HAND + ", \"cursor\": {\"type\": \"object\", \"required\": [\"x\", \"y\", \"z\"], \"additionalProperties\": false,"
                        + " \"properties\": {\"x\": {\"type\": \"number\", \"minimum\": 0, \"maximum\": 1}, \"y\": {\"type\": \"number\", \"minimum\": 0, \"maximum\": 1},"
                        + " \"z\": {\"type\": \"number\", \"minimum\": 0, \"maximum\": 1}}}", "x", "y", "z", "face"),
                true, true, INTERACT, INTERACT_MAX, context -> {
                    var args = context.args();
                    var cursor = Json.objectOrNull(args, "cursor");
                    return context.action(new ActionSpec.UseItemOn(point(args).block(), Objects.requireNonNull(face(args)), hand(args),
                            cursor == null ? new Vec3(0.5, 0.5, 0.5) : new Vec3(cursor.get("x").getAsDouble(),
                                    cursor.get("y").getAsDouble(), cursor.get("z").getAsDouble())));
                });
        add("interaction.useItem", ToolLayer.INTERACTION, "使用手中的物品；holdTicks 给出时保持使用这么多 tick",
                schema(HAND + ", \"holdTicks\": {\"type\": \"integer\", \"minimum\": 1, \"maximum\": 1200}"), true, true, INTERACT, INTERACT_MAX,
                context -> {
                    var args = context.args();
                    return context.action(new ActionSpec.UseItem(hand(args), args.has("holdTicks") ? args.get("holdTicks").getAsInt() : null));
                });
        add("interaction.attack", ToolLayer.INTERACTION, "攻击实体",
                schema(ENTITY, "entityId"), true, true, INTERACT, INTERACT_MAX,
                context -> context.action(new ActionSpec.Attack(context.args().get("entityId").getAsInt())));
        add("interaction.interact", ToolLayer.INTERACTION, "与实体交互（右键）",
                schema(ENTITY + ", " + HAND, "entityId"), true, true, INTERACT, INTERACT_MAX,
                context -> context.action(new ActionSpec.Interact(context.args().get("entityId").getAsInt(), hand(context.args()))));
        add("interaction.selectHotbar", ToolLayer.INTERACTION, "选择快捷栏槽位（0 到 8）",
                schema("\"slot\": {\"type\": \"integer\", \"minimum\": 0, \"maximum\": 8}", "slot"), true, true, INTERACT, INTERACT_MAX,
                context -> context.action(new ActionSpec.SelectHotbar(context.args().get("slot").getAsInt())));
        add("interaction.swapHands", ToolLayer.INTERACTION, "交换主手与副手的物品",
                schema(""), true, true, INTERACT, INTERACT_MAX, context -> context.action(new ActionSpec.SwapHands()));
        add("interaction.click", ToolLayer.INTERACTION, "点击打开的窗口（没有时为物品栏）的槽位：left、right、shift 或 swapHotbar（与 hotbarSlot 交换）",
                schema("\"slot\": {\"type\": \"integer\", \"minimum\": 0, \"maximum\": 255}, \"mode\": {\"enum\": [\"left\", \"right\", \"shift\", \"swapHotbar\"]},"
                        + " \"hotbarSlot\": {\"type\": \"integer\", \"minimum\": 0, \"maximum\": 8}", "slot", "mode"),
                true, true, INTERACT, INTERACT_MAX, context -> {
                    var args = context.args();
                    var mode = Json.requireString(args, "mode");
                    if (mode.equals("swapHotbar") && !args.has("hotbarSlot")) {
                        return ToolResult.of(ResultKind.INVALID_ARGUMENT, "swapHotbar requires hotbarSlot");
                    }
                    return context.action(new ActionSpec.Click(args.get("slot").getAsInt(), mode,
                            args.has("hotbarSlot") ? args.get("hotbarSlot").getAsInt() : null));
                });
        add("interaction.clickButton", ToolLayer.INTERACTION, "点击窗口中的按钮",
                schema("\"buttonId\": {\"type\": \"integer\", \"minimum\": 0, \"maximum\": 255}", "buttonId"), true, true, INTERACT, INTERACT_MAX,
                context -> context.action(new ActionSpec.ClickButton(context.args().get("buttonId").getAsInt())));
        add("interaction.closeWindow", ToolLayer.INTERACTION, "关闭打开的窗口",
                schema(""), true, true, INTERACT, INTERACT_MAX, context -> context.action(new ActionSpec.CloseWindow()));
        add("interaction.respawn", ToolLayer.INTERACTION, "死亡后请求重生；是否重生用 waitFor 观察",
                schema(""), true, true, INTERACT, INTERACT_MAX, context -> context.action(new ActionSpec.Respawn()));
    }

    private void externalTools() {
        add("external.chat", ToolLayer.EXTERNAL, "发送聊天消息（高风险）",
                schema("\"message\": {\"type\": \"string\", \"minLength\": 1, \"maxLength\": 256}", "message"),
                true, true, EXTERNAL, EXTERNAL_MAX,
                context -> context.action(new ActionSpec.Chat(Json.requireString(context.args(), "message"))));
        add("external.command", ToolLayer.EXTERNAL, "执行命令，不带开头的斜线（高风险，只允许策略列出的命令）",
                schema("\"command\": {\"type\": \"string\", \"minLength\": 1, \"maxLength\": 1024, \"pattern\": \"^[^/]\"}", "command"),
                true, true, EXTERNAL, EXTERNAL_MAX,
                context -> context.action(new ActionSpec.Command(Json.requireString(context.args(), "command"))));
        add("external.drop", ToolLayer.EXTERNAL, "丢出手中的物品（高风险）",
                schema("\"wholeStack\": {\"type\": \"boolean\"}", "wholeStack"), true, true, EXTERNAL, EXTERNAL_MAX,
                context -> context.action(new ActionSpec.Drop(context.args().get("wholeStack").getAsBoolean())));
        add("external.dropSlot", ToolLayer.EXTERNAL, "丢出窗口槽位中的物品（高风险）",
                schema("\"slot\": {\"type\": \"integer\", \"minimum\": 0, \"maximum\": 255}, \"wholeStack\": {\"type\": \"boolean\"}", "slot", "wholeStack"),
                true, true, EXTERNAL, EXTERNAL_MAX,
                context -> context.action(new ActionSpec.DropSlot(context.args().get("slot").getAsInt(), context.args().get("wholeStack").getAsBoolean())));
        add("external.configure", ToolLayer.EXTERNAL, "修改 agent 的运行时配置（高风险）",
                schema("\"key\": {\"type\": \"string\", \"maxLength\": 64}, \"value\": true", "key", "value"),
                false, true, EXTERNAL, EXTERNAL_MAX, context -> {
                    var key = Json.requireString(context.args(), "key");
                    if (!context.configurator().keys().contains(key)) {
                        return ToolResult.of(ResultKind.INVALID_ARGUMENT, "unknown configuration key " + key
                                + ", known keys: " + context.configurator().keys());
                    }
                    try {
                        context.configurator().set(key, context.args().get("value"));
                    } catch (IllegalArgumentException e) {
                        return ToolResult.of(ResultKind.INVALID_ARGUMENT, e.getMessage());
                    }
                    return ToolResult.ok(Json.object("key", key, "value", context.args().get("value")));
                });
        add("unsafe.send", ToolLayer.UNSAFE, "发送任意发往服务端的 MCProtocolLib 数据包（只在 unsafe profile 中可用）：packet 为完整类名，args 为构造方法参数",
                schema("\"packet\": {\"type\": \"string\", \"maxLength\": 256}, \"args\": {\"type\": \"array\", \"maxItems\": 16}", "packet", "args"),
                true, true, EXTERNAL, EXTERNAL_MAX, context -> {
                    var args = context.args();
                    MinecraftPacket packet;
                    try {
                        packet = PacketFactory.create(Json.requireString(args, "packet"), args.getAsJsonArray("args"));
                    } catch (IllegalArgumentException e) {
                        return ToolResult.of(ResultKind.INVALID_ARGUMENT, e.getMessage());
                    }
                    var result = context.action(new ActionSpec.Send(packet));
                    var value = Json.object("packet", packet.getClass().getName(), "direction", "serverbound",
                            "summary", packet.toString(), "result", result.value());
                    return new ToolResult(result.ok(), result.kind(), result.reason(), result.actionId(), result.elapsedMs(),
                            value, result.observation(), result.error(), result.stackRef());
                });
    }

    private void add(String name, ToolLayer layer, String description, JsonSchema schema, boolean worldChanging,
                     boolean sideEffect, Duration defaultTimeout, Duration maxTimeout, ToolSpec.ToolExecutor executor) {
        var acceptsObserver = worldChanging && layer != ToolLayer.EXTERNAL && layer != ToolLayer.UNSAFE;
        tools.put(name, new ToolSpec(name, layer, description, schema, worldChanging, sideEffect, acceptsObserver,
                defaultTimeout, maxTimeout, executor));
    }

    /**
     * @param properties 属性定义（不带外层花括号），可以为空
     */
    private static JsonSchema schema(String properties, String... required) {
        var props = properties.isEmpty() ? COMMON : properties + ", " + COMMON;
        var document = Json.parseObject("{\"type\": \"object\", \"additionalProperties\": false, \"properties\": {" + props + "}}");
        var requiredArray = new JsonArray();
        for (var key : required) {
            requiredArray.add(key);
        }
        document.add("required", requiredArray);
        return JsonSchema.of(Condition.withDefs(document));
    }

    private static Vec3 point(JsonObject args) {
        return new Vec3(args.get("x").getAsDouble(), args.get("y").getAsDouble(), args.get("z").getAsDouble());
    }

    private static @Nullable MovementMode mode(JsonObject args) {
        var mode = Json.string(args, "mode");
        return mode == null ? null : MovementMode.valueOf(mode);
    }

    private static @Nullable Direction face(JsonObject args) {
        var face = Json.string(args, "face");
        return face == null ? null : Direction.valueOf(face);
    }

    private static Hand hand(JsonObject args) {
        var hand = Json.string(args, "hand");
        return hand == null ? Hand.MAIN_HAND : Hand.valueOf(hand);
    }

    /**
     * @return 目标与玩家的水平距离超过 limit 时的结果；没有超过时为 null
     */
    private static @Nullable ToolResult checkDistance(ToolContext context, Vec3 target, double limit) {
        var check = context.read(view -> {
            var player = view.player();
            if (player == null) {
                return ToolResult.of(ResultKind.NOT_IN_WORLD, "not in a world");
            }
            var distance = player.position().horizontalDistance(target);
            return distance > limit
                    ? ToolResult.of(ResultKind.INVALID_ARGUMENT, "target is " + distance + " blocks away, the limit is " + limit)
                    : ToolResult.ok(null);
        });
        return check.ok() ? null : check;
    }

    private static ToolResult blocks(ToolContext context) {
        var args = context.args();
        var min = point(args.getAsJsonObject("min")).block();
        var max = point(args.getAsJsonObject("max")).block();
        var lower = new BlockPos(Math.min(min.x(), max.x()), Math.min(min.y(), max.y()), Math.min(min.z(), max.z()));
        var upper = new BlockPos(Math.max(min.x(), max.x()), Math.max(min.y(), max.y()), Math.max(min.z(), max.z()));
        var volume = (long) (upper.x() - lower.x() + 1) * (upper.y() - lower.y() + 1) * (upper.z() - lower.z() + 1);
        if (volume > MAX_BLOCKS) {
            return ToolResult.of(ResultKind.INVALID_ARGUMENT, "region has " + volume + " blocks, the limit is " + MAX_BLOCKS);
        }
        var onlyNonAir = Json.booleanOr(args, "onlyNonAir", false);
        return context.read(view -> {
            if (!view.status().inWorld()) {
                return ToolResult.of(ResultKind.NOT_IN_WORLD, "not in a world");
            }
            var blocks = new ArrayList<Object>();
            var unknown = new HashSet<String>();
            for (var x = lower.x(); x <= upper.x(); x++) {
                for (var z = lower.z(); z <= upper.z(); z++) {
                    var column = new BlockPos(x, 0, z);
                    if (!view.chunkLoaded(column.chunkX(), column.chunkZ())) {
                        unknown.add(column.chunkX() + "," + column.chunkZ());
                        continue;
                    }
                    for (var y = lower.y(); y <= upper.y(); y++) {
                        var block = view.block(new BlockPos(x, y, z));
                        if (!onlyNonAir || !block.air()) {
                            blocks.add(block);
                        }
                    }
                }
            }
            return ToolResult.ok(Json.object("blocks", Json.array(blocks), "unknownChunks", Json.array(unknown),
                    "complete", unknown.isEmpty()));
        });
    }

    private static ToolResult entities(ToolContext context) {
        var args = context.args();
        var radius = Json.doubleOr(args, "radius", 32);
        var type = Json.string(args, "type");
        var name = Json.string(args, "name");
        var limit = (int) Json.longOr(args, "limit", 32);
        return context.readValue(view -> {
            var matched = view.entities().stream()
                    .filter(entity -> entity.distance() >= 0 && entity.distance() <= radius)
                    .filter(entity -> type == null || type.equals(entity.type()))
                    .filter(entity -> name == null || name.equals(entity.name()))
                    .toList();
            return Json.object("entities", Json.array(matched.subList(0, Math.min(limit, matched.size()))),
                    "total", matched.size(), "truncated", matched.size() > limit);
        });
    }
}
