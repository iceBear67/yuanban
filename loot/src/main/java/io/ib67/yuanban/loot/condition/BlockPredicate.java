package io.ib67.yuanban.loot.condition;

import net.kyori.adventure.key.Key;

import java.util.Map;
import java.util.Optional;

/**
 * 方块状态谓词（{@code block_state_property} 的 {@code predicate} 字段），对应原版
 * {@code net.minecraft.advancements.predicates.StatePropertiesPredicate}。
 * 本模块承载方块 ID 与期望的属性键值；原版 StatePropertiesPredicate 中更复杂的范围匹配由解析器原样保存。
 */
public record BlockPredicate(Key block, Optional<Map<String, String>> properties) {
    public BlockPredicate {
        properties = properties.map(Map::copyOf);
    }

    public BlockPredicate(Key block) {
        this(block, Optional.empty());
    }
}
