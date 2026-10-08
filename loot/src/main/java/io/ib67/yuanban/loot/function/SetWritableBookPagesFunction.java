package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.item.book.FilteredText;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code set_writable_book_pages}：修改书与笔的书页。JSON 字段 {@code pages}（可过滤文本，最多 100 页，
 * 每页最长 1024 个字符）、内联的列表操作（必填的 {@code mode}，结果最多 100 页）。
 */
public record SetWritableBookPagesFunction(List<LootCondition> conditions,
                                           List<FilteredText<String>> pages,
                                           ListOperation mode) implements LootFunction {
    public SetWritableBookPagesFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        pages = List.copyOf(pages);
        Objects.requireNonNull(mode, "mode");
    }
}
