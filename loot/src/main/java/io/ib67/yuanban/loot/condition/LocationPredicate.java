package io.ib67.yuanban.loot.condition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

/**
 * 位置谓词（{@code location_check} 的 {@code predicate} 字段），对应原版
 * {@code net.minecraft.advancements.predicates.LocationPredicate}。
 * 本模块承载原版 JSON 中的结构、生物群系与维度引用。
 */
public record LocationPredicate(@Nullable Key structure,
                                @Nullable Key biome,
                                @Nullable Key dimension,
                                @Nullable Key structureTag,
                                @Nullable Key biomeTag) {
}
