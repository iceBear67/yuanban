package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code sequence}：按顺序应用子修饰器。JSON 字段 {@code functions}，元素只能是带 {@code function}
 * 字段的修饰器对象；修饰器的位置上直接写修饰器列表时也解码成本类型。原版的 {@code sequence} 没有
 * {@code conditions} 字段，{@link #conditions()} 总是空列表。
 */
public record SequenceFunction(List<LootFunction> functions) implements LootFunction {
    public SequenceFunction {
        functions = List.copyOf(functions);
    }

    @Override
    public List<LootCondition> conditions() {
        return List.of();
    }

    @Override
    public List<LootFunction> children() {
        return functions;
    }
}
