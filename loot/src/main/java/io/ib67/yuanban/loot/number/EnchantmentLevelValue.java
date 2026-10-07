package io.ib67.yuanban.loot.number;

/**
 * 按魔咒等级取值：原版 {@code providers.number.EnchantmentLevelProvider}，JSON 字段 {@code amount}。
 */
public record EnchantmentLevelValue(LevelBasedValue amount) implements NumberProvider {
}
