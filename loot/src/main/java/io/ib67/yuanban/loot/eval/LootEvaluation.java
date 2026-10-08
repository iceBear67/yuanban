package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.context.ContextKey;
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
import net.minestom.server.ServerProcess;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.utils.Range;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Random;

/**
 * 求值器层的运行时辅助方法：数据 record 保持纯数据，这里按原版同名类的运行语义读取它们。
 * 数字提供器的取值规则逐条来自原版 26.2 {@code providers.number} 包。
 */
public final class LootEvaluation {
    private LootEvaluation() {
    }

    /**
     * Minestom 缺少原版求值所需的数据时抛出的异常。每个调用处都带有写明缺少哪些数据的待办注释。
     */
    public static LootException unsupported(String what) {
        return new LootException("Not supported on Minestom: " + what);
    }

    // ------------------------------------------------------------------
    // 数字提供器
    // ------------------------------------------------------------------

    /**
     * 原版 {@code NumberProvider#getInt}：缺省是 {@code Math.round(getFloat())}；{@code uniform} 用
     * {@code Mth.nextInt} 在两端的整数值之间取值，{@code binomial} 直接计数，{@code sum} 对浮点数之和向下取整，
     * {@code storage} 取数值标签的整数值。
     */
    public static int getInt(NumberProvider provider, LootContext context, LootEnvironment env) {
        return switch (provider) {
            case UniformValue uniform -> Mth.nextInt(context.random(),
                    getInt(uniform.min(), context, env), getInt(uniform.max(), context, env));
            case BinomialValue binomial -> binomial(binomial, context, env);
            case SumValue sum -> Mth.floor(sum(sum, context, env));
            case StorageValue storage -> NbtPaths.numeric(storage.path(), env.storage().root(storage.storage()), 0).intValue();
            case ConstantValue ignored -> Math.round(getFloat(provider, context, env));
            case ScoreboardValue ignored -> Math.round(getFloat(provider, context, env));
            case EnchantmentLevelValue ignored -> Math.round(getFloat(provider, context, env));
            case EnvironmentAttributeValue ignored -> Math.round(getFloat(provider, context, env));
        };
    }

    /**
     * 原版 {@code NumberProvider#getFloat}。
     */
    public static float getFloat(NumberProvider provider, LootContext context, LootEnvironment env) {
        return switch (provider) {
            case ConstantValue constant -> constant.value();
            case UniformValue uniform -> Mth.nextFloat(context.random(),
                    getFloat(uniform.min(), context, env), getFloat(uniform.max(), context, env));
            case BinomialValue binomial -> binomial(binomial, context, env);
            case SumValue sum -> sum(sum, context, env);
            case ScoreboardValue scoreboard -> score(scoreboard, context, env);
            case EnchantmentLevelValue enchantmentLevel -> levelBased(enchantmentLevel.amount(),
                    requireTyped(context, ContextKey.ENCHANTMENT_LEVEL, Integer.class));
            case StorageValue storage -> NbtPaths.numeric(storage.path(), env.storage().root(storage.storage()), 0.0F).floatValue();
            // TODO(minestom): Minestom 不计算环境属性在某个位置的取值（原版 EnvironmentAttributeSystem）
            case EnvironmentAttributeValue attribute -> throw unsupported("value of the environment attribute "
                    + attribute.attribute().key().asString());
        };
    }

    /**
     * 原版 {@code BinomialDistributionGenerator#getInt}：{@code n} 次 {@code nextFloat() < p} 中成立的次数。
     */
    private static int binomial(BinomialValue binomial, LootContext context, LootEnvironment env) {
        int n = getInt(binomial.n(), context, env);
        float p = getFloat(binomial.p(), context, env);
        var random = context.random();
        int result = 0;
        for (int i = 0; i < n; i++) {
            if (random.nextFloat() < p) {
                result++;
            }
        }
        return result;
    }

    private static float sum(SumValue sum, LootContext context, LootEnvironment env) {
        float value = 0.0F;
        for (var summand : sum.summands()) {
            value += getFloat(summand, context, env);
        }
        return value;
    }

    /**
     * 原版 {@code ScoreboardValue#getFloat}：记录名不存在（上下文中没有该实体，或实体不参与记分）、计分项不存在或
     * 没有分数时为 0，否则为分数乘以 {@code scale}。
     */
    private static float score(ScoreboardValue scoreboard, LootContext context, LootEnvironment env) {
        var view = env.scoreboards();
        Optional<String> holder = switch (scoreboard.target()) {
            case ScoreboardName.Fixed fixed -> Optional.of(fixed.name());
            case ScoreboardName.Context target -> {
                var entity = entity(context, target.target().contextParam());
                yield entity == null ? Optional.empty() : view.getScoreboardName(entity);
            }
        };
        if (holder.isEmpty()) {
            return 0.0F;
        }
        var score = view.getScore(holder.get(), scoreboard.score());
        return score.isEmpty() ? 0.0F : score.getAsInt() * scoreboard.scale();
    }

    /**
     * 原版 {@code LevelBasedValue#calculate(int level)}。
     */
    public static float levelBased(LevelBasedValue value, int level) {
        return switch (value) {
            case LevelBasedValue.Constant constant -> constant.value();
            case LevelBasedValue.Linear linear -> linear.base() + linear.perLevelAboveFirst() * (level - 1);
            case LevelBasedValue.Clamped clamped -> Mth.clamp(levelBased(clamped.value(), level), clamped.min(), clamped.max());
            case LevelBasedValue.Fraction fraction -> {
                float denominator = levelBased(fraction.denominator(), level);
                yield denominator == 0.0F ? 0.0F : levelBased(fraction.numerator(), level) / denominator;
            }
            case LevelBasedValue.LevelsSquared levelsSquared -> level * level + levelsSquared.added();
            case LevelBasedValue.Lookup lookup -> level <= lookup.values().size()
                    ? lookup.values().get(level - 1)
                    : levelBased(lookup.fallback(), level);
            case LevelBasedValue.Exponent exponent ->
                    (float) Math.pow(levelBased(exponent.base(), level), levelBased(exponent.power(), level));
        };
    }

    /**
     * 原版 {@code IntRange#clamp}：两端都有时用 {@code Mth.clamp}，下界大于上界时结果是上界。
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
        return Mth.clamp(value, getInt(min, context, env), getInt(max, context, env));
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
        return Mth.floor(value);
    }

    /**
     * 原版 {@code Mth.nextInt(random, min, max)}：下界不小于上界时直接取下界，否则在 {@code [min, max]} 闭区间抽取。
     */
    public static int nextInt(Random random, int min, int max) {
        return Mth.nextInt(random, min, max);
    }

    // ------------------------------------------------------------------
    // 范围
    // ------------------------------------------------------------------

    /**
     * 原版 {@code MinMaxBounds.Doubles#matches}：下界大于取值或上界小于取值时不通过，与 NaN 比较时通过。
     */
    public static boolean matches(Range.Double bounds, double value) {
        if (bounds.min() != null && bounds.min() > value) {
            return false;
        }
        return bounds.max() == null || !(bounds.max() < value);
    }

    /**
     * 原版 {@code MinMaxBounds.Doubles#matchesSqr}：上下界先平方再与平方值比较。
     */
    public static boolean matchesSqr(Range.Double bounds, double valueSqr) {
        return io.ib67.yuanban.codec.MinMaxBounds.matchesSqr(bounds, valueSqr);
    }

    // ------------------------------------------------------------------
    // 上下文参数
    // ------------------------------------------------------------------

    /**
     * 原版 {@code LootContext#getLevel}：本模块的上下文可以没有世界，需要世界的求值在没有世界时抛出异常。
     */
    public static Instance level(LootContext context) {
        var level = context.level();
        if (level == null) {
            throw new LootException("The loot context has no level, but the evaluation needs one");
        }
        return level;
    }

    /**
     * 按原版 {@code getOptionalParameter} 读取实体参数；值不是实体时抛出异常。
     */
    public static @Nullable Entity entity(LootContext context, ContextKey<?> key) {
        return findTyped(context, key, Entity.class);
    }

    /**
     * 读取 {@link ContextKey#ORIGIN}。
     */
    public static @Nullable Point origin(LootContext context) {
        return findTyped(context, ContextKey.ORIGIN, Point.class);
    }

    /**
     * 读取 {@link ContextKey#TOOL}。
     */
    public static @Nullable ItemStack tool(LootContext context) {
        return findTyped(context, ContextKey.TOOL, ItemStack.class);
    }

    /**
     * 读取 {@link ContextKey#BLOCK_STATE} 或 {@link ContextKey#BLOCK_ENTITY}。
     */
    public static @Nullable Block block(LootContext context, ContextKey<?> key) {
        return findTyped(context, key, Block.class);
    }

    /**
     * 读取 {@link ContextKey#DAMAGE_SOURCE}。
     */
    public static @Nullable Damage damageSource(LootContext context) {
        return findTyped(context, ContextKey.DAMAGE_SOURCE, Damage.class);
    }

    /**
     * 读取 {@link ContextKey#EXPLOSION_RADIUS}。
     */
    public static @Nullable Float explosionRadius(LootContext context) {
        return findTyped(context, ContextKey.EXPLOSION_RADIUS, Float.class);
    }

    /**
     * 原版 {@code getOptionalParameter}：参数缺失时为 {@code null}，类型不符时抛出 {@link LootException}。
     */
    public static <T> @Nullable T findTyped(LootContext context, ContextKey<?> key, Class<T> type) {
        var value = context.find(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw wrongType(key, type, value);
        }
        return type.cast(value);
    }

    /**
     * 原版 {@code getParameter}：参数缺失时抛出 {@link java.util.NoSuchElementException}，类型不符时抛出 {@link LootException}。
     */
    public static <T> T requireTyped(LootContext context, ContextKey<?> key, Class<T> type) {
        var value = context.require(key);
        if (!type.isInstance(value)) {
            throw wrongType(key, type, value);
        }
        return type.cast(value);
    }

    private static LootException wrongType(ContextKey<?> key, Class<?> type, Object value) {
        return new LootException(key.id().asString() + " must be a " + type.getSimpleName() + ", but was " + value.getClass().getName());
    }

    // ------------------------------------------------------------------
    // 附魔等级
    // ------------------------------------------------------------------

    /**
     * 原版 {@code EnchantmentHelper.getItemEnchantmentLevel(Holder, ItemInstance)}：只读 {@code enchantments} 组件。
     */
    public static int itemEnchantmentLevel(RegistryKey<Enchantment> enchantment, ItemStack item) {
        return item.get(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY).level(enchantment);
    }

    /**
     * 原版 {@code EnchantmentHelper.getEnchantmentLevel(Holder, LivingEntity)}：附魔生效的各装备槽中物品等级的最大值。
     */
    public static int enchantmentLevel(RegistryKey<Enchantment> enchantment, LivingEntity entity, ServerProcess process) {
        var value = Enchanting.enchantment(enchantment, process);
        int bestLevel = 0;
        for (var slot : EquipmentSlot.values()) {
            if (value.slots().stream().anyMatch(group -> group.contains(slot))) {
                var item = entity.getEquipment(slot);
                if (!item.isAir()) {
                    bestLevel = Math.max(bestLevel, itemEnchantmentLevel(enchantment, item));
                }
            }
        }
        return bestLevel;
    }
}
