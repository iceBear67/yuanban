package io.ib67.yuanban.agent.bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.view.BlockPos;
import io.ib67.yuanban.agent.view.BlockView;
import io.ib67.yuanban.agent.view.BotStatusView;
import io.ib67.yuanban.agent.view.ChatView;
import io.ib67.yuanban.agent.view.EntityView;
import io.ib67.yuanban.agent.view.GameView;
import io.ib67.yuanban.agent.view.InventoryView;
import io.ib67.yuanban.agent.view.PlayerView;
import io.ib67.yuanban.agent.view.Vec3;
import io.ib67.yuanban.agent.view.WorldStatusView;
import io.ib67.yuanban.bot.ChunkNotLoadedException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * fixture 描述的世界，可以被预设动作的效果修改。线程安全：全部读写在 this 上同步。
 * <p>
 * JSON 格式（全部字段可省略）：
 * <pre>{@code
 * {
 *   "status": BotStatusView 的字段,
 *   "player": PlayerView 的字段（与默认玩家合并）,
 *   "worldStatus": WorldStatusView 的字段,
 *   "loadedChunks": "all" 或 [[chunkX, chunkZ], ...]（默认 "all"）,
 *   "ground": {"y": 63, "name": "minecraft:stone"}（y 不大于它的位置为这种方块，其余为空气）,
 *   "blocks": [{"x":0,"y":64,"z":0,"name":"minecraft:lava", "air":?, "solid":?, "blocksMotion":?, "liquid":?, "properties":?}],
 *   "entities": [EntityView 的字段（distance 由玩家位置算出）],
 *   "inventory": InventoryView 的字段,
 *   "chat": [ChatView 的字段]
 * }
 * }</pre>
 */
public final class FixtureWorld implements GameView {
    private static final JsonObject DEFAULT_PLAYER = Json.parseObject("""
            {"entityId": 1, "uuid": "00000000-0000-0000-0000-000000000001", "username": "fixture",
             "position": {"x": 0.5, "y": 64, "z": 0.5}, "yaw": 0, "pitch": 0, "velocity": {"x": 0, "y": 0, "z": 0},
             "onGround": true, "health": 20, "food": 20, "saturation": 5, "dead": false, "gameMode": "SURVIVAL",
             "flying": false, "heldSlot": 0, "level": 0, "effects": [], "dimension": "minecraft:overworld"}
            """);
    private static final JsonObject DEFAULT_STATUS = Json.parseObject("""
            {"status": "PLAY", "loaded": true, "inWorld": true, "movementSuspended": false,
             "disconnectReason": null, "failure": null, "ticks": 0}
            """);
    private static final JsonObject DEFAULT_WORLD = Json.parseObject("""
            {"dimension": "minecraft:overworld", "epoch": 1, "entryCause": "JOIN", "worldAge": 0, "rain": 0, "thunder": 0,
             "difficulty": "NORMAL", "frozen": false}
            """);
    private static final JsonObject DEFAULT_INVENTORY = Json.parseObject("""
            {"heldSlot": 0, "items": [], "mainHand": null, "offHand": null, "cursor": null, "openWindow": null}
            """);
    private static final Set<String> LIQUIDS = Set.of("minecraft:water", "minecraft:lava");

    private JsonObject status;
    private JsonObject player;
    private JsonObject worldStatus;
    private @Nullable Set<Long> loadedChunks;
    private @Nullable Integer groundY;
    private String groundName = "minecraft:stone";
    private final Map<BlockPos, BlockView> blocks = new HashMap<>();
    private final List<EntityView> entities = new ArrayList<>();
    private JsonObject inventory;
    private final List<ChatView> chat = new ArrayList<>();

    public FixtureWorld(JsonObject json) {
        status = merge(DEFAULT_STATUS, Json.objectOrNull(json, "status"));
        player = merge(DEFAULT_PLAYER, Json.objectOrNull(json, "player"));
        worldStatus = merge(DEFAULT_WORLD, Json.objectOrNull(json, "worldStatus"));
        inventory = merge(DEFAULT_INVENTORY, Json.objectOrNull(json, "inventory"));
        var chunks = json.get("loadedChunks");
        if (chunks != null && chunks.isJsonArray()) {
            loadedChunks = new HashSet<>();
            for (var chunk : chunks.getAsJsonArray()) {
                var pair = chunk.getAsJsonArray();
                loadedChunks.add(chunkKey(pair.get(0).getAsInt(), pair.get(1).getAsInt()));
            }
        }
        var ground = Json.objectOrNull(json, "ground");
        if (ground != null) {
            groundY = ground.get("y").getAsInt();
            groundName = Json.requireString(ground, "name");
        }
        setBlocks(json.getAsJsonArray("blocks"));
        setEntities(json.getAsJsonArray("entities"));
        addChat(json.getAsJsonArray("chat"));
    }

    /**
     * 应用一个效果：{@code {"player": {...}, "status": {...}, "worldStatus": {...}, "inventory": {...},
     * "blocks": [...], "entities": [...]（替换全部实体）, "removeEntities": [编号], "chat": [...]（追加）}}
     */
    public synchronized void apply(@Nullable JsonObject effects) {
        if (effects == null) {
            return;
        }
        status = merge(status, Json.objectOrNull(effects, "status"));
        player = merge(player, Json.objectOrNull(effects, "player"));
        worldStatus = merge(worldStatus, Json.objectOrNull(effects, "worldStatus"));
        inventory = merge(inventory, Json.objectOrNull(effects, "inventory"));
        setBlocks(effects.getAsJsonArray("blocks"));
        if (effects.has("entities")) {
            entities.clear();
            setEntities(effects.getAsJsonArray("entities"));
        }
        var removed = effects.getAsJsonArray("removeEntities");
        if (removed != null) {
            var ids = new HashSet<Integer>();
            removed.forEach(id -> ids.add(id.getAsInt()));
            entities.removeIf(entity -> ids.contains(entity.id()));
        }
        addChat(effects.getAsJsonArray("chat"));
    }

    /**
     * 把玩家移动到 position
     */
    public synchronized void movePlayer(Vec3 position) {
        apply(Json.object("player", Json.object("position", position)));
    }

    @Override
    public synchronized BotStatusView status() {
        return Json.read(status, BotStatusView.class);
    }

    @Override
    public synchronized @Nullable PlayerView player() {
        return status().inWorld() ? Json.read(player, PlayerView.class) : null;
    }

    @Override
    public synchronized @Nullable WorldStatusView world() {
        return status().inWorld() ? Json.read(worldStatus, WorldStatusView.class) : null;
    }

    @Override
    public synchronized BlockView block(BlockPos pos) {
        if (!status().inWorld()) {
            throw new IllegalStateException("not in a world");
        }
        if (!chunkLoaded(pos.chunkX(), pos.chunkZ())) {
            throw new ChunkNotLoadedException(pos.chunkX(), pos.chunkZ());
        }
        var block = blocks.get(pos);
        if (block != null) {
            return block;
        }
        if (groundY != null && pos.y() <= groundY) {
            return view(pos, groundName, null);
        }
        return view(pos, "minecraft:air", null);
    }

    @Override
    public synchronized boolean chunkLoaded(int chunkX, int chunkZ) {
        return status().inWorld() && (loadedChunks == null || loadedChunks.contains(chunkKey(chunkX, chunkZ)));
    }

    @Override
    public synchronized List<EntityView> entities() {
        var self = player();
        var result = new ArrayList<EntityView>();
        for (var entity : entities) {
            var distance = self == null ? -1 : self.position().distance(entity.position());
            result.add(new EntityView(entity.id(), entity.uuid(), entity.type(), entity.name(), entity.position(), distance,
                    entity.onGround(), entity.health(), entity.vehicle(), entity.passengers(), entity.width(), entity.height()));
        }
        result.sort(Comparator.comparingDouble(EntityView::distance));
        return result;
    }

    @Override
    public synchronized @Nullable EntityView entity(int id) {
        return entities().stream().filter(entity -> entity.id() == id).findFirst().orElse(null);
    }

    @Override
    public synchronized InventoryView inventory() {
        return Json.read(inventory, InventoryView.class);
    }

    @Override
    public synchronized List<ChatView> chat(int limit) {
        return List.copyOf(chat.subList(Math.max(0, chat.size() - limit), chat.size()));
    }

    private void setBlocks(@Nullable JsonArray array) {
        if (array == null) {
            return;
        }
        for (var element : array) {
            var object = element.getAsJsonObject();
            var pos = new BlockPos(object.get("x").getAsInt(), object.get("y").getAsInt(), object.get("z").getAsInt());
            blocks.put(pos, view(pos, Json.requireString(object, "name"), object));
        }
    }

    private void setEntities(@Nullable JsonArray array) {
        if (array == null) {
            return;
        }
        for (var element : array) {
            var object = merge(Json.parseObject("""
                    {"uuid": "00000000-0000-0000-0000-000000000000", "name": null, "distance": -1, "onGround": true,
                     "health": null, "vehicle": null, "passengers": [], "width": 0.6, "height": 1.8}
                    """), element.getAsJsonObject());
            entities.add(Json.read(object, EntityView.class));
        }
    }

    private void addChat(@Nullable JsonArray array) {
        if (array == null) {
            return;
        }
        for (var element : array) {
            var object = merge(Json.parseObject("{\"kind\": \"SYSTEM\", \"sender\": null, \"senderName\": null, \"signature\": \"NONE\"}"),
                    element.getAsJsonObject());
            chat.add(Json.read(object, ChatView.class));
        }
    }

    private static BlockView view(BlockPos pos, String name, @Nullable JsonObject flags) {
        var air = name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air");
        var liquid = LIQUIDS.contains(name);
        var blocksMotion = !air && !liquid;
        Map<String, String> properties = Map.of();
        if (flags != null) {
            air = Json.booleanOr(flags, "air", air);
            liquid = Json.booleanOr(flags, "liquid", liquid);
            blocksMotion = Json.booleanOr(flags, "blocksMotion", !air && !liquid);
            var props = Json.objectOrNull(flags, "properties");
            if (props != null) {
                var map = new HashMap<String, String>();
                props.entrySet().forEach(entry -> map.put(entry.getKey(), entry.getValue().getAsString()));
                properties = map;
            }
        }
        var solid = flags == null ? blocksMotion : Json.booleanOr(flags, "solid", blocksMotion);
        return new BlockView(pos, name, properties, air, solid, blocksMotion, liquid);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static JsonObject merge(JsonObject base, @Nullable JsonObject override) {
        var result = base.deepCopy();
        if (override != null) {
            for (var entry : override.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonObject() && result.has(entry.getKey()) && result.get(entry.getKey()).isJsonObject()) {
                    result.add(entry.getKey(), merge(result.getAsJsonObject(entry.getKey()), value.getAsJsonObject()));
                } else {
                    result.add(entry.getKey(), value.deepCopy());
                }
            }
        }
        return result;
    }
}
