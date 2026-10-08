package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.item.Material;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code set_item}：替换物品，保留数量与组件补丁。JSON 字段 {@code item}（物品 ID，不接受空气）。
 */
public record SetItemFunction(List<LootCondition> conditions, Material item) implements LootFunction {
    public SetItemFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(item, "item");
    }
}
