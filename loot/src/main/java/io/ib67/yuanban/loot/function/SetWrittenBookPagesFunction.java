package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * 原版 {@code set_written_book_pages}：修改成书的书页（带格式文本）。JSON 字段 {@code pages}、
 * {@code mode}（原版 {@code ListOperation}；缺省为 {@code AppendOperation}）。
 */
public record SetWrittenBookPagesFunction(List<LootCondition> conditions,
                                          List<Component> pages,
                                          ListOperation mode) implements LootFunction {
    public SetWrittenBookPagesFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        pages = pages == null ? List.of() : List.copyOf(pages);
        mode = mode == null ? AppendOperation.INSTANCE : mode;
    }

    public SetWrittenBookPagesFunction(List<LootCondition> conditions, List<Component> pages) {
        this(conditions, pages, AppendOperation.INSTANCE);
    }
}
