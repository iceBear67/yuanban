package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.BlockEntityType;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code set_loot_table}：向物品写入容器战利品表的引用（{@code container_loot} 组件）。JSON 字段
 * {@code name}（战利品表 ID，经 {@link #name()} 暴露给 {@code LootContents.tables()} 收集）、
 * {@code seed}（默认 {@code 0}）、{@code type}（方块实体类型 ID，按方块实体类型注册表解码）。
 */
public record SetContainerLootTableFunction(List<LootCondition> conditions,
                                            Key name,
                                            long seed,
                                            BlockEntityType type) implements LootFunction {
    public SetContainerLootTableFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }

    public SetContainerLootTableFunction(List<LootCondition> conditions, Key name, BlockEntityType type) {
        this(conditions, name, 0L, type);
    }
}
