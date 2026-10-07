package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.context.LootContext;
import io.ib67.yuanban.loot.context.LootEnvironment;
import io.ib67.yuanban.loot.number.BinomialValue;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EnchantmentLevelValue;
import io.ib67.yuanban.loot.number.EnvironmentAttributeValue;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import io.ib67.yuanban.loot.number.NumberProvider;
import io.ib67.yuanban.loot.number.ScoreboardName;
import io.ib67.yuanban.loot.number.ScoreboardValue;
import io.ib67.yuanban.loot.number.StorageValue;
import io.ib67.yuanban.loot.number.SumValue;
import io.ib67.yuanban.loot.number.UniformValue;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

import java.util.Random;

/**
 * 求值器层的运行时辅助方法：数据 record 保持纯数据，这里按原版同名类的运行语义读取它们。
 * 数字提供器的取值规则逐条来自原版 26.2 {@code providers.number} 包。
 */
public final class LootEvaluation {
    private LootEvaluation() {
    }

    /**
     * 原版 {@code NumberProvider#getInt}：{@code Math.round(getFloat())}。
     */
    public static int getInt(NumberProvider provider, LootContext context, LootEnvironment env) {
        return Math.round(getFloat(provider, context, env));
    }

    /**
     * 原版 {@code NumberProvider#getFloat}：{@code constant} 直接返回；{@code uniform} 用
     * {@code Mth.nextFloat(random, min.getFloat, max.getFloat)}；{@code binomial} 用
     * {@code n} 次 {@code nextFloat < p} 计数；{@code score} 读记分板后按 {@code scale} 缩放；
     * {@code storage} 与 {@code environment_attribute} 在本模块没有可用的宿主映射（见 javadoc 说明），
     * 按空处理和就地抛错。
     */
    public static float getFloat(NumberProvider provider, LootContext context, LootEnvironment env) {
        var random = context.random();
        return switch (provider) {
            case ConstantValue constant -> constant.value();
            case UniformValue uniform -> {
                float min = getFloat(uniform.min(), context, env);
                float max = getFloat(uniform.max(), context, env);
                // 原版 Mth.nextFloat(random, min, max)
                yield random.nextFloat() * (max - min) + min;
            }
            case BinomialValue binomial -> {
                int n = getInt(binomial.n(), context, env);
                float p = getFloat(binomial.p(), context, env);
                int successes = 0;
                for (int i = 0; i < n; i++) {
                    if (random.nextFloat() < p) successes++;
                }
                yield (float) successes;
            }
            case SumValue sum -> {
                float total = 0;
                for (var term : sum.terms()) {
                    total += getFloat(term, context, env);
                }
                yield total;
            }
            case ScoreboardValue scoreboard -> scoreFromBoard(scoreboard.target(), scoreboard.score(),
                    context, env) * scoreboard.scale();
            case EnchantmentLevelValue enchantmentLevel -> {
                var raw = context.require(io.ib67.yuanban.loot.context.ContextKey.ENCHANTMENT_LEVEL);
                var level = raw instanceof Number number ? number.intValue() : 0;
                yield levelBased(enchantmentLevel.amount(), level);
            }
            case StorageValue storage -> throw new io.ib67.yuanban.loot.LootException(
                    "storage number provider is not evaluable: loot only keeps CompoundBinaryTag, numeric results need the parser to supply a typed read: "
                            + storage.storage().asString() + " " + storage.path());
            case EnvironmentAttributeValue attribute -> throw new io.ib67.yuanban.loot.LootException(
                    "environment_attribute number provider has no minestom counterpart: " + attribute.attribute().asString());
        };
    }

    /**
     * 原版 {@code ScoresProvider#getOrDefault}：先按 {@code ScoreboardName} 求出记录名，再读记分板。
     */
    private static float scoreFromBoard(ScoreboardName target, String objective, LootContext context,
                                        LootEnvironment env) {
        var view = env.scoreboards();
        var name = switch (target) {
            case ScoreboardName.Fixed fixed -> java.util.Optional.of(fixed.name());
            case ScoreboardName.Context contextTarget -> {
                var entity = (Entity) context.require((io.ib67.yuanban.loot.context.ContextKey<Object>)
                        contextTarget.target().contextParam());
                yield view.getScoreboardName(entity);
            }
        };
        if (name.isEmpty()) return 0.0f;
        return view.getScore(name.get(), objective).orElse(0);
    }

    /**
     * 原版 {@code EnchantmentHelper.getItemEnchantmentLevel(Holder, ItemStack)}：读物品上的
     * {@code ENCHANTMENTS}（武器/装备类物品的当前魔咒，含 {@code STORED_ENCHANTMENTS} 书包装）。
     * Minestom 侧对应的语义：直接读 {@link DataComponents#ENCHANTMENTS}（附魔书则读
     * {@code STORED_ENCHANTMENTS}），逐层查询等级。
     */
    public static int itemEnchantmentLevel(LootContext context, net.kyori.adventure.key.Key enchantment,
                                            net.minestom.server.item.ItemStack tool) {
        var registry = context.process().enchantment();
        var key = RegistryKey.<Enchantment>of(enchantment);
        var level = 0;
        var enchantments = tool.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) level = Math.max(level, enchantments.level(key));
        var stored = tool.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) level = Math.max(level, stored.level(key));
        return level;
    }

    /**
     * 原版 {@code LevelBasedValue#calculate(int level)}。
     */
    public static float levelBased(LevelBasedValue value, int level) {
        return switch (value) {
            case LevelBasedValue.Constant constant -> constant.value();
            case LevelBasedValue.Linear linear -> linear.base() + linear.perLevelAboveFirst() * (level - 1);
            case LevelBasedValue.Clamped clamped ->
                    Math.clamp(levelBased(clamped.value(), level), clamped.min(), clamped.max());
            case LevelBasedValue.Fraction fraction -> {
                float denominator = levelBased(fraction.denominator(), level);
                yield denominator == 0 ? 0 : levelBased(fraction.numerator(), level) / denominator;
            }
            case LevelBasedValue.LevelsSquared levelsSquared ->
                    levelsSquared.added() + (float) level * level;
            case LevelBasedValue.Lookup lookup -> {
                var values = lookup.values();
                if (level < values.size()) {
                    yield values.get(level);
                }
                yield levelBased(lookup.fallback(), level);
            }
            case LevelBasedValue.Exponent exponent ->
                    (float) Math.pow(levelBased(exponent.base(), level), levelBased(exponent.power(), level));
        };
    }

    /**
     * 原版 {@code IntRange#clamp}。
     */
    public static int clamp(IntRange range, int value, LootContext context, LootEnvironment env) {
        var min = range.min();
        var max = range.max();
        if (min == null) {
            return max == null ? value : Math.min(getInt(max, context, env), value);
        }
        if (max == null) {
            return Math.max(getInt(min, context, env), value);
        }
        return Math.clamp(value, getInt(min, context, env), getInt(max, context, env));
    }

    /**
     * 原版 {@code IntRange#test}。
     */
    public static boolean test(IntRange range, int value, LootContext context, LootEnvironment env) {
        var min = range.min();
        var max = range.max();
        if (min == null) {
            return max == null || value <= getInt(max, context, env);
        }
        if (max == null) {
            return value >= getInt(min, context, env);
        }
        return value >= getInt(min, context, env) && value <= getInt(max, context, env);
    }

    /**
     * 原版 {@code Mth.floor(float)}。
     */
    public static int floor(float value) {
        return (int) Math.floor(value);
    }

    /**
     * 原版 {@code Mth.nextInt(random, min, max)}：上下界等价时直接取下界，否则在
     * {@code [min, max]} 闭区间抽取。
     */
    public static int nextInt(Random random, int min, int max) {
        if (min >= max) return min;
        return random.nextInt(max - min + 1) + min;
    }
}
