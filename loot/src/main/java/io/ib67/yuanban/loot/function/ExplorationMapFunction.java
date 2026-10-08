package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.codec.Structure;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;
import net.minestom.server.registry.TagKey;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 原版 {@code exploration_map}：把空地图换成指向最近结构的探索地图。JSON 字段
 * {@code destination}（结构标签 ID，默认 {@code minecraft:on_treasure_maps}，不检查是否存在）、
 * {@code decoration}（地图装饰类型 ID，默认 {@code minecraft:mansion}）、
 * {@code zoom}（按 byte 读取，默认 {@code 2}）、{@code search_radius}（默认 {@code 50}）、
 * {@code skip_existing_chunks}（默认 {@code true}）。
 */
public record ExplorationMapFunction(List<LootCondition> conditions,
                                     TagKey<Structure> destination,
                                     Key mapDecoration,
                                     byte zoom,
                                     int searchRadius,
                                     boolean skipExistingChunks) implements LootFunction {
    /**
     * 原版 {@code ExplorationMapFunction.DEFAULT_DESTINATION}，即 {@code StructureTags.ON_TREASURE_MAPS}。
     */
    public static final TagKey<Structure> DEFAULT_DESTINATION = TagKey.of(Key.key("on_treasure_maps"));
    /**
     * 原版 {@code ExplorationMapFunction.DEFAULT_DECORATION}，即 {@code MapDecorationTypes.WOODLAND_MANSION}。
     */
    public static final Key DEFAULT_DECORATION = Key.key("mansion");

    public ExplorationMapFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(mapDecoration, "mapDecoration");
    }

    /**
     * 对应原版 {@code ExplorationMapFunction#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ORIGIN);
    }
}
