package io.ib67.yuanban.loot.eval;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.fluid.Fluid;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 由方块状态得到流体状态，移植原版各方块的 {@code getFluidState}：
 * <ul>
 *   <li>水与岩浆（{@code LiquidBlock}）：{@code level} 为 0 时是静止流体，1 到 7 是流动流体（流体等级为 {@code 8 - level}），
 *       8 及以上是下落的流动流体（流体等级 8）；</li>
 *   <li>海带、海带植株、海草、高海草、气泡柱总是静止的水；</li>
 *   <li>其余带有 {@code waterlogged} 属性的方块在它为 {@code true} 时是静止的水；</li>
 *   <li>其他方块没有流体。</li>
 * </ul>
 * 流体状态的属性与原版 {@code FlowingFluid} 相同：静止流体只有 {@code falling}，流动流体另有 {@code level}（1 到 8）。
 */
final class FluidStates {
    private static final Set<Key> ALWAYS_WATER = Set.of(Key.key("kelp"), Key.key("kelp_plant"), Key.key("seagrass"),
            Key.key("tall_seagrass"), Key.key("bubble_column"));
    private static final StatePredicates.Property FALLING = new StatePredicates.Property(StatePredicates.Kind.BOOLEAN,
            List.of("false", "true"));
    private static final StatePredicates.Property LEVEL = new StatePredicates.Property(StatePredicates.Kind.INTEGER,
            List.of("1", "2", "3", "4", "5", "6", "7", "8"));
    private static final Map<String, StatePredicates.Property> SOURCE_DEFINITION = Map.of("falling", FALLING);
    private static final Map<String, StatePredicates.Property> FLOWING_DEFINITION = Map.of("falling", FALLING, "level", LEVEL);

    private FluidStates() {
    }

    /**
     * 流体状态：流体类型与属性取值。
     *
     * @param definition 流体类型的属性定义
     */
    record FluidState(Fluid fluid, Map<String, String> properties, Map<String, StatePredicates.Property> definition) {
    }

    static FluidState of(Block state) {
        var id = state.key();
        if (id.equals(Block.WATER.key()) || id.equals(Block.LAVA.key())) {
            boolean water = id.equals(Block.WATER.key());
            int level = Math.min(Integer.parseInt(Objects.requireNonNull(state.getProperty("level"))), 8);
            if (level == 0) {
                return source(water ? Fluid.WATER : Fluid.LAVA);
            }
            var flowing = water ? Fluid.FLOWING_WATER : Fluid.FLOWING_LAVA;
            return level < 8
                    ? new FluidState(flowing, Map.of("falling", "false", "level", Integer.toString(8 - level)), FLOWING_DEFINITION)
                    : new FluidState(flowing, Map.of("falling", "true", "level", "8"), FLOWING_DEFINITION);
        }
        if (ALWAYS_WATER.contains(id) || "true".equals(state.properties().get("waterlogged"))) {
            return source(Fluid.WATER);
        }
        return new FluidState(Fluid.EMPTY, Map.of(), Map.of());
    }

    private static FluidState source(Fluid fluid) {
        return new FluidState(fluid, Map.of("falling", "false"), SOURCE_DEFINITION);
    }
}
