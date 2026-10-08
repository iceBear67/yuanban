package io.ib67.yuanban.enchant;

import io.ib67.yuanban.codec.HolderSet;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * 一个附魔提供器的解码结果，按原版的三个类型分派：
 * {@link Single}（{@code single}）、{@link ByCost}（{@code by_cost}）和
 * {@link ByCostWithDifficulty}（{@code by_cost_with_difficulty}）。
 */
public sealed interface ProviderEntry {
    /**
     * 本条目直接引用的附魔 ID：{@code single} 的 {@code enchantment} 字段，
     * 以及 {@code by_cost*} 的 {@code enchantments} 写成 ID 列表时的全部元素。
     */
    Set<Key> referencedEnchantments();

    /**
     * 本条目引用的附魔标签 ID：{@code by_cost*} 的 {@code enchantments} 写成 {@code #标签} 时的标签。
     */
    Set<Key> referencedEnchantmentTags();

    /**
     * 原版 {@code single}：给物品加上指定附魔，等级由整数提供器取样。
     */
    record Single(Key enchantment, IntProvider level) implements ProviderEntry {
        public Single {
            Objects.requireNonNull(enchantment, "enchantment");
            Objects.requireNonNull(level, "level");
        }

        @Override
        public Set<Key> referencedEnchantments() {
            return Set.of(enchantment);
        }

        @Override
        public Set<Key> referencedEnchantmentTags() {
            return Set.of();
        }
    }

    /**
     * 原版 {@code by_cost}：按提供的经验等级在一组附魔中选取。
     * {@code enchantments} 是原版接受的三种写法之一：{@code #标签}、单个附魔 ID 或者附魔 ID 列表。
     */
    record ByCost(HolderSet<Enchantment> enchantments, IntProvider cost) implements ProviderEntry {
        public ByCost {
            Objects.requireNonNull(enchantments, "enchantments");
            Objects.requireNonNull(cost, "cost");
        }

        @Override
        public Set<Key> referencedEnchantments() {
            return elements(enchantments);
        }

        @Override
        public Set<Key> referencedEnchantmentTags() {
            return tags(enchantments);
        }
    }

    /**
     * 原版 {@code by_cost_with_difficulty}：按经验等级下限和跨度（实际消耗随当前难度在
     * {@code [minCost, minCost + maxCostSpan * 难度]} 中取整）在一组附魔中选取。
     * 原版用 {@code intRange(1, 10000)} 校验 {@code minCost}、{@code intRange(0, 10000)} 校验
     * {@code maxCostSpan}，超出范围的数据在解码阶段被拒绝。
     */
    record ByCostWithDifficulty(HolderSet<Enchantment> enchantments, int minCost, int maxCostSpan) implements ProviderEntry {
        public ByCostWithDifficulty {
            Objects.requireNonNull(enchantments, "enchantments");
        }

        @Override
        public Set<Key> referencedEnchantments() {
            return elements(enchantments);
        }

        @Override
        public Set<Key> referencedEnchantmentTags() {
            return tags(enchantments);
        }
    }

    private static Set<Key> elements(HolderSet<Enchantment> set) {
        return switch (set) {
            case HolderSet.Direct<Enchantment> direct -> Set.copyOf(direct.keys().stream().map(RegistryKey::key).toList());
            case HolderSet.Named<Enchantment> _ -> Set.of();
        };
    }

    private static Set<Key> tags(HolderSet<Enchantment> set) {
        return switch (set) {
            case HolderSet.Direct<Enchantment> _ -> Set.of();
            case HolderSet.Named<Enchantment> named -> Set.of(named.tag().key());
        };
    }

    /**
     * 原版 {@code net.minecraft.util.valueproviders.IntProvider} 的 7 种类型。JSON 中的整数字面量解码为
     * {@link ConstantInt}，带 {@code type} 字段的对象按类型解码。
     * 只提供无参 {@link #sample()}，随机数取自 {@link ThreadLocalRandom}；原版的 {@code sample(RandomSource)}
     * 在 Minestom 中没有对应物。嵌套的提供器使用同一个线程的随机数。
     */
    sealed interface IntProvider {
        int sample();

        /**
         * 原版 {@code IntProvider.minInclusive()}：取样结果的下限。
         */
        int minInclusive();

        /**
         * 原版 {@code IntProvider.maxInclusive()}：取样结果的上限。
         */
        int maxInclusive();

        /**
         * 原版 {@code ConstantInt}（{@code constant}）。
         */
        record ConstantInt(int value) implements IntProvider {
            @Override
            public int sample() {
                return value;
            }

            @Override
            public int minInclusive() {
                return value;
            }

            @Override
            public int maxInclusive() {
                return value;
            }
        }

        /**
         * 原版 {@code UniformInt}（{@code uniform}）：在 {@code [minInclusive, maxInclusive]} 中均匀取样。
         */
        record UniformInt(int minInclusive, int maxInclusive) implements IntProvider {
            @Override
            public int sample() {
                return randomBetweenInclusive(ThreadLocalRandom.current(), minInclusive, maxInclusive);
            }
        }

        /**
         * 原版 {@code BiasedToBottomInt}（{@code biased_to_bottom}）：先取样一个上限，再在下限和这个上限之间取样，
         * 结果偏向较小的值。
         */
        record BiasedToBottomInt(int minInclusive, int maxInclusive) implements IntProvider {
            @Override
            public int sample() {
                var random = ThreadLocalRandom.current();
                return minInclusive + random.nextInt(random.nextInt(maxInclusive - minInclusive + 1) + 1);
            }
        }

        /**
         * 原版 {@code ClampedInt}（{@code clamped}）：把 {@code source} 的取样结果限制在
         * {@code [minInclusive, maxInclusive]} 中。
         * <p>
         * 与原版相同，{@link #minInclusive()} 和 {@link #maxInclusive()} 返回和 {@code source} 的范围求交之后的值，
         * JSON 中的两个字段只用于取样时的限制。
         */
        record ClampedInt(IntProvider source, int minInclusive, int maxInclusive) implements IntProvider {
            public ClampedInt {
                Objects.requireNonNull(source, "source");
            }

            @Override
            public int sample() {
                return Math.min(Math.max(source.sample(), this.minInclusive), this.maxInclusive);
            }

            @Override
            public int minInclusive() {
                return Math.max(this.minInclusive, source.minInclusive());
            }

            @Override
            public int maxInclusive() {
                return Math.min(this.maxInclusive, source.maxInclusive());
            }
        }

        /**
         * 原版 {@code WeightedListInt}（{@code weighted_list}）：按权重选一个提供器再取样。
         * 权重之和超过 {@link Integer#MAX_VALUE} 时构造失败，与原版 {@code WeightedList} 相同。
         */
        record WeightedListInt(List<Weighted> distribution) implements IntProvider {
            public WeightedListInt {
                distribution = List.copyOf(distribution);
                totalWeight(distribution);
            }

            /**
             * 原版 {@code WeightedRandom.getTotalWeight}。
             *
             * @throws IllegalArgumentException 权重之和超过 {@link Integer#MAX_VALUE}
             */
            public int totalWeight() {
                return totalWeight(distribution);
            }

            @Override
            public int sample() {
                var total = totalWeight();
                if (total == 0) {
                    throw new IllegalStateException("Weighted list has no elements");
                }
                var selection = ThreadLocalRandom.current().nextInt(total);
                for (var entry : distribution) {
                    selection -= entry.weight();
                    if (selection < 0) {
                        return entry.data().sample();
                    }
                }
                throw new IllegalStateException(selection + " exceeded total weight");
            }

            @Override
            public int minInclusive() {
                var min = Integer.MAX_VALUE;
                for (var entry : distribution) {
                    min = Math.min(min, entry.data().minInclusive());
                }
                return min;
            }

            @Override
            public int maxInclusive() {
                var max = Integer.MIN_VALUE;
                for (var entry : distribution) {
                    max = Math.max(max, entry.data().maxInclusive());
                }
                return max;
            }

            private static int totalWeight(List<Weighted> distribution) {
                var total = 0L;
                for (var entry : distribution) {
                    total += entry.weight();
                }
                if (total > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
                }
                return (int) total;
            }

            /**
             * 原版 {@code Weighted}：{@code data} 是提供器，{@code weight} 非负。
             */
            public record Weighted(IntProvider data, int weight) {
                public Weighted {
                    Objects.requireNonNull(data, "data");
                    if (weight < 0) {
                        throw new IllegalArgumentException("Weight should be >= 0");
                    }
                }
            }
        }

        /**
         * 原版 {@code ClampedNormalInt}（{@code clamped_normal}）：按正态分布取样，限制在
         * {@code [minInclusive, maxInclusive]} 中之后截断为整数。
         */
        record ClampedNormalInt(float mean, float deviation, int minInclusive, int maxInclusive) implements IntProvider {
            @Override
            public int sample() {
                var value = mean + (float) ThreadLocalRandom.current().nextGaussian() * deviation;
                float min = minInclusive;
                return (int) (value < min ? min : Math.min(value, maxInclusive));
            }
        }

        /**
         * 原版 {@code TrapezoidInt}（{@code trapezoid}，字段名为 {@code min}、{@code max}、{@code plateau}）：
         * 两个均匀分布相加得到的梯形分布，{@code plateau} 是中间平台的宽度。
         */
        record TrapezoidInt(int minInclusive, int maxInclusive, int plateau) implements IntProvider {
            @Override
            public int sample() {
                var random = ThreadLocalRandom.current();
                if (plateau == 0 && maxInclusive == -minInclusive) {
                    return random.nextInt(maxInclusive + 1) - random.nextInt(maxInclusive + 1);
                }
                var range = maxInclusive - minInclusive;
                if (plateau == range) {
                    return randomBetweenInclusive(random, minInclusive, maxInclusive);
                }
                var plateauStart = (range - plateau) / 2;
                var plateauEnd = range - plateauStart;
                return minInclusive + randomBetweenInclusive(random, 0, plateauEnd) + randomBetweenInclusive(random, 0, plateauStart);
            }
        }

        /**
         * 原版 {@code Mth.randomBetweenInclusive}。
         */
        private static int randomBetweenInclusive(RandomGenerator random, int minInclusive, int maxInclusive) {
            return random.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
        }
    }
}
