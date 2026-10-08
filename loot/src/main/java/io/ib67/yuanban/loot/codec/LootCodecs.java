package io.ib67.yuanban.loot.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.Identifiers;
import io.ib67.yuanban.codec.RegistryCodecs;
import io.ib67.yuanban.codec.RegistryId;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.condition.AllOfCondition;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.function.SequenceFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.NumberProvider;
import io.ib67.yuanban.loot.type.LootConditionType;
import io.ib67.yuanban.loot.type.LootEntryType;
import io.ib67.yuanban.loot.type.LootFunctionType;
import io.ib67.yuanban.loot.type.NumberProviderType;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 战利品数据的编解码器，对应原版 {@code LootTable.DIRECT_CODEC}、{@code LootPool.CODEC}、{@code LootPoolEntries.CODEC}、
 * {@code LootItemCondition.DIRECT_CODEC}、{@code LootItemFunctions.ROOT_CODEC}、{@code NumberProviders.CODEC} 与
 * {@code IntRange.CODEC}。
 * <p>
 * 条目、条件、修饰器、数字提供器按类型字段分派：类型 ID 在解码时到 {@link LootTypeRegistry} 中查找，找不到为
 * {@code Unknown registry key in ResourceKey[minecraft:root / minecraft:<注册表>]: <ID>}；每个类型的
 * {@link MapCodec} 由类型对象的 {@code codec} 函数在第一次用到时构造。这四种分派编解码器只能解码：
 * 类型对象只保存 ID 和构造函数，从 record 推不出它的类型 ID，编码时返回错误结果。
 * <p>
 * 解码中的注册表引用（附魔、结构、标签等）经 {@link io.ib67.yuanban.codec.RegistryOps} 检查，解码时要传入带注册表的 ops。
 */
public final class LootCodecs {
    private static final RegistryId<LootEntryType<?>> ENTRY_TYPE = RegistryId.of("loot_pool_entry_type");
    private static final RegistryId<LootConditionType<?>> CONDITION_TYPE = RegistryId.of("loot_condition_type");
    private static final RegistryId<LootFunctionType<?>> FUNCTION_TYPE = RegistryId.of("loot_function_type");
    private static final RegistryId<NumberProviderType<?>> NUMBER_PROVIDER_TYPE = RegistryId.of("loot_number_provider_type");

    private final LootTypeRegistry types;
    private final Map<Object, MapCodec<?>> typeCodecs = new ConcurrentHashMap<>();
    private final Codec<NumberProvider> number;
    private final Codec<IntRange> intRange;
    private final Codec<LootCondition> condition;
    private final Codec<LootFunction> typedFunction;
    private final Codec<LootFunction> function;
    private final Codec<LootEntry> entry;
    private final Codec<LootPool> pool;
    private final Codec<LootTable> table;

    public LootCodecs(LootTypeRegistry types) {
        this.types = Objects.requireNonNull(types, "types");

        Codec<NumberProvider> typedNumber = dispatch("type", NUMBER_PROVIDER_TYPE,
                id -> types.numberProvider(id).orElse(null), NumberProviderType::id, NumberProviderType::codec);
        this.number = Codec.recursive("NumberProvider", self -> {
            // 原版 NumberProviders.CODEC：裸数字是常量；对象先按 type 分派，没有 type 时按 uniform 解码
            Codec<NumberProvider> object = Codec.withAlternative(typedNumber, NumberCodecs.uniform(self).codec());
            Codec<ConstantValue> inline = Codec.FLOAT.xmap(ConstantValue::new, ConstantValue::value);
            return Codec.either(inline, object).xmap(either -> either.map(value -> value, value -> value),
                    value -> value instanceof ConstantValue constant ? Either.left(constant) : Either.right(value));
        });

        Codec<IntRange> rangeObject = RecordCodecBuilder.create(i -> i.group(
                number.optionalFieldOf("min").forGetter(range -> Optional.ofNullable(range.min())),
                number.optionalFieldOf("max").forGetter(range -> Optional.ofNullable(range.max()))
        ).apply(i, (min, max) -> new IntRange(min.orElse(null), max.orElse(null))));
        this.intRange = Codec.either(Codec.INT, rangeObject).xmap(
                either -> either.map(LootCodecs::exactRange, range -> range),
                LootCodecs::packRange);

        Codec<LootCondition> typedCondition = dispatch("condition", CONDITION_TYPE,
                id -> types.condition(id).orElse(null), LootConditionType::id, LootConditionType::codec);
        // 原版 LootItemCondition.DIRECT_CODEC：带 condition 字段的对象，或条件列表（all_of）
        this.condition = Codec.recursive("LootCondition", self -> Codec.withAlternative(typedCondition,
                self.listOf().xmap(AllOfCondition::new, AllOfCondition::terms)));

        this.typedFunction = dispatch("function", FUNCTION_TYPE,
                id -> types.function(id).orElse(null), LootFunctionType::id, LootFunctionType::codec);
        // 原版 LootItemFunctions.ROOT_CODEC：带 function 字段的对象，或带 function 字段的对象列表（sequence）
        this.function = Codec.withAlternative(typedFunction,
                typedFunction.listOf().xmap(SequenceFunction::new, SequenceFunction::functions));

        this.entry = dispatch("type", ENTRY_TYPE,
                id -> types.entry(id).orElse(null), LootEntryType::id, LootEntryType::codec);

        this.pool = RecordCodecBuilder.create(i -> i.group(
                entry.listOf().fieldOf("entries").forGetter(LootPool::entries),
                condition.listOf().optionalFieldOf("conditions", List.of()).forGetter(LootPool::conditions),
                function.listOf().optionalFieldOf("functions", List.of()).forGetter(LootPool::functions),
                number.fieldOf("rolls").forGetter(LootPool::rolls),
                number.optionalFieldOf("bonus_rolls", ConstantValue.ZERO).forGetter(LootPool::bonusRolls)
        ).apply(i, (entries, conditions, functions, rolls, bonusRolls) ->
                new LootPool(conditions, functions, rolls, bonusRolls, entries)));

        // 原版 LootContextParamSets.CODEC
        Codec<Key> paramSet = Identifiers.CODEC.validate(id -> LootTable.isKnownType(id)
                ? DataResult.success(id)
                : DataResult.error(() -> "No parameter set exists with id: '" + id.asString() + "'"));
        // 内联的嵌套表经条目的编解码器回到这里：条目类型的编解码器在解码时才构造，那时本字段已经赋值
        this.table = RecordCodecBuilder.create(i -> i.group(
                paramSet.lenientOptionalFieldOf("type", LootTable.DEFAULT_TYPE).forGetter(LootTable::type),
                Identifiers.CODEC.optionalFieldOf("random_sequence").forGetter(LootTable::randomSequence),
                pool.listOf().optionalFieldOf("pools", List.of()).forGetter(LootTable::pools),
                function.listOf().optionalFieldOf("functions", List.of()).forGetter(LootTable::functions)
        ).apply(i, (type, randomSequence, pools, functions) -> new LootTable(type, functions, pools, randomSequence)));
    }

    /**
     * 构造本对象的类型注册表。
     */
    public LootTypeRegistry types() {
        return types;
    }

    /**
     * 原版 {@code NumberProviders.CODEC}。
     */
    public Codec<NumberProvider> number() {
        return number;
    }

    /**
     * 原版 {@code IntRange.CODEC}：裸整数（两端都是这个常数），或 {@code {min?, max?}}。
     */
    public Codec<IntRange> intRange() {
        return intRange;
    }

    /**
     * 原版 {@code LootItemCondition.DIRECT_CODEC}。
     */
    public Codec<LootCondition> condition() {
        return condition;
    }

    /**
     * 原版 {@code LootItemFunctions.TYPED_CODEC}：带 {@code function} 字段的修饰器对象。
     */
    public Codec<LootFunction> typedFunction() {
        return typedFunction;
    }

    /**
     * 原版 {@code LootItemFunctions.ROOT_CODEC}。
     */
    public Codec<LootFunction> function() {
        return function;
    }

    /**
     * 原版 {@code LootPoolEntries.CODEC}。
     */
    public Codec<LootEntry> entry() {
        return entry;
    }

    /**
     * 原版 {@code LootPool.CODEC}：{@code entries}（必填）、{@code conditions}、{@code functions}、{@code rolls}、
     * {@code bonus_rolls}（默认常量 0）。
     */
    public Codec<LootPool> pool() {
        return pool;
    }

    /**
     * 原版 {@code LootTable.DIRECT_CODEC}：{@code type}（无法识别时按 {@code minecraft:generic}）、
     * {@code random_sequence}、{@code pools}、{@code functions}。
     */
    public Codec<LootTable> table() {
        return table;
    }

    private static IntRange exactRange(int value) {
        var constant = new ConstantValue(value);
        return new IntRange(constant, constant);
    }

    /**
     * 原版 {@code IntRange#unpackExact}：两端是同一个整数常量时写成裸整数。
     */
    private static Either<Integer, IntRange> packRange(IntRange range) {
        if (range.min() instanceof ConstantValue min && range.max() instanceof ConstantValue max
                && min.value() == max.value() && min.value() == (float) Math.floor(min.value())) {
            return Either.left((int) Math.floor(min.value()));
        }
        return Either.right(range);
    }

    /**
     * 按类型字段分派。类型 ID 解码时在注册表中查找；每个类型的 {@link MapCodec} 构造一次后缓存。
     */
    private <T, Y> Codec<T> dispatch(String typeKey, RegistryId<Y> registry, Function<Key, Y> lookup, Function<Y, Key> idOf,
                                     Function<Y, Function<LootCodecs, ? extends MapCodec<? extends T>>> factoryOf) {
        Codec<Y> typeCodec = RegistryCodecs.byName(registry, lookup, idOf);
        return typeCodec.partialDispatch(typeKey,
                value -> DataResult.error(() -> "Loot data is decode only, " + registry + " has no id for " + value),
                type -> DataResult.success(typeCodec(type, factoryOf)));
    }

    @SuppressWarnings("unchecked")
    private <T, Y> MapCodec<? extends T> typeCodec(Y type, Function<Y, Function<LootCodecs, ? extends MapCodec<? extends T>>> factoryOf) {
        var cached = typeCodecs.get(type);
        if (cached != null) {
            return (MapCodec<? extends T>) cached;
        }
        MapCodec<? extends T> created = factoryOf.apply(type).apply(this);
        var existing = typeCodecs.putIfAbsent(type, created);
        return existing != null ? (MapCodec<? extends T>) existing : created;
    }
}
