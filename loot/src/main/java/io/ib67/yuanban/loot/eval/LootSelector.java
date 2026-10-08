package io.ib67.yuanban.loot.eval;

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
import io.ib67.yuanban.loot.context.LootContextArg;
import io.ib67.yuanban.loot.context.LootContextImpl;
import io.ib67.yuanban.loot.context.LootEnvironment;
import io.ib67.yuanban.loot.entry.AlternativesEntry;
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
import io.ib67.yuanban.loot.function.CopyBlockStateFunction;
import io.ib67.yuanban.loot.function.CopyComponentsFunction;
import io.ib67.yuanban.loot.function.CopyCustomDataFunction;
import io.ib67.yuanban.loot.function.CopyNameFunction;
import io.ib67.yuanban.loot.function.DiscardFunction;
import io.ib67.yuanban.loot.function.EnchantRandomlyFunction;
import io.ib67.yuanban.loot.function.EnchantWithLevelsFunction;
import io.ib67.yuanban.loot.function.EnchantedCountIncreaseFunction;
import io.ib67.yuanban.loot.function.ExplorationMapFunction;
import io.ib67.yuanban.loot.function.ExplosionDecayFunction;
import io.ib67.yuanban.loot.function.FillPlayerHeadFunction;
import io.ib67.yuanban.loot.function.FilteredFunction;
import io.ib67.yuanban.loot.function.FunctionReference;
import io.ib67.yuanban.loot.function.LimitCountFunction;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.function.ModifyContentsFunction;
import io.ib67.yuanban.loot.function.NbtSource;
import io.ib67.yuanban.loot.function.SequenceFunction;
import io.ib67.yuanban.loot.function.SetAttributesFunction;
import io.ib67.yuanban.loot.function.SetBannerPatternFunction;
import io.ib67.yuanban.loot.function.SetBookCoverFunction;
import io.ib67.yuanban.loot.function.SetComponentsFunction;
import io.ib67.yuanban.loot.function.SetContainerContentsFunction;
import io.ib67.yuanban.loot.function.SetContainerLootTableFunction;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.function.SetCustomDataFunction;
import io.ib67.yuanban.loot.function.SetCustomModelDataFunction;
import io.ib67.yuanban.loot.function.SetDamageFunction;
import io.ib67.yuanban.loot.function.SetEnchantmentsFunction;
import io.ib67.yuanban.loot.function.SetFireworkExplosionFunction;
import io.ib67.yuanban.loot.function.SetFireworksFunction;
import io.ib67.yuanban.loot.function.SetInstrumentFunction;
import io.ib67.yuanban.loot.function.SetItemFunction;
import io.ib67.yuanban.loot.function.SetLoreFunction;
import io.ib67.yuanban.loot.function.SetNameFunction;
import io.ib67.yuanban.loot.function.SetOminousBottleAmplifierFunction;
import io.ib67.yuanban.loot.function.SetPotionFunction;
import io.ib67.yuanban.loot.function.SetRandomDyesFunction;
import io.ib67.yuanban.loot.function.SetRandomPotionFunction;
import io.ib67.yuanban.loot.function.SetStewEffectFunction;
import io.ib67.yuanban.loot.function.SetWritableBookPagesFunction;
import io.ib67.yuanban.loot.function.SetWrittenBookPagesFunction;
import io.ib67.yuanban.loot.function.SmeltFunction;
import io.ib67.yuanban.loot.function.StandAloneOperation;
import io.ib67.yuanban.loot.function.ToggleTooltipsFunction;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.NumberProvider;
import io.ib67.yuanban.loot.slot.SlotSource;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.ServerProcess;
import net.minestom.server.color.Color;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.book.FilteredText;
import net.minestom.server.item.component.AttributeList;
import net.minestom.server.item.component.BannerPatterns;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.item.component.CustomModelData;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.FireworkList;
import net.minestom.server.item.component.ItemBlockState;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.item.component.SeededContainerLoot;
import net.minestom.server.item.component.SuspiciousStewEffects;
import net.minestom.server.item.component.TooltipDisplay;
import net.minestom.server.item.component.WritableBookContent;
import net.minestom.server.item.component.WrittenBookContent;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.potion.PotionType;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
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
    /**
     * 原版 {@code WrittenBookContent.EMPTY}：空标题、空作者、第 0 代、没有页、已解析。
     */
    private static final WrittenBookContent EMPTY_WRITTEN_BOOK = new WrittenBookContent(
            new FilteredText<>("", null), "", 0, List.of(), true);
    /**
     * 原版 {@code SetFireworkExplosionFunction.DEFAULT_VALUE}。
     */
    private static final FireworkExplosion DEFAULT_FIREWORK_EXPLOSION = new FireworkExplosion(
            FireworkExplosion.Shape.SMALL_BALL, List.of(), List.of(), false, false);

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
        var missing = new TreeSet<ContextKey<?>>(Comparator.comparing(ContextKey::id));
        // 原版 {@code ValidationContext$ParametersNotProvidedProblem.report} 的文案是
        // "<key> is/are not provided in this context"；loot 模块沿用同一份文案。
        collectMissing(table, context, Collections.newSetFromMap(new IdentityHashMap<>()), missing);
        if (!missing.isEmpty()) {
            var text = missing.stream().map(k -> k.id().asString()).collect(Collectors.joining(", "));
            var plural = missing.size() > 1;
            throw new LootException(text + (plural ? " are not provided in this context"
                    : " is not provided in this context"));
        }
        var actualRandom = selectRandom(table, random);
        var effectiveContext = context instanceof LootContextImpl impl ? impl.withRandom(actualRandom) : context;
        var output = new ArrayList<ItemStack>();
        processTable(table, effectiveContext, stackSplitter(output::add), 0);
        return mergeStacks(output);
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
            checkFunction(function, context, visiting, missing);
        }
        for (var pool : table.pools()) {
            for (var condition : pool.conditions()) checkCondition(condition, context, missing);
            for (var function : pool.functions()) checkFunction(function, context, visiting, missing);
            checkNumber(pool.rolls(), context, missing);
            checkNumber(pool.bonusRolls(), context, missing);
            for (var entry : pool.entries()) checkEntry(entry, context, visiting, missing);
        }
        visiting.remove(table);
    }

    private void checkEntry(LootEntry entry, LootContext context, Set<LootTable> visiting,
                            Set<ContextKey<?>> missing) {
        for (var condition : entry.conditions()) checkCondition(condition, context, missing);
        switch (entry) {
            case AlternativesEntry composite -> composite.children().forEach(child -> checkEntry(child, context, visiting, missing));
            case GroupEntry composite -> composite.children().forEach(child -> checkEntry(child, context, visiting, missing));
            case SequenceEntry composite -> composite.children().forEach(child -> checkEntry(child, context, visiting, missing));
            case SingletonEntry singleton -> {
                for (var function : singleton.functions()) checkFunction(function, context, visiting, missing);
                switch (singleton) {
                    case NestedTableEntry nested -> {
                        // 内联表立即向下遍历，ID 形式的表在 manager 可解析时继续；manager 拿不到的表在 roll 时会
                        // 以 "Missing loot table" 报错。
                        nested.value().ifRight(inline -> collectMissing(inline, context, visiting, missing));
                        nested.value().ifLeft(id -> manager.get(id)
                                .ifPresent(t -> collectMissing(t, context, visiting, missing)));
                    }
                    case SlotsEntry slots -> checkSlotSource(slots.slotSource(), context, missing);
                    case ItemEntry ignored -> {
                    }
                    case TagEntry ignored -> {
                    }
                    case DynamicEntry ignored -> {
                    }
                    case EmptyEntry ignored -> {
                    }
                }
            }
        }
    }

    private void checkSlotSource(SlotSource source, LootContext context, Set<ContextKey<?>> missing) {
        mergeMissing(missing, source.referencedParams(), context);
        switch (source) {
            case SlotSource.Group group -> group.terms().forEach(term -> checkSlotSource(term, context, missing));
            case SlotSource.Filtered filtered -> checkSlotSource(filtered.slotSource(), context, missing);
            case SlotSource.LimitSlots limit -> checkSlotSource(limit.slotSource(), context, missing);
            case SlotSource.Contents contents -> checkSlotSource(contents.slotSource(), context, missing);
            case SlotSource.SlotRangeSource ignored -> {
            }
            case SlotSource.Empty ignored -> {
            }
        }
    }

    private void checkCondition(LootCondition condition, LootContext context, Set<ContextKey<?>> missing) {
        mergeMissing(missing, condition.referencedParams(), context);
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
            case EntityScoresCondition scores -> scores.scores().values().forEach(range -> checkIntRange(range, context, missing));
            case TimeCheckCondition timeCheck -> checkIntRange(timeCheck.value(), context, missing);
            default -> {
            }
        }
    }

    private void checkFunction(LootFunction function, LootContext context, Set<LootTable> visiting, Set<ContextKey<?>> missing) {
        mergeMissing(missing, function.referencedParams(), context);
        for (var condition : function.conditions()) checkCondition(condition, context, missing);
        switch (function) {
            case SetCountFunction setCount -> checkNumber(setCount.count(), context, missing);
            case SetDamageFunction setDamage -> checkNumber(setDamage.damage(), context, missing);
            case LimitCountFunction limit -> checkIntRange(limit.limit(), context, missing);
            case EnchantedCountIncreaseFunction increase -> checkNumber(increase.count(), context, missing);
            case EnchantWithLevelsFunction levels -> checkNumber(levels.levels(), context, missing);
            case SetEnchantmentsFunction enchantments ->
                    enchantments.enchantments().values().forEach(level -> checkNumber(level, context, missing));
            case SetAttributesFunction attributes ->
                    attributes.modifiers().forEach(modifier -> checkNumber(modifier.amount(), context, missing));
            case SetStewEffectFunction stew -> stew.effects().forEach(effect -> checkNumber(effect.duration(), context, missing));
            case SetRandomDyesFunction dyes -> checkNumber(dyes.numberOfDyes(), context, missing);
            case SetOminousBottleAmplifierFunction amplifier -> checkNumber(amplifier.amplifier(), context, missing);
            case SetCustomModelDataFunction modelData -> {
                modelData.floats().ifPresent(floats -> floats.values().forEach(value -> checkNumber(value, context, missing)));
                modelData.colors().ifPresent(colors -> colors.values().forEach(value -> checkNumber(value, context, missing)));
            }
            case CopyCustomDataFunction copy -> mergeMissing(missing, copy.source().referencedParams(), context);
            case SetContainerContentsFunction contents ->
                    contents.entries().forEach(entry -> checkEntry(entry, context, visiting, missing));
            case FunctionReference reference -> environment.findFunction(reference.name())
                    .ifPresent(f -> checkFunction(f, context, visiting, missing));
            default -> function.children().forEach(child -> checkFunction(child, context, visiting, missing));
        }
    }

    private void checkIntRange(IntRange range, LootContext context, Set<ContextKey<?>> missing) {
        if (range.min() != null) checkNumber(range.min(), context, missing);
        if (range.max() != null) checkNumber(range.max(), context, missing);
    }

    private void checkNumber(NumberProvider provider, LootContext context, Set<ContextKey<?>> missing) {
        mergeMissing(missing, provider.referencedParams(), context);
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
                    sum.summands().forEach(term -> checkNumber(term, context, missing));
            default -> {
            }
        }
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

    /**
     * 原版 {@code LootTable#getRandomItemsRaw(LootContext, Consumer)}：表的修饰器包在输出上，各个池依次产出。
     */
    private void processTable(LootTable table, LootContext context, Consumer<ItemStack> output, int depth) {
        if (depth >= NESTED_TABLE_DEPTH_LIMIT) {
            throw new LootException("Loot table nesting exceeds " + NESTED_TABLE_DEPTH_LIMIT);
        }
        var decorated = decorate(table.functions(), output, context);
        for (var pool : table.pools()) {
            processPool(pool, context, decorated, depth);
        }
    }

    /**
     * 原版 {@code LootPool#addRandomItems}：条件不满足时不产出；抽取次数是 {@code rolls.getInt} 加
     * {@code floor(bonus_rolls.getFloat * luck)}。
     */
    private void processPool(LootPool pool, LootContext context, Consumer<ItemStack> output, int depth) {
        if (!conditionsPass(pool.conditions(), context)) return;
        var decorated = decorate(pool.functions(), output, context);
        int rolls = LootEvaluation.getInt(pool.rolls(), context, environment)
                + LootEvaluation.floor(LootEvaluation.getFloat(pool.bonusRolls(), context, environment) * context.luck());
        for (int i = 0; i < rolls; i++) {
            drawOnce(pool, context, decorated, depth);
        }
    }

    /**
     * 原版 {@code LootPool#addRandomItem}：展开全部条目，只保留权重大于 0 的候选并累加权重（按 {@code int} 相加）；
     * 总权重为 0 或没有候选时不产出，只有一个候选时直接产出，否则用 {@code nextInt(总权重)} 依次减去权重，小于 0 时命中。
     */
    private void drawOnce(LootPool pool, LootContext context, Consumer<ItemStack> output, int depth) {
        var candidates = new ArrayList<Candidate>();
        int totalWeight = 0;
        for (var entry : pool.entries()) {
            var expanded = new ArrayList<Candidate>();
            expand(entry, context, expanded::add);
            for (var candidate : expanded) {
                int weight = candidate.weight(context.luck());
                if (weight > 0) {
                    candidates.add(candidate);
                    totalWeight += weight;
                }
            }
        }
        if (totalWeight == 0 || candidates.isEmpty()) {
            return;
        }
        if (candidates.size() == 1) {
            create(candidates.getFirst(), output, context, depth);
            return;
        }
        int index = context.random().nextInt(totalWeight);
        for (var candidate : candidates) {
            index -= candidate.weight(context.luck());
            if (index < 0) {
                create(candidate, output, context, depth);
                return;
            }
        }
    }

    /**
     * 一次抽取的候选项：原版 {@code LootPoolEntry}。{@code expandedTagItem} 不为空时是 {@code tag} 条目展开出的一个物品，
     * 原版这种候选项直接产出物品，不经过条目的修饰器。
     */
    private record Candidate(SingletonEntry entry, @Nullable Material expandedTagItem) {
        /**
         * 原版 {@code LootPoolSingletonContainer$EntryBase#getWeight}：{@code max(floor(weight + quality * luck), 0)}。
         */
        int weight(float luck) {
            return Math.max(Mth.floor(entry.weight() + entry.quality() * luck), 0);
        }
    }

    /**
     * 原版 {@code LootPoolEntryContainer#expand}：返回值表示条目是否成功展开，复合条目据此决定是否继续。
     */
    private boolean expand(LootEntry entry, LootContext context, Consumer<Candidate> output) {
        return switch (entry) {
            case TagEntry tag when tag.expand() -> {
                // 原版 TagEntry#expandTag：条件满足时为标签的每个物品产出一个候选项，标签为空时同样算作展开成功
                if (!conditionsPass(tag.conditions(), context)) {
                    yield false;
                }
                for (var member : tagMembers(tag.name())) {
                    output.accept(new Candidate(tag, member));
                }
                yield true;
            }
            case SingletonEntry singleton -> {
                if (!conditionsPass(singleton.conditions(), context)) {
                    yield false;
                }
                output.accept(new Candidate(singleton, null));
                yield true;
            }
            case AlternativesEntry alternatives -> {
                if (!conditionsPass(alternatives.conditions(), context)) {
                    yield false;
                }
                // 原版 AlternativesEntry#compose：第一个展开成功的子条目为止，没有子条目时为失败
                for (var child : alternatives.children()) {
                    if (expand(child, context, output)) {
                        yield true;
                    }
                }
                yield false;
            }
            case SequenceEntry sequence -> {
                if (!conditionsPass(sequence.conditions(), context)) {
                    yield false;
                }
                // 原版 SequentialEntry#compose：遇到展开失败的子条目即停止，没有子条目时为成功
                for (var child : sequence.children()) {
                    if (!expand(child, context, output)) {
                        yield false;
                    }
                }
                yield true;
            }
            case GroupEntry group -> {
                if (!conditionsPass(group.conditions(), context)) {
                    yield false;
                }
                // 原版 EntryGroup#compose：展开全部子条目，结果总是成功
                for (var child : group.children()) {
                    expand(child, context, output);
                }
                yield true;
            }
        };
    }

    /**
     * 原版 {@code LootPoolEntry#createItemStack}。展开的标签物品直接产出；其余候选项的产出先经过条目的修饰器。
     */
    private void create(Candidate candidate, Consumer<ItemStack> output, LootContext context, int depth) {
        if (candidate.expandedTagItem() != null) {
            output.accept(ItemStack.of(candidate.expandedTagItem()));
            return;
        }
        var entry = candidate.entry();
        var decorated = decorate(entry.functions(), output, context);
        switch (entry) {
            case ItemEntry item -> decorated.accept(ItemStack.of(item.name()));
            case TagEntry tag -> tagMembers(tag.name()).forEach(member -> decorated.accept(ItemStack.of(member)));
            case DynamicEntry dynamic -> environment.dynamicLoot(dynamic.name()).forEach(decorated);
            case EmptyEntry ignored -> {
            }
            case NestedTableEntry nested -> {
                var table = nested.value().isLeft()
                        ? tableById(nested.value().left().orElseThrow())
                        : nested.value().right().orElseThrow();
                processTable(table, context, decorated, depth + 1);
            }
            case SlotsEntry slots -> SlotSources.provide(slots.slotSource(), context, process).stream()
                    .filter(stack -> !stack.isAir())
                    .forEach(decorated);
        }
    }

    /**
     * 原版 {@code BuiltInRegistries.ITEM.getTagOrEmpty}：标签不存在时为空。
     */
    private List<Material> tagMembers(Key tag) {
        var members = process.material().getTag(net.minestom.server.registry.TagKey.<Material>of(tag));
        if (members == null) {
            return List.of();
        }
        var result = new ArrayList<Material>();
        for (var member : members) {
            result.add(Objects.requireNonNull(process.material().get(member), member.key().asString()));
        }
        return result;
    }

    /**
     * 原版 {@code LootItemFunction.decorate}：修饰器列表不为空时，产出先经过它们再交给下游。
     */
    private Consumer<ItemStack> decorate(List<LootFunction> functions, Consumer<ItemStack> output, LootContext context) {
        if (functions.isEmpty()) {
            return output;
        }
        return stack -> output.accept(applyChain(stack, functions, context));
    }

    private boolean conditionsPass(List<LootCondition> conditions, LootContext context) {
        for (var condition : conditions) {
            if (!test(condition, context)) return false;
        }
        return true;
    }

    private ItemStack applyChain(ItemStack start, List<LootFunction> chain, LootContext context) {
        var current = start;
        for (var function : chain) {
            current = applyFunction(current, function, context);
        }
        return current;
    }

    // ------------------------------------------------------------------
    // 拆分
    // ------------------------------------------------------------------

    /**
     * 原版 {@code LootTable.createStackSplitter}：数量不小于最大堆叠数的物品按最大堆叠数切成多堆；空物品原样交给下游。
     * Minestom 没有原版 {@code isItemEnabled} 的功能开关，跳过这一步。
     */
    private static Consumer<ItemStack> stackSplitter(Consumer<ItemStack> output) {
        return result -> {
            if (result.isAir() || result.amount() < result.maxStackSize()) {
                output.accept(result);
                return;
            }
            int count = result.amount();
            while (count > 0) {
                var copy = result.withAmount(Math.min(result.maxStackSize(), count));
                count -= copy.amount();
                output.accept(copy);
            }
        };
    }

    /**
     * 产出尾部把 {@link ItemStack#isSimilar 物品完全相同} 的部分按 {@link ItemStack#maxStackSize}
     * 做合并（DESIGN 8.3 第 8 步，原版没有这一步）；空物品不进入结果。
     */
    private static List<ItemStack> mergeStacks(List<ItemStack> raw) {
        var result = new ArrayList<ItemStack>();
        for (var stack : raw) {
            if (stack.isAir()) continue;
            mergeOrAppend(result, stack);
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
     * 原版各个条件类的 {@code test}。
     */
    boolean test(LootCondition condition, LootContext context) {
        return switch (condition) {
            case InvertedCondition inverted -> !test(inverted.term(), context);
            case AnyOfCondition anyOf -> {
                for (var term : anyOf.terms()) {
                    if (test(term, context)) {
                        yield true;
                    }
                }
                yield false;
            }
            case AllOfCondition allOf -> conditionsPass(allOf.terms(), context);
            // 原版 LootItemRandomChanceCondition：nextFloat < chance
            case RandomChanceCondition randomChance -> {
                float probability = LootEvaluation.getFloat(randomChance.chance(), context, environment);
                yield context.random().nextFloat() < probability;
            }
            case RandomChanceWithEnchantedBonusCondition bonus -> {
                var killer = LootEvaluation.entity(context, ContextKey.ATTACKING_ENTITY);
                int level = killer instanceof LivingEntity living
                        ? LootEvaluation.enchantmentLevel(bonus.enchantment(), living, process) : 0;
                float chance = level > 0 ? LootEvaluation.levelBased(bonus.enchantedChance(), level) : bonus.unenchantedChance();
                yield context.random().nextFloat() < chance;
            }
            case EntityPropertiesCondition properties -> {
                var entity = LootEvaluation.entity(context, properties.entity().contextParam());
                var origin = LootEvaluation.origin(context);
                yield properties.predicate().isEmpty()
                        || EntityPredicates.matches(properties.predicate().get(), context.level(), origin, entity, process);
            }
            // 原版 LootItemKilledByPlayerCondition：只判断参数是否存在
            case KilledByPlayerCondition ignored -> context.find(ContextKey.LAST_DAMAGE_PLAYER) != null;
            case EntityScoresCondition scores -> testEntityScores(scores, context);
            case BlockStatePropertyCondition blockState -> {
                var state = LootEvaluation.block(context, ContextKey.BLOCK_STATE);
                yield state != null && state.id() == blockState.block().id()
                        && (blockState.properties().isEmpty() || StatePredicates.matches(blockState.properties().get(), state));
            }
            case MatchToolCondition matchTool -> {
                var tool = LootEvaluation.tool(context);
                yield tool != null && (matchTool.predicate().isEmpty() || ItemPredicates.test(matchTool.predicate().get(), tool, process));
            }
            case TableBonusCondition tableBonus -> {
                var tool = LootEvaluation.tool(context);
                int level = tool != null ? LootEvaluation.itemEnchantmentLevel(tableBonus.enchantment(), tool) : 0;
                float chance = tableBonus.values().get(Math.min(level, tableBonus.values().size() - 1));
                yield context.random().nextFloat() < chance;
            }
            case SurvivesExplosionCondition ignored -> {
                var radius = LootEvaluation.explosionRadius(context);
                if (radius == null) {
                    yield true;
                }
                float probability = 1.0F / radius;
                yield context.random().nextFloat() <= probability;
            }
            case DamageSourcePropertiesCondition damageSource -> {
                var damage = LootEvaluation.damageSource(context);
                var origin = LootEvaluation.origin(context);
                yield origin != null && damage != null && (damageSource.predicate().isEmpty()
                        || EntityPredicates.matches(damageSource.predicate().get(), context.level(), origin, damage, process));
            }
            case LocationCheckCondition location -> {
                var origin = LootEvaluation.origin(context);
                yield origin != null && (location.predicate().isEmpty()
                        || LocationPredicates.matches(location.predicate().get(), LootEvaluation.level(context),
                        origin.x() + location.offsetX(), origin.y() + location.offsetY(), origin.z() + location.offsetZ(), process));
            }
            case WeatherCheckCondition weather -> {
                var level = LootEvaluation.level(context);
                // 原版按过渡中的当前雨量与雷暴强度判断；Minestom 的 getWeather 返回天气过渡的目标值，过渡中的取值不对外提供
                var current = level.getWeather();
                boolean canHaveWeather = level.getCachedDimensionType().hasSkylight() && !level.getCachedDimensionType().hasCeiling()
                        && !Key.key(level.getDimensionName()).equals(Key.key("the_end"));
                boolean raining = canHaveWeather && current.rainLevel() > 0.2F;
                boolean thundering = canHaveWeather && current.thunderLevel() * current.rainLevel() > 0.9F;
                if (weather.isRaining().isPresent() && weather.isRaining().get() != raining) {
                    yield false;
                }
                yield weather.isThundering().isEmpty() || weather.isThundering().get() == thundering;
            }
            case ReferenceCondition reference -> {
                var inner = environment.findCondition(reference.name())
                        .orElseThrow(() -> new LootException("Unknown condition: " + reference.name().asString()));
                yield test(inner, context);
            }
            case TimeCheckCondition timeCheck -> {
                long time = LootEvaluation.level(context).getTime(timeCheck.clock());
                if (timeCheck.period().isPresent()) {
                    time %= timeCheck.period().get();
                }
                yield LootEvaluation.test(timeCheck.value(), (int) time, context, environment);
            }
            case ValueCheckCondition valueCheck -> LootEvaluation.test(valueCheck.range(),
                    LootEvaluation.getInt(valueCheck.value(), context, environment), context, environment);
            // 原版 EnchantmentActiveCheck：读取必需的参数
            case EnchantmentActiveCheckCondition check ->
                    LootEvaluation.requireTyped(context, ContextKey.ENCHANTMENT_ACTIVE, Boolean.class) == check.active();
            // TODO(minestom): Minestom 不计算环境属性在某个位置的取值（原版 EnvironmentAttributeSystem）
            case EnvironmentAttributeCheckCondition<?> check -> throw LootEvaluation.unsupported(
                    "value of the environment attribute " + check.attribute().key().asString());
        };
    }

    /**
     * 原版 {@code EntityHasScoreCondition#test}：实体不存在时不通过；计分项不存在、没有分数或分数不在范围内时不通过。
     */
    private boolean testEntityScores(EntityScoresCondition condition, LootContext context) {
        var entity = LootEvaluation.entity(context, condition.entity().contextParam());
        if (entity == null) {
            return false;
        }
        var view = environment.scoreboards();
        var holder = view.getScoreboardName(entity);
        for (var entry : condition.scores().entrySet()) {
            if (holder.isEmpty()) {
                return false;
            }
            var score = view.getScore(holder.get(), entry.getKey());
            if (score.isEmpty() || !LootEvaluation.test(entry.getValue(), score.getAsInt(), context, environment)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 修饰器（原版 net.minecraft.world.level.storage.loot.functions 同名类）
    // ------------------------------------------------------------------

    /**
     * 原版 {@code LootItemConditionalFunction#apply}：条件都满足时执行修饰器，否则物品不变；{@code sequence} 没有条件。
     */
    private ItemStack applyFunction(ItemStack stack, LootFunction function, LootContext context) {
        if (!conditionsPass(function.conditions(), context)) {
            return stack;
        }
        return run(stack, function, context);
    }

    private ItemStack run(ItemStack stack, LootFunction function, LootContext context) {
        var random = context.random();
        return switch (function) {
            case SetCountFunction setCount -> {
                int base = setCount.add() ? count(stack) : 0;
                yield stack.withAmount(base + LootEvaluation.getInt(setCount.count(), context, environment));
            }
            case SetItemFunction setItem -> stack.isAir() ? stack : stack.withMaterial(setItem.item());
            case EnchantWithLevelsFunction enchant -> {
                int cost = LootEvaluation.getInt(enchant.levels(), context, environment);
                var source = enchant.options().map(options -> Holders.members(options, process.enchantment()))
                        .orElseGet(this::allEnchantments);
                var result = Enchanting.enchantItem(random, stack, cost, source, process);
                if (enchant.includeAdditionalCostComponent() && context.find(ContextKey.ADDITIONAL_COST_COMPONENT_ALLOWED) != null
                        && !result.isAir() && cost > 0) {
                    result = result.with(DataComponents.ADDITIONAL_TRADE_COST, cost);
                }
                yield result;
            }
            case EnchantRandomlyFunction enchant -> enchantRandomly(stack, enchant, context);
            case SetEnchantmentsFunction setEnchantments -> {
                var target = stack.material() == Material.BOOK ? stack.withMaterial(Material.ENCHANTED_BOOK) : stack;
                yield Enchanting.updateEnchantments(target, enchantments -> setEnchantments.enchantments().forEach((key, provider) -> {
                    int value = LootEvaluation.getInt(provider, context, environment);
                    int base = setEnchantments.add() ? enchantments.getOrDefault(key, 0) : 0;
                    Enchanting.set(enchantments, key, Mth.clamp(base + value, 0, 255));
                }));
            }
            case SetCustomDataFunction setCustomData -> {
                var current = stack.get(DataComponents.CUSTOM_DATA, CustomData.EMPTY).nbt();
                yield withCustomData(stack, io.ib67.yuanban.codec.NbtUtils.merge(current, setCustomData.tag()));
            }
            case SetComponentsFunction setComponents -> applyComponentsAndValidate(stack, setComponents);
            case SmeltFunction smelt -> {
                if (stack.isAir()) {
                    yield stack;
                }
                var result = environment.smelting().smelt(stack);
                if (result.isPresent() && !result.get().isAir()) {
                    var smelted = result.get();
                    int newCount = (smelt.useInputCount() ? stack.amount() : 1) * smelted.amount();
                    yield smelted.withAmount(Math.min(newCount, smelted.maxStackSize()));
                }
                yield stack;
            }
            case EnchantedCountIncreaseFunction increase -> {
                var killer = LootEvaluation.entity(context, ContextKey.ATTACKING_ENTITY);
                if (!(killer instanceof LivingEntity living)) {
                    yield stack;
                }
                int level = LootEvaluation.enchantmentLevel(increase.enchantment(), living, process);
                if (level == 0) {
                    yield stack;
                }
                float addition = level * LootEvaluation.getFloat(increase.count(), context, environment);
                var result = stack.withAmount(count(stack) + Math.round(addition));
                if (increase.limit() > 0 && count(result) > increase.limit()) {
                    result = result.withAmount(increase.limit());
                }
                yield result;
            }
            case SetDamageFunction setDamage -> {
                if (!ContainerContents.isDamageable(stack)) {
                    yield stack;
                }
                int maxDamage = stack.get(DataComponents.MAX_DAMAGE, 0);
                int damageValue = Mth.clamp(stack.get(DataComponents.DAMAGE, 0), 0, maxDamage);
                float base = setDamage.add() ? 1.0F - (float) damageValue / maxDamage : 0.0F;
                float pct = 1.0F - Mth.clamp(LootEvaluation.getFloat(setDamage.damage(), context, environment) + base, 0.0F, 1.0F);
                yield stack.with(DataComponents.DAMAGE, Mth.clamp(Mth.floor(pct * maxDamage), 0, maxDamage));
            }
            case SetAttributesFunction attributes -> {
                var modifiers = attributes.replace() ? AttributeList.EMPTY : stack.get(DataComponents.ATTRIBUTE_MODIFIERS, AttributeList.EMPTY);
                for (var modifier : attributes.modifiers()) {
                    var slot = modifier.slots().get(random.nextInt(modifier.slots().size()));
                    double amount = LootEvaluation.getFloat(modifier.amount(), context, environment);
                    var kept = new ArrayList<AttributeList.Modifier>();
                    for (var existing : modifiers.modifiers()) {
                        if (!(existing.attribute().equals(modifier.attribute()) && existing.modifier().id().equals(modifier.id()))) {
                            kept.add(existing);
                        }
                    }
                    kept.add(new AttributeList.Modifier(modifier.attribute(),
                            new AttributeModifier(modifier.id(), amount, modifier.operation()), slot));
                    modifiers = new AttributeList(kept);
                }
                yield stack.with(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
            }
            case SetNameFunction setName -> {
                if (setName.name().isEmpty()) {
                    yield stack;
                }
                var name = resolve(setName.name().get(), setName.resolutionContext(), context);
                yield switch (setName.target()) {
                    case CUSTOM_NAME -> stack.with(DataComponents.CUSTOM_NAME, name);
                    case ITEM_NAME -> stack.with(DataComponents.ITEM_NAME, name);
                };
            }
            case ExplorationMapFunction ignored -> {
                if (stack.material() != Material.MAP || LootEvaluation.origin(context) == null) {
                    yield stack;
                }
                // TODO(minestom): Minestom 没有结构，也没有地图数据，无法查找最近的结构并生成探险地图
                throw LootEvaluation.unsupported("structure search and map data for the exploration map");
            }
            case SetStewEffectFunction stew -> {
                if (stack.material() != Material.SUSPICIOUS_STEW || stew.effects().isEmpty()) {
                    yield stack;
                }
                var entry = stew.effects().get(random.nextInt(stew.effects().size()));
                int duration = LootEvaluation.getInt(entry.duration(), context, environment);
                if (!entry.effect().instantaneous()) {
                    duration *= 20;
                }
                var effects = new ArrayList<>(stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY).effects());
                effects.add(new SuspiciousStewEffects.Effect(entry.effect(), duration));
                yield stack.with(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(effects));
            }
            case CopyNameFunction copyName -> switch (copyName.source()) {
                case LootContextArg.Entity target -> {
                    var entity = LootEvaluation.entity(context, target.contextParam());
                    if (entity == null) {
                        yield stack;
                    }
                    var name = entity.get(DataComponents.CUSTOM_NAME);
                    yield name == null ? stack.without(DataComponents.CUSTOM_NAME) : stack.with(DataComponents.CUSTOM_NAME, name);
                }
                case LootContextArg.BlockEntity blockEntity -> {
                    var block = LootEvaluation.block(context, blockEntity.contextParam());
                    if (block == null) {
                        yield stack;
                    }
                    // TODO(minestom): Minestom 的方块实体没有原版 Nameable#getCustomName 的自定义名称
                    throw LootEvaluation.unsupported("custom name of the block entity " + block.key().asString());
                }
                case LootContextArg.Tool ignored -> throw new IllegalStateException("copy_name does not accept the tool as source");
            };
            case SetContainerContentsFunction contents -> {
                if (stack.isAir()) {
                    yield stack;
                }
                var items = new ArrayList<ItemStack>();
                for (var entry : contents.entries()) {
                    expand(entry, context, candidate -> create(candidate, stackSplitter(items::add), context, 0));
                }
                yield ContainerContents.setContents(stack, contents.component(), items);
            }
            case ModifyContentsFunction modify -> stack.isAir() ? stack
                    : ContainerContents.modifyItems(stack, modify.component(), item -> applyFunction(item, modify.modifier(), context));
            case FilteredFunction filtered -> {
                var next = ItemPredicates.test(filtered.itemFilter(), stack, process) ? filtered.onPass() : filtered.onFail();
                yield next.isPresent() ? applyFunction(stack, next.get(), context) : stack;
            }
            case LimitCountFunction limit -> stack.withAmount(LootEvaluation.clamp(limit.limit(), count(stack), context, environment));
            case ApplyBonusFunction applyBonus -> {
                var tool = LootEvaluation.tool(context);
                if (tool == null) {
                    yield stack;
                }
                int level = LootEvaluation.itemEnchantmentLevel(applyBonus.enchantment(), tool);
                yield stack.withAmount(applyBonusCount(applyBonus.formula(), random, count(stack), level));
            }
            case SetContainerLootTableFunction lootTable -> stack.isAir() ? stack
                    : stack.with(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(lootTable.name().asString(), lootTable.seed()));
            case ExplosionDecayFunction ignored -> {
                var radius = LootEvaluation.explosionRadius(context);
                if (radius == null) {
                    yield stack;
                }
                float probability = 1.0F / radius;
                int currentCount = count(stack);
                int resultCount = 0;
                for (int i = 0; i < currentCount; i++) {
                    if (random.nextFloat() <= probability) {
                        resultCount++;
                    }
                }
                yield stack.withAmount(resultCount);
            }
            case SetLoreFunction setLore -> {
                var current = stack.get(DataComponents.LORE, List.of());
                var resolved = setLore.lore().stream().map(line -> resolve(line, setLore.resolutionContext(), context)).toList();
                yield stack.with(DataComponents.LORE, ListOperations.apply(setLore.mode(), current, resolved, 256));
            }
            case FillPlayerHeadFunction fill -> {
                if (stack.material() != Material.PLAYER_HEAD
                        || !(LootEvaluation.entity(context, fill.entity().contextParam()) instanceof Player donor)) {
                    yield stack;
                }
                yield stack.with(DataComponents.PROFILE, new ResolvableProfile(gameProfile(donor)));
            }
            case CopyCustomDataFunction copy -> copyCustomData(stack, copy, context);
            case CopyBlockStateFunction copyState -> {
                var state = LootEvaluation.block(context, ContextKey.BLOCK_STATE);
                if (state == null) {
                    yield stack;
                }
                var known = copyState.block().properties().keySet();
                var blockState = stack.get(DataComponents.BLOCK_STATE, ItemBlockState.EMPTY);
                for (var property : copyState.properties()) {
                    var value = state.properties().get(property);
                    if (known.contains(property) && value != null) {
                        blockState = blockState.with(property, value);
                    }
                }
                yield stack.with(DataComponents.BLOCK_STATE, blockState);
            }
            case SetBannerPatternFunction banner -> {
                if (!banner.append()) {
                    yield stack.with(DataComponents.BANNER_PATTERNS, banner.patterns());
                }
                var layers = new ArrayList<>(stack.get(DataComponents.BANNER_PATTERNS, new BannerPatterns(List.of())).layers());
                layers.addAll(banner.patterns().layers());
                yield stack.with(DataComponents.BANNER_PATTERNS, new BannerPatterns(layers));
            }
            case SetPotionFunction setPotion -> withPotion(stack, setPotion.id());
            case SetRandomDyesFunction dyes -> {
                int rolls = LootEvaluation.getInt(dyes.numberOfDyes(), context, environment);
                if (rolls <= 0) {
                    yield stack;
                }
                var values = DyeColor.values();
                var chosen = new ArrayList<DyeColor>(rolls);
                for (int i = 0; i < rolls; i++) {
                    chosen.add(values[random.nextInt(values.length)]);
                }
                yield applyDyes(stack, chosen);
            }
            case SetRandomPotionFunction randomPotion -> {
                Optional<RegistryKey<PotionType>> potion = randomPotion.options().isPresent()
                        ? Holders.randomElement(randomPotion.options().get(), process.potionType(), random)
                        : Holders.randomElement(process.potionType(), random);
                yield potion.map(key -> withPotion(stack, Objects.requireNonNull(process.potionType().get(key), key.key().asString())))
                        .orElse(stack);
            }
            case SetInstrumentFunction instrument -> Holders.randomElement(instrument.options(), process.instrument(), random)
                    .map(key -> stack.with(DataComponents.INSTRUMENT, key))
                    .orElse(stack);
            case FunctionReference reference -> {
                var inner = environment.findFunction(reference.name())
                        .orElseThrow(() -> new LootException("Unknown function: " + reference.name().asString()));
                yield applyFunction(stack, inner, context);
            }
            case SequenceFunction sequence -> applyChain(stack, sequence.functions(), context);
            case CopyComponentsFunction copy -> copyComponents(stack, copy, context);
            case SetFireworksFunction fireworks -> {
                var old = stack.get(DataComponents.FIREWORKS, new FireworkList(0, List.of()));
                yield stack.with(DataComponents.FIREWORKS, new FireworkList(
                        fireworks.flightDuration().orElse(old.flightDuration()),
                        fireworks.explosions().map(operation -> ListOperations.apply(operation, old.explosions())).orElse(old.explosions())));
            }
            case SetFireworkExplosionFunction explosion -> {
                var original = stack.get(DataComponents.FIREWORK_EXPLOSION, DEFAULT_FIREWORK_EXPLOSION);
                yield stack.with(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(
                        explosion.shape().orElse(original.shape()),
                        explosion.colors().map(LootSelector::colors).orElse(original.colors()),
                        explosion.fadeColors().map(LootSelector::colors).orElse(original.fadeColors()),
                        explosion.trail().orElse(original.hasTrail()),
                        explosion.twinkle().orElse(original.hasTwinkle())));
            }
            case SetBookCoverFunction cover -> {
                var original = stack.get(DataComponents.WRITTEN_BOOK_CONTENT, EMPTY_WRITTEN_BOOK);
                yield stack.with(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                        cover.title().orElse(original.title()),
                        cover.author().orElse(original.author()),
                        cover.generation().orElse(original.generation()),
                        original.pages(),
                        original.resolved()));
            }
            case SetWrittenBookPagesFunction pages -> {
                var original = stack.get(DataComponents.WRITTEN_BOOK_CONTENT, EMPTY_WRITTEN_BOOK);
                // 原版 WrittenBookContent#withReplacedPages：替换页之后为未解析
                yield stack.with(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(original.title(), original.author(),
                        original.generation(), ListOperations.apply(pages.mode(), original.pages(), pages.pages(), Integer.MAX_VALUE), false));
            }
            case SetWritableBookPagesFunction pages -> {
                var original = stack.get(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of()));
                yield stack.with(DataComponents.WRITABLE_BOOK_CONTENT,
                        new WritableBookContent(ListOperations.apply(pages.mode(), original.pages(), pages.pages(), 100)));
            }
            case ToggleTooltipsFunction toggle -> {
                var display = stack.get(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(false, Set.of()));
                // 原版 TooltipDisplay#withHidden(type, !shown)；Minestom 的 with 表示显示，without 表示隐藏
                for (var entry : toggle.toggles().entrySet()) {
                    display = entry.getValue() ? display.with(entry.getKey()) : display.without(entry.getKey());
                }
                yield stack.with(DataComponents.TOOLTIP_DISPLAY, display);
            }
            case SetOminousBottleAmplifierFunction amplifier -> stack.with(DataComponents.OMINOUS_BOTTLE_AMPLIFIER,
                    Mth.clamp(LootEvaluation.getInt(amplifier.amplifier(), context, environment), 0, 4));
            case SetCustomModelDataFunction modelData -> {
                var current = stack.get(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of()));
                yield stack.with(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                        apply(modelData.floats(), current.floats(), provider -> LootEvaluation.getFloat(provider, context, environment)),
                        apply(modelData.flags(), current.flags(), Function.identity()),
                        apply(modelData.strings(), current.strings(), Function.identity()),
                        apply(modelData.colors(), current.colors(),
                                provider -> new Color(LootEvaluation.getInt(provider, context, environment)))));
            }
            case DiscardFunction ignored -> ItemStack.AIR;
        };
    }

    /**
     * 原版空物品堆的数量是 0。
     */
    private static int count(ItemStack stack) {
        return stack.isAir() ? 0 : stack.amount();
    }

    /**
     * 原版附魔注册表的全部附魔，按注册顺序。
     */
    private List<RegistryKey<Enchantment>> allEnchantments() {
        var registry = process.enchantment();
        var result = new ArrayList<RegistryKey<Enchantment>>(registry.size());
        for (int i = 0; i < registry.size(); i++) {
            result.add(registry.getKey(i));
        }
        return result;
    }

    /**
     * 原版 {@code EnchantRandomlyFunction#run}：书之外的物品在 {@code only_compatible} 时只考虑能附在它上的附魔；
     * 没有候选时物品不变。
     */
    private ItemStack enchantRandomly(ItemStack stack, EnchantRandomlyFunction function, LootContext context) {
        var random = context.random();
        boolean targetIsBook = stack.material() == Material.BOOK;
        boolean checkCompatibility = !targetIsBook && function.onlyCompatible();
        var candidates = function.options().map(options -> Holders.members(options, process.enchantment()))
                .orElseGet(this::allEnchantments)
                .stream()
                .filter(key -> !checkCompatibility || Enchanting.canEnchant(Enchanting.enchantment(key, process), stack))
                .toList();
        if (candidates.isEmpty()) {
            return stack;
        }
        var key = candidates.get(random.nextInt(candidates.size()));
        var enchantment = Enchanting.enchantment(key, process);
        int level = Mth.nextInt(random, 1, enchantment.maxLevel());
        var result = targetIsBook ? ItemStack.of(Material.ENCHANTED_BOOK) : stack;
        result = Enchanting.enchant(result, key, level);
        if (function.includeAdditionalCostComponent() && context.find(ContextKey.ADDITIONAL_COST_COMPONENT_ALLOWED) != null) {
            result = result.with(DataComponents.ADDITIONAL_TRADE_COST, 2 + random.nextInt(5 + level * 10) + 3 * level);
        }
        return result;
    }

    /**
     * 原版 {@code ApplyBonusCount$Formula#calculateNewCount}。
     */
    private static int applyBonusCount(ApplyBonusFormula formula, Random random, int count, int level) {
        return switch (formula) {
            case ApplyBonusFormula.BinomialWithBonusCount binomial -> {
                int result = count;
                for (int i = 0; i < level + binomial.extraRounds(); i++) {
                    if (random.nextFloat() < binomial.probability()) {
                        result++;
                    }
                }
                yield result;
            }
            case ApplyBonusFormula.UniformBonusCount uniform -> count + random.nextInt(uniform.bonusMultiplier() * level + 1);
            case ApplyBonusFormula.OreDrops ignored -> {
                if (level <= 0) {
                    yield count;
                }
                int bonus = random.nextInt(level + 2) - 1;
                if (bonus < 0) {
                    bonus = 0;
                }
                yield count * (bonus + 1);
            }
        };
    }

    /**
     * 原版 {@code CustomData.set}：标签为空时移除组件。
     */
    private static ItemStack withCustomData(ItemStack stack, CompoundBinaryTag tag) {
        return tag.isEmpty() ? stack.without(DataComponents.CUSTOM_DATA) : stack.with(DataComponents.CUSTOM_DATA, new CustomData(tag));
    }

    /**
     * 原版 {@code ItemStack#applyComponentsAndValidate}：应用组件补丁之后不满足 {@code ItemStack.validateStrict} 时恢复原样。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ItemStack applyComponentsAndValidate(ItemStack stack, SetComponentsFunction function) {
        var result = stack;
        for (var entry : function.components().entrySet()) {
            DataComponent component = entry.component();
            result = entry.value() == null ? result.without(component) : result.with(component, entry.value());
        }
        return isValidStrict(result) ? result : stack;
    }

    /**
     * 原版 {@code ItemStack.validateStrict}：可损坏的物品不能堆叠，容器类组件中物品的数量不超过最大堆叠数，
     * 收纳袋内容的重量能够计算，物品数量不超过最大堆叠数。
     */
    private static boolean isValidStrict(ItemStack stack) {
        if (stack.has(DataComponents.MAX_DAMAGE) && stack.get(DataComponents.MAX_STACK_SIZE, 1) > 1) {
            return false;
        }
        var container = stack.get(DataComponents.CONTAINER);
        if (container != null && !sizesValid(container.stream().filter(item -> !item.isAir()).toList())) {
            return false;
        }
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !(sizesValid(bundle) && ContainerContents.isBundleWeightValid(bundle))) {
            return false;
        }
        var projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (projectiles != null && !sizesValid(projectiles)) {
            return false;
        }
        return count(stack) <= stack.maxStackSize();
    }

    private static boolean sizesValid(List<ItemStack> items) {
        for (var item : items) {
            if (count(item) > item.maxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 原版 {@code SetNameFunction.createResolver}：指定了实体且上下文中有该实体时，按该实体解析文本中的选择器、计分板等内容；
     * 否则文本原样使用。
     */
    private static Component resolve(Component text, Optional<EntityTarget> target, LootContext context) {
        if (target.isEmpty() || LootEvaluation.entity(context, target.get().contextParam()) == null) {
            return text;
        }
        // TODO(minestom): Minestom 没有原版 ComponentUtils#updateForEntity 按实体解析文本组件的功能
        throw LootEvaluation.unsupported("resolving text components for an entity");
    }

    /**
     * 原版 {@code ResolvableProfile.createResolved(GameProfile)}：玩家的 UUID、名字与皮肤属性。
     */
    private static GameProfile gameProfile(Player player) {
        var skin = player.getSkin();
        var properties = skin == null
                ? List.<GameProfile.Property>of()
                : List.of(new GameProfile.Property("textures", skin.textures(), skin.signature()));
        return new GameProfile(player.getUuid(), player.getUsername(), properties);
    }

    /**
     * 原版 {@code PotionContents#withPotion}：替换药水，保留自定义颜色、效果与名称。
     */
    private static ItemStack withPotion(ItemStack stack, PotionType potion) {
        var contents = stack.get(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return stack.with(DataComponents.POTION_CONTENTS,
                new PotionContents(potion, contents.customColor(), contents.customEffects(), contents.customName()));
    }

    /**
     * 原版 {@code DyedItemColor.applyDyes(ItemStack, List)}：与原有颜色和各染料的颜色求平均并保持平均亮度，
     * 结果的数量为 1。
     */
    private static ItemStack applyDyes(ItemStack stack, List<DyeColor> dyes) {
        int redTotal = 0;
        int greenTotal = 0;
        int blueTotal = 0;
        int intensityTotal = 0;
        int colorCount = 0;
        var current = stack.get(DataComponents.DYED_COLOR);
        if (current != null) {
            intensityTotal += Math.max(current.red(), Math.max(current.green(), current.blue()));
            redTotal += current.red();
            greenTotal += current.green();
            blueTotal += current.blue();
            colorCount++;
        }
        for (var dye : dyes) {
            var color = dye.color();
            intensityTotal += Math.max(color.red(), Math.max(color.green(), color.blue()));
            redTotal += color.red();
            greenTotal += color.green();
            blueTotal += color.blue();
            colorCount++;
        }
        int red = redTotal / colorCount;
        int green = greenTotal / colorCount;
        int blue = blueTotal / colorCount;
        float averageIntensity = (float) intensityTotal / colorCount;
        float resultIntensity = Math.max(red, Math.max(green, blue));
        red = (int) (red * averageIntensity / resultIntensity);
        green = (int) (green * averageIntensity / resultIntensity);
        blue = (int) (blue * averageIntensity / resultIntensity);
        return stack.withAmount(1).with(DataComponents.DYED_COLOR, new Color(red, green, blue));
    }

    private static List<RGBLike> colors(List<Integer> colors) {
        return colors.stream().<RGBLike>map(Color::new).toList();
    }

    /**
     * 原版 {@code SetCustomModelDataFunction#apply}：没有给出操作时保持原值，否则先转换新值再按列表操作合并。
     */
    private static <T, E> List<E> apply(Optional<StandAloneOperation<T>> operation, List<E> current, Function<T, E> mapper) {
        return operation.map(o -> ListOperations.apply(o.operation(), current, o.values().stream().map(mapper).toList(), Integer.MAX_VALUE))
                .orElse(current);
    }

    /**
     * 原版 {@code CopyCustomDataFunction#run}：来源不存在时不修改；有操作找到来源值时写回自定义数据。
     */
    private ItemStack copyCustomData(ItemStack stack, CopyCustomDataFunction function, LootContext context) {
        BinaryTag source = switch (function.source()) {
            case NbtSource.Storage storage -> environment.storage().root(storage.storage());
            case NbtSource.Context target -> switch (target.target()) {
                case LootContextArg.Entity entityTarget -> {
                    var entity = LootEvaluation.entity(context, entityTarget.contextParam());
                    if (entity == null) {
                        yield null;
                    }
                    // TODO(minestom): Minestom 没有原版保存实体时写出的 NBT（原版 NbtPredicate.getEntityTagToCompare）
                    throw LootEvaluation.unsupported("NBT of the entity " + entity.getEntityType().key().asString());
                }
                case LootContextArg.BlockEntity blockEntity -> {
                    var block = LootEvaluation.block(context, blockEntity.contextParam());
                    yield block == null ? null : blockEntityNbt(block);
                }
                case LootContextArg.Tool ignored -> throw new IllegalStateException("NBT providers do not accept the tool as source");
            };
        };
        if (source == null) {
            return stack;
        }
        var current = stack.get(DataComponents.CUSTOM_DATA, CustomData.EMPTY).nbt();
        var result = NbtPaths.copy(function.ops(), source, current);
        return result == null ? stack : withCustomData(stack, result);
    }

    /**
     * 原版 {@code ContextNbtProvider} 的方块实体来源（{@code BlockEntity#saveWithFullMetadata}）：方块实体的数据加上 {@code id}。
     * 上下文的方块参数没有位置，原版写入的坐标 {@code x}、{@code y}、{@code z} 不在其中。
     */
    private static CompoundBinaryTag blockEntityNbt(net.minestom.server.instance.block.Block block) {
        var nbt = block.nbtOrEmpty();
        var type = block.blockEntityType();
        return type == null ? nbt : nbt.put("id", StringBinaryTag.stringBinaryTag(type.key().asString()));
    }

    /**
     * 原版 {@code CopyComponentsFunction#run}：来源是实体或工具时，逐个复制 {@code include}（缺省为全部组件类型，按注册顺序）中
     * 不在 {@code exclude} 里、来源上存在的组件。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ItemStack copyComponents(ItemStack stack, CopyComponentsFunction function, LootContext context) {
        DataComponent.Holder source = switch (function.source()) {
            case LootContextArg.Entity target -> LootEvaluation.entity(context, target.contextParam());
            case LootContextArg.Tool ignored -> LootEvaluation.tool(context);
            case LootContextArg.BlockEntity blockEntity -> {
                var block = LootEvaluation.block(context, blockEntity.contextParam());
                if (block == null) {
                    yield null;
                }
                // TODO(minestom): Minestom 的方块实体没有原版 BlockEntity#collectComponents 收集的组件
                throw LootEvaluation.unsupported("data components of the block entity " + block.key().asString());
            }
        };
        if (source == null) {
            return stack;
        }
        var exclude = function.exclude().orElse(List.of());
        var include = function.include().orElseGet(() -> DataComponent.values().stream()
                .sorted(Comparator.comparingInt(DataComponent::id))
                .toList());
        var result = stack;
        for (DataComponent component : include) {
            if (!exclude.contains(component)) {
                var value = source.get(component);
                if (value != null) {
                    result = result.with(component, value);
                }
            }
        }
        return result;
    }
}
