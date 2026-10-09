package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.view.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * {@code withObserver} 在动作执行期间注册的 observer 的描述。实时模式下映射到 bot 的内置 observer，经过
 * {@code Bot.observeDuring} 与动作一起提交，动作结束后读取它的结论；重放模式下结论来自 fixture。
 */
public sealed interface ObserverSpec {
    JsonSchema SCHEMA = JsonSchema.of(Condition.withDefs(Json.parseObject("""
            {
              "type": "object", "required": ["kind"],
              "allOf": [
                {"if": {"properties": {"kind": {"const": "movementReached"}}}, "then": {"required": ["target"],
                  "properties": {"target": {"$ref": "#/$defs/point"}, "distance": {"type": "number", "exclusiveMinimum": 0, "maximum": 64}}}},
                {"if": {"properties": {"kind": {"enum": ["blockBroken", "blockAt"]}}}, "then": {"required": ["pos"],
                  "properties": {"pos": {"$ref": "#/$defs/point"}}}},
                {"if": {"properties": {"kind": {"const": "blockAt"}}}, "then": {"required": ["is"],
                  "properties": {"is": {"oneOf": [{"$ref": "#/$defs/name"}, {"type": "array", "items": {"$ref": "#/$defs/name"}, "minItems": 1, "maxItems": 64}]}}}},
                {"if": {"properties": {"kind": {"enum": ["blockChanges", "sounds", "particles"]}}}, "then": {"required": ["min", "max"],
                  "properties": {"min": {"$ref": "#/$defs/point"}, "max": {"$ref": "#/$defs/point"}}}},
                {"if": {"properties": {"kind": {"const": "chat"}}}, "then": {
                  "properties": {"contains": {"type": "string", "minLength": 1, "maxLength": 256}}}},
                {"if": {"properties": {"kind": {"const": "entityMovement"}}}, "then": {
                  "properties": {"type": {"$ref": "#/$defs/name"}, "id": {"type": "integer"},
                    "atLeast": {"type": "number", "minimum": 0, "maximum": 1024},
                    "towards": {"type": "object", "required": ["x", "y", "z", "angle"], "properties": {
                      "x": {"$ref": "#/$defs/horizontal"}, "y": {"$ref": "#/$defs/vertical"}, "z": {"$ref": "#/$defs/horizontal"},
                      "angle": {"type": "number", "minimum": 0, "maximum": 180}}},
                    "into": {"type": "object", "required": ["min", "max"], "properties": {
                      "min": {"$ref": "#/$defs/point"}, "max": {"$ref": "#/$defs/point"}}},
                    "count": {"type": "integer", "minimum": 1, "maximum": 256}}}}
              ],
              "properties": {
                "kind": {"enum": ["movementReached", "blockBroken", "blockAt", "blockChanges", "damage", "chat",
                  "entityMovement", "pickups", "slots", "windowOpened", "sounds", "particles"]},
                "target": true, "distance": true, "pos": true, "is": true, "min": true, "max": true,
                "contains": true, "type": true, "id": true, "atLeast": true, "towards": true, "into": true, "count": true,
                "limit": {"type": "integer", "minimum": 1, "maximum": 1024}
              },
              "additionalProperties": false,
              "$defs": {
                "point": {"type": "object", "required": ["x", "y", "z"], "properties": {
                  "x": {"$ref": "#/$defs/horizontal"}, "y": {"$ref": "#/$defs/vertical"}, "z": {"$ref": "#/$defs/horizontal"}}}
              }
            }
            """)));

    /**
     * 记录类 observer 默认最多保存的条数
     */
    int DEFAULT_LIMIT = 64;

    String kind();

    /**
     * @throws io.ib67.yuanban.agent.json.SchemaViolationException 不符合 schema
     */
    static ObserverSpec parse(JsonElement json) {
        SCHEMA.require(json, "observer spec");
        var object = json.getAsJsonObject();
        var limit = (int) Json.longOr(object, "limit", DEFAULT_LIMIT);
        return switch (Json.requireString(object, "kind")) {
            case "movementReached" -> new MovementReached(point(object, "target"), Json.doubleOr(object, "distance", 1.0));
            case "blockBroken" -> new BlockBroken(point(object, "pos"));
            case "blockAt" -> new BlockAt(point(object, "pos"), names(object.get("is")));
            case "blockChanges" -> new BlockChanges(point(object, "min"), point(object, "max"), limit);
            case "damage" -> new Damage(limit);
            case "chat" -> new Chat(Json.string(object, "contains"), limit);
            case "entityMovement" -> {
                var towards = Json.objectOrNull(object, "towards");
                var into = Json.objectOrNull(object, "into");
                yield new EntityMovement(Json.string(object, "type"),
                        object.has("id") ? object.get("id").getAsInt() : null,
                        object.has("atLeast") ? object.get("atLeast").getAsDouble() : null,
                        towards == null ? null : new Vec3(towards.get("x").getAsDouble(), towards.get("y").getAsDouble(), towards.get("z").getAsDouble()),
                        towards == null ? 0 : towards.get("angle").getAsDouble(),
                        into == null ? null : point(into, "min"), into == null ? null : point(into, "max"),
                        (int) Json.longOr(object, "count", 1));
            }
            case "pickups" -> new Pickups(limit);
            case "slots" -> new Slots(limit);
            case "windowOpened" -> new WindowOpened();
            case "sounds" -> new Sounds(point(object, "min"), point(object, "max"), limit);
            case "particles" -> new Particles(point(object, "min"), point(object, "max"), limit);
            default -> throw new IllegalStateException("observer kind passed the schema but is not handled");
        };
    }

    private static Vec3 point(JsonObject object, String key) {
        var point = object.getAsJsonObject(key);
        return new Vec3(point.get("x").getAsDouble(), point.get("y").getAsDouble(), point.get("z").getAsDouble());
    }

    private static Set<String> names(JsonElement is) {
        if (!is.isJsonArray()) {
            return Set.of(is.getAsString());
        }
        var names = new HashSet<String>();
        is.getAsJsonArray().forEach(element -> names.add(element.getAsString()));
        return Set.copyOf(names);
    }

    /**
     * 结论：动作结束时玩家脚部与目标的距离是否不大于 distance，以及观察期间的最近距离
     */
    record MovementReached(Vec3 target, double distance) implements ObserverSpec {
        @Override
        public String kind() {
            return "movementReached";
        }
    }

    /**
     * 结论：方块是否变成了编号不同的方块（{@code Bot.breakBlock} 的语义）
     */
    record BlockBroken(Vec3 pos) implements ObserverSpec {
        @Override
        public String kind() {
            return "blockBroken";
        }
    }

    /**
     * 结论：方块是否变成 names 中的一种
     */
    record BlockAt(Vec3 pos, Set<String> names) implements ObserverSpec {
        @Override
        public String kind() {
            return "blockAt";
        }
    }

    record BlockChanges(Vec3 min, Vec3 max, int limit) implements ObserverSpec {
        @Override
        public String kind() {
            return "blockChanges";
        }
    }

    record Damage(int limit) implements ObserverSpec {
        @Override
        public String kind() {
            return "damage";
        }
    }

    record Chat(@Nullable String contains, int limit) implements ObserverSpec {
        @Override
        public String kind() {
            return "chat";
        }
    }

    /**
     * 给出 atLeast、towards、into 之一时，满足条件的实体数达到 count 时得出结论；都没有时只记录
     */
    record EntityMovement(@Nullable String type, @Nullable Integer id, @Nullable Double atLeast, @Nullable Vec3 towards,
                          double angle, @Nullable Vec3 intoMin, @Nullable Vec3 intoMax, int count) implements ObserverSpec {
        @Override
        public String kind() {
            return "entityMovement";
        }

        public boolean hasCriteria() {
            return atLeast != null || towards != null || intoMin != null;
        }
    }

    record Pickups(int limit) implements ObserverSpec {
        @Override
        public String kind() {
            return "pickups";
        }
    }

    record Slots(int limit) implements ObserverSpec {
        @Override
        public String kind() {
            return "slots";
        }
    }

    record WindowOpened() implements ObserverSpec {
        @Override
        public String kind() {
            return "windowOpened";
        }
    }

    record Sounds(Vec3 min, Vec3 max, int limit) implements ObserverSpec {
        @Override
        public String kind() {
            return "sounds";
        }
    }

    record Particles(Vec3 min, Vec3 max, int limit) implements ObserverSpec {
        @Override
        public String kind() {
            return "particles";
        }
    }
}
