package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import net.kyori.adventure.key.Key;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code match_tool}：匹配工具。JSON 字段 {@code predicate}（可选，原版 {@code ItemPredicate}）。
 * 上下文缺少工具时不通过。谓词里以标签给出的物品集合经 {@link #referencedItemTags()} 暴露给 {@code LootContents}。
 */
public record MatchToolCondition(Optional<ItemPredicate> predicate) implements LootCondition {
    public MatchToolCondition {
        Objects.requireNonNull(predicate, "predicate");
    }

    /**
     * 谓词的 {@code items} 是标签时的标签 ID；没有谓词或物品集合是物品列表时为空集合。
     */
    public Set<Key> referencedItemTags() {
        return predicate.flatMap(ItemPredicate::items)
                .<Set<Key>>map(items -> items instanceof HolderSet.Named<?>(var tag) ? Set.of(tag.key()) : Set.of())
                .orElse(Set.of());
    }

    /**
     * 对应原版 {@code MatchTool#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.TOOL);
    }
}
