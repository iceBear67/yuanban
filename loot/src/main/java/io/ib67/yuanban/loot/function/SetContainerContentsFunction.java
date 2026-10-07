package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.entry.LootEntry;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * 原版 {@code set_contents}：设置容器的完整内容。JSON 字段 {@code component}（容器组件 ID）、
 * {@code entries}（战利品条目列表，原版编码是 {@code LootPoolEntryContainer}，本模块复用
 * {@link LootEntry}）。
 */
public record SetContainerContentsFunction(List<LootCondition> conditions,
                                           Key component,
                                           List<LootEntry> entries) implements LootFunction {
    public SetContainerContentsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
