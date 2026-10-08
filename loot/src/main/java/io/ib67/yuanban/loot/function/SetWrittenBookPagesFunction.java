package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.text.Component;
import net.minestom.server.item.book.FilteredText;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code set_written_book_pages}：修改成书的书页。JSON 字段 {@code pages}（可过滤的文本组件，每页序列化后
 * 最长 32767 个字符）、内联的列表操作（必填的 {@code mode}，不限长度）。修改之后书页标记为未解析。
 */
public record SetWrittenBookPagesFunction(List<LootCondition> conditions,
                                          List<FilteredText<Component>> pages,
                                          ListOperation mode) implements LootFunction {
    public SetWrittenBookPagesFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        pages = List.copyOf(pages);
        Objects.requireNonNull(mode, "mode");
    }
}
