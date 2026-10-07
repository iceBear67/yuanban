package io.ib67.yuanban.loot.condition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 实体谓词（{@code entity_properties} 的 {@code predicate} 字段），对应原版
 * {@code net.minecraft.advancements.predicates.EntityPredicate}。
 * 本模块承载原版 JSON 中战利品表会用到的引用：可选的实体类型列表与实体类型标签。
 */
public record EntityPredicate(@Nullable List<Key> entityTypes,
                              @Nullable Key entityTypeTag) {
    public EntityPredicate {
        if (entityTypes != null) entityTypes = List.copyOf(entityTypes);
    }
}
