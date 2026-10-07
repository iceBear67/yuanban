package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code match_tool}：匹配工具。JSON 字段 {@code predicate}（原版 {@code ItemPredicate}）。
 * 谓词里以 {@code #} 开头的物品标签引用经 {@link #referencedItemTags()} 暴露给 {@code LootContents}。
 */
public record MatchToolCondition(Optional<ItemPredicate> predicate) implements LootCondition {
    public MatchToolCondition {
        predicate = predicate == null ? Optional.empty() : predicate;
    }

    public MatchToolCondition(@Nullable ItemPredicate predicate) {
        this(Optional.ofNullable(predicate));
    }

    /**
     * 谓词中声明的物品标签 ID 集合；没有谓词时为空集合。
     */
    public Set<Key> referencedItemTags() {
        if (predicate.isEmpty()) return Set.of();
        var tag = predicate.get().itemTag();
        return tag == null ? Set.of() : Set.of(tag);
    }

    /**
     * 对应原版 {@code MatchTool#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.TOOL);
    }
}
