package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.potion.PotionType;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code set_potion}：写入 {@code potion_contents} 组件的药水。JSON 字段 {@code id}（药水 ID，按药水注册表解码）。
 */
public record SetPotionFunction(List<LootCondition> conditions, PotionType id) implements LootFunction {
    public SetPotionFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(id, "id");
    }
}
