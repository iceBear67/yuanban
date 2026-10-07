package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import net.kyori.adventure.key.Key;

import java.util.Set;

/**
 * 原版 {@code random_chance_with_enchanted_bonus}：魔咒存在时按魔咒等级计算加成后判定概率。JSON 字段
 * {@code unenchanted_chance}、{@code enchanted_chance}（原版 {@code LevelBasedValue}）、{@code enchantment}。
 */
public record RandomChanceWithEnchantedBonusCondition(float unenchantedChance,
                                                      LevelBasedValue enchantedChance,
                                                      Key enchantment) implements LootCondition {

    /**
     * 对应原版 {@code LootItemRandomChanceWithEnchantedBonusCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ATTACKING_ENTITY);
    }
}
