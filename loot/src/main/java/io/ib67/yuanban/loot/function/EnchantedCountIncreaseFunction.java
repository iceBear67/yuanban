package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.NumberProvider;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 原版 {@code enchanted_count_increase}：按攻击者身上的附魔等级增加数量。JSON 字段 {@code enchantment}
 * （附魔 ID，按附魔注册表检查）、{@code count}（数字提供器）、{@code limit}（默认 {@code 0}，表示不限制）。
 */
public record EnchantedCountIncreaseFunction(List<LootCondition> conditions,
                                             RegistryKey<Enchantment> enchantment,
                                             NumberProvider count,
                                             int limit) implements LootFunction {
    public EnchantedCountIncreaseFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(enchantment, "enchantment");
        Objects.requireNonNull(count, "count");
    }

    public EnchantedCountIncreaseFunction(List<LootCondition> conditions,
                                          RegistryKey<Enchantment> enchantment,
                                          NumberProvider count) {
        this(conditions, enchantment, count, 0);
    }

    /**
     * 对应原版 {@code EnchantedCountIncreaseFunction#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ATTACKING_ENTITY);
    }
}
