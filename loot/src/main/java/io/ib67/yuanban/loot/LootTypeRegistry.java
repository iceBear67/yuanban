package io.ib67.yuanban.loot;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.number.NumberProvider;
import io.ib67.yuanban.loot.type.LootConditionType;
import io.ib67.yuanban.loot.type.LootEntryType;
import io.ib67.yuanban.loot.type.LootFunctionType;
import io.ib67.yuanban.loot.type.NumberProviderType;
import net.kyori.adventure.key.Key;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 战利品表的类型注册表，保管条目、条件、修饰器、数字提供器四组类型对象。
 * 解析器按 JSON 的 {@code type} / {@code condition} / {@code function} 字段从这里查类型。
 * <p>
 * 原版 26.2 的 {@code LootPoolEntries}（10 种条目）、{@code LootItemConditions}（20 种条件）、
 * {@code LootItemFunctions}（44 种修饰器）、{@code NumberProviders}（8 种数字提供器）注册的全部类型
 * 在 {@link #vanilla()} 里按原版的 ID（和注册次序）登记；修饰器 {@code minecraft:set_loot_table}
 * 和序列种类修饰器 {@code minecraft:function} 沿用原版注册名，见 DESIGN 第 4 节。
 * 宿主注册自己的类型时必须使用自己的命名空间；原版注册表的注册器本身是冻结的，不允许追加注册，
 * 本模块用禁止 {@code minecraft} 命名空间代替这个约束。
 */
public final class LootTypeRegistry {
    private final Map<Key, LootEntryType<?>> entries = new HashMap<>();
    private final Map<Key, LootConditionType<?>> conditions = new HashMap<>();
    private final Map<Key, LootFunctionType<?>> functions = new HashMap<>();
    private final Map<Key, NumberProviderType<?>> numberProviders = new HashMap<>();

    /**
     * 登记了原版 26.2 全部 10 + 20 + 44 + 8 种类型 ID 的注册表。
     * 类型对象的 codec 在 loot 数据类型模块装配时填入，这里为 null；ID 和记录类型的对应见 DESIGN 第 5 节。
     */
    public static LootTypeRegistry vanilla() {
        var registry = new LootTypeRegistry();

        // LootPoolEntries：10 种条目
        registry.entries.put(Key.key("empty"), new LootEntryType<>(Key.key("empty"), null));
        registry.entries.put(Key.key("item"), new LootEntryType<>(Key.key("item"), null));
        registry.entries.put(Key.key("loot_table"), new LootEntryType<>(Key.key("loot_table"), null));
        registry.entries.put(Key.key("dynamic"), new LootEntryType<>(Key.key("dynamic"), null));
        registry.entries.put(Key.key("tag"), new LootEntryType<>(Key.key("tag"), null));
        registry.entries.put(Key.key("slots"), new LootEntryType<>(Key.key("slots"), null));
        registry.entries.put(Key.key("alternatives"), new LootEntryType<>(Key.key("alternatives"), null));
        registry.entries.put(Key.key("sequence"), new LootEntryType<>(Key.key("sequence"), null));
        registry.entries.put(Key.key("group"), new LootEntryType<>(Key.key("group"), null));

        // LootItemConditions：20 种条件
        registry.conditions.put(Key.key("inverted"), new LootConditionType<>(Key.key("inverted"), null));
        registry.conditions.put(Key.key("any_of"), new LootConditionType<>(Key.key("any_of"), null));
        registry.conditions.put(Key.key("all_of"), new LootConditionType<>(Key.key("all_of"), null));
        registry.conditions.put(Key.key("random_chance"), new LootConditionType<>(Key.key("random_chance"), null));
        registry.conditions.put(Key.key("random_chance_with_enchanted_bonus"), new LootConditionType<>(Key.key("random_chance_with_enchanted_bonus"), null));
        registry.conditions.put(Key.key("entity_properties"), new LootConditionType<>(Key.key("entity_properties"), null));
        registry.conditions.put(Key.key("killed_by_player"), new LootConditionType<>(Key.key("killed_by_player"), null));
        registry.conditions.put(Key.key("entity_scores"), new LootConditionType<>(Key.key("entity_scores"), null));
        registry.conditions.put(Key.key("block_state_property"), new LootConditionType<>(Key.key("block_state_property"), null));
        registry.conditions.put(Key.key("match_tool"), new LootConditionType<>(Key.key("match_tool"), null));
        registry.conditions.put(Key.key("table_bonus"), new LootConditionType<>(Key.key("table_bonus"), null));
        registry.conditions.put(Key.key("survives_explosion"), new LootConditionType<>(Key.key("survives_explosion"), null));
        registry.conditions.put(Key.key("damage_source_properties"), new LootConditionType<>(Key.key("damage_source_properties"), null));
        registry.conditions.put(Key.key("location_check"), new LootConditionType<>(Key.key("location_check"), null));
        registry.conditions.put(Key.key("weather_check"), new LootConditionType<>(Key.key("weather_check"), null));
        registry.conditions.put(Key.key("reference"), new LootConditionType<>(Key.key("reference"), null));
        registry.conditions.put(Key.key("time_check"), new LootConditionType<>(Key.key("time_check"), null));
        registry.conditions.put(Key.key("value_check"), new LootConditionType<>(Key.key("value_check"), null));
        registry.conditions.put(Key.key("enchantment_active_check"), new LootConditionType<>(Key.key("enchantment_active_check"), null));
        registry.conditions.put(Key.key("environment_attribute_check"), new LootConditionType<>(Key.key("environment_attribute_check"), null));

        // LootItemFunctions：44 种修饰器
        registry.functions.put(Key.key("set_count"), new LootFunctionType<>(Key.key("set_count"), null));
        registry.functions.put(Key.key("set_item"), new LootFunctionType<>(Key.key("set_item"), null));
        registry.functions.put(Key.key("enchant_with_levels"), new LootFunctionType<>(Key.key("enchant_with_levels"), null));
        registry.functions.put(Key.key("enchant_randomly"), new LootFunctionType<>(Key.key("enchant_randomly"), null));
        registry.functions.put(Key.key("set_enchantments"), new LootFunctionType<>(Key.key("set_enchantments"), null));
        registry.functions.put(Key.key("set_custom_data"), new LootFunctionType<>(Key.key("set_custom_data"), null));
        registry.functions.put(Key.key("set_components"), new LootFunctionType<>(Key.key("set_components"), null));
        registry.functions.put(Key.key("furnace_smelt"), new LootFunctionType<>(Key.key("furnace_smelt"), null));
        registry.functions.put(Key.key("enchanted_count_increase"), new LootFunctionType<>(Key.key("enchanted_count_increase"), null));
        registry.functions.put(Key.key("set_damage"), new LootFunctionType<>(Key.key("set_damage"), null));
        registry.functions.put(Key.key("set_attributes"), new LootFunctionType<>(Key.key("set_attributes"), null));
        registry.functions.put(Key.key("set_name"), new LootFunctionType<>(Key.key("set_name"), null));
        registry.functions.put(Key.key("exploration_map"), new LootFunctionType<>(Key.key("exploration_map"), null));
        registry.functions.put(Key.key("set_stew_effect"), new LootFunctionType<>(Key.key("set_stew_effect"), null));
        registry.functions.put(Key.key("copy_name"), new LootFunctionType<>(Key.key("copy_name"), null));
        registry.functions.put(Key.key("set_contents"), new LootFunctionType<>(Key.key("set_contents"), null));
        registry.functions.put(Key.key("modify_contents"), new LootFunctionType<>(Key.key("modify_contents"), null));
        registry.functions.put(Key.key("filtered"), new LootFunctionType<>(Key.key("filtered"), null));
        registry.functions.put(Key.key("limit_count"), new LootFunctionType<>(Key.key("limit_count"), null));
        registry.functions.put(Key.key("apply_bonus"), new LootFunctionType<>(Key.key("apply_bonus"), null));
        registry.functions.put(Key.key("set_loot_table"), new LootFunctionType<>(Key.key("set_loot_table"), null));
        registry.functions.put(Key.key("explosion_decay"), new LootFunctionType<>(Key.key("explosion_decay"), null));
        registry.functions.put(Key.key("set_lore"), new LootFunctionType<>(Key.key("set_lore"), null));
        registry.functions.put(Key.key("fill_player_head"), new LootFunctionType<>(Key.key("fill_player_head"), null));
        registry.functions.put(Key.key("copy_custom_data"), new LootFunctionType<>(Key.key("copy_custom_data"), null));
        registry.functions.put(Key.key("copy_state"), new LootFunctionType<>(Key.key("copy_state"), null));
        registry.functions.put(Key.key("set_banner_pattern"), new LootFunctionType<>(Key.key("set_banner_pattern"), null));
        registry.functions.put(Key.key("set_potion"), new LootFunctionType<>(Key.key("set_potion"), null));
        registry.functions.put(Key.key("set_random_dyes"), new LootFunctionType<>(Key.key("set_random_dyes"), null));
        registry.functions.put(Key.key("set_random_potion"), new LootFunctionType<>(Key.key("set_random_potion"), null));
        registry.functions.put(Key.key("set_instrument"), new LootFunctionType<>(Key.key("set_instrument"), null));
        registry.functions.put(Key.key("reference"), new LootFunctionType<>(Key.key("reference"), null));
        registry.functions.put(Key.key("sequence"), new LootFunctionType<>(Key.key("sequence"), null));
        registry.functions.put(Key.key("copy_components"), new LootFunctionType<>(Key.key("copy_components"), null));
        registry.functions.put(Key.key("set_fireworks"), new LootFunctionType<>(Key.key("set_fireworks"), null));
        registry.functions.put(Key.key("set_firework_explosion"), new LootFunctionType<>(Key.key("set_firework_explosion"), null));
        registry.functions.put(Key.key("set_book_cover"), new LootFunctionType<>(Key.key("set_book_cover"), null));
        registry.functions.put(Key.key("set_written_book_pages"), new LootFunctionType<>(Key.key("set_written_book_pages"), null));
        registry.functions.put(Key.key("set_writable_book_pages"), new LootFunctionType<>(Key.key("set_writable_book_pages"), null));
        registry.functions.put(Key.key("toggle_tooltips"), new LootFunctionType<>(Key.key("toggle_tooltips"), null));
        registry.functions.put(Key.key("set_ominous_bottle_amplifier"), new LootFunctionType<>(Key.key("set_ominous_bottle_amplifier"), null));
        registry.functions.put(Key.key("set_custom_model_data"), new LootFunctionType<>(Key.key("set_custom_model_data"), null));
        registry.functions.put(Key.key("discard"), new LootFunctionType<>(Key.key("discard"), null));
        registry.functions.put(Key.key("function"), new LootFunctionType<>(Key.key("function"), null));

        // NumberProviders：8 种数字提供器
        registry.numberProviders.put(Key.key("constant"), new NumberProviderType<>(Key.key("constant"), null));
        registry.numberProviders.put(Key.key("uniform"), new NumberProviderType<>(Key.key("uniform"), null));
        registry.numberProviders.put(Key.key("binomial"), new NumberProviderType<>(Key.key("binomial"), null));
        registry.numberProviders.put(Key.key("score"), new NumberProviderType<>(Key.key("score"), null));
        registry.numberProviders.put(Key.key("storage"), new NumberProviderType<>(Key.key("storage"), null));
        registry.numberProviders.put(Key.key("sum"), new NumberProviderType<>(Key.key("sum"), null));
        registry.numberProviders.put(Key.key("enchantment_level"), new NumberProviderType<>(Key.key("enchantment_level"), null));
        registry.numberProviders.put(Key.key("environment_attribute"), new NumberProviderType<>(Key.key("environment_attribute"), null));

        return registry;
    }

    /**
     * 注册一个条目类型。ID 已被占用、或使用 {@code minecraft} 命名空间注册时抛出 {@link IllegalArgumentException}。
     */
    public void registerEntry(Key id, LootEntryType<?> type) {
        register(entries, id, Objects.requireNonNull(type, "type"));
    }

    /**
     * 注册一个条件类型。ID 已被占用、或使用 {@code minecraft} 命名空间注册时抛出 {@link IllegalArgumentException}。
     */
    public void registerCondition(Key id, LootConditionType<?> type) {
        register(conditions, id, Objects.requireNonNull(type, "type"));
    }

    /**
     * 注册一个修饰器类型。ID 已被占用、或使用 {@code minecraft} 命名空间注册时抛出 {@link IllegalArgumentException}。
     */
    public void registerFunction(Key id, LootFunctionType<?> type) {
        register(functions, id, Objects.requireNonNull(type, "type"));
    }

    /**
     * 注册一个数字提供器类型。ID 已被占用、或使用 {@code minecraft} 命名空间注册时抛出 {@link IllegalArgumentException}。
     */
    public void registerNumberProvider(Key id, NumberProviderType<?> type) {
        register(numberProviders, id, Objects.requireNonNull(type, "type"));
    }

    public Optional<LootEntryType<? extends LootEntry>> entry(Key id) {
        @SuppressWarnings("unchecked")
        var type = (LootEntryType<? extends LootEntry>) entries.get(id);
        return Optional.ofNullable(type);
    }

    public Optional<LootConditionType<? extends LootCondition>> condition(Key id) {
        @SuppressWarnings("unchecked")
        var type = (LootConditionType<? extends LootCondition>) conditions.get(id);
        return Optional.ofNullable(type);
    }

    public Optional<LootFunctionType<? extends LootFunction>> function(Key id) {
        @SuppressWarnings("unchecked")
        var type = (LootFunctionType<? extends LootFunction>) functions.get(id);
        return Optional.ofNullable(type);
    }

    public Optional<NumberProviderType<? extends NumberProvider>> numberProvider(Key id) {
        @SuppressWarnings("unchecked")
        var type = (NumberProviderType<? extends NumberProvider>) numberProviders.get(id);
        return Optional.ofNullable(type);
    }

    private static <T> void register(Map<Key, T> types, Key id, T type) {
        Objects.requireNonNull(id, "id");
        if (Key.MINECRAFT_NAMESPACE.equals(id.namespace())) {
            throw new IllegalArgumentException("Custom loot types must not use the minecraft namespace: " + id.asString());
        }
        if (types.putIfAbsent(id, type) != null) {
            throw new IllegalArgumentException("Duplicate loot type id: " + id.asString());
        }
    }

    /**
     * loot-table-parser 用来给 {@link #vanilla()} 里登记过的原版类型填 {@code codec} 的内部接口。
     * 宿主请勿调用；本模块对原版注册表是冻结的，codec 只允许在装配阶段写入一次。
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public <T extends LootEntry> void wireEntryCodec(Key id, com.mojang.serialization.Codec<? extends T> codec) {
        var existing = entries.get(id);
        if (existing == null) throw new IllegalArgumentException("Unknown entry id: " + id);
        entries.put(id, new LootEntryType<>(id, codec));
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public <T extends LootCondition> void wireConditionCodec(Key id, com.mojang.serialization.Codec<? extends T> codec) {
        var existing = conditions.get(id);
        if (existing == null) throw new IllegalArgumentException("Unknown condition id: " + id);
        conditions.put(id, new LootConditionType<>(id, codec));
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public <T extends LootFunction> void wireFunctionCodec(Key id, com.mojang.serialization.Codec<? extends T> codec) {
        var existing = functions.get(id);
        if (existing == null) throw new IllegalArgumentException("Unknown function id: " + id);
        functions.put(id, new LootFunctionType<>(id, codec));
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public <T extends NumberProvider> void wireNumberProviderCodec(Key id, com.mojang.serialization.Codec<? extends T> codec) {
        var existing = numberProviders.get(id);
        if (existing == null) throw new IllegalArgumentException("Unknown number provider id: " + id);
        numberProviders.put(id, new NumberProviderType<>(id, codec));
    }
}
