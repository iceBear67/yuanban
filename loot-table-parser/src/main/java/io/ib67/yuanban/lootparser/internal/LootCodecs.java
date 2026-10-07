package io.ib67.yuanban.lootparser.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.loot.EitherId;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.condition.AllOfCondition;
import io.ib67.yuanban.loot.condition.AnyOfCondition;
import io.ib67.yuanban.loot.condition.BlockPredicate;
import io.ib67.yuanban.loot.condition.BlockStatePropertyCondition;
import io.ib67.yuanban.loot.condition.DamageSourcePredicate;
import io.ib67.yuanban.loot.condition.DamageSourcePropertiesCondition;
import io.ib67.yuanban.loot.condition.EnchantmentActiveCheckCondition;
import io.ib67.yuanban.loot.condition.EntityPredicate;
import io.ib67.yuanban.loot.condition.EntityPropertiesCondition;
import io.ib67.yuanban.loot.condition.EntityScoresCondition;
import io.ib67.yuanban.loot.condition.EnvironmentAttributeCheckCondition;
import io.ib67.yuanban.loot.condition.InvertedCondition;
import io.ib67.yuanban.loot.condition.ItemPredicate;
import io.ib67.yuanban.loot.condition.KilledByPlayerCondition;
import io.ib67.yuanban.loot.condition.LocationCheckCondition;
import io.ib67.yuanban.loot.condition.LocationPredicate;
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
import io.ib67.yuanban.loot.entry.SlotSource;
import io.ib67.yuanban.loot.entry.SlotsEntry;
import io.ib67.yuanban.loot.entry.TagEntry;
import io.ib67.yuanban.loot.function.EnchantedOptions;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.number.BinomialValue;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EnchantmentLevelValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.EnvironmentAttributeValue;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import io.ib67.yuanban.loot.number.NumberProvider;
import io.ib67.yuanban.loot.number.ScoreboardName;
import io.ib67.yuanban.loot.number.ScoreboardValue;
import io.ib67.yuanban.loot.number.StorageValue;
import io.ib67.yuanban.loot.number.SumValue;
import io.ib67.yuanban.loot.number.UniformValue;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.registry.RegistryTranscoder;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 原版 26.2 战利品表 JSON 的全部编解码器。字段名与缺省值逐一对照原版类（{@code LootTable.DIRECT_CODEC}、
 * {@code LootPool.CODEC}、各 {@code LootItemCondition} / {@code LootPoolEntryContainer} /
 * {@code NumberProvider} 的 {@code MapCodec}）。本模块只负责解码方向，编码端所有 getter 直接报
 * {@link UnsupportedOperationException}。
 */
@ApiStatus.Internal
public final class LootCodecs {
    /** 原版 {@code ResourceLocation}（adventure 的 {@link Key}）。 */
    static final Codec<Key> ID = Codec.STRING.comapFlatMap(LootCodecs::readId, Key::asString);

    /** 原版各 ContextParamSet 名到 loot record {@code LootTable#type} 的映射（原版 {@code LootContextParamSets.REGISTRY}）。 */
    private static final Map<String, Key> CONTEXT_TYPES = contextTypes();

    /** 原版 {@code LevelBasedValue.CODEC}。 */
    static final Codec<LevelBasedValue> LEVEL_BASED_VALUE = buildLevelBasedValueCodec();

    private final RegistryTranscoder<JsonElement> transcoder;
    private final LootTypeRegistry types;
    private final Specifics specifics;

    private final Codec<LootCondition> condition;
    private final Codec<List<LootCondition>> conditionList;
    private final Codec<LootFunction> function;
    /** 原版 {@code LootItemFunctions.ROOT_CODEC.listOf()}：单对象折叠成长度 1 的列表。 */
    private final Codec<List<LootFunction>> functions;
    private final Codec<LootEntry> entry;
    private final Codec<NumberProvider> number;
    private final Codec<LootTable> table;
    private final Codec<LootPool> pool;

    private LootCodecs(ServerProcess process, LootTypeRegistry types) {
        this.transcoder = new RegistryTranscoder<>(Transcoder.JSON, process);
        this.types = types;
        this.specifics = new Specifics(this);
        // 装配时把 specifics 的 codec 反向写回 vanilla() 注册表
        fillVanilla();
        this.condition = dispatchCondition();
        this.conditionList = compactList(condition);
        var holder = new Codec[1];
        Codec<LootFunction> lazyFn = Codec.lazyInitialized(() -> holder[0]);
        var functionCodecs = FunctionCodecs.assemble(this, () -> lazyFn);
        for (var e3 : functionCodecs.entrySet()) {
            types.wireFunctionCodec(e3.getKey(), (Codec<? extends LootFunction>) e3.getValue().codec());
        }
        holder[0] = FunctionCodecs.rootCodec(this, () -> lazyFn, functionCodecs);
        this.function = Codec.lazyInitialized(() -> holder[0]);
        this.functions = compactList(function);
        this.entry = dispatchEntry();
        this.number = dispatchNumber();
        this.table = assembleTableCodec();
        this.pool = assemblePoolCodec();
    }

    public static LootCodecs of(ServerProcess process, LootTypeRegistry types) {
        return new LootCodecs(process, types);
    }

    public DataResult<LootTable> decodeTable(JsonElement json) { return table.parse(JsonOps.INSTANCE, json); }
    public DataResult<LootPool> decodePool(JsonElement json) { return pool.parse(JsonOps.INSTANCE, json); }
    public DataResult<LootCondition> decodeCondition(JsonElement json) { return condition.parse(JsonOps.INSTANCE, json); }
    public DataResult<LootFunction> decodeFunction(JsonElement json) { return function.parse(JsonOps.INSTANCE, json); }
    public DataResult<LootEntry> decodeEntry(JsonElement json) { return entry.parse(JsonOps.INSTANCE, json); }
    public DataResult<NumberProvider> decodeNumber(JsonElement json) { return number.parse(JsonOps.INSTANCE, json); }

    public Codec<LootCondition> condition() { return condition; }
    public Codec<LootFunction> function() { return function; }
    public Codec<List<LootFunction>> functions() { return functions; }
    public Codec<LootEntry> entry() { return entry; }
    public Codec<NumberProvider> number() { return number; }
    public Codec<LootTable> table() { return table; }
    public Codec<LootPool> pool() { return pool; }
    public RegistryTranscoder<JsonElement> transcoder() { return transcoder; }
    public LootTypeRegistry types() { return types; }

    // ------------------------------------------------------------------
    // LootTable / LootPool 顶层
    // ------------------------------------------------------------------

    /**
     * 原版 {@code LootTable.DIRECT_CODEC}：{@code type} 缺省 {@code minecraft:generic}、
     * {@code random_sequence} 可选、{@code pools} 与 {@code functions} 缺省空列表。
     */
    private Codec<LootTable> assembleTableCodec() {
        var typeCodec = Codec.STRING.comapFlatMap(name -> {
            var key = CONTEXT_TYPES.get(name.contains(":") ? name : "minecraft:" + name);
            return key != null ? DataResult.success(key)
                    : DataResult.error(() -> "Unknown loot table type: " + name);
        }, Key::asString);
        return RecordCodecBuilder.create(i -> i.group(
                typeCodec.optionalFieldOf("type", LootTable.DEFAULT_TYPE).forGetter(LootTable::type),
                ID.optionalFieldOf("random_sequence").forGetter(LootTable::randomSequence),
                Codec.lazyInitialized(() -> pool).listOf().optionalFieldOf("pools", List.of()).forGetter(LootTable::pools),
                Codec.lazyInitialized(() -> functions).optionalFieldOf("functions", List.of()).forGetter(LootTable::functions)
        ).apply(i, (type, randomSequence, pools, fs) -> new LootTable(type, fs, pools, randomSequence)));
    }

    /**
     * 原版 {@code LootPool.CODEC}：{@code entries} 必填、{@code conditions} 与 {@code functions}
     * 缺省空列表、{@code rolls} 必填、{@code bonus_rolls} 缺省 {@code ConstantValue.exactly(0)}。
     */
    private Codec<LootPool> assemblePoolCodec() {
        return RecordCodecBuilder.create(i -> i.group(
                nonEmptyList(Codec.lazyInitialized(() -> entry), "entries").fieldOf("entries").forGetter(LootPool::entries),
                Codec.lazyInitialized(() -> conditionList).optionalFieldOf("conditions", List.of()).forGetter(LootPool::conditions),
                Codec.lazyInitialized(() -> functions).optionalFieldOf("functions", List.of()).forGetter(LootPool::functions),
                Codec.lazyInitialized(() -> number).fieldOf("rolls").forGetter(LootPool::rolls),
                Codec.lazyInitialized(() -> number).optionalFieldOf("bonus_rolls", ConstantValue.ZERO).forGetter(LootPool::bonusRolls)
        ).apply(i, (entries, conditions, fs, rolls, bonusRolls) ->
                new LootPool(conditions, fs, rolls, bonusRolls, entries)));
    }

    // ------------------------------------------------------------------
    // 三个分发器：按原版字段名分发到 Specifics
    // ------------------------------------------------------------------

    private Codec<LootCondition> dispatchCondition() {
        var keyCodec = Codec.STRING.comapFlatMap(this::readKnownCondition, Key::asString).fieldOf("condition");
        var dispatch = new com.mojang.serialization.codecs.KeyDispatchCodec<Key, LootCondition>(keyCodec,
                v -> DataResult.success(conditionTypeOf(v)),
                id -> DataResult.success((MapCodec<LootCondition>) requireKnown(specifics.conditions, id, "loot_condition_type")));
        return dispatch.codec();
    }

    private Codec<LootEntry> dispatchEntry() {
        var keyCodec = Codec.STRING.comapFlatMap(this::readKnownEntry, Key::asString).fieldOf("type");
        var dispatch = new com.mojang.serialization.codecs.KeyDispatchCodec<Key, LootEntry>(keyCodec,
                v -> DataResult.success(entryTypeOf(v)),
                id -> DataResult.success((MapCodec<LootEntry>) requireKnown(specifics.entries, id, "loot_pool_entry_type")));
        return dispatch.codec();
    }

    /**
     * 原版 {@code NumberProviders.CODEC}：对象按 {@code type} 字段分发；裸数字折叠成
     * {@code constant}（原版 {@code ConstantValue.INLINE_CODEC}）。
     */
    private Codec<NumberProvider> dispatchNumber() {
        var keyCodec = Codec.STRING.comapFlatMap(this::readKnownNumber, Key::asString).fieldOf("type");
        var objectForm = new com.mojang.serialization.codecs.KeyDispatchCodec<Key, NumberProvider>(keyCodec,
                v -> DataResult.success(numberTypeOf(v)),
                id -> DataResult.success((MapCodec<NumberProvider>) requireKnown(specifics.numbers, id, "loot_number_provider_type")));
        var inline = Codec.FLOAT.xmap(ConstantValue::new, ConstantValue::value);
        return Codec.either(objectForm.codec(), inline).xmap(
                e -> e.map(Function.identity(), Function.identity()),
                n -> n instanceof ConstantValue c ? Either.right(c) : Either.left(n));
    }

    private DataResult<Key> readKnownCondition(String raw) {
        var id = readKey(raw);
        return specifics.conditions.containsKey(id)
                ? DataResult.success(id)
                : DataResult.error(() -> unknownKey("loot_condition_type", id));
    }

    private DataResult<Key> readKnownEntry(String raw) {
        var id = readKey(raw);
        return specifics.entries.containsKey(id)
                ? DataResult.success(id)
                : DataResult.error(() -> unknownKey("loot_pool_entry_type", id));
    }

    private DataResult<Key> readKnownNumber(String raw) {
        var id = readKey(raw);
        return specifics.numbers.containsKey(id)
                ? DataResult.success(id)
                : DataResult.error(() -> unknownKey("loot_number_provider_type", id));
    }

    private static Key readKey(String raw) {
        return Key.key(raw);
    }

    /**
     * 往编码侧走路径用不上原版 loot_table 之外的用例；解码也用不到这三个反查函数——
     * DataFixerUpper 的 KeyDispatchCodec 的 encode 路径需要类型反查，这里只做 record 类到 key 的穷举映射。
     */
    private Key conditionTypeOf(LootCondition condition) {
        return switch (condition) {
            case AllOfCondition _ -> Key.key("all_of");
            case AnyOfCondition _ -> Key.key("any_of");
            case BlockStatePropertyCondition _ -> Key.key("block_state_property");
            case DamageSourcePropertiesCondition _ -> Key.key("damage_source_properties");
            case EnchantmentActiveCheckCondition _ -> Key.key("enchantment_active_check");
            case EntityPropertiesCondition _ -> Key.key("entity_properties");
            case EntityScoresCondition _ -> Key.key("entity_scores");
            case EnvironmentAttributeCheckCondition _ -> Key.key("environment_attribute_check");
            case InvertedCondition _ -> Key.key("inverted");
            case KilledByPlayerCondition _ -> Key.key("killed_by_player");
            case LocationCheckCondition _ -> Key.key("location_check");
            case RandomChanceCondition _ -> Key.key("random_chance");
            case MatchToolCondition _ -> Key.key("match_tool");
            case RandomChanceWithEnchantedBonusCondition _ -> Key.key("random_chance_with_enchanted_bonus");
            case ReferenceCondition _ -> Key.key("reference");
            case SurvivesExplosionCondition _ -> Key.key("survives_explosion");
            case TableBonusCondition _ -> Key.key("table_bonus");
            case TimeCheckCondition _ -> Key.key("time_check");
            case ValueCheckCondition _ -> Key.key("value_check");
            case WeatherCheckCondition _ -> Key.key("weather_check");
            default -> throw new IllegalArgumentException("No vanilla condition codec for: " + condition.getClass());
        };
    }

    private Key entryTypeOf(LootEntry entry) {
        return switch (entry) {
            case AlternativesEntry _ -> Key.key("alternatives");
            case GroupEntry _ -> Key.key("group");
            case SequenceEntry _ -> Key.key("sequence");
            case ItemEntry _ -> Key.key("item");
            case TagEntry _ -> Key.key("tag");
            case NestedTableEntry _ -> Key.key("loot_table");
            case DynamicEntry _ -> Key.key("dynamic");
            case EmptyEntry _ -> Key.key("empty");
            case SlotsEntry _ -> Key.key("slots");
            default -> throw new IllegalArgumentException("No vanilla entry codec for: " + entry.getClass());
        };
    }

    private Key numberTypeOf(NumberProvider provider) {
        return switch (provider) {
            case ConstantValue _ -> Key.key("constant");
            case UniformValue _ -> Key.key("uniform");
            case BinomialValue _ -> Key.key("binomial");
            case ScoreboardValue _ -> Key.key("score");
            case EnchantmentLevelValue _ -> Key.key("enchantment_level");
            case EnvironmentAttributeValue _ -> Key.key("environment_attribute");
            case StorageValue _ -> Key.key("storage");
            case SumValue _ -> Key.key("sum");
            default -> throw new IllegalArgumentException("No vanilla number codec for: " + provider.getClass());
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> MapCodec<? extends T> requireKnown(Map<Key, ? extends MapCodec<? extends T>> map, Key id, String category) {
        var codec = map.get(id);
        if (codec == null) throw new IllegalArgumentException("Unknown " + category + ": " + id);
        return codec;
    }

    static String unknownKey(String vanillaRegistry, Key id) {
        return "Unknown registry key in ResourceKey[minecraft:root / minecraft:" + vanillaRegistry + "]: "
                + id.asString();
    }

    /** 原版 {@code ExtraCodecs.compactListCodec}：先按列表解码，失败再按单个元素折叠成长度 1 的列表。 */
    public static <E> Codec<List<E>> compactList(Codec<E> element) {
        return Codec.either(element.listOf(), element)
                .xmap(e -> e.map(Function.identity(), List::of),
                        list -> list.size() == 1 ? Either.right(list.getFirst()) : Either.left(list));
    }

    /** 原版 {@code #tag} 引用。 */
    static Codec<Key> tagReference() {
        return Codec.STRING.comapFlatMap(s -> s.startsWith("#")
                        ? readId(s.substring(1))
                        : DataResult.error(() -> "Expected a tag reference starting with '#': " + s),
                id -> "#" + id.asString());
    }

    static <T> Either<Key, T> eitherIdToEither(EitherId<T> e) {
        return e.isLeft() ? Either.left(e.left().orElseThrow()) : Either.right(e.right().orElseThrow());
    }

    /** 原版 {@code Util.nonEmptyList}：解码长度至少为 1 的列表。 */
    static <E> Codec<List<E>> nonEmptyList(Codec<E> element, String field) {
        return element.listOf().comapFlatMap(
                list -> list.isEmpty()
                        ? DataResult.error(() -> "List in field '" + field + "' must not be empty")
                        : DataResult.success(list),
                Function.identity());
    }

    /** 原版 {@code DynamicDrop enum}：dynamic 条目的 name 只允许 contents 或 sherds。 */
    private static DataResult<Key> readDynamicName(Key id) {
        return switch (id.value()) {
            case "contents", "sherds" -> DataResult.success(id);
            default -> DataResult.error(() -> "Expected 'contents' or 'sherds' for dynamic name, got: " + id.asString());
        };
    }

    /** reference 条件的 name 必须显式带命名空间。 */
    private static DataResult<Key> requireNamespacedId(Key id) {
        return "minecraft".equals(id.namespace())
                ? DataResult.success(id)
                : DataResult.error(() -> "reference name must be a valid namespaced id: " + id.asString());
    }

    static <T> EitherId<T> eitherToEitherId(Either<Key, T> e) {
        return e.map(EitherId::ofLeft, EitherId::ofRight);
    }

    static Either<Key, Key> enchantedOptionsToEither(EnchantedOptions o) {
        return o.enchantmentTag() != null
                ? Either.left(o.enchantmentTag())
                : Either.right(o.enchantments() == null || o.enchantments().isEmpty()
                ? Key.key("air") : o.enchantments().getFirst());
    }

    static List<Key> enchantedOptionsItems(EnchantedOptions options, String what) {
        if (options.enchantments() == null) {
            throw new IllegalArgumentException("Expected a list of " + what + ", got a tag reference");
        }
        return options.enchantments();
    }

    /** 原版 {@code LootContext.EntityTarget} 的字符串形式。 */
    static Codec<EntityTarget> entityTargetCodec() {
        return Codec.STRING.comapFlatMap(s -> {
            var target = switch (s) {
                case "this" -> EntityTarget.THIS;
                case "attacker" -> EntityTarget.ATTACKER;
                case "direct_attacker" -> EntityTarget.DIRECT_ATTACKER;
                case "attacking_player" -> EntityTarget.ATTACKING_PLAYER;
                case "target_entity" -> EntityTarget.TARGET_ENTITY;
                case "interacting_entity" -> EntityTarget.INTERACTING_ENTITY;
                default -> null;
            };
            return target != null ? DataResult.success(target)
                    : DataResult.error(() -> "Unknown entity target: " + s);
        }, t -> t.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // 装配时把 codec 反向写回 vanilla() 的注册表
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void fillVanilla() {
        // LootCodecs 只在 codecWiring 装配 vanilla() 时调用；其它宿主不应调用 of()。
        for (var e : specifics.entries.entrySet()) {
            var codec = e.getValue();
            types.wireEntryCodec(e.getKey(), (Codec<? extends LootEntry>) codec.codec());
        }
        for (var e2 : specifics.conditions.entrySet()) {
            var codec = e2.getValue();
            types.wireConditionCodec(e2.getKey(), (Codec<? extends LootCondition>) codec.codec());
        }
        for (var e3 : specifics.numbers.entrySet()) {
            var codec = e3.getValue();
            types.wireNumberProviderCodec(e3.getKey(), (Codec<? extends NumberProvider>) codec.codec());
        }
    }

    // ------------------------------------------------------------------
    // Specifics：条件 / 条目 / 数字提供器 81 种具体类型
    // ------------------------------------------------------------------

    static final class Specifics {
        final Map<Key, MapCodec<? extends LootCondition>> conditions = new LinkedHashMap<>();
        final Map<Key, MapCodec<? extends LootEntry>> entries = new LinkedHashMap<>();
        final Map<Key, MapCodec<? extends NumberProvider>> numbers = new LinkedHashMap<>();

        private final LootCodecs c;

        Specifics(LootCodecs c) {
            this.c = c;
            assembleConditions(conditions);
            assembleEntries(entries);
            assembleNumbers(numbers);
        }

        // ---------------- 条件 ----------------

        private void assembleConditions(Map<Key, MapCodec<? extends LootCondition>> out) {
            put(out, "all_of", alias("terms", AllOfCondition::new));
            put(out, "any_of", alias("terms", AnyOfCondition::new));
            put(out, "block_state_property", RecordCodecBuilder.<BlockStatePropertyCondition>mapCodec(i -> i.group(
                    ID.fieldOf("block").forGetter(BlockStatePropertyCondition::block),
                    blockStatePropertiesCodec().optionalFieldOf("properties").forGetter(BlockStatePropertyCondition::properties)
            ).apply(i, BlockStatePropertyCondition::new)));
            put(out, "damage_source_properties", RecordCodecBuilder.<DamageSourcePropertiesCondition>mapCodec(i -> i.group(
                    damageSourcePredicateCodec().optionalFieldOf("predicate").forGetter(DamageSourcePropertiesCondition::predicate)
            ).apply(i, DamageSourcePropertiesCondition::new)));
            put(out, "enchantment_active_check", RecordCodecBuilder.<EnchantmentActiveCheckCondition>mapCodec(i -> i.group(
                    Codec.BOOL.fieldOf("active").forGetter(EnchantmentActiveCheckCondition::active)
            ).apply(i, EnchantmentActiveCheckCondition::new)));
            put(out, "entity_properties", RecordCodecBuilder.<EntityPropertiesCondition>mapCodec(i -> i.group(
                    entityTargetCodec().optionalFieldOf("entity", EntityTarget.THIS).forGetter(EntityPropertiesCondition::entity),
                    entityPredicateCodec().optionalFieldOf("predicate").forGetter(EntityPropertiesCondition::predicate)
            ).apply(i, (entity, predicate) -> new EntityPropertiesCondition(predicate.orElse(null), entity))));
            put(out, "entity_scores", RecordCodecBuilder.<EntityScoresCondition>mapCodec(i -> i.group(
                    entityTargetCodec().optionalFieldOf("entity", EntityTarget.THIS).forGetter(EntityScoresCondition::entity),
                    Codec.unboundedMap(Codec.STRING, intRangeCodec()).fieldOf("scores").forGetter(EntityScoresCondition::scores)
            ).apply(i, (entity, scores) -> new EntityScoresCondition(scores, entity))));
            // 原版 environment_attribute_check 的 value 由 attribute 类型决定；loot 模型用 String 承载原值，
            // 这里直接走 JsonElement passthrough 编码后再压缩回 String。
            put(out, "environment_attribute_check", RecordCodecBuilder.<EnvironmentAttributeCheckCondition>mapCodec(i -> i.group(
                    ID.fieldOf("attribute").forGetter(EnvironmentAttributeCheckCondition::attribute),
                    Codec.STRING.fieldOf("value").forGetter(
                            (EnvironmentAttributeCheckCondition c) -> c.value() == null ? "" : c.value())
            ).apply(i, (Key attribute, String value) -> new EnvironmentAttributeCheckCondition(attribute, value))));
            put(out, "inverted", RecordCodecBuilder.<InvertedCondition>mapCodec(i -> i.group(
                    Codec.lazyInitialized(() -> c.condition).fieldOf("term").forGetter(InvertedCondition::term)
            ).apply(i, InvertedCondition::new)));
            put(out, "killed_by_player", MapCodec.unit(KilledByPlayerCondition.INSTANCE));
            put(out, "location_check", RecordCodecBuilder.<LocationCheckCondition>mapCodec(i -> i.group(
                    locationPredicateCodec().optionalFieldOf("predicate").forGetter(LocationCheckCondition::predicate),
                    Codec.INT.optionalFieldOf("offsetX", 0).forGetter(LocationCheckCondition::offsetX),
                    Codec.INT.optionalFieldOf("offsetY", 0).forGetter(LocationCheckCondition::offsetY),
                    Codec.INT.optionalFieldOf("offsetZ", 0).forGetter(LocationCheckCondition::offsetZ)
            ).apply(i, (predicate, x, y, z) -> new LocationCheckCondition(predicate.orElse(null), x, y, z))));
            put(out, "match_tool", RecordCodecBuilder.<MatchToolCondition>mapCodec(i -> i.group(
                    itemPredicateCodec().optionalFieldOf("predicate").forGetter(MatchToolCondition::predicate)
            ).apply(i, (Optional<ItemPredicate> p) -> new MatchToolCondition(p.orElse(null)))));
            put(out, "random_chance", RecordCodecBuilder.<RandomChanceCondition>mapCodec(i -> i.group(
                    lazyNumber().fieldOf("chance").forGetter(RandomChanceCondition::chance)
            ).apply(i, RandomChanceCondition::new)));
            put(out, "random_chance_with_enchanted_bonus", RecordCodecBuilder.<RandomChanceWithEnchantedBonusCondition>mapCodec(i -> i.group(
                    Codec.FLOAT.fieldOf("unenchanted_chance").forGetter(RandomChanceWithEnchantedBonusCondition::unenchantedChance),
                    ID.fieldOf("enchantment").forGetter(RandomChanceWithEnchantedBonusCondition::enchantment),
                    LEVEL_BASED_VALUE.fieldOf("enchanted_chance").forGetter(RandomChanceWithEnchantedBonusCondition::enchantedChance)
            ).apply(i, (Float unenchanted, Key enchantment, LevelBasedValue enchantedChance) ->
                    new RandomChanceWithEnchantedBonusCondition(unenchanted, enchantedChance, enchantment))));
            put(out, "reference", RecordCodecBuilder.<ReferenceCondition>mapCodec(i -> i.group(
                    ID.comapFlatMap(LootCodecs::requireNamespacedId, Function.identity()).fieldOf("name")
                            .forGetter(ReferenceCondition::name)
            ).apply(i, ReferenceCondition::new)));
            put(out, "survives_explosion", MapCodec.unit(SurvivesExplosionCondition.INSTANCE));
            put(out, "table_bonus", RecordCodecBuilder.<TableBonusCondition>mapCodec(i -> i.group(
                    ID.fieldOf("enchantment").forGetter(TableBonusCondition::enchantment),
                    Codec.FLOAT.listOf().fieldOf("chances").forGetter(TableBonusCondition::values)
            ).apply(i, TableBonusCondition::new)));
            put(out, "time_check", RecordCodecBuilder.<TimeCheckCondition>mapCodec(i -> i.group(
                    ID.optionalFieldOf("clock", Key.key("minecraft:clock")).forGetter(TimeCheckCondition::clock),
                    Codec.LONG.optionalFieldOf("period").forGetter(TimeCheckCondition::period),
                    intRangeCodec().fieldOf("value").forGetter(TimeCheckCondition::value)
            ).apply(i, TimeCheckCondition::new)));
            put(out, "value_check", RecordCodecBuilder.<ValueCheckCondition>mapCodec(i -> i.group(
                    lazyNumber().fieldOf("value").forGetter(ValueCheckCondition::value),
                    intRangeCodec().fieldOf("range").forGetter(ValueCheckCondition::range)
            ).apply(i, ValueCheckCondition::new)));
            put(out, "weather_check", RecordCodecBuilder.<WeatherCheckCondition>mapCodec(i -> i.group(
                    Codec.BOOL.optionalFieldOf("raining").forGetter(WeatherCheckCondition::isRaining),
                    Codec.BOOL.optionalFieldOf("thundering").forGetter(WeatherCheckCondition::isThundering)
            ).apply(i, WeatherCheckCondition::new)));
        }

        private MapCodec<LootCondition> alias(String field, Function<List<LootCondition>, LootCondition> make) {
            var self = Codec.lazyInitialized(() -> c.condition);
            return RecordCodecBuilder.<LootCondition>mapCodec(i -> i.group(
                    compactList(self).fieldOf(field).forGetter(x -> {
                        if (x instanceof AllOfCondition a) return a.terms();
                        return ((AnyOfCondition) x).terms();
                    })
            ).apply(i, children -> make.apply(children)));
        }

        // ---------------- 条目 ----------------

        private void assembleEntries(Map<Key, MapCodec<? extends LootEntry>> out) {
            put(out, "item", entryTemplate(
                    ID.fieldOf("name"),
                    (conds, fs, w, q, body) -> (LootEntry) new ItemEntry(conds, fs, w, q, (Key) body)));
            put(out, "tag", entryTemplate(
                    tagSpecific(),
                    (conds, fs, w, q, body) -> {
                        var b = (TagFlag) body;
                        return (LootEntry) new TagEntry(conds, fs, w, q, b.name(), b.expand());
                    }));
            put(out, "loot_table", entryTemplate(
                    Codec.either(ID, Codec.lazyInitialized(() -> c.table)).fieldOf("value"),
                    (conds, fs, w, q, body) -> {
                        Either<Key, LootTable> v = (Either<Key, LootTable>) body;
                        return (LootEntry) new NestedTableEntry(conds, fs, w, q, eitherToEitherId(v));
                    }));
            put(out, "dynamic", entryTemplate(
                    ID.comapFlatMap(LootCodecs::readDynamicName, Function.identity()).fieldOf("name"),
                    (conds, fs, w, q, body) -> (LootEntry) new DynamicEntry(conds, fs, w, q, (Key) body)));
            put(out, "empty", entryTemplate(
                    MapCodec.unit(new Object()),
                    (conds, fs, w, q, body) -> (LootEntry) new EmptyEntry(conds, fs, w, q)));
            put(out, "slots", entryTemplate(
                    slotSourceCodec().fieldOf("slot_source"),
                    (conds, fs, w, q, body) -> (LootEntry) new SlotsEntry(conds, fs, w, q, (SlotSource) body)));
            put(out, "group", compositeEntry(children -> new GroupEntry(List.of(), List.of(), children)));
            put(out, "alternatives", compositeEntry(children -> new AlternativesEntry(List.of(), List.of(), children)));
            put(out, "sequence", compositeEntry(children -> new SequenceEntry(List.of(), List.of(), children)));
        }

        /** 原版单例条目的公共字段：{@code conditions}、{@code functions}（缺省空列表）、{@code weight}（缺省 1）、{@code quality}（缺省 0）。 */
        private <B> MapCodec<LootEntry> entryTemplate(MapCodec<B> specific, SingletonAssembler<B> assembler) {
            var conditions = compactList(Codec.lazyInitialized(() -> c.condition));
            var functions = compactList(Codec.lazyInitialized(() -> c.function));
            return RecordCodecBuilder.<LootEntry>mapCodec(i -> i.group(
                    specific.forGetter(_ -> null),
                    conditions.optionalFieldOf("conditions", List.of()).forGetter(LootEntry::conditions),
                    functions.optionalFieldOf("functions", List.of()).forGetter(LootEntry::functions),
                    Codec.INT.optionalFieldOf("weight", 1).forGetter(e -> ((SingletonEntry) e).weight()),
                    Codec.INT.optionalFieldOf("quality", 0).forGetter(e -> ((SingletonEntry) e).quality())
            ).apply(i, (body, conds, fs, w, q) -> assembler.assemble(conds, fs, w, q, body)));
        }

        private interface SingletonAssembler<B> {
            LootEntry assemble(List<LootCondition> conditions, List<LootFunction> functions, int weight, int quality, B body);
        }

        private record TagFlag(net.kyori.adventure.key.Key name, boolean expand) {}

        private MapCodec<TagFlag> tagSpecific() {
            return RecordCodecBuilder.<TagFlag>mapCodec(i -> i.group(
                    ID.fieldOf("name").forGetter(TagFlag::name),
                    Codec.BOOL.optionalFieldOf("expand", false).forGetter(TagFlag::expand)
            ).apply(i, TagFlag::new));
        }

        /** 原版 {@code SlotSource} 仅用于透传，loot 模块按 JSON 原样承载。 */
        private Codec<SlotSource> slotSourceCodec() {
            return Codec.PASSTHROUGH.flatXmap(
                    dyn -> {
                        var converted = dyn.convert(JsonOps.INSTANCE);
                        return converted.getValue() instanceof JsonObject obj
                                ? DataResult.success(new SlotSource(obj))
                                : DataResult.error(() -> "slot_source must be a JSON object");
                    },
                    _ -> DataResult.error(() -> "slots entry decode only"));
        }

        private MapCodec<LootEntry> compositeEntry(Function<List<LootEntry>, LootEntry> make) {
            var self = Codec.lazyInitialized(() -> c.entry);
            var conditions = compactList(Codec.lazyInitialized(() -> c.condition));
            var functions = compactList(Codec.lazyInitialized(() -> c.function));
            return RecordCodecBuilder.<LootEntry>mapCodec(i -> i.group(
                    nonEmptyList(self, "children").fieldOf("children").forGetter(e -> ((CompositeEntry) e).children()),
                    conditions.optionalFieldOf("conditions", List.of()).forGetter(LootEntry::conditions),
                    functions.optionalFieldOf("functions", List.of()).forGetter(LootEntry::functions)
            ).apply(i, (children, conds, fs) -> make.apply(children)));
        }

        // ---------------- 数字提供器 ----------------

        private void assembleNumbers(Map<Key, MapCodec<? extends NumberProvider>> out) {
            out.put(Key.key("constant"), RecordCodecBuilder.<NumberProvider>mapCodec(i -> i.group(
                    Codec.FLOAT.fieldOf("value").forGetter(n -> ((ConstantValue) n).value())
            ).apply(i, (Float v) -> (NumberProvider) new ConstantValue(v))));
            out.put(Key.key("uniform"), RecordCodecBuilder.mapCodec(i -> i.group(
                    lazyNumber().fieldOf("min").forGetter(n -> ((UniformValue) n).min()),
                    lazyNumber().fieldOf("max").forGetter(n -> ((UniformValue) n).max())
            ).apply(i, (NumberProvider lo, NumberProvider hi) -> (NumberProvider) new UniformValue(lo, hi))));
            out.put(Key.key("binomial"), RecordCodecBuilder.mapCodec(i -> i.group(
                    lazyNumber().fieldOf("n").forGetter(n -> ((BinomialValue) n).n()),
                    lazyNumber().fieldOf("p").forGetter(n -> ((BinomialValue) n).p())
            ).apply(i, (NumberProvider n, NumberProvider p) -> (NumberProvider) new BinomialValue(n, p))));
            out.put(Key.key("score"), RecordCodecBuilder.mapCodec(i -> i.group(
                    scoreboardNameCodec().fieldOf("target").forGetter(n -> ((ScoreboardValue) n).target()),
                    Codec.STRING.fieldOf("score").forGetter(n -> ((ScoreboardValue) n).score()),
                    Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter(n -> ((ScoreboardValue) n).scale())
            ).apply(i, (ScoreboardName target, String score, Float scale) -> (NumberProvider) new ScoreboardValue(target, score, scale))));
            out.put(Key.key("enchantment_level"), RecordCodecBuilder.mapCodec(i -> i.group(
                    LEVEL_BASED_VALUE.fieldOf("amount").forGetter(n -> ((EnchantmentLevelValue) n).amount())
            ).apply(i, (LevelBasedValue v) -> (NumberProvider) new EnchantmentLevelValue(v))));
            out.put(Key.key("environment_attribute"), RecordCodecBuilder.mapCodec(i -> i.group(
                    ID.fieldOf("attribute").forGetter(n -> ((EnvironmentAttributeValue) n).attribute())
            ).apply(i, (Key attribute) -> (NumberProvider) new EnvironmentAttributeValue(attribute))));
            out.put(Key.key("storage"), RecordCodecBuilder.mapCodec(i -> i.group(
                    ID.fieldOf("storage").forGetter(n -> ((StorageValue) n).storage()),
                    Codec.STRING.fieldOf("path").forGetter(n -> ((StorageValue) n).path())
            ).apply(i, (Key source, String path) -> (NumberProvider) new StorageValue(source, path))));
            out.put(Key.key("sum"), RecordCodecBuilder.mapCodec(i -> i.group(
                    lazyNumber().listOf().fieldOf("terms").forGetter(n -> ((SumValue) n).terms())
            ).apply(i, (List<NumberProvider> terms) -> (NumberProvider) new SumValue(terms))));
        }

        private Codec<NumberProvider> lazyNumber() {
            return Codec.lazyInitialized(() -> c.number);
        }

        // ---------------- 谓词与辅助 ----------------

        /**
         * 原版 {@code ItemPredicate} 的 loot 侧最小承载。Vanilla 26.2 接受三种形态：
         * 字符串（{@code #tag} 或物品 ID）、物品 ID 列表、对象（{@code {"items": ...}, "predicates": {…}}）。
         * 对象形态只承载 {@code items} 键中的物品引用；谓词内容由 Minestom 求值时处理。
         */
        static Codec<ItemPredicate> itemPredicateCodec() {
            var single = Codec.either(tagReference(), ID).xmap(
                    e -> e.map(EnchantedOptions::ofTag, id -> EnchantedOptions.ofList(List.of(id))),
                    LootCodecs::enchantedOptionsToEither);
            var listForm = ID.listOf().xmap(EnchantedOptions::ofList, o -> enchantedOptionsItems(o, "items"));
            var stringOrList = Codec.either(single, listForm).xmap(
                    e -> e.map(Function.identity(), Function.identity()),
                    o -> o.enchantments() != null && o.enchantments().size() > 1 ? Either.right(o) : Either.left(o));
            var dictForm = Codec.PASSTHROUGH.flatXmap(
                    dyn -> dyn.convert(JsonOps.INSTANCE).getValue() instanceof JsonObject obj
                            ? DataResult.success(readItemPredicateObject(obj))
                            : DataResult.error(() -> "item predicate must be a JSON object"),
                    _ -> DataResult.error(() -> "encode not supported"));
            return Codec.either(stringOrList, dictForm)
                    .xmap(e -> e.map(o -> ofItemPredicate(o), Function.identity()),
                            p -> Either.right(new ItemPredicate(p.itemTag() != null ? null : p.items(), p.itemTag())));
        }

        private static ItemPredicate ofItemPredicate(EnchantedOptions opts) {
            return new ItemPredicate(opts.enchantments(), opts.enchantmentTag());
        }

        private static ItemPredicate readItemPredicateObject(JsonObject obj) {
            Key tag = null;
            java.util.List<Key> items = java.util.List.of();
            var itemsEl = obj.get("items");
            if (itemsEl != null && !itemsEl.isJsonNull()) {
                if (itemsEl.isJsonPrimitive() && itemsEl.getAsString().startsWith("#")) {
                    tag = Key.key(itemsEl.getAsString().substring(1));
                } else if (itemsEl.isJsonPrimitive()) {
                    items = java.util.List.of(Key.key(itemsEl.getAsString()));
                } else {
                    var list = new java.util.ArrayList<Key>();
                    itemsEl.getAsJsonArray().forEach(each -> list.add(Key.key(each.getAsString())));
                    items = java.util.List.copyOf(list);
                }
            }
            return new ItemPredicate(items, tag);
        }

        /**
         * 原版 {@code EntityPredicate} 的 loot 侧最小承载。Vanilla 26.2 接受字符串（{@code #tag} 或实体类型 ID）、
         * ID 列表、或对象（{@code {"minecraft:entity_type": ...}, ...}）。对象形态只识别
         * {@code minecraft:entity_type} 键中的实体引用，其余谓词由 Minestom 求值阶段处理。
         */
        static Codec<EntityPredicate> entityPredicateCodec() {
            var single = Codec.either(tagReference(), ID).xmap(
                    e -> e.map(EnchantedOptions::ofTag, id -> EnchantedOptions.ofList(List.of(id))),
                    LootCodecs::enchantedOptionsToEither);
            var listForm = ID.listOf().xmap(EnchantedOptions::ofList, o -> enchantedOptionsItems(o, "entity types"));
            var stringOrList = Codec.either(single, listForm).xmap(
                    e -> e.map(Function.identity(), Function.identity()),
                    o -> o.enchantments() != null && o.enchantments().size() > 1 ? Either.right(o) : Either.left(o));
            var dictForm = Codec.PASSTHROUGH.flatXmap(
                    dyn -> dyn.convert(JsonOps.INSTANCE).getValue() instanceof JsonObject obj
                            ? DataResult.success(readEntityPredicateObject(obj))
                            : DataResult.error(() -> "entity predicate must be a JSON object"),
                    _ -> DataResult.error(() -> "encode not supported"));
            return Codec.either(stringOrList, dictForm)
                    .xmap(e -> e.map(o -> new EntityPredicate(o.enchantments(), o.enchantmentTag()), Function.identity()),
                            p -> p.entityTypeTag() != null
                                    ? Either.left(EnchantedOptions.ofTag(p.entityTypeTag()))
                                    : Either.right(p));
        }

        private static EntityPredicate readEntityPredicateObject(JsonObject obj) {
            var key = obj.get("minecraft:entity_type");
            if (key == null || key.isJsonNull()) {
                return new EntityPredicate(java.util.List.of(), null);
            }
            Key tag = null;
            java.util.List<Key> types = java.util.List.of();
            if (key.isJsonPrimitive() && key.getAsString().startsWith("#")) {
                tag = Key.key(key.getAsString().substring(1));
            } else if (key.isJsonPrimitive()) {
                types = java.util.List.of(Key.key(key.getAsString()));
            } else if (key.isJsonArray()) {
                var list = new java.util.ArrayList<Key>();
                key.getAsJsonArray().forEach(each -> list.add(Key.key(each.getAsString())));
                types = java.util.List.copyOf(list);
            }
            return new EntityPredicate(types, tag);
        }

        /** 原版 {@code StatePropertiesPredicate}：{@code {属性名: 期望值}} 映射。 */
        static Codec<BlockPredicate> blockStatePropertiesCodec() {
            return Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(
                    m -> new BlockPredicate(null, Optional.of(m)),
                    p -> p.properties().orElse(Map.of()));
        }

        /** 原版 {@code DamageSourcePredicate} 的 loot 侧承载。 */
        static Codec<DamageSourcePredicate> damageSourcePredicateCodec() {
            return RecordCodecBuilder.create(i -> i.group(
                    tagReference().optionalFieldOf("damage_type").forGetter(p -> Optional.ofNullable(p.damageTypeTag())),
                    entityPredicateCodec().optionalFieldOf("direct_entity").forGetter(p -> Optional.ofNullable(p.directEntity())),
                    entityPredicateCodec().optionalFieldOf("source_entity").forGetter(p -> Optional.ofNullable(p.sourceEntity()))
            ).apply(i, (dmg, direct, source) ->
                    new DamageSourcePredicate(dmg.orElse(null), direct.orElse(null), source.orElse(null))));
        }

        /** 原版 {@code LocationPredicate} 的 loot 侧承载：{@code structure}/{@code biome} 可带 {@code #} 前缀。 */
        static Codec<LocationPredicate> locationPredicateCodec() {
            var tagOrId = Codec.either(tagReference(), ID);
            Function<LocationPredicate, Optional<Either<Key, Key>>> structure = p -> p.structure() != null
                    ? Optional.of(Either.<Key, Key>right(p.structure()))
                    : Optional.ofNullable(p.structureTag()).map(Either::left);
            Function<LocationPredicate, Optional<Either<Key, Key>>> biome = p -> p.biome() != null
                    ? Optional.of(Either.<Key, Key>right(p.biome()))
                    : Optional.ofNullable(p.biomeTag()).map(Either::left);
            return RecordCodecBuilder.create(i -> i.group(
                    tagOrId.optionalFieldOf("structure").forGetter(structure::apply),
                    tagOrId.optionalFieldOf("biome").forGetter(biome::apply),
                    ID.optionalFieldOf("dimension").forGetter(p -> Optional.ofNullable(p.dimension()))
            ).apply(i, (s, b, dimension) -> new LocationPredicate(
                    s.flatMap(e -> e.map(Optional::of, _ -> Optional.<Key>empty())).orElse(null),
                    b.flatMap(e -> e.map(Optional::of, _ -> Optional.<Key>empty())).orElse(null),
                    dimension.orElse(null),
                    s.flatMap(e -> e.map(_ -> Optional.<Key>empty(), Optional::of)).orElse(null),
                    b.flatMap(e -> e.map(_ -> Optional.<Key>empty(), Optional::of)).orElse(null))));
        }

        /** 原版 {@code IntRange.CODEC}：{@code {min?, max?}} 或裸整数（展开为同一边界）。 */
        static Codec<IntRange> intRangeCodec() {
            var objectForm = RecordCodecBuilder.<IntRange>mapCodec(i -> i.group(
                    Codec.INT.optionalFieldOf("min").forGetter(r -> intEndpoint(r.min())),
                    Codec.INT.optionalFieldOf("max").forGetter(r -> intEndpoint(r.max()))
            ).apply(i, (Optional<Integer> lo, Optional<Integer> hi) -> new IntRange(
                    lo.<NumberProvider>map(ConstantValue::new).orElse(null),
                    hi.<NumberProvider>map(ConstantValue::new).orElse(null)))).codec();
            var inline = Codec.INT.xmap(v -> new IntRange(ConstantValue.of(v), ConstantValue.of(v)),
                    r -> constantEndpoint(r.min()));
            return Codec.either(objectForm, inline).xmap(
                    Either::unwrap,
                    r -> r.min() instanceof ConstantValue c && r.max() instanceof ConstantValue c2
                            && Float.floatToIntBits(c.value()) == Float.floatToIntBits(c2.value())
                            ? Either.right(r) : Either.left(r));
        }

        private static Optional<Integer> intEndpoint(NumberProvider provider) {
            if (provider instanceof ConstantValue c && Math.rint(c.value()) == c.value()) {
                return Optional.of((int) c.value());
            }
            return Optional.empty();
        }

        private static int constantEndpoint(NumberProvider provider) {
            if (provider instanceof ConstantValue c) return (int) c.value();
            throw new IllegalArgumentException("IntRange inline form requires a constant endpoint: " + provider);
        }

        /** 原版 {@code ScoreboardNameProviders.CODEC}：{@code fixed}（{@code name}）或 {@code context}（{@code target}）。 */
        static Codec<ScoreboardName> scoreboardNameCodec() {
            MapCodec<ScoreboardName> fixed = RecordCodecBuilder.<ScoreboardName>mapCodec(i -> i.group(
                    Codec.STRING.fieldOf("name").forGetter(n -> ((ScoreboardName.Fixed) n).name())
            ).apply(i, (String name) -> (ScoreboardName) new ScoreboardName.Fixed(name)));
            MapCodec<ScoreboardName> byContext = RecordCodecBuilder.<ScoreboardName>mapCodec(i -> i.group(
                    entityTargetCodec().fieldOf("target").forGetter(n -> ((ScoreboardName.Context) n).target())
            ).apply(i, (EntityTarget t) -> (ScoreboardName) new ScoreboardName.Context(t)));
            return Codec.STRING.fieldOf("type").dispatch(
                    n -> n instanceof ScoreboardName.Context ? "context" : "fixed",
                    name -> switch (name.contains(":") ? name.substring(name.indexOf(':') + 1) : name) {
                        case "fixed" -> fixed;
                        case "context" -> byContext;
                        default -> throw new IllegalArgumentException("Unknown scoreboard name type: " + name);
                    });
        }

        private static <T> void put(Map<Key, MapCodec<? extends T>> out, String id, MapCodec<? extends T> codec) {
            out.put(Key.key(id), codec);
        }

        // Body/value 转换
        private static List<Key> enchantedOptionsItems(EnchantedOptions options, String what) {
            if (options.enchantments() == null) {
                throw new IllegalArgumentException("Expected a list of " + what + ", got a tag reference");
            }
            return options.enchantments();
        }
    }

    // ------------------------------------------------------------------
    // LevelBasedValue：type 缺省 linear
    // ------------------------------------------------------------------

    /** DataFixerUpper 没有 dispatchOptional；用 PASSTHROUGH 先读 type 再交给对应的编解码器。 */
    private static Codec<LevelBasedValue> buildLevelBasedValueCodec() {
        var holder = new Codec[1];
        Codec<LevelBasedValue> self = Codec.lazyInitialized(() -> holder[0]);

        var constant = RecordCodecBuilder.<LevelBasedValue>mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("value").forGetter(v -> ((LevelBasedValue.Constant) v).value())
        ).apply(i, (Float v) -> (LevelBasedValue) new LevelBasedValue.Constant(v))).codec();
        var linear = RecordCodecBuilder.<LevelBasedValue>mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("base").forGetter(v -> ((LevelBasedValue.Linear) v).base()),
                Codec.FLOAT.fieldOf("per_level_above_first").forGetter(v -> ((LevelBasedValue.Linear) v).perLevelAboveFirst())
        ).apply(i, (Float b, Float p) -> (LevelBasedValue) new LevelBasedValue.Linear(b, p))).codec();
        var clamped = RecordCodecBuilder.<LevelBasedValue>mapCodec(i -> i.group(
                self.fieldOf("value").forGetter(v -> ((LevelBasedValue.Clamped) v).value()),
                Codec.FLOAT.optionalFieldOf("min", -Float.MAX_VALUE).forGetter(v -> ((LevelBasedValue.Clamped) v).min()),
                Codec.FLOAT.optionalFieldOf("max", Float.MAX_VALUE).forGetter(v -> ((LevelBasedValue.Clamped) v).max())
        ).apply(i, (LevelBasedValue v, Float lo, Float hi) ->
                (LevelBasedValue) new LevelBasedValue.Clamped(v, lo, hi))).codec();
        var fraction = RecordCodecBuilder.<LevelBasedValue>mapCodec(i -> i.group(
                self.fieldOf("numerator").forGetter(v -> ((LevelBasedValue.Fraction) v).numerator()),
                self.fieldOf("denominator").forGetter(v -> ((LevelBasedValue.Fraction) v).denominator())
        ).apply(i, (LevelBasedValue n, LevelBasedValue d) ->
                (LevelBasedValue) new LevelBasedValue.Fraction(n, d))).codec();
        var levelsSquared = RecordCodecBuilder.<LevelBasedValue>mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("added").forGetter(v -> ((LevelBasedValue.LevelsSquared) v).added())
        ).apply(i, (Float a) -> (LevelBasedValue) new LevelBasedValue.LevelsSquared(a))).codec();
        var lookup = RecordCodecBuilder.<LevelBasedValue>mapCodec(i -> i.group(
                Codec.FLOAT.listOf().fieldOf("values").forGetter(v -> ((LevelBasedValue.Lookup) v).values()),
                self.fieldOf("fallback").forGetter(v -> ((LevelBasedValue.Lookup) v).fallback())
        ).apply(i, (List<Float> values, LevelBasedValue fallback) ->
                (LevelBasedValue) new LevelBasedValue.Lookup(values, fallback))).codec();
        var exponent = RecordCodecBuilder.<LevelBasedValue>mapCodec(i -> i.group(
                self.fieldOf("base").forGetter(v -> ((LevelBasedValue.Exponent) v).base()),
                self.fieldOf("power").forGetter(v -> ((LevelBasedValue.Exponent) v).power())
        ).apply(i, (LevelBasedValue b, LevelBasedValue p) ->
                (LevelBasedValue) new LevelBasedValue.Exponent(b, p))).codec();

        Map<String, Codec<? extends LevelBasedValue>> byName = new HashMap<>();
        byName.put("constant", constant);
        byName.put("linear", linear);
        byName.put("clamped", clamped);
        byName.put("fraction", fraction);
        byName.put("levels_squared", levelsSquared);
        byName.put("lookup", lookup);
        byName.put("exponent", exponent);
        // 原版 LevelBasedValue.CODEC 的 type 缺省为 linear
        var direct = RecordCodecBuilder.<LevelBasedValue>mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("base").forGetter(v -> ((LevelBasedValue.Linear) v).base()),
                Codec.FLOAT.fieldOf("per_level_above_first").forGetter(v -> ((LevelBasedValue.Linear) v).perLevelAboveFirst())
        ).apply(i, (Float b, Float p) -> (LevelBasedValue) new LevelBasedValue.Linear(b, p))).codec();
        byName.put("", direct);
        Codec<LevelBasedValue> dispatch = Codec.PASSTHROUGH.comapFlatMap(
                dyn -> {
                    var raw = dyn.get("type").asString().result().orElse("");
                    var typeName = raw.startsWith("minecraft:") ? raw.substring("minecraft:".length()) : raw;
                    var codec = byName.get(typeName);
                    if (codec == null) {
                        return DataResult.error(() -> "Unknown level based value type: " + raw);
                    }
                    return codec.parse(dyn);
                },
                value -> {
                    var codec = byName.get(levelBasedValueTypeName(value));
                    var encoded = encodeLevelBasedValue(codec, value);
                    return new com.mojang.serialization.Dynamic<>(JsonOps.INSTANCE,
                            encoded.result().orElseThrow(() -> new IllegalArgumentException("Cannot encode: " + value)));
                });
        holder[0] = dispatch;
        return dispatch;
    }

    @SuppressWarnings("unchecked")
    private static DataResult<com.google.gson.JsonElement> encodeLevelBasedValue(
            Codec<? extends LevelBasedValue> codec, LevelBasedValue value) {
        return ((Codec<LevelBasedValue>) codec).encodeStart(JsonOps.INSTANCE, value);
    }

    static String levelBasedValueTypeName(LevelBasedValue v) {
        return switch (v) {
            case LevelBasedValue.Constant _ -> "constant";
            case LevelBasedValue.Linear _ -> "linear";
            case LevelBasedValue.Clamped _ -> "clamped";
            case LevelBasedValue.Fraction _ -> "fraction";
            case LevelBasedValue.LevelsSquared _ -> "levels_squared";
            case LevelBasedValue.Lookup _ -> "lookup";
            case LevelBasedValue.Exponent _ -> "exponent";
        };
    }

    private static Map<String, Key> contextTypes() {
        var names = new String[]{"generic", "chest", "command", "selector", "villager_trade", "fishing",
                "entity", "equipment", "archaeology", "gift", "barter", "vault",
                "advancement_reward", "advancement_entity", "advancement_location", "block_use", "block",
                "shearing", "entity_interact", "block_interact", "enchanted_damage", "enchanted_item",
                "enchanted_location"};
        var out = new HashMap<String, Key>();
        for (var n : names) out.put("minecraft:" + n, Key.key("minecraft:" + n));
        return out;
    }

    private static DataResult<Key> readId(String raw) {
        try {
            return DataResult.success(Key.key(raw));
        } catch (Exception e) {
            return DataResult.error(() -> "Not a valid resource location: " + raw);
        }
    }
}
