package io.ib67.yuanban.loot.condition;

import net.kyori.adventure.key.Key;

import java.util.Objects;

/**
 * 原版 {@code minecraft:reference} 条件：仅记录谓词的 ID（任意命名空间），运行时经谓词解析器查找。
 * JSON 字段 {@code name}。
 */
public record ReferenceCondition(Key name) implements LootCondition {
    public ReferenceCondition {
        Objects.requireNonNull(name, "name");
    }
}
