package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code copy_custom_data}：从 NBT 来源按路径复制数据到物品的自定义数据。JSON 字段
 * {@code source}（原版 {@code NbtProvider}）、{@code ops}（{@link CopyOperation} 列表）。
 * 与原版相同，本修饰器自己不引用上下文参数，来源引用的参数见 {@link NbtSource#referencedParams()}。
 */
public record CopyCustomDataFunction(List<LootCondition> conditions,
                                     NbtSource source,
                                     List<CopyOperation> ops) implements LootFunction {
    public CopyCustomDataFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(source, "source");
        ops = List.copyOf(ops);
    }
}
