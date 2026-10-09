package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.view.BlockPos;
import io.ib67.yuanban.agent.view.GameView;
import io.ib67.yuanban.agent.view.ItemView;
import io.ib67.yuanban.agent.view.Vec3;
import io.ib67.yuanban.bot.ChunkNotLoadedException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 声明式的状态条件，用于 {@code waitFor}、{@code assertState} 与 supervisor 的检查。
 * 实时模式下在 bot 调度线程上求值，所以条件只能是数据，不能是脚本函数。每个条件是只有一个键的 JSON 对象，见 {@link #SCHEMA}。
 */
public sealed interface Condition {
    /**
     * 条件的 JSON Schema 定义，工具参数中以 {@code {"$ref": "#/$defs/condition"}} 引用，放在 {@code $defs.condition}
     */
    JsonObject SCHEMA_DEF = Json.parseObject("""
            {
              "type": "object", "minProperties": 1, "maxProperties": 1,
              "properties": {
                "all": {"type": "array", "items": {"$ref": "#/$defs/condition"}, "minItems": 1, "maxItems": 32},
                "any": {"type": "array", "items": {"$ref": "#/$defs/condition"}, "minItems": 1, "maxItems": 32},
                "not": {"$ref": "#/$defs/condition"},
                "near": {"$ref": "#/$defs/near"},
                "far": {"$ref": "#/$defs/near"},
                "onGround": {"type": "boolean"},
                "loaded": {"type": "boolean"},
                "inWorld": {"type": "boolean"},
                "dead": {"type": "boolean"},
                "health": {"$ref": "#/$defs/compare"},
                "food": {"$ref": "#/$defs/compare"},
                "chunkLoaded": {"type": "object", "required": ["x", "z"], "additionalProperties": false,
                  "properties": {"x": {"$ref": "#/$defs/horizontal"}, "z": {"$ref": "#/$defs/horizontal"}}},
                "block": {"type": "object", "required": ["x", "y", "z"], "additionalProperties": false,
                  "properties": {"x": {"$ref": "#/$defs/horizontal"}, "y": {"$ref": "#/$defs/vertical"}, "z": {"$ref": "#/$defs/horizontal"},
                    "is": {"oneOf": [{"$ref": "#/$defs/name"}, {"type": "array", "items": {"$ref": "#/$defs/name"}, "minItems": 1, "maxItems": 64}]},
                    "air": {"type": "boolean"}, "blocksMotion": {"type": "boolean"}, "liquid": {"type": "boolean"}}},
                "entityNear": {"type": "object", "required": ["radius"], "additionalProperties": false,
                  "properties": {"type": {"$ref": "#/$defs/name"}, "name": {"type": "string", "maxLength": 64},
                    "radius": {"type": "number", "exclusiveMinimum": 0, "maximum": 128},
                    "count": {"type": "integer", "minimum": 1, "maximum": 1024}}},
                "entityExists": {"type": "object", "required": ["id"], "additionalProperties": false,
                  "properties": {"id": {"type": "integer"}}},
                "windowOpen": {"oneOf": [{"type": "boolean"}, {"type": "object", "required": ["type"], "additionalProperties": false,
                  "properties": {"type": {"type": "string", "maxLength": 64}}}]},
                "holding": {"type": "object", "required": ["item"], "additionalProperties": false,
                  "properties": {"item": {"$ref": "#/$defs/name"}}},
                "hasItem": {"type": "object", "required": ["item"], "additionalProperties": false,
                  "properties": {"item": {"$ref": "#/$defs/name"}, "count": {"type": "integer", "minimum": 1, "maximum": 2304}}},
                "chatContains": {"type": "object", "required": ["text"], "additionalProperties": false,
                  "properties": {"text": {"type": "string", "minLength": 1, "maxLength": 256}, "last": {"type": "integer", "minimum": 1, "maximum": 256}}},
                "dimension": {"$ref": "#/$defs/name"},
                "gameMode": {"enum": ["SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"]}
              },
              "additionalProperties": false
            }
            """);

    /**
     * 条件引用的其它定义
     */
    JsonObject SUPPORT_DEFS = Json.parseObject("""
            {
              "horizontal": {"type": "number", "minimum": -30000000, "maximum": 30000000},
              "vertical": {"type": "number", "minimum": -2048, "maximum": 2048},
              "name": {"type": "string", "pattern": "^[a-z0-9_.-]+:[a-z0-9_./-]+$", "maxLength": 128},
              "near": {"type": "object", "required": ["x", "y", "z", "distance"], "additionalProperties": false,
                "properties": {"x": {"$ref": "#/$defs/horizontal"}, "y": {"$ref": "#/$defs/vertical"}, "z": {"$ref": "#/$defs/horizontal"},
                  "distance": {"type": "number", "minimum": 0, "maximum": 1024}}},
              "compare": {"type": "object", "minProperties": 1, "additionalProperties": false,
                "properties": {"lt": {"type": "number"}, "lte": {"type": "number"}, "gt": {"type": "number"},
                  "gte": {"type": "number"}, "eq": {"type": "number"}}}
            }
            """);

    /**
     * 单独校验一个条件的 schema
     */
    JsonSchema SCHEMA = JsonSchema.of(withDefs(Json.parseObject("{\"$ref\": \"#/$defs/condition\"}")));

    boolean test(GameView view);

    /**
     * 不满足的叶子条件的说明，用于 {@code ASSERTION_FAILED}
     */
    default List<String> failures(GameView view) {
        return test(view) ? List.of() : List.of(describe());
    }

    String describe();

    /**
     * 把条件需要的 {@code $defs} 加入一个 schema 文档
     */
    static JsonObject withDefs(JsonObject schema) {
        var defs = schema.has("$defs") ? schema.getAsJsonObject("$defs") : new JsonObject();
        defs.add("condition", SCHEMA_DEF.deepCopy());
        for (var entry : SUPPORT_DEFS.entrySet()) {
            defs.add(entry.getKey(), entry.getValue().deepCopy());
        }
        schema.add("$defs", defs);
        return schema;
    }

    /**
     * @throws io.ib67.yuanban.agent.json.SchemaViolationException 不符合 schema
     */
    static Condition parse(JsonElement json) {
        SCHEMA.require(json, "condition");
        return parseValid(json.getAsJsonObject());
    }

    private static Condition parseValid(JsonObject json) {
        var entry = json.entrySet().iterator().next();
        var value = entry.getValue();
        return switch (entry.getKey()) {
            case "all" -> new All(list(value.getAsJsonArray()));
            case "any" -> new Any(list(value.getAsJsonArray()));
            case "not" -> new Not(parseValid(value.getAsJsonObject()));
            case "near" -> near(value.getAsJsonObject(), true);
            case "far" -> near(value.getAsJsonObject(), false);
            case "onGround" -> new Flag("onGround", value.getAsBoolean());
            case "loaded" -> new Flag("loaded", value.getAsBoolean());
            case "inWorld" -> new Flag("inWorld", value.getAsBoolean());
            case "dead" -> new Flag("dead", value.getAsBoolean());
            case "health" -> new Compare("health", compare(value.getAsJsonObject()));
            case "food" -> new Compare("food", compare(value.getAsJsonObject()));
            case "chunkLoaded" -> {
                var object = value.getAsJsonObject();
                var pos = new Vec3(object.get("x").getAsDouble(), 0, object.get("z").getAsDouble()).block();
                yield new ChunkLoaded(pos.chunkX(), pos.chunkZ());
            }
            case "block" -> block(value.getAsJsonObject());
            case "entityNear" -> {
                var object = value.getAsJsonObject();
                yield new EntityNear(Json.string(object, "type"), Json.string(object, "name"),
                        object.get("radius").getAsDouble(), (int) Json.longOr(object, "count", 1));
            }
            case "entityExists" -> new EntityExists(value.getAsJsonObject().get("id").getAsInt());
            case "windowOpen" -> value.isJsonPrimitive()
                    ? new WindowOpen(value.getAsBoolean(), null)
                    : new WindowOpen(true, Json.requireString(value.getAsJsonObject(), "type"));
            case "holding" -> new Holding(Json.requireString(value.getAsJsonObject(), "item"));
            case "hasItem" -> new HasItem(Json.requireString(value.getAsJsonObject(), "item"),
                    (int) Json.longOr(value.getAsJsonObject(), "count", 1));
            case "chatContains" -> new ChatContains(Json.requireString(value.getAsJsonObject(), "text"),
                    (int) Json.longOr(value.getAsJsonObject(), "last", 20));
            case "dimension" -> new Dimension(value.getAsString());
            case "gameMode" -> new GameModeIs(value.getAsString());
            default -> throw new IllegalStateException("condition " + entry.getKey() + " passed the schema but is not handled");
        };
    }

    private static List<Condition> list(JsonArray array) {
        var result = new ArrayList<Condition>();
        for (var element : array) {
            result.add(parseValid(element.getAsJsonObject()));
        }
        return result;
    }

    private static Near near(JsonObject object, boolean within) {
        return new Near(new Vec3(object.get("x").getAsDouble(), object.get("y").getAsDouble(), object.get("z").getAsDouble()),
                object.get("distance").getAsDouble(), within);
    }

    private static List<Bound> compare(JsonObject object) {
        var bounds = new ArrayList<Bound>();
        for (var entry : object.entrySet()) {
            bounds.add(new Bound(entry.getKey(), entry.getValue().getAsDouble()));
        }
        return bounds;
    }

    private static BlockIs block(JsonObject object) {
        var pos = new Vec3(object.get("x").getAsDouble(), object.get("y").getAsDouble(), object.get("z").getAsDouble()).block();
        Set<String> names = null;
        var is = object.get("is");
        if (is != null) {
            if (is.isJsonArray()) {
                var collected = new HashSet<String>();
                is.getAsJsonArray().forEach(element -> collected.add(element.getAsString()));
                names = Set.copyOf(collected);
            } else {
                names = Set.of(is.getAsString());
            }
        }
        return new BlockIs(pos, names, bool(object, "air"), bool(object, "blocksMotion"), bool(object, "liquid"));
    }

    private static @Nullable Boolean bool(JsonObject object, String key) {
        var element = object.get(key);
        return element == null ? null : element.getAsBoolean();
    }

    record All(List<Condition> conditions) implements Condition {
        @Override
        public boolean test(GameView view) {
            return conditions.stream().allMatch(condition -> condition.test(view));
        }

        @Override
        public List<String> failures(GameView view) {
            return conditions.stream().flatMap(condition -> condition.failures(view).stream()).toList();
        }

        @Override
        public String describe() {
            return "all of " + conditions.stream().map(Condition::describe).toList();
        }
    }

    record Any(List<Condition> conditions) implements Condition {
        @Override
        public boolean test(GameView view) {
            return conditions.stream().anyMatch(condition -> condition.test(view));
        }

        @Override
        public String describe() {
            return "any of " + conditions.stream().map(Condition::describe).toList();
        }
    }

    record Not(Condition condition) implements Condition {
        @Override
        public boolean test(GameView view) {
            return !condition.test(view);
        }

        @Override
        public String describe() {
            return "not " + condition.describe();
        }
    }

    /**
     * @param within true：玩家脚部与目标的距离不大于 distance；false：大于 distance。不在世界中时都不满足
     */
    record Near(Vec3 target, double distance, boolean within) implements Condition {
        @Override
        public boolean test(GameView view) {
            var player = view.player();
            if (player == null) {
                return false;
            }
            var actual = player.position().distance(target);
            return within ? actual <= distance : actual > distance;
        }

        @Override
        public String describe() {
            return (within ? "within " : "farther than ") + distance + " of " + target;
        }
    }

    record Flag(String name, boolean expected) implements Condition {
        @Override
        public boolean test(GameView view) {
            var player = view.player();
            var actual = switch (name) {
                case "onGround" -> player != null && player.onGround();
                case "loaded" -> view.status().loaded();
                case "inWorld" -> view.status().inWorld();
                case "dead" -> player != null && player.dead();
                default -> throw new IllegalStateException("unknown flag " + name);
            };
            return actual == expected;
        }

        @Override
        public String describe() {
            return name + " is " + expected;
        }
    }

    record Bound(String op, double value) {
        boolean test(double actual) {
            return switch (op) {
                case "lt" -> actual < value;
                case "lte" -> actual <= value;
                case "gt" -> actual > value;
                case "gte" -> actual >= value;
                case "eq" -> actual == value;
                default -> throw new IllegalStateException("unknown comparison " + op);
            };
        }
    }

    record Compare(String field, List<Bound> bounds) implements Condition {
        @Override
        public boolean test(GameView view) {
            var player = view.player();
            if (player == null) {
                return false;
            }
            double actual = switch (field) {
                case "health" -> player.health();
                case "food" -> player.food();
                default -> throw new IllegalStateException("unknown field " + field);
            };
            return bounds.stream().allMatch(bound -> bound.test(actual));
        }

        @Override
        public String describe() {
            return field + " " + bounds;
        }
    }

    record ChunkLoaded(int chunkX, int chunkZ) implements Condition {
        @Override
        public boolean test(GameView view) {
            return view.chunkLoaded(chunkX, chunkZ);
        }

        @Override
        public String describe() {
            return "chunk " + chunkX + ", " + chunkZ + " loaded";
        }
    }

    /**
     * 所在区块没有加载或不在世界中时不满足
     */
    record BlockIs(BlockPos pos, @Nullable Set<String> names, @Nullable Boolean air, @Nullable Boolean blocksMotion,
                   @Nullable Boolean liquid) implements Condition {
        @Override
        public boolean test(GameView view) {
            if (!view.status().inWorld() || !view.chunkLoaded(pos.chunkX(), pos.chunkZ())) {
                return false;
            }
            try {
                var block = view.block(pos);
                return (names == null || names.contains(block.name()))
                        && (air == null || air == block.air())
                        && (blocksMotion == null || blocksMotion == block.blocksMotion())
                        && (liquid == null || liquid == block.liquid());
            } catch (ChunkNotLoadedException e) {
                return false;
            }
        }

        @Override
        public String describe() {
            return "block at " + pos + (names == null ? "" : " is " + names) + (air == null ? "" : " air=" + air)
                    + (blocksMotion == null ? "" : " blocksMotion=" + blocksMotion) + (liquid == null ? "" : " liquid=" + liquid);
        }
    }

    record EntityNear(@Nullable String type, @Nullable String name, double radius, int count) implements Condition {
        @Override
        public boolean test(GameView view) {
            var found = view.entities().stream()
                    .filter(entity -> entity.distance() >= 0 && entity.distance() <= radius)
                    .filter(entity -> type == null || type.equals(entity.type()))
                    .filter(entity -> name == null || name.equals(entity.name()))
                    .count();
            return found >= count;
        }

        @Override
        public String describe() {
            return "at least " + count + " entities" + (type == null ? "" : " of " + type)
                    + (name == null ? "" : " named " + name) + " within " + radius;
        }
    }

    record EntityExists(int id) implements Condition {
        @Override
        public boolean test(GameView view) {
            return view.entity(id) != null;
        }

        @Override
        public String describe() {
            return "entity " + id + " exists";
        }
    }

    /**
     * @param type 为 null 时不限制窗口类型
     */
    record WindowOpen(boolean expected, @Nullable String type) implements Condition {
        @Override
        public boolean test(GameView view) {
            var window = view.inventory().openWindow();
            if (!expected) {
                return window == null;
            }
            return window != null && (type == null || type.equals(window.type()));
        }

        @Override
        public String describe() {
            return expected ? "window " + (type == null ? "" : type + " ") + "open" : "no window open";
        }
    }

    record Holding(String item) implements Condition {
        @Override
        public boolean test(GameView view) {
            var held = view.inventory().mainHand();
            return held != null && item.equals(held.item());
        }

        @Override
        public String describe() {
            return "holding " + item;
        }
    }

    record HasItem(String item, int count) implements Condition {
        @Override
        public boolean test(GameView view) {
            return view.inventory().items().stream().filter(stack -> item.equals(stack.item()))
                    .mapToInt(ItemView::count).sum() >= count;
        }

        @Override
        public String describe() {
            return "has " + count + " " + item;
        }
    }

    record ChatContains(String text, int last) implements Condition {
        @Override
        public boolean test(GameView view) {
            return view.chat(last).stream().anyMatch(line -> line.text().contains(text));
        }

        @Override
        public String describe() {
            return "one of the last " + last + " chat lines contains " + text;
        }
    }

    record Dimension(String name) implements Condition {
        @Override
        public boolean test(GameView view) {
            var world = view.world();
            return world != null && name.equals(world.dimension());
        }

        @Override
        public String describe() {
            return "in dimension " + name;
        }
    }

    record GameModeIs(String mode) implements Condition {
        @Override
        public boolean test(GameView view) {
            var player = view.player();
            return player != null && mode.equals(player.gameMode());
        }

        @Override
        public String describe() {
            return "game mode is " + mode;
        }
    }
}
