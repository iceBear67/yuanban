package io.ib67.yuanban.loot.condition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 物品谓词（{@code match_tool} 的 {@code predicate} 字段），对应原版
 * {@code net.minecraft.advancements.predicates.ItemPredicate}。
 * 本模块承载原版 JSON 中的物品 ID 列表与物品标签 ID 两种引用。
 */
public record ItemPredicate(@Nullable List<Key> items,
                            @Nullable Key itemTag) {
    public ItemPredicate {
        if (items != null) items = List.copyOf(items);
    }
}
