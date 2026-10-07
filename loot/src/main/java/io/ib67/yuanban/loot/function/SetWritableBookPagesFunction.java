package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code set_writable_book_pages}：修改书与笔的书页。JSON 字段 {@code pages}、
 * {@code mode}（原版 {@code ListOperation}；本模块承载，缺省为 {@code AppendOperation} 时
 * 相当于在原书页后追加）。
 */
public record SetWritableBookPagesFunction(List<LootCondition> conditions,
                                           List<FilteredText> pages,
                                           ListOperation mode) implements LootFunction {
    public SetWritableBookPagesFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        pages = pages == null ? List.of() : List.copyOf(pages);
        mode = mode == null ? AppendOperation.INSTANCE : mode;
    }

    public SetWritableBookPagesFunction(List<LootCondition> conditions, List<FilteredText> pages) {
        this(conditions, pages, AppendOperation.INSTANCE);
    }
}
