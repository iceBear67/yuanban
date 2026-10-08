package io.ib67.yuanban.loot.predicate;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.Structure;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.fluid.Fluid;
import net.minestom.server.utils.Range;
import net.minestom.server.world.biome.Biome;

import java.util.Objects;
import java.util.Optional;

/**
 * 位置谓词，对应原版 {@code net.minecraft.advancements.predicates.LocationPredicate}。
 * 字段与原版相同，原版按 {@code position}、{@code dimension}、{@code biomes}、{@code structures}、{@code smokey}、
 * {@code light}、{@code block}、{@code fluid}、{@code can_see_sky} 的顺序检查。
 *
 * @param dimension 维度 ID，任意命名空间 ID，原版不检查维度是否存在
 */
public record LocationPredicate(Optional<PositionPredicate> position,
                                Optional<HolderSet<Biome>> biomes,
                                Optional<HolderSet<Structure>> structures,
                                Optional<Key> dimension,
                                Optional<Boolean> smokey,
                                Optional<LightPredicate> light,
                                Optional<BlockPredicate> block,
                                Optional<FluidPredicate> fluid,
                                Optional<Boolean> canSeeSky) {
    public LocationPredicate {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(biomes, "biomes");
        Objects.requireNonNull(structures, "structures");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(smokey, "smokey");
        Objects.requireNonNull(light, "light");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(canSeeSky, "canSeeSky");
    }

    /**
     * 原版 {@code LocationPredicate$PositionPredicate}：坐标的范围。
     */
    public record PositionPredicate(Range.Double x, Range.Double y, Range.Double z) {
        public PositionPredicate {
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(y, "y");
            Objects.requireNonNull(z, "z");
        }
    }

    /**
     * 原版 {@code LightPredicate}：JSON 字段 {@code light}，亮度范围。
     */
    public record LightPredicate(Range.Int light) {
        public LightPredicate {
            Objects.requireNonNull(light, "light");
        }
    }

    /**
     * 原版 {@code BlockPredicate}：方块集合、状态属性、方块实体的 NBT，以及方块实体的组件。
     *
     * @param nbt JSON 字段 {@code nbt}，原版 {@code NbtPredicate}，按部分匹配与方块实体的 NBT 比较
     */
    public record BlockPredicate(Optional<HolderSet<Block>> blocks, Optional<StatePropertiesPredicate> state,
                                 Optional<CompoundBinaryTag> nbt, ComponentMatchers components) {
        public BlockPredicate {
            Objects.requireNonNull(blocks, "blocks");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(nbt, "nbt");
            Objects.requireNonNull(components, "components");
        }
    }

    /**
     * 原版 {@code FluidPredicate}：流体集合与流体状态属性。
     */
    public record FluidPredicate(Optional<HolderSet<Fluid>> fluids, Optional<StatePropertiesPredicate> state) {
        public FluidPredicate {
            Objects.requireNonNull(fluids, "fluids");
            Objects.requireNonNull(state, "state");
        }
    }
}
