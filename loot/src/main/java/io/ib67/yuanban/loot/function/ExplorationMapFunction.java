package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Set;

/**
 * 原版 {@code exploration_map}：把物品填入指向结构的探索地图。JSON 字段
 * {@code destination}（结构标签 ID，默认 {@code minecraft:on_treasure_maps}）、
 * {@code decoration}（地图装饰类型 ID，默认 {@code minecraft:woodland_mansion}）、
 * {@code zoom}（默认 {@code 2}）、{@code search_radius}（默认 {@code 50}）、
 * {@code skip_existing_chunks}（默认 {@code true}）。
 */
public record ExplorationMapFunction(List<LootCondition> conditions,
                                     Key destination,
                                     Key mapDecoration,
                                     int zoom,
                                     int searchRadius,
                                     boolean skipExistingChunks) implements LootFunction {
    public static final Key DEFAULT_DESTINATION = Key.key("on_treasure_maps");
    public static final Key DEFAULT_DECORATION = Key.key("woodland_mansion");

    public ExplorationMapFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        destination = destination == null ? DEFAULT_DESTINATION : destination;
        mapDecoration = mapDecoration == null ? DEFAULT_DECORATION : mapDecoration;
    }

    public ExplorationMapFunction(List<LootCondition> conditions) {
        this(conditions, DEFAULT_DESTINATION, DEFAULT_DECORATION, 2, 50, true);
    }

    /**
     * 对应原版 {@code ExplorationMapFunction#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ORIGIN);
    }
}
