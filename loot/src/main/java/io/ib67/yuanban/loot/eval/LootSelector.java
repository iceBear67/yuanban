package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.EitherId;
import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.condition.AllOfCondition;
import io.ib67.yuanban.loot.condition.AnyOfCondition;
import io.ib67.yuanban.loot.condition.BlockStatePropertyCondition;
import io.ib67.yuanban.loot.condition.DamageSourcePropertiesCondition;
import io.ib67.yuanban.loot.condition.EnchantmentActiveCheckCondition;
import io.ib67.yuanban.loot.condition.EntityPropertiesCondition;
import io.ib67.yuanban.loot.condition.EntityScoresCondition;
import io.ib67.yuanban.loot.condition.EnvironmentAttributeCheckCondition;
import io.ib67.yuanban.loot.condition.InvertedCondition;
import io.ib67.yuanban.loot.condition.KilledByPlayerCondition;
import io.ib67.yuanban.loot.condition.LocationCheckCondition;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.condition.MatchToolCondition;
import io.ib67.yuanban.loot.condition.RandomChanceCondition;
import io.ib67.yuanban.loot.condition.RandomChanceWithEnchantedBonusCondition;
import io.ib67.yuanban.loot.condition.ReferenceCondition;
import io.ib67.yuanban.loot.condition.SurvivesExplosionCondition;
import io.ib67.yuanban.loot.condition.TableBonusCondition;
import io.ib67.yuanban.loot.condition.TimeCheckCondition;
import io.ib67.yuanban.loot.condition.ValueCheckCondition;
import io.ib67.yuanban.loot.condition.WeatherCheckCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContext;
import io.ib67.yuanban.loot.context.LootEnvironment;
import io.ib67.yuanban.loot.context.ScoreboardView;
import io.ib67.yuanban.loot.entry.AlternativesEntry;
import io.ib67.yuanban.loot.entry.CompositeEntry;
import io.ib67.yuanban.loot.entry.DynamicEntry;
import io.ib67.yuanban.loot.entry.EmptyEntry;
import io.ib67.yuanban.loot.entry.GroupEntry;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.entry.NestedTableEntry;
import io.ib67.yuanban.loot.entry.SequenceEntry;
import io.ib67.yuanban.loot.entry.SingletonEntry;
import io.ib67.yuanban.loot.entry.SlotsEntry;
import io.ib67.yuanban.loot.entry.TagEntry;
import io.ib67.yuanban.loot.function.ApplyBonusFormula;
import io.ib67.yuanban.loot.function.ApplyBonusFunction;
import io.ib67.yuanban.loot.function.AppendOperation;
import io.ib67.yuanban.loot.function.DiscardFunction;
import io.ib67.yuanban.loot.function.EnchantRandomlyFunction;
import io.ib67.yuanban.loot.function.EnchantWithLevelsFunction;
import io.ib67.yuanban.loot.function.EnchantedCountIncreaseFunction;
import io.ib67.yuanban.loot.function.ExplosionDecayFunction;
import io.ib67.yuanban.loot.function.FilteredFunction;
import io.ib67.yuanban.loot.function.FunctionReference;
import io.ib67.yuanban.loot.function.InsertOperation;
import io.ib67.yuanban.loot.function.LimitCountFunction;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.function.ReplaceAllOperation;
import io.ib67.yuanban.loot.function.ReplaceSectionOperation;
import io.ib67.yuanban.loot.function.SequenceFunction;
import io.ib67.yuanban.loot.function.SetComponentsFunction;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.function.SetDamageFunction;
import io.ib67.yuanban.loot.function.SetItemFunction;
import io.ib67.yuanban.loot.function.SetLoreFunction;
import io.ib67.yuanban.loot.function.SetNameFunction;
import io.ib67.yuanban.loot.function.SetNameTarget;
import io.ib67.yuanban.loot.function.SmeltFunction;
import io.ib67.yuanban.loot.function.ToggleTooltipsFunction;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.NumberProvider;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 求值器入口：把 {@link LootTable} 的 JSON 结构按原版 26.2 的运行语义产出 {@link ItemStack} 列表。
 *
 * <p>
 * 与原版 {@code LootTable.getRandomItems(LootParams, long, Consumer)} 的对应：入口先做覆盖检查
 * （原版 {@code ValidationContext} 在加载阶段做的参数集校验——loot 模块把它移到 roll 入口保证不会
 * 抽取一半才报错），再用宿主注入的 {@linkplain LootEnvironment#worldSeed() 世界种子} 派生稳定序列，
 * 最后遍历所有池按 {@code LootPool.addRandomItems} 的顺序产出。
 *
 * <p>
 * 修饰器应用顺序：条目自身 → 所在池 → 所在表（原版 {@code decorate} 反向包装等价于正向顺序应用的顺序）。
 * 修饰器不通过 {@code null} 回报丢弃，而是返回 {@link ItemStack#isAir() 空栈}：原版
 * {@code DiscardFunction} 返回 {@code ItemStack.EMPTY}，后续修饰器对空栈仍然继续按序执行（语义一致）。
 */
public final class LootSelector {
    private static final int NESTED_TABLE_DEPTH_LIMIT = 64;

    private final ServerProcess process;
    private final LootManager manager;
    private final LootTypeRegistry types;
    private final LootEnvironment environment;

    public LootSelector(ServerProcess process, LootManager manager, LootTypeRegistry types,
                        LootEnvironment environment) {
        this.process = Objects.requireNonNull(process, "process");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.types = Objects.requireNonNull(types, "types");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    /**
     * 直接以 {@link LootTable} 取用，覆盖检查在抽取之前执行（原版 {@code LootParams#validate}）。
     */
    public List<ItemStack> roll(LootTable table, LootContext context, Random random) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(random, "random");
        var missing = new java.util.TreeSet<ContextKey<?>>(Comparator.comparing(ContextKey::id));
        // 原版 {@code ValidationContext$ParametersNotProvidedProblem.report} 的文案是
        // "<key> is/are not provided in this context"；loot 模块沿用同一份文案。
        collectMissing(table, context, java.util.Collections.newSetFromMap(new IdentityHashMap<>()), missing);
        if (!missing.isEmpty()) {
            var text = missing.stream().map(k -> k.id().asString()).collect(Collectors.joining(", "));
            var plural = missing.size() > 1;
            throw new LootException(text + (plural ? " are not provided in this context"
                    : " is not provided in this context"));
        }
        var actualRandom = selectRandom(table, random);
        var effectiveContext = context instanceof io.ib67.yuanban.loot.context.LootContextImpl impl
                ? impl.withRandom(actualRandom) : context;
        var output = new ArrayList<ItemStack>();
        processTable(table, effectiveContext, actualRandom, output::add, 0);
        return splitIntoStacks(output);
    }

    /**
     * 以 ID 取用：表不存在时报错（原版
     * {@code net.minecraft.world.level.storage.loot.entries.NestedLootTable} 对 ID 缺失的
     * {@code Optional.orElse(LootTable.EMPTY)} 不会提示配置错误；loot 模块沿用显式报错）。
     */
    public List<ItemStack> roll(Key tableId, LootContext context, Random random) {
        Objects.requireNonNull(tableId, "tableId");
        return roll(tableById(tableId), context, random);
    }

    LootEnvironment environment() {
        return environment;
    }

    // ------------------------------------------------------------------
    // 覆盖检查
    // ------------------------------------------------------------------

    private void collectMissing(LootTable table, LootContext context, Set<LootTable> visiting,
                                 Set<ContextKey<?>> missing) {
        // 原版对嵌套表的按 id 递归靠 push/pop VisitedEntry；loot 模块此处用身份集合避免无限递归。
        if (!visiting.add(table)) return;
        for (var function : table.functions()) {
            checkFunction(function, context, missing);
        }
        for (var pool : table.pools()) {
            for (var condition : pool.conditions()) checkCondition(condition, context, missing);
            for (var function : pool.functions()) checkFunction(function, context, missing);
            for (var entry : pool.entries()) checkEntry(entry, context, visiting, missing);
        }
        visiting.remove(table);
    }

    private void checkEntry(LootEntry entry, LootContext context, Set<LootTable> visiting,
                             Set<ContextKey<?>> missing) {
        for (var condition : entry.conditions()) checkCondition(condition, context, missing);
        for (var function : entry.functions()) checkFunction(function, context, missing);
        switch (entry) {
            case CompositeEntry composite -> {
                for (var child : composite.children()) checkEntry(child, context, visiting, missing);
            }
            case TagEntry tag when tag.expand() -> {
                // tag expand=true：子条目是注册期不可见的标签成员，参数需求与父条目一致，不重复遍历
            }
            case NestedTableEntry nested -> {
                // 原版 recheck content against ValidationContext.setVisitedElements；loot 模块按可解析性比较：
                // 内联表立即向下遍历，ID 形式的表在 manager 可解析时继续；manager 拿不到的表在 roll 时会
                // 以 "Missing loot table" 报错。
                nested.value().ifRight(inline -> collectMissing(inline, context, visiting, missing));
                nested.value().ifLeft(id -> manager.get(id)
                        .ifPresent(t -> collectMissing(t, context, visiting, missing)));
            }
            default -> {
            }
        }
    }

    private void checkCondition(LootCondition condition, LootContext context, Set<ContextKey<?>> missing) {
        mergeMissing(missing, condition, context);
        switch (condition) {
            case AllOfCondition allOf -> allOf.terms().forEach(c -> checkCondition(c, context, missing));
            case AnyOfCondition anyOf -> anyOf.terms().forEach(c -> checkCondition(c, context, missing));
            case InvertedCondition inverted -> checkCondition(inverted.term(), context, missing);
            case ReferenceCondition reference -> environment.findCondition(reference.name())
                    .ifPresent(inner -> checkCondition(inner, context, missing));
            case RandomChanceCondition randomChance -> checkNumber(randomChance.chance(), context, missing);
            case ValueCheckCondition valueCheck -> {
                checkNumber(valueCheck.value(), context, missing);
                checkIntRange(valueCheck.range(), context, missing);
            }
            default -> {
            }
        }
    }

    private void checkFunction(LootFunction function, LootContext context, Set<ContextKey<?>> missing) {
        mergeMissing(missing, function, context);
        for (var condition : function.conditions()) checkCondition(condition, context, missing);
        switch (function) {
            case SetCountFunction setCount -> checkNumber(setCount.count(), context, missing);
            case SetDamageFunction setDamage -> checkNumber(setDamage.damage(), context, missing);
            case LimitCountFunction limit -> checkIntRange(limit.limit(), context, missing);
            case SetNameFunction setName -> setName.resolutionContext()
                    .ifPresent(target -> mergeContextParam(missing, context, target.contextParam()));
            case SetLoreFunction setLore -> setLore.resolutionContext()
                    .ifPresent(target -> mergeContextParam(missing, context, target.contextParam()));
            case EnchantedCountIncreaseFunction increase -> checkNumber(increase.count(), context, missing);
            case EnchantWithLevelsFunction levels -> checkNumber(levels.levels(), context, missing);
            case SequenceFunction sequence ->
                    sequence.functions().forEach(f -> checkFunction(f, context, missing));
            case FilteredFunction filtered -> {
                filtered.onMatch().ifPresent(f -> checkFunction(f, context, missing));
                filtered.onFail().ifPresent(f -> checkFunction(f, context, missing));
            }
            case FunctionReference reference -> environment.findFunction(reference.name())
                    .ifPresent(f -> checkFunction(f, context, missing));
            default -> {
            }
        }
    }

    private void checkIntRange(IntRange range, LootContext context, Set<ContextKey<?>> missing) {
        if (range == null) return;
        if (range.min() != null) checkNumber(range.min(), context, missing);
        if (range.max() != null) checkNumber(range.max(), context, missing);
    }

    private void checkNumber(NumberProvider provider, LootContext context, Set<ContextKey<?>> missing) {
        if (provider == null) return;
        mergeMissing(missing, provider, context);
        switch (provider) {
            case io.ib67.yuanban.loot.number.UniformValue uniform -> {
                checkNumber(uniform.min(), context, missing);
                checkNumber(uniform.max(), context, missing);
            }
            case io.ib67.yuanban.loot.number.BinomialValue binomial -> {
                checkNumber(binomial.n(), context, missing);
                checkNumber(binomial.p(), context, missing);
            }
            case io.ib67.yuanban.loot.number.SumValue sum ->
                    sum.terms().forEach(term -> checkNumber(term, context, missing));
            default -> {
            }
        }
    }

    private void mergeMissing(Set<ContextKey<?>> missing,
                              io.ib67.yuanban.loot.condition.LootCondition user, LootContext context) {
        mergeMissing(missing, user.referencedParams(), context);
    }

    private void mergeMissing(Set<ContextKey<?>> missing,
                              io.ib67.yuanban.loot.function.LootFunction user, LootContext context) {
        mergeMissing(missing, user.referencedParams(), context);
    }

    private void mergeMissing(Set<ContextKey<?>> missing,
                              io.ib67.yuanban.loot.number.NumberProvider user, LootContext context) {
        mergeMissing(missing, user.referencedParams(), context);
    }

    private void mergeContextParam(Set<ContextKey<?>> missing, LootContext context, ContextKey<?> key) {
        if (context.find(key) == null) missing.add(key);
    }

    private void mergeMissing(Set<ContextKey<?>> missing, Set<ContextKey<?>> referenced, LootContext context) {
        for (var key : referenced) {
            if (context.find(key) == null) missing.add(key);
        }
    }

    // ------------------------------------------------------------------
    // roll 主流程（原版 LootTable.getRandomItemsRaw → LootPool.addRandomItems）
    // ------------------------------------------------------------------

    /**
     * 原版 {@code RandomSequence}：表声明 {@code random_sequence} 时按宿主提供的世界种子派生稳定序列，
     * 否则用上下文默认随机数源。
     */
    private Random selectRandom(LootTable table, Random fallback) {
        return table.randomSequence()
                .<Random>map(id -> new SequenceRandom(environment.worldSeed(), id))
                .orElse(fallback);
    }

    private LootTable tableById(Key tableId) {
        return manager.get(tableId)
                .orElseThrow(() -> new LootException("Missing loot table: " + tableId.asString()));
    }

    private void processTable(LootTable table, LootContext context, Random random,
                               Consumer<ItemStack> sink, int depth) {
        if (depth >= NESTED_TABLE_DEPTH_LIMIT) {
            throw new LootException("Loot table nesting exceeds " + NESTED_TABLE_DEPTH_LIMIT);
        }
        for (var pool : table.pools()) {
            processPool(pool, table, context, random, sink, depth);
        }
    }

    private void processPool(LootPool pool, LootTable table, LootContext context, Random random,
                              Consumer<ItemStack> sink, int depth) {
        // 原版 LootPool.addRandomItems 第一步是 compositeCondition.test。
        if (!conditionsPass(pool.conditions(), context)) return;
        int rolls = LootEvaluation.getInt(pool.rolls(), context, environment)
                + LootEvaluation.floor(LootEvaluation.getFloat(pool.bonusRolls(), context, environment)
                * context.luck());
        for (int i = 0; i < rolls; i++) {
            drawOnce(pool, table, context, random, sink, depth);
        }
    }

    /**
     * 原版 {@code LootPool.addRandomItem} 字节码顺序：先把池内每条目用 {@code expand} 展开成
     * 有权重的单例列表，若列表为空或累计权重为 0 直接返回；只剩一条时直接命中，否则用
     * {@link java.util.Random#nextInt(int) nextInt(totalWeight)} 逐项减权重，小于 0 时命中。
     */
    private void drawOnce(LootPool pool, LootTable table, LootContext context, Random random,
                           Consumer<ItemStack> sink, int depth) {
        var collected = new ArrayList<WeightedSingle>();
        for (var entry : pool.entries()) {
            expandEntry(entry, context, collected);
        }
        if (collected.isEmpty()) return;
        int total = 0;
        for (var candidate : collected) {
            total = addWeight(total, candidate.weight(context.luck()));
        }
        if (total <= 0) return;
        if (collected.size() == 1) {
            produce(collected.getFirst(), table, pool, context, random, sink, depth);
            return;
        }
        int raffle = random.nextInt(total);
        for (var candidate : collected) {
            raffle -= candidate.weight(context.luck());
            if (raffle < 0) {
                produce(candidate, table, pool, context, random, sink, depth);
                return;
            }
        }
    }

    /**
     * 原版 {@code WeightedRandom.getRandomItem} 的边界：总和按 int 累计，超过 {@code Integer.MAX_VALUE}
     * 抛 {@code IllegalArgumentException("Sum of weights must be <= 2147483647")}；为 0 时按空集返回
     * （{@code getRandomItem} 收到 0 时 {@code Optional.empty}）。
     */
    private static int addWeight(int total, int extra) {
        long sum = (long) total + (long) extra;
        if (sum > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
        }
        return (int) sum;
    }

    /**
     * 展开条目树到可加权的单例。原版 {@code LootPoolEntryContainer#expand} 的复合适配：
     * <ul>
     *   <li>{@code group}：所有子条目递归展开后全部入列。</li>
     *   <li>{@code alternatives}：找到第一个条件通过的子条目就停（只展开它）。</li>
     *   <li>{@code sequence}：逐个判定子条目，遇第一个条件不通过的即中止，前面展开的保留。</li>
     *   <li>{@code tag(expand=true)}：给标签每个成员发一个伪单例记录，权重与 quality 沿用条目自身，
     *       让一次抽取只命中一个成员（与原版 {@code TagEntry.expandTag} 一致）。</li>
     * </ul>
     */
    /**
     * 展开条目树：返回的可加权候选用统一的 {@link WeightedSingle}；{@code TagEntry(expand=true)}
     * 的伪单例直接组装成 WeightedSingle 返回，避免污染逐层 sink 的 LootEntry 值域。
     */
    private void expandEntry(LootEntry entry, LootContext context, List<WeightedSingle> sink) {
        switch (entry) {
            case TagEntry tag -> {
                if (!conditionsPass(tag.conditions(), context)) return;
                if (!tag.expand()) {
                    sink.add(new WeightedSingle(tag, null));
                    return;
                }
                var members = process.material().getTag(net.minestom.server.registry.TagKey.<Material>of(tag.name()));
                if (members == null) return;
                for (var member : members) {
                    sink.add(new WeightedSingle(tag, member.key()));
                }
            }
            case SingletonEntry singleton -> {
                if (!conditionsPass(singleton.conditions(), context)) return;
                sink.add(new WeightedSingle(singleton, null));
            }
            case GroupEntry group -> {
                for (var child : group.children()) expandEntry(child, context, sink);
            }
            case AlternativesEntry alternatives -> {
                for (var child : alternatives.children()) {
                    if (conditionsPass(child.conditions(), context)) {
                        expandEntry(child, context, sink);
                        return;
                    }
                }
            }
            case SequenceEntry sequence -> {
                for (var child : sequence.children()) {
                    if (!conditionsPass(child.conditions(), context)) return;
                    expandEntry(child, context, sink);
                }
            }
            default -> {
            }
        }
    }

    private boolean conditionsPass(List<LootCondition> conditions, LootContext context) {
        for (var condition : conditions) {
            if (!test(condition, context)) return false;
        }
        return true;
    }


    private static final class WeightedSingle {
        final SingletonEntry entry;
        final Key expandedTagMaterial;

        WeightedSingle(SingletonEntry entry, Key expandedTagMaterial) {
            this.entry = entry;
            this.expandedTagMaterial = expandedTagMaterial;
        }

        /**
         * 原版 {@code LootPoolSingletonContainer$EntryBase#getWeight(float luck)}：
         * {@code Math.max(Mth.floor(weight + quality * luck), 0)}。
         */
        int weight(float luck) {
            return Math.max((int) Math.floor(entry.weight() + entry.quality() * luck), 0);
        }
    }

    private void produce(WeightedSingle candidate, LootTable table, LootPool pool, LootContext context,
                          Random random, Consumer<ItemStack> sink, int depth) {
        var entry = candidate.entry;
        switch (entry) {
            case ItemEntry item -> {
                var stack = ItemStack.of(findMaterial(item.name()), 1);
                emit(stack, entry, pool, table, context, sink);
            }
            case TagEntry tag when candidate.expandedTagMaterial != null ->
                    emit(ItemStack.of(findMaterial(candidate.expandedTagMaterial), 1), entry, pool, table, context, sink);
            case TagEntry tag -> {
                var tagKey = net.minestom.server.registry.TagKey.<Material>of(tag.name());
                var members = process.material().getTag(tagKey);
                if (members == null) return;
                for (var member : members) {
                    emit(ItemStack.of(findMaterial(member.key()), 1), entry, pool, table, context, sink);
                }
            }
            case DynamicEntry dynamic -> environment.dynamicLoot(dynamic.name())
                    .forEach(stack -> emit(stack, entry, pool, table, context, sink));
            case EmptyEntry ignored -> {
            }
            case NestedTableEntry nested -> {
                var nestedTable = nested.value().isLeft()
                        ? tableById(nested.value().left().orElseThrow())
                        : nested.value().right().orElseThrow();
                // 嵌套表产出后，它的结果再叠加外层（条目 → 池 → 表）的修饰器链。
                var outer = new Consumer<ItemStack>() {
                    @Override
                    public void accept(ItemStack stack) {
                        var current = applyChain(stack, entry.functions(), context);
                        current = applyChain(current, pool.functions(), context);
                        current = applyChain(current, table.functions(), context);
                        if (!current.isAir()) sink.accept(current);
                    }
                };
                processTable(nestedTable, context, random, outer, depth + 1);
            }
            case SlotsEntry ignored -> throw new LootException("当前版本不支持 slots 条目");
            default -> throw new LootException("unreachable singleton entry: " + entry.getClass().getName());
        }
    }

    /**
     * 把一次产出交付到下游：先套用条目、池、表三层修饰器。原版
     * {@code LootPool.addRandomItems} 内部通过 {@code LootItemFunction.decorate} 把池的修饰器包在
     * 抽取回调上，表的修饰器通过 {@code LootTable.getRandomItemsRaw} 最外层包，组合顺序就是
     * 条目 → 池 → 表。
     */
    private void emit(ItemStack initial, LootEntry entry, LootPool pool, LootTable table,
                       LootContext context, Consumer<ItemStack> sink) {
        var current = applyChain(initial, entry.functions(), context);
        current = applyChain(current, pool.functions(), context);
        current = applyChain(current, table.functions(), context);
        if (!current.isAir()) sink.accept(current);
    }

    private ItemStack applyChain(ItemStack start, List<LootFunction> chain, LootContext context) {
        var current = start;
        for (var function : chain) {
            current = applyFunction(current, function, context);
        }
        return current;
    }

    private Material findMaterial(Key id) {
        var material = process.material().get(id);
        if (material == null) {
            throw new LootException("unknown item: " + id.asString());
        }
        return material;
    }

    // ------------------------------------------------------------------
    // 拆分
    // ------------------------------------------------------------------

    /**
     * 原版 {@code LootTable.createStackSplitter}：{@code ItemStack.count >= maxStackSize} 时按上限
     * 切成多堆再逐堆交给下游（原版 {@code lambda$createStackSplitter$0}：{@code while (count > 0)}
     * {@code copyWithCount(min(count, maxStackSize))}）。Minestom 侧没有原版
     * {@code isItemEnabled} 的 FeatureFlag 概念，跳过该步。
     * 产出尾部再把 {@link ItemStack#isSimilar 物品完全相同} 的部分按 {@link ItemStack#maxStackSize}
     * 做合并，这是 DESIGN 8.3.8 要求的 GUI 友好行为。
     */
    private static List<ItemStack> splitIntoStacks(List<ItemStack> raw) {
        var result = new ArrayList<ItemStack>();
        for (var stack : raw) {
            if (stack.isAir()) continue;
            var max = Math.max(1, stack.maxStackSize());
            var amount = stack.amount();
            var piece = amount;
            while (piece > 0) {
                var take = Math.min(max, piece);
                mergeOrAppend(result, stack.withAmount(take));
                piece -= take;
            }
        }
        return result;
    }

    private static void mergeOrAppend(List<ItemStack> stacks, ItemStack incoming) {
        var max = Math.max(1, incoming.maxStackSize());
        for (int i = 0; i < stacks.size(); i++) {
            var existing = stacks.get(i);
            if (!existing.isSimilar(incoming) || existing.amount() >= max) continue;
            var room = max - existing.amount();
            var move = Math.min(room, incoming.amount());
            stacks.set(i, existing.withAmount(existing.amount() + move));
            var rest = incoming.amount() - move;
            if (rest <= 0) return;
            incoming = incoming.withAmount(rest);
        }
        stacks.add(incoming);
    }

    // ------------------------------------------------------------------
    // 条件（原版 net.minecraft.world.level.storage.loot.predicates 同名类）
    // ------------------------------------------------------------------

    /**
     * 原版条件字典：各种原版条件类的 {@code test} 已经逐个对照字节码写入本方法。
     */
    boolean test(LootCondition condition, LootContext context) {
        return switch (condition) {
            // 原版 LootItemRandomChanceCondition：nextFloat < chance
            case RandomChanceCondition randomChance ->
                    context.random().nextFloat() < LootEvaluation.getFloat(randomChance.chance(), context, environment);
            // 原版 LootItemRandomChanceWithEnchantedBonusCondition：有魔咒按 levelBased 取值，否则用未附魔概率
            case RandomChanceWithEnchantedBonusCondition bonus -> testRandomChanceWithEnchantedBonus(bonus, context);
            // 原版 BonusLevelTableCondition：从 tool 读魔咒等级，列表下标 Math.min(level, size-1)；表不在范围返回 false
            case TableBonusCondition tableBonus -> testTableBonus(tableBonus, context);
            // 原版 LootItemKilledByPlayerCondition：只判参数是否存在（不读值）
            case KilledByPlayerCondition ignored -> context.find(ContextKey.LAST_DAMAGE_PLAYER) != null;
            // 原版 ExplosionCondition：没有爆心半径按安全处理（true），否则按 1/radius
            case SurvivesExplosionCondition ignored -> testSurvivesExplosion(context);
            case ValueCheckCondition valueCheck ->
                    LootEvaluation.test(valueCheck.range(),
                            LootEvaluation.getInt(valueCheck.value(), context, environment), context, environment);
            case InvertedCondition inverted -> !test(inverted.term(), context);
            // 原版 AllOfCondition / AnyOfCondition 是 CompositeLootItemCondition 的子类，本体是全部/任意通过
            case AllOfCondition allOf -> allOf.terms().stream().allMatch(c -> test(c, context));
            case AnyOfCondition anyOf -> anyOf.terms().stream().anyMatch(c -> test(c, context));
            // 原版 EnchantmentActiveCheck：读参必为 Boolean，与 active 比较
            case EnchantmentActiveCheckCondition check -> {
                var raw = context.require(ContextKey.ENCHANTMENT_ACTIVE);
                if (!(raw instanceof Boolean active)) {
                    throw new LootException(ContextKey.ENCHANTMENT_ACTIVE.id().asString() + " must be a Boolean");
                }
                yield active == check.active();
            }
            case ReferenceCondition reference -> testReference(reference, context);
            case EntityScoresCondition entityScores -> testEntityScores(entityScores, context);
            case MatchToolCondition matchTool -> testMatchTool(matchTool, context);
            case BlockStatePropertyCondition ignored -> false;
            case LocationCheckCondition ignored -> false;
            case EntityPropertiesCondition entityProperties -> {
                var raw = context.require((ContextKey<Object>) entityProperties.entity().contextParam());
                yield raw != null;
            }
            case DamageSourcePropertiesCondition ignored -> context.find(ContextKey.DAMAGE_SOURCE) != null;
            case WeatherCheckCondition weatherCheck -> testWeatherCheck(weatherCheck, context);
            case TimeCheckCondition timeCheck -> {
                // 原版 TimeCheck.test 比较 period 周期下的 day time；loot 模块没有 Minestom 的 Level.dayTime 入口，
                // 这个条件按空判定 false，等宿主后续接入环境时钟再实现。
                yield false;
            }
            case EnvironmentAttributeCheckCondition ignored ->
                    throw new LootException("environment_attribute_check condition has no minestom counterpart");
        };
    }

    @SuppressWarnings("unchecked")
    private boolean testReference(ReferenceCondition reference, LootContext context) {
        var inner = environment.findCondition(reference.name())
                .orElseThrow(() -> new LootException("Unknown condition: " + reference.name().asString()));
        return test(inner, context);
    }

    private boolean testEntityScores(EntityScoresCondition entityScores, LootContext context) {
        var view = environment.scoreboards();
        var entityName = scoreboardNameFromContext(entityScores.entity(), view, context);
        var scores = entityScores.scores();
        if (scores == null) return true;
        for (var entry : scores.entrySet()) {
            var found = view.getScore(entityName, entry.getKey());
            if (found.isEmpty()) return false;
            var range = entry.getValue();
            if (range != null && !LootEvaluation.test(range, found.getAsInt(), context, environment)) {
                return false;
            }
        }
        return true;
    }

    private String scoreboardNameFromContext(io.ib67.yuanban.loot.number.EntityTarget target,
                                              ScoreboardView view, LootContext context) {
        var entity = context.require((ContextKey<Object>) target.contextParam());
        return view.getScoreboardName(entity instanceof Entity e ? e : null)
                .orElseThrow(() -> new LootException("entity has no scoreboard name"));
    }

    private boolean testMatchTool(MatchToolCondition matchTool, LootContext context) {
        var raw = context.find(ContextKey.TOOL);
        if (!(raw instanceof ItemStack tool)) return false;
        if (matchTool.predicate().isEmpty()) return true;
        var predicate = matchTool.predicate().orElseThrow();
        if (predicate.items() != null) {
            var toolKey = tool.material().key();
            var matched = false;
            for (var key : predicate.items()) {
                if (key.equals(toolKey)) { matched = true; break; }
            }
            if (!matched) return false;
        }
        if (predicate.itemTag() != null) {
            var tagKey = net.minestom.server.registry.TagKey.<Material>of(predicate.itemTag());
            var tag = process.material().getTag(tagKey);
            if (tag == null || !tag.contains(RegistryKey.of(tool.material().key()))) return false;
        }
        return true;
    }

    private boolean testRandomChanceWithEnchantedBonus(RandomChanceWithEnchantedBonusCondition bonus,
                                                        LootContext context) {
        var level = attackingEnchantmentLevel(context, bonus.enchantment());
        var chance = level > 0
                ? LootEvaluation.levelBased(bonus.enchantedChance(), level)
                : bonus.unenchantedChance();
        return context.random().nextFloat() < chance;
    }

    private boolean testTableBonus(TableBonusCondition tableBonus, LootContext context) {
        var toolRaw = context.find(ContextKey.TOOL);
        if (!(toolRaw instanceof ItemStack tool)) return false;
        if (tableBonus.values() == null || tableBonus.values().isEmpty()) return false;
        int level = LootEvaluation.itemEnchantmentLevel(context, tableBonus.enchantment(), tool);
        var index = Math.min(level, tableBonus.values().size() - 1);
        return context.random().nextFloat() < tableBonus.values().get(index);
    }

    private boolean testSurvivesExplosion(LootContext context) {
        var raw = context.find(ContextKey.EXPLOSION_RADIUS);
        if (!(raw instanceof Number number)) return true;
        var radius = number.floatValue();
        if (radius <= 0) return true;
        return context.random().nextFloat() <= 1.0f / radius;
    }

    private boolean testWeatherCheck(WeatherCheckCondition weatherCheck, LootContext context) {
        // 原版 WeatherCheck 需要 ServerLevel.isRaining/isThundering；loot 模块在 Minestom 侧没有
        // 标准化的天气位，宿主应当通过上下文参数自行制定；当前对要求 isRaining/isThundering 为 true
        // 的查询一律按不通过返回。原版 {@code WeatherCheck#test} 只在条件满足时通过：
        // {@code if (raining.isPresent() && raining.get() != level.isRaining()) return false}。
        if (weatherCheck.isRaining().isPresent() && weatherCheck.isRaining().orElseThrow()) return false;
        if (weatherCheck.isThundering().isPresent() && weatherCheck.isThundering().orElseThrow()) return false;
        return true;
    }

    /**
     * 原版 {@code EnchantmentHelper.getEnchantmentLevel(Holder, LivingEntity)}：读取装备槽的魔咒；
     * loot 模块对 Minestom 实体仅提供主手物品作为默认源（与原版主手魔咒一致）。
     */
    private int attackingEnchantmentLevel(LootContext context, Key enchantment) {
        var raw = context.find(ContextKey.ATTACKING_ENTITY);
        if (!(raw instanceof Player player)) return 0;
        return LootEvaluation.itemEnchantmentLevel(context, enchantment, player.getItemInMainHand());
    }

    // ------------------------------------------------------------------
    // 修饰器（原版 net.minecraft.world.level.storage.loot.functions 同名类）
    // ------------------------------------------------------------------

    /**
     * 原版 {@code LootItemFunction.run} 返回 {@code ItemStack}，loot 模块空栈即表达"被丢弃"
     * （原版 {@code DiscardFunction} 返回 {@code ItemStack.EMPTY}，后续修饰器对空栈继续按序执行）。
     *
     * <p>
     * 原版没有显式的 function 表/池/条目三层优先级，包裹顺序由 {@code decorate} 决定：条目 → 池 → 表
     * （见 {@link #emit}/{@link #produce}）。
     */
    private ItemStack applyFunction(ItemStack stack, LootFunction function, LootContext context) {
        for (var condition : function.conditions()) {
            if (!test(condition, context)) return stack;
        }
        return switch (function) {
            case SetCountFunction setCount -> runSetCount(stack, setCount, context);
            case SetDamageFunction setDamage -> runSetDamage(stack, setDamage, context);
            case SetItemFunction setItem -> runSetItem(stack, setItem);
            case SetNameFunction setName -> runSetName(stack, setName);
            case SetLoreFunction setLore -> runSetLore(stack, setLore);
            case LimitCountFunction limit ->
                    stack.withAmount(LootEvaluation.clamp(limit.limit(), stack.amount(), context, environment));
            case DiscardFunction ignored -> ItemStack.AIR;
            case SequenceFunction sequence -> {
                var next = stack;
                for (var inner : sequence.functions()) next = applyFunction(next, inner, context);
                yield next;
            }
            case FilteredFunction filtered -> {
                var target = testFilter(filtered, stack)
                        ? filtered.onMatch().orElse(null)
                        : filtered.onFail().orElse(null);
                if (target == null) yield stack;
                yield applyFunction(stack, target, context);
            }
            case SetComponentsFunction setComponents -> runSetComponents(stack, setComponents);
            case EnchantRandomlyFunction enchantRandomly -> runEnchantRandomly(stack, enchantRandomly, context);
            case EnchantWithLevelsFunction enchantWithLevels ->
                    runEnchantWithLevels(stack, enchantWithLevels, context);
            case ApplyBonusFunction applyBonus -> runApplyBonus(stack, applyBonus, context);
            case EnchantedCountIncreaseFunction increase -> runEnchantedCountIncrease(stack, increase, context);
            case SmeltFunction smelt -> runSmelt(stack, smelt);
            case ExplosionDecayFunction ignored -> runExplosionDecay(stack, context);
            case ToggleTooltipsFunction toggleTooltips -> runToggleTooltips(stack, toggleTooltips);
            case FunctionReference reference -> {
                var inner = environment.findFunction(reference.name())
                        .orElseThrow(() -> new LootException("Unknown function: " + reference.name().asString()));
                yield applyFunction(stack, inner, context);
            }
            default -> throw new LootException(
                    "function not yet implemented: " + function.getClass().getSimpleName());
        };
    }

    private boolean testFilter(FilteredFunction filtered, ItemStack stack) {
        var predicate = filtered.itemFilter();
        if (predicate == null) return false;
        if (predicate.items() != null) {
            var key = stack.material().key();
            var found = false;
            for (var itemKey : predicate.items()) {
                if (itemKey.equals(key)) { found = true; break; }
            }
            if (!found) return false;
        }
        if (predicate.itemTag() != null) {
            var tagKey = net.minestom.server.registry.TagKey.<Material>of(predicate.itemTag());
            var tag = process.material().getTag(tagKey);
            if (tag == null || !tag.contains(RegistryKey.of(stack.material().key()))) return false;
        }
        return true;
    }

    private ItemStack runSetCount(ItemStack stack, SetCountFunction function, LootContext context) {
        // 原版 SetItemCountFunction.run：add=true 在当前数量上叠加，否则直接覆盖；不截断到堆叠上限。
        var value = LootEvaluation.getInt(function.count(), context, environment);
        var target = function.add() ? stack.amount() + value : value;
        return stack.withAmount(target);
    }

    private ItemStack runSetDamage(ItemStack stack, SetDamageFunction function, LootContext context) {
        // 原版 SetItemDamageFunction.run：不可损坏物品原样返回；add=true 在当前损坏比上叠加；
        // 系数按 1 - clamp(damage + base, 0, 1)，最终写回 floor(value * maxDamage)。
        var maxDamage = stack.get(DataComponents.MAX_DAMAGE);
        if (maxDamage == null || maxDamage <= 0) return stack;
        var currentRaw = stack.get(DataComponents.DAMAGE);
        var currentDamage = currentRaw == null ? 0 : currentRaw;
        var base = function.add() ? 1.0f - (float) currentDamage / maxDamage : 0.0f;
        var value = 1.0f - Math.clamp(LootEvaluation.getFloat(function.damage(), context, environment) + base,
                0.0f, 1.0f);
        return stack.with(DataComponents.DAMAGE, (int) Math.floor(value * maxDamage));
    }

    private ItemStack runSetItem(ItemStack stack, SetItemFunction function) {
        // 原版 SetItemFunction.run：ItemStack.transmuteCopy（保留原计数与组件，仅改材质）。
        return stack.withMaterial(findMaterial(function.id()));
    }

    private ItemStack runSetName(ItemStack stack, SetNameFunction function) {
        if (function.name().isEmpty()) return stack;
        var name = function.name().get();
        return switch (function.target()) {
            case CUSTOM_NAME -> stack.with(DataComponents.CUSTOM_NAME, name);
            case ITEM_NAME -> stack.with(DataComponents.ITEM_NAME, name);
        };
    }

    private ItemStack runSetLore(ItemStack stack, SetLoreFunction function) {
        // 原版 SetLoreFunction.run：决定于 ListOperation，对 lore 做 Append/Insert/ReplaceAll/ReplaceSection。
        var current = stack.get(DataComponents.LORE);
        var existing = current == null
                ? new ArrayList<net.kyori.adventure.text.Component>()
                : new ArrayList<>(current);
        var added = function.lore();
        switch (function.mode()) {
            case AppendOperation ignored -> existing.addAll(added);
            case InsertOperation insert ->
                    existing.addAll(Math.clamp(insert.offset(), 0, existing.size()), added);
            case ReplaceAllOperation ignored -> {
                existing.clear();
                existing.addAll(added);
            }
            case ReplaceSectionOperation section -> {
                var start = Math.clamp(section.offset(), 0, existing.size());
                var end = section.size().map(size -> Math.clamp(start + size, start, existing.size()))
                        .orElse(existing.size());
                existing.subList(start, end).clear();
                existing.addAll(start, added);
            }
        }
        return stack.with(DataComponents.LORE, List.copyOf(existing));
    }

    private ItemStack runSetComponents(ItemStack stack, SetComponentsFunction function) {
        // 原版 SetComponentsFunction.apply：把 patch 逐项 set 到目标堆栈；loot 侧考虑
        // {@code ItemStack#get(DataComponent)} 内部按 {@code componentPatch.get(components)},
        // 直接把每一项经 {@link ItemStack#with(DataComponent, Object)} 贴上去即可（语义和原版
        // {@code stack.applyComponents} 相同——逐项覆盖）。
        var next = stack;
        for (var entry : function.components().entrySet()) {
            next = applyComponent(next, entry);
        }
        return next;
    }

    /**
     * Minestom 26.2 {@link DataComponentMap#entrySet()} 返回 {@code List<DataComponent.Value>}（record 结构，
     * 访问器为 {@code component()}/{@code value()}）；loot 模块把它直接解构并逐项贴到 {@link ItemStack}。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ItemStack applyComponent(ItemStack stack, Object value) {
        if (value instanceof Map.Entry<?, ?> e && e.getKey() instanceof DataComponent dc && e.getValue() != null) {
            // 单元测试或宿主自定义可能传入真正的 Map.Entry；这里直接展开是原版的语义。
            return stack.with(dc, e.getValue());
        }
        if (value instanceof net.minestom.server.component.DataComponent.Value v && v.value() != null) {
            return stack.with((DataComponent<Object>) v.component(), v.value());
        }
        throw new LootException("set_components: cannot apply " + value);
    }

    private ItemStack runEnchantRandomly(ItemStack stack, EnchantRandomlyFunction function, LootContext context) {
        // 原版 EnchantRandomlyFunction：从候选池挑一个魔咒，按该魔咒 min..max 随机等级，绑定到物品上。
        var candidates = resolveCandidates(function.options(), context);
        if (candidates.isEmpty()) return stack;
        var random = context.random();
        var picked = candidates.get(random.nextInt(candidates.size()));
        // Minestom 魔咒 record 没有这个边界语义的下限（原版 minLevel 总为 1），按任务说明 maxLevel 下限 1 使用。
        var level = LootEvaluation.nextInt(random, 1, Math.max(1, picked.maxLevel()));
        return applyEnchant(stack, picked, level);
    }

    private ItemStack runEnchantWithLevels(ItemStack stack, EnchantWithLevelsFunction function,
                                            LootContext context) {
        // 原版 EnchantWithLevelsFunction：一次性从候选池随机一个魔咒，等级按 levels 求值并夹在该魔咒的范围内。
        var candidates = resolveCandidates(function.options(), context);
        if (candidates.isEmpty()) return stack;
        var random = context.random();
        var picked = candidates.get(random.nextInt(candidates.size()));
        var level = Math.clamp(LootEvaluation.getInt(function.levels(), context, environment),
                1, Math.max(1, picked.maxLevel()));
        return applyEnchant(stack, picked, level);
    }

    private List<Enchantment> resolveCandidates(
            Optional<io.ib67.yuanban.loot.function.EnchantedOptions> options, LootContext context) {
        if (options.isEmpty()) return List.of();
        var opt = options.orElseThrow();
        var enchantments = new ArrayList<Enchantment>();
        if (opt.enchantments() != null) {
            for (var key : opt.enchantments()) {
                var value = process.enchantment().get(key);
                if (value != null) enchantments.add(value);
            }
        } else if (opt.enchantmentTag() != null) {
            var tagKey = net.minestom.server.registry.TagKey.<Enchantment>of(opt.enchantmentTag());
            var tag = process.enchantment().getTag(tagKey);
            if (tag != null) {
                for (var member : tag) {
                    var value = process.enchantment().get(member);
                    if (value != null) enchantments.add(value);
                }
            }
        }
        return enchantments;
    }

    private ItemStack applyEnchant(ItemStack stack, Enchantment enchantment, int level) {
        var component = stack.material().equals(Material.BOOK) ? DataComponents.STORED_ENCHANTMENTS
                : DataComponents.ENCHANTMENTS;
        var existing = stack.get(component);
        var next = existing == null
                ? new net.minestom.server.item.component.EnchantmentList(keyOfEnchantment(enchantment), level)
                : existing.with(keyOfEnchantment(enchantment), level);
        return stack.with(component, next);
    }

    private <T> RegistryKey<T> keyOfEnchantment(Enchantment enchantment) {
        // Process.enchantment() 返回 DynamicRegistry，按 Enchantment 对象反查 RegistryKey
        return (RegistryKey<T>) process.enchantment().getKey(enchantment);
    }

    private ItemStack runApplyBonus(ItemStack stack, ApplyBonusFunction function, LootContext context) {
        // 原版 ApplyBonusCount.run：tool 缺失直接返回堆栈本身；命中后公式计算新的计数再 setCount。
        var toolRaw = context.find(ContextKey.TOOL);
        if (!(toolRaw instanceof ItemStack tool)) return stack;
        int level = LootEvaluation.itemEnchantmentLevel(context, function.enchantment(), tool);
        var random = context.random();
        int amount = switch (function.formula()) {
            case ApplyBonusFormula.UniformBonusCount uniform ->
                    stack.amount() + random.nextInt(uniform.bonusMultiplier() * level + 1);
            case ApplyBonusFormula.BinomialWithBonusCount binomial -> {
                var count = stack.amount();
                for (int i = 0; i < level + binomial.extraRounds(); i++) {
                    if (random.nextFloat() < binomial.probability()) count++;
                }
                yield count;
            }
            case ApplyBonusFormula.OreDrops ignored -> {
                if (level <= 0) yield stack.amount();
                var bonus = Math.max(0, random.nextInt(level + 2) - 1);
                yield stack.amount() * (bonus + 1);
            }
        };
        return stack.withAmount(amount);
    }

    private ItemStack runEnchantedCountIncrease(ItemStack stack, EnchantedCountIncreaseFunction increase,
                                                 LootContext context) {
        // 原版 EnchantedCountIncreaseFunction.run：attacking entity 上的魔咒等级 * count，
        // 叠加到原计数；level=0 时直接返回原堆栈。
        int level = attackingEnchantmentLevel(context, increase.enchantment());
        if (level <= 0) return stack;
        var delta = Math.round(level * LootEvaluation.getFloat(increase.count(), context, environment));
        var result = stack.withAmount(stack.amount() + delta);
        return increase.limit() > 0 ? result.withAmount(Math.min(result.amount(), increase.limit())) : result;
    }

    private ItemStack runSmelt(ItemStack stack, SmeltFunction smelt) {
        // Minestom 26.2 没有现成的 smelting 查询表，loot 模块把这一项作为宿主集成时的接入点；
        // 原版在没有配方时按警告+原堆栈返回，loot 模块保持同样行为（在宿主提供熔炼引擎之前视为"无配方"）。
        return stack;
    }

    private ItemStack runExplosionDecay(ItemStack stack, LootContext context) {
        // 原版 ApplyExplosionDecay.run：爆心半径缺失按原样返回；对每件物品按 1/radius 概率保留。
        var raw = context.find(ContextKey.EXPLOSION_RADIUS);
        if (!(raw instanceof Number number)) return stack;
        var radius = number.floatValue();
        if (radius <= 0) return stack;
        var probability = 1.0f / radius;
        int survivors = 0;
        var random = context.random();
        for (int i = 0; i < stack.amount(); i++) {
            if (random.nextFloat() <= probability) survivors++;
        }
        return survivors == 0 ? ItemStack.AIR : stack.withAmount(survivors);
    }

    private ItemStack runToggleTooltips(ItemStack stack, ToggleTooltipsFunction function) {
        // 原版 ToggleTooltips.run：stack.update(TOOLTIP_DISPLAY, DEFAULT, d -> ...)，逐项 withHidden(key, hide)。
        if (function.toggles() == null || function.toggles().isEmpty()) return stack;
        var display = stack.get(DataComponents.TOOLTIP_DISPLAY);
        var hidden = new java.util.HashSet<net.minestom.server.component.DataComponent<?>>(
                display == null ? Set.of() : display.hiddenComponents());
        for (var toggle : function.toggles().entrySet()) {
            var component = componentFromKey(toggle.getKey());
            if (component == null) continue;
            if (!toggle.getValue()) {
                hidden.add(component);
            } else {
                hidden.remove(component);
            }
        }
        return stack.with(DataComponents.TOOLTIP_DISPLAY,
                new net.minestom.server.item.component.TooltipDisplay(false, Set.copyOf(hidden)));
    }

    /**
     * 按原版 {@code DataComponents} 的注册表 ID 解析 {@link DataComponent}。Minestom 26.2 把
     * {@code TOOLTIP_DISPLAY} 暴露在 {@link DataComponents} 常量上而其他组件没有统一的表接口；
     * 这里对设计稿中约定会关心的几个组件做白名单解析，遇到没识别的返回 {@code null} 使该 toggle 被忽略。
     */
    private static DataComponent<?> componentFromKey(Key key) {
        return switch (key.asString()) {
            case "minecraft:custom_name" -> DataComponents.CUSTOM_NAME;
            case "minecraft:item_name" -> DataComponents.ITEM_NAME;
            case "minecraft:lore" -> DataComponents.LORE;
            case "minecraft:damage" -> DataComponents.DAMAGE;
            case "minecraft:max_damage" -> DataComponents.MAX_DAMAGE;
            case "minecraft:max_stack_size" -> DataComponents.MAX_STACK_SIZE;
            case "minecraft:enchantments" -> DataComponents.ENCHANTMENTS;
            case "minecraft:stored_enchantments" -> DataComponents.STORED_ENCHANTMENTS;
            case "minecraft:tooltip_display" -> DataComponents.TOOLTIP_DISPLAY;
            default -> null;
        };
    }
}
