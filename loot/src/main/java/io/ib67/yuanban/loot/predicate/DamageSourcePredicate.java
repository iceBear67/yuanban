package io.ib67.yuanban.loot.predicate;

import io.ib67.yuanban.loot.predicate.entity.EntityPredicate;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.registry.TagKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 伤害来源谓词，对应原版 {@code net.minecraft.advancements.predicates.DamageSourcePredicate}。
 *
 * @param tags         JSON 字段 {@code tags}：伤害类型标签的期望，缺省为空
 * @param directEntity JSON 字段 {@code direct_entity}：直接造成伤害的实体
 * @param sourceEntity JSON 字段 {@code source_entity}：伤害的来源实体
 * @param isDirect     JSON 字段 {@code is_direct}：来源实体与直接实体是否相同
 */
public record DamageSourcePredicate(List<TagPredicate<DamageType>> tags,
                                    Optional<EntityPredicate> directEntity,
                                    Optional<EntityPredicate> sourceEntity,
                                    Optional<Boolean> isDirect) {
    public DamageSourcePredicate {
        tags = List.copyOf(tags);
        Objects.requireNonNull(directEntity, "directEntity");
        Objects.requireNonNull(sourceEntity, "sourceEntity");
        Objects.requireNonNull(isDirect, "isDirect");
    }

    /**
     * 原版 {@code TagPredicate}：对象 {@code {id, expected}}，元素是否在标签中要等于 {@code expected}。
     * 标签 ID 不检查是否存在，求值时找不到的标签按空标签处理。
     */
    public record TagPredicate<T>(TagKey<T> tag, boolean expected) {
        public TagPredicate {
            Objects.requireNonNull(tag, "tag");
        }
    }
}
