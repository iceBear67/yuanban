package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

import java.util.Objects;
import java.util.Set;

/**
 * 原版 {@code random_chance_with_enchanted_bonus}：攻击者带有附魔时按附魔等级计算概率，否则用未附魔的概率。
 * JSON 字段 {@code unenchanted_chance}（[0, 1] 的浮点数）、{@code enchanted_chance}（原版 {@code LevelBasedValue}）、
 * {@code enchantment}（附魔 ID，按附魔注册表检查）。
 */
public record RandomChanceWithEnchantedBonusCondition(float unenchantedChance,
                                                      LevelBasedValue enchantedChance,
                                                      RegistryKey<Enchantment> enchantment) implements LootCondition {
    public RandomChanceWithEnchantedBonusCondition {
        Objects.requireNonNull(enchantedChance, "enchantedChance");
        Objects.requireNonNull(enchantment, "enchantment");
    }

    /**
     * 对应原版 {@code LootItemRandomChanceWithEnchantedBonusCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ATTACKING_ENTITY);
    }
}
