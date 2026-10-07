package io.ib67.yuanban.enchant;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一个附魔提供器的解码结果，按原版的三个类型分派：
 * {@link Single}（{@code single}）、{@link ByCost}（{@code by_cost}）和
 * {@link ByCostWithDifficulty}（{@code by_cost_with_difficulty}）。
 */
public sealed interface ProviderEntry {
    /**
     * 本条目引用的附魔 ID；{@code single} 返回 {@code enchantment} 字段。
     * 引用附魔标签的类型返回 {@code null}。
     */
    default @Nullable Key referencedEnchantment() {
        return null;
    }

    /**
     * 本条目引用的附魔标签 ID；{@code by_cost} 和 {@code by_cost_with_difficulty}
     * 返回 {@code enchantments} 字段去掉 {@code #} 之后的值，其余类型返回 {@code null}。
     */
    default @Nullable Key referencedEnchantmentTag() {
        return null;
    }

    /**
     * 原版 {@code single}：给物品加上固定等级的指定附魔。
     */
    record Single(Key enchantment, IntProvider level) implements ProviderEntry {
        public Single {
            Objects.requireNonNull(enchantment, "enchantment");
            Objects.requireNonNull(level, "level");
        }

        @Override
        public Key referencedEnchantment() {
            return enchantment;
        }
    }

    /**
     * 原版 {@code by_cost}：按提供的经验等级在附魔标签中选一组附魔。
     * {@code enchantments} 是附魔标签 ID（{@code #} 已在解码时去掉）。
     */
    record ByCost(Key enchantments, IntProvider cost) implements ProviderEntry {
        public ByCost {
            Objects.requireNonNull(enchantments, "enchantments");
            Objects.requireNonNull(cost, "cost");
        }

        @Override
        public Key referencedEnchantmentTag() {
            return enchantments;
        }
    }

    /**
     * 原版 {@code by_cost_with_difficulty}：按经验等级下限和跨度（实际消耗随当前难度在
     * {@code [minCost, minCost + maxCostSpan * 难度]} 中取整）在附魔标签中选一组附魔。
     * {@code enchantments} 是附魔标签 ID（{@code #} 已在解码时去掉）。
     * 原版用 {@code intRange(1, 10000)} 校验 {@code minCost}、{@code intRange(0, 10000)} 校验
     * {@code maxCostSpan}，超出范围的数据在解码阶段被拒绝。
     */
    record ByCostWithDifficulty(Key enchantments, int minCost, int maxCostSpan) implements ProviderEntry {
        public ByCostWithDifficulty {
            Objects.requireNonNull(enchantments, "enchantments");
        }

        @Override
        public Key referencedEnchantmentTag() {
            return enchantments;
        }
    }

    /**
     * 整数提供器。原版 JSON 中有两种表示：整数字面量解码为 {@link ConstantInt}，
     * 带 {@code type} 字段的对象按类型解码（目前只有 {@code minecraft:uniform}，解码为 {@link UniformInt}）。
     * 只提供无参 {@link #sample()}；原版的 {@code sample(RandomSource)} 签名在 Minestom 中没有对应物。
     */
    sealed interface IntProvider {
        int sample();

        record ConstantInt(int value) implements IntProvider {
            @Override
            public int sample() {
                return value;
            }
        }

        record UniformInt(int minInclusive, int maxInclusive) implements IntProvider {
            @Override
            public int sample() {
                return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
            }
        }
    }
}
