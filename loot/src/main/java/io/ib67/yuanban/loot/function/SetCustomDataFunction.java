package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.util.List;

/**
 * 原版 {@code set_custom_data}：把一段 NBT 写到物品的自定义数据组件。JSON 字段 {@code tag}
 * （原版 NBT 字符串或复合标签对象，解析在 loot-table-parser 完成）。
 */
public record SetCustomDataFunction(List<LootCondition> conditions,
                                    CompoundBinaryTag tag) implements LootFunction {
    public SetCustomDataFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
