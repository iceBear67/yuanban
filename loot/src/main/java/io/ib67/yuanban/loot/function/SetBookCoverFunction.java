package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 原版 {@code set_book_cover}：写入成书封面。JSON 字段 {@code author}、{@code title}、
 * {@code generation}（都可缺省）。
 */
public record SetBookCoverFunction(List<LootCondition> conditions,
                                   Optional<String> author,
                                   Optional<FilteredText> title,
                                   Optional<Integer> generation) implements LootFunction {
    public SetBookCoverFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        author = author == null ? Optional.empty() : author;
        title = title == null ? Optional.empty() : title;
        generation = generation == null ? Optional.empty() : generation;
    }

    public SetBookCoverFunction(List<LootCondition> conditions,
                                @Nullable String author,
                                @Nullable FilteredText title,
                                @Nullable Integer generation) {
        this(conditions, Optional.ofNullable(author), Optional.ofNullable(title), Optional.ofNullable(generation));
    }
}
