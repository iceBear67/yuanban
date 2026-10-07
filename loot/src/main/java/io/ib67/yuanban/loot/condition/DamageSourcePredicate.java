package io.ib67.yuanban.loot.condition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

/**
 * 伤害来源谓词（{@code damage_source_properties} 的 {@code predicate} 字段），对应原版
 * {@code net.minecraft.advancements.predicates.DamageSourcePredicate}。
 * 本模块承载伤害类型标签与直接/间接实体谓词的引用。
 */
public record DamageSourcePredicate(@Nullable Key damageTypeTag,
                                    @Nullable EntityPredicate directEntity,
                                    @Nullable EntityPredicate sourceEntity) {
}
