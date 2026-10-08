package io.ib67.yuanban.loot.predicate.entity;

import java.util.List;

/**
 * 实体谓词，对应原版 {@code net.minecraft.advancements.predicates.entity.EntityPredicate}。
 * JSON 是子谓词类型 ID（原版 {@code entity_sub_predicate_type} 注册表）到子谓词的对象。
 * <p>
 * {@link #parts()} 保持 JSON 中的顺序；求值时按原版 {@code PREDICATE_TYPE_ORDER} 稳定排序后逐个检查：
 * {@code entity_type} 最先，{@code vehicle}、{@code passenger}、{@code targeted_entity}、
 * {@code type_specific/lightning}、{@code type_specific/player} 靠后，{@code nbt} 最后。
 */
public record EntityPredicate(List<EntitySubPredicate> parts) {
    public EntityPredicate {
        parts = List.copyOf(parts);
    }
}
