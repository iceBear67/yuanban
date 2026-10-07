package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * 原版 {@code set_container_loot_table}：向容器方块实体写入战利品表引用。JSON 字段
 * {@code name}（战利品表 ID，本模块经 {@link #name()} 暴露给 {@code LootContents.tables()} 收集）、
 * {@code seed}（默认 {@code 0}）、{@code type}（方块实体类型 ID）。
 */
public record SetContainerLootTableFunction(List<LootCondition> conditions,
                                            Key name,
                                            long seed,
                                            Key type) implements LootFunction {
    public SetContainerLootTableFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public SetContainerLootTableFunction(List<LootCondition> conditions, Key name, Key type) {
        this(conditions, name, 0L, type);
    }

    @Override
    public java.util.List<LootFunction> children() {
        return List.of();
    }
}
