package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.item.book.FilteredText;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 原版 {@code set_book_cover}：写入成书的封面。JSON 字段 {@code title}（可过滤文本，最长 32 个字符）、
 * {@code author}、{@code generation}（0 到 3），都可缺省；缺省的项保持物品原有的值。
 */
public record SetBookCoverFunction(List<LootCondition> conditions,
                                   Optional<FilteredText<String>> title,
                                   Optional<String> author,
                                   Optional<Integer> generation) implements LootFunction {
    public SetBookCoverFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(generation, "generation");
    }
}
