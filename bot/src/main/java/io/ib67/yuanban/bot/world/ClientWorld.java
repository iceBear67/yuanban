package io.ib67.yuanban.bot.world;

import io.ib67.yuanban.bot.ChunkNotLoadedException;
import io.ib67.yuanban.bot.entity.ClientEntity;
import io.ib67.yuanban.bot.entity.EntityStore;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.world.Difficulty;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * bot 所在的世界。收到 Login (play)，或维度名称改变的 Respawn 时重新创建；Start Configuration 与 Transfer 时清除。
 * 只在调度线程上使用。
 */
public final class ClientWorld implements Block.Getter {
    private final Key dimension;
    private final int dimensionTypeId;
    private final int minY;
    private final int height;
    private final Long2ObjectMap<ChunkColumn> chunks = new Long2ObjectOpenHashMap<>();
    private final BlockEntityStore blockEntities = new BlockEntityStore();
    private final EntityStore entities = new EntityStore();
    private long worldAge;
    private final TickState tickState = new TickState();
    private final WorldBorderState border = new WorldBorderState();
    private final Weather weather = new Weather();
    private int viewDistance;
    private int simulationDistance;
    private int centerX;
    private int centerZ;
    private @Nullable Difficulty difficulty;
    private @Nullable Point defaultSpawn;

    public ClientWorld(Key dimension, int dimensionTypeId, int minY, int height, int viewDistance, int simulationDistance) {
        this.dimension = dimension;
        this.dimensionTypeId = dimensionTypeId;
        this.minY = minY;
        this.height = height;
        this.viewDistance = viewDistance;
        this.simulationDistance = simulationDistance;
    }

    public Key dimension() {
        return dimension;
    }

    public int dimensionTypeId() {
        return dimensionTypeId;
    }

    public int minY() {
        return minY;
    }

    public int height() {
        return height;
    }

    public EntityStore entities() {
        return entities;
    }

    public BlockEntityStore blockEntities() {
        return blockEntities;
    }

    /**
     * World Age（原版 {@code ClientLevelData.gameTime}）：新的世界从 0 开始，取自 Update Time，每个正常运行的 tick 在实体 tick 之后加一
     */
    public long worldAge() {
        return worldAge;
    }

    public void worldAge(long worldAge) {
        this.worldAge = worldAge;
    }

    public TickState tickState() {
        return tickState;
    }

    public WorldBorderState border() {
        return border;
    }

    public Weather weather() {
        return weather;
    }

    public int simulationDistance() {
        return simulationDistance;
    }

    public void simulationDistance(int simulationDistance) {
        this.simulationDistance = simulationDistance;
    }

    public int viewDistance() {
        return viewDistance;
    }

    public @Nullable Difficulty difficulty() {
        return difficulty;
    }

    public void difficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * Set Default Spawn Position 给出的方块坐标
     */
    public @Nullable Point defaultSpawn() {
        return defaultSpawn;
    }

    public void defaultSpawn(Point defaultSpawn) {
        this.defaultSpawn = defaultSpawn;
    }

    // 加载区域

    /**
     * 加载区域为以中心区块为中心、半径 {@code max(2, 服务端视距) + 3} 的正方形（原版 {@code ClientChunkCache.calculateStorageRange}）
     */
    public boolean inLoadArea(int chunkX, int chunkZ) {
        var radius = Math.max(2, viewDistance) + 3;
        return Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius;
    }

    /**
     * Set Center Chunk：移动加载区域，立即卸载落到区域外的区块
     *
     * @return 被卸载的区块，以 {@link CoordConversion#chunkIndex} 编码
     */
    public LongList center(int chunkX, int chunkZ) {
        this.centerX = chunkX;
        this.centerZ = chunkZ;
        return unloadOutside();
    }

    /**
     * Set Render Distance：改变服务端视距，立即卸载落到区域外的区块
     */
    public LongList viewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        return unloadOutside();
    }

    private LongList unloadOutside() {
        var removed = new LongArrayList();
        var keys = chunks.keySet().iterator();
        while (keys.hasNext()) {
            var key = keys.nextLong();
            if (!inLoadArea(CoordConversion.chunkIndexGetX(key), CoordConversion.chunkIndexGetZ(key))) {
                removed.add(key);
            }
        }
        for (int i = 0; i < removed.size(); i++) {
            var key = removed.getLong(i);
            unloadChunk(CoordConversion.chunkIndexGetX(key), CoordConversion.chunkIndexGetZ(key));
        }
        return removed;
    }

    // 实体

    /**
     * 原版 {@code ClientLevel.tickEntities}：所在区块已加载、没有乘坐其它实体的实体每 tick 执行一步，
     * 冻结并且不在步进中时（{@link TickState#runsNormally()}）玩家以外、没有玩家乘客的实体除外；乘客在它的载具之后执行，
     * 玩家以外的乘客所在的区块也要已加载。每一步的内容见 {@link ClientEntity#tick()}；本地玩家不在实体模型中，由移动步骤执行
     *
     * @param localPlayerId 本地玩家的实体编号，判断载具是否有玩家乘客时使用
     */
    public void tickEntities(int localPlayerId) {
        var runsNormally = tickState.runsNormally();
        for (var entity : entities.all()) {
            if (entity.vehicle() == null && ticking(entity)
                    && (runsNormally || entity.type() == EntityType.PLAYER || hasPlayerPassenger(entity, localPlayerId))) {
                tickWithPassengers(entity);
            }
        }
    }

    /**
     * 原版 {@code tickNonPassenger} 与 {@code tickPassenger}：实体执行一步，然后依次执行它的乘客
     */
    private void tickWithPassengers(ClientEntity entity) {
        entity.tick();
        var passengers = entity.passengers();
        for (int i = 0; i < passengers.size(); i++) {
            var passenger = entities.get(passengers.getInt(i));
            if (passenger != null && Objects.equals(passenger.vehicle(), entity.id())
                    && (passenger.type() == EntityType.PLAYER || ticking(passenger))) {
                tickWithPassengers(passenger);
            }
        }
    }

    /**
     * 实体所在的区块已加载（原版客户端只 tick 已加载区块中的实体）
     */
    private boolean ticking(ClientEntity entity) {
        var position = entity.position();
        return chunkLoaded(position.chunkX(), position.chunkZ());
    }

    /**
     * 原版 {@code countPlayerPassengers() > 0}：直接或间接的乘客中有玩家（本地玩家或其它玩家实体）
     */
    private boolean hasPlayerPassenger(ClientEntity entity, int localPlayerId) {
        var passengers = entity.passengers();
        for (int i = 0; i < passengers.size(); i++) {
            var id = passengers.getInt(i);
            if (id == localPlayerId) {
                return true;
            }
            var passenger = entities.get(id);
            if (passenger != null && (passenger.type() == EntityType.PLAYER || hasPlayerPassenger(passenger, localPlayerId))) {
                return true;
            }
        }
        return false;
    }

    // 区块

    public boolean chunkLoaded(int chunkX, int chunkZ) {
        return chunks.containsKey(CoordConversion.chunkIndex(chunkX, chunkZ));
    }

    /**
     * 安装区块；同一位置已有区块时替换它
     *
     * @param sections      从维度最低 y 开始的各区段
     * @param blockEntities 以 {@link CoordConversion#chunkBlockIndex} 为键、发给客户端的方块实体 NBT；
     *                      方块实体的方块由区段中该位置的方块决定
     */
    public void installChunk(int chunkX, int chunkZ, SectionRef[] sections, Map<Integer, CompoundBinaryTag> blockEntities) {
        if (sections.length != height / 16) {
            throw new IllegalArgumentException("chunk " + chunkX + ", " + chunkZ + " has " + sections.length
                    + " sections, dimension height " + height + " requires " + height / 16);
        }
        this.blockEntities.removeChunk(chunkX, chunkZ);
        chunks.put(CoordConversion.chunkIndex(chunkX, chunkZ), new ChunkColumn(sections));
        blockEntities.forEach((index, nbt) -> {
            var x = chunkX * 16 + CoordConversion.chunkBlockIndexGetX(index);
            var y = CoordConversion.chunkBlockIndexGetY(index);
            var z = chunkZ * 16 + CoordConversion.chunkBlockIndexGetZ(index);
            this.blockEntities.put(x, y, z, nbt);
        });
    }

    /**
     * @return 区块原本是否已加载
     */
    public boolean unloadChunk(int chunkX, int chunkZ) {
        var removed = chunks.remove(CoordConversion.chunkIndex(chunkX, chunkZ));
        if (removed != null) {
            blockEntities.removeChunk(chunkX, chunkZ);
        }
        return removed != null;
    }

    /**
     * 改变一个方块。新方块的方块实体类型与旧方块不同时删除旧的方块实体数据（原版客户端行为）
     *
     * @return 旧方块（不带 NBT）；所在区块没有加载或者超出维度高度时不改变任何东西，返回 null
     */
    public @Nullable Block setBlock(int x, int y, int z, int stateId) {
        if (y < minY || y >= minY + height) {
            return null;
        }
        var column = chunks.get(CoordConversion.chunkIndex(x >> 4, z >> 4));
        if (column == null) {
            return null;
        }
        var section = column.section((y - minY) >> 4);
        var old = Objects.requireNonNull(Block.fromStateId(section.block(x & 15, y & 15, z & 15)));
        section.setBlock(x & 15, y & 15, z & 15, stateId);
        var now = Objects.requireNonNull(Block.fromStateId(stateId));
        if (old.blockEntityType() != now.blockEntityType()) {
            blockEntities.remove(x, y, z);
        }
        return old;
    }

    /**
     * Chunk Biomes：依次读取每个区段的生物群系
     *
     * @param serializer 生物群系调色板的编解码器，按生物群系数量决定直接调色板的位数
     * @return 区块是否已加载
     */
    public boolean setBiomes(int chunkX, int chunkZ, byte[] data, NetworkBuffer.Type<Palette> serializer) {
        var column = chunks.get(CoordConversion.chunkIndex(chunkX, chunkZ));
        if (column == null) {
            return false;
        }
        var buffer = NetworkBuffer.wrap(data, 0, data.length);
        for (int i = 0; i < column.sectionCount(); i++) {
            column.section(i).setBiomes(buffer.read(serializer));
        }
        return true;
    }

    /**
     * 方块所在的生物群系编号（生物群系按 4 × 4 × 4 的格子保存）
     *
     * @throws ChunkNotLoadedException  所在区块没有加载
     * @throws IllegalArgumentException 超出维度高度范围
     */
    public int biome(int x, int y, int z) {
        if (y < minY || y >= minY + height) {
            throw new IllegalArgumentException("y " + y + " is outside the dimension height");
        }
        var column = chunks.get(CoordConversion.chunkIndex(x >> 4, z >> 4));
        if (column == null) {
            throw new ChunkNotLoadedException(x >> 4, z >> 4);
        }
        return column.section((y - minY) >> 4).biome((x & 15) >> 2, (y & 15) >> 2, (z & 15) >> 2);
    }

    /**
     * 方块；维度高度范围之外为空气，{@code condition} 为 NONE 时附加方块实体的 NBT
     *
     * @throws ChunkNotLoadedException 所在区块没有加载
     */
    @Override
    public Block getBlock(int x, int y, int z, Condition condition) {
        if (y < minY || y >= minY + height) {
            return Block.AIR;
        }
        var column = chunks.get(CoordConversion.chunkIndex(x >> 4, z >> 4));
        if (column == null) {
            throw new ChunkNotLoadedException(x >> 4, z >> 4);
        }
        var block = Objects.requireNonNull(Block.fromStateId(column.section((y - minY) >> 4).block(x & 15, y & 15, z & 15)));
        if (condition == Condition.NONE && block.blockEntityType() != null) {
            var nbt = blockEntities.get(x, y, z);
            if (nbt != null) {
                return block.withNbt(nbt);
            }
        }
        return block;
    }

    /**
     * 在以 center 为中心、水平半径 radius 的范围内复制区段引用，供寻路线程使用
     */
    public WorldSnapshot snapshot(Point center, double radius) {
        var minChunkX = CoordConversion.globalToChunk(center.x() - radius);
        var maxChunkX = CoordConversion.globalToChunk(center.x() + radius);
        var minChunkZ = CoordConversion.globalToChunk(center.z() - radius);
        var maxChunkZ = CoordConversion.globalToChunk(center.z() + radius);
        var captured = new Long2ObjectOpenHashMap<SharedSection[]>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                var key = CoordConversion.chunkIndex(chunkX, chunkZ);
                var column = chunks.get(key);
                if (column == null) {
                    continue;
                }
                var sections = new SharedSection[column.sectionCount()];
                for (int i = 0; i < sections.length; i++) {
                    sections[i] = column.section(i).share();
                }
                captured.put(key, sections);
            }
        }
        return new WorldSnapshot(minY, height, captured);
    }
}
