package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code sequence}：按顺序应用子修饰器。JSON 字段 {@code functions}。
 */
public record SequenceFunction(List<LootCondition> conditions,
                               List<LootFunction> functions) implements LootFunction {
    public SequenceFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
    }

    @Override
    public List<LootFunction> children() {
        return functions;
    }
}
