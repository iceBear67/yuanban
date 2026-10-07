package io.ib67.yuanban.loot.function;

import java.util.List;

/**
 * "一组值跟一个列表操作"的组合：原版 {@code ListOperation$StandAlone}，JSON 字段 {@code values}、
 * {@code operation}（拆开后在 loot-table-parser 里组装）。本模块在 record 上把它们并列。
 */
public record StandAloneOperation<T>(List<T> values, ListOperation operation) {
    public StandAloneOperation {
        values = List.copyOf(values);
    }
}
