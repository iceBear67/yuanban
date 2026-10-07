package io.ib67.yuanban.loot.condition;

import net.kyori.adventure.key.Key;

/**
 * 原版 {@code minecraft:reference} 条件：仅记录谓词表的 ID，运行时经谓词解析器查找。
 * JSON 字段 {@code name}。
 */
public record ReferenceCondition(Key name) implements LootCondition {
}
