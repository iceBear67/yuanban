package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.predicate.LocationPredicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.minestom.server.ServerProcess;
import net.minestom.server.collision.ShapeImpl;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.TagKey;

/**
 * 原版 {@code LocationPredicate}、{@code BlockPredicate}、{@code FluidPredicate}、{@code LightPredicate} 的求值，
 * 世界取上下文的世界。
 */
final class LocationPredicates {
    private static final TagKey<Block> CAMPFIRES = TagKey.of(Key.key("campfires"));

    private LocationPredicates() {
    }

    /**
     * 原版 {@code LocationPredicate#matches(ServerLevel, double, double, double)}，检查顺序与原版相同。
     */
    static boolean matches(LocationPredicate predicate, Instance level, double x, double y, double z, ServerProcess process) {
        if (predicate.position().isPresent()) {
            var position = predicate.position().get();
            if (!(LootEvaluation.matches(position.x(), x) && LootEvaluation.matches(position.y(), y)
                    && LootEvaluation.matches(position.z(), z))) {
                return false;
            }
        }
        if (predicate.dimension().isPresent() && !predicate.dimension().get().equals(Key.key(level.getDimensionName()))) {
            return false;
        }
        int blockX = Mth.floor(x);
        int blockY = Mth.floor(y);
        int blockZ = Mth.floor(z);
        boolean loaded = isLoaded(level, blockX, blockY, blockZ);
        if (predicate.biomes().isPresent() && !(loaded && Holders.contains(predicate.biomes().get(),
                BiomeZoom.biome(level, blockX, blockY, blockZ).key(), process.biome()))) {
            return false;
        }
        if (predicate.structures().isPresent()) {
            if (!loaded) {
                return false;
            }
            // TODO(minestom): Minestom 没有结构，也就无法查找某个位置所在的结构
            throw LootEvaluation.unsupported("structures at a position");
        }
        if (predicate.smokey().isPresent() && !(loaded && predicate.smokey().get() == isSmokeyPos(level, blockX, blockY, blockZ, process))) {
            return false;
        }
        if (predicate.light().isPresent() && !matchesLight(predicate.light().get(), level, blockX, blockY, blockZ)) {
            return false;
        }
        if (predicate.block().isPresent() && !matches(predicate.block().get(), level, blockX, blockY, blockZ, process)) {
            return false;
        }
        if (predicate.fluid().isPresent() && !matches(predicate.fluid().get(), level, blockX, blockY, blockZ, process)) {
            return false;
        }
        if (predicate.canSeeSky().isPresent()) {
            // TODO(minestom): Minestom 不计算天空光照的高度图，无法判断位置能否看到天空（原版 Level#canSeeSky）
            throw LootEvaluation.unsupported("whether a position can see the sky");
        }
        return true;
    }

    /**
     * 原版 {@code Level#isLoaded(BlockPos)}：位置在世界高度内且所在区块已加载。原版还要求区块坐标不超过世界生成的范围，
     * 这个范围远大于 Minestom 能使用的坐标。
     */
    static boolean isLoaded(Instance level, int x, int y, int z) {
        var dimension = level.getCachedDimensionType();
        if (y < dimension.minY() || y >= dimension.minY() + dimension.height()) {
            return false;
        }
        return level.isChunkLoaded(x >> 4, z >> 4);
    }

    /**
     * 原版 {@code LightPredicate#matches}：位置未加载时不通过。范围不限时与光照无关，范围有界时需要
     * {@code getMaxLocalRawBrightness}。
     */
    private static boolean matchesLight(LocationPredicate.LightPredicate light, Instance level, int x, int y, int z) {
        if (!isLoaded(level, x, y, z)) {
            return false;
        }
        if (light.light().min() == null && light.light().max() == null) {
            return true;
        }
        // TODO(minestom): 原版的局部亮度要减去由环境属性得到的天空变暗值，Minestom 不计算环境属性
        throw LootEvaluation.unsupported("raw brightness at a position");
    }

    /**
     * 原版 {@code BlockPredicate#matches(ServerLevel, BlockPos)}。
     */
    static boolean matches(LocationPredicate.BlockPredicate predicate, Instance level, int x, int y, int z, ServerProcess process) {
        if (!isLoaded(level, x, y, z)) {
            return false;
        }
        var state = level.getBlock(x, y, z);
        if (predicate.blocks().isPresent() && !Holders.contains(predicate.blocks().get(), state.key(), process.blocks())) {
            return false;
        }
        if (predicate.state().isPresent() && !StatePredicates.matches(predicate.state().get(), state)) {
            return false;
        }
        if (predicate.nbt().isPresent() || !predicate.components().isEmpty()) {
            var blockEntityType = state.blockEntityType();
            if (predicate.nbt().isPresent()) {
                if (blockEntityType == null) {
                    return false;
                }
                var saved = blockEntityNbt(state, blockEntityType.key(), x, y, z);
                if (!io.ib67.yuanban.codec.NbtUtils.compareNbt(predicate.nbt().get(), saved, true)) {
                    return false;
                }
            }
            if (!predicate.components().isEmpty()) {
                if (blockEntityType == null) {
                    return false;
                }
                // TODO(minestom): Minestom 的方块实体没有原版 BlockEntity#collectComponents 收集的组件
                throw LootEvaluation.unsupported("data components of the block entity " + blockEntityType.key().asString());
            }
        }
        return true;
    }

    /**
     * 原版 {@code BlockEntity#saveWithFullMetadata}：方块实体数据加上 {@code id} 与坐标。
     */
    private static CompoundBinaryTag blockEntityNbt(Block state, Key type, int x, int y, int z) {
        return state.nbtOrEmpty().put("id", StringBinaryTag.stringBinaryTag(type.asString()))
                .put("x", IntBinaryTag.intBinaryTag(x))
                .put("y", IntBinaryTag.intBinaryTag(y))
                .put("z", IntBinaryTag.intBinaryTag(z));
    }

    /**
     * 原版 {@code FluidPredicate#matches(ServerLevel, BlockPos)}。
     */
    static boolean matches(LocationPredicate.FluidPredicate predicate, Instance level, int x, int y, int z, ServerProcess process) {
        if (!isLoaded(level, x, y, z)) {
            return false;
        }
        var state = FluidStates.of(level.getBlock(x, y, z));
        if (predicate.fluids().isPresent() && !Holders.contains(predicate.fluids().get(), state.fluid().key(), process.fluid())) {
            return false;
        }
        return predicate.state().isEmpty() || StatePredicates.matches(predicate.state().get(), state.definition(), state.properties());
    }

    /**
     * 原版 {@code CampfireBlock#isSmokeyPos}：向下最多 5 格找点燃的营火，遇到挡住烟柱的方块时只再看它下面一格。
     * 烟柱是原版 {@code Block.column(4, 0, 16)}，按方块碰撞箱是否与它有重叠体积判断是否挡住。
     */
    private static boolean isSmokeyPos(Instance level, int x, int y, int z, ServerProcess process) {
        for (int i = 1; i <= 5; i++) {
            var state = level.getBlock(x, y - i, z);
            if (isLitCampfire(state, process)) {
                return true;
            }
            if (blocksSmoke(state)) {
                return isLitCampfire(level.getBlock(x, y - i - 1, z), process);
            }
        }
        return false;
    }

    /**
     * 原版 {@code CampfireBlock#isLitCampfire}：有 {@code lit} 属性、在 {@code #minecraft:campfires} 中且 {@code lit} 为真。
     */
    private static boolean isLitCampfire(Block state, ServerProcess process) {
        var lit = state.getProperty("lit");
        if (lit == null) {
            return false;
        }
        var tag = process.blocks().getTag(CAMPFIRES);
        return tag != null && tag.contains(RegistryKey.of(state.key())) && lit.equals("true");
    }

    private static boolean blocksSmoke(Block state) {
        if (!(state.collisionShape() instanceof ShapeImpl shape)) {
            throw new IllegalStateException("Unexpected collision shape type " + state.collisionShape().getClass());
        }
        for (var box : shape.boundingBoxes()) {
            if (box.minX() < 0.625 && box.maxX() > 0.375 && box.minY() < 1.0 && box.maxY() > 0.0
                    && box.minZ() < 0.625 && box.maxZ() > 0.375) {
                return true;
            }
        }
        return false;
    }
}
