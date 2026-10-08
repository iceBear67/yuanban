package io.ib67.yuanban.loot.function;

import java.util.List;
import java.util.Objects;

/**
 * 一组值加一个列表操作：原版 {@code ListOperation$StandAlone}。JSON 是对象，{@code values} 是值的列表，
 * 列表操作的 {@code mode} 等字段内联在同一个对象中。
 */
public record StandAloneOperation<T>(List<T> values, ListOperation operation) {
    public StandAloneOperation {
        values = List.copyOf(values);
        Objects.requireNonNull(operation, "operation");
    }
}
