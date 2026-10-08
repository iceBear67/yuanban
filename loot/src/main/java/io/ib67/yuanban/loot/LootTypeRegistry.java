package io.ib67.yuanban.loot;

import io.ib67.yuanban.loot.codec.ConditionCodecs;
import io.ib67.yuanban.loot.codec.EntryCodecs;
import io.ib67.yuanban.loot.codec.FunctionCodecs;
import io.ib67.yuanban.loot.codec.NumberCodecs;
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
 * {@link io.ib67.yuanban.loot.codec.LootCodecs} 解码时按 JSON 的 {@code type} / {@code condition} /
 * {@code function} 字段从这里查类型。
 * <p>
 * 原版 26.2 的 {@code LootPoolEntries}（9 种条目）、{@code LootItemConditions}（20 种条件）、
 * {@code LootItemFunctions}（43 种修饰器）、{@code NumberProviders}（8 种数字提供器）注册的全部类型
 * 在 {@link #vanilla()} 里按原版的 ID 和注册次序登记，每个类型带有自己的编解码器。
 * 宿主注册自己的类型时必须使用自己的命名空间；原版注册表的注册器本身是冻结的，不允许追加注册，
 * 本模块用禁止 {@code minecraft} 命名空间代替这个约束。
 */
public final class LootTypeRegistry {
    private final Map<Key, LootEntryType<?>> entries = new HashMap<>();
    private final Map<Key, LootConditionType<?>> conditions = new HashMap<>();
    private final Map<Key, LootFunctionType<?>> functions = new HashMap<>();
    private final Map<Key, NumberProviderType<?>> numberProviders = new HashMap<>();

    /**
     * 登记了原版 26.2 全部 9 + 20 + 43 + 8 种类型的注册表，编解码器见 {@code io.ib67.yuanban.loot.codec} 包。
     */
    public static LootTypeRegistry vanilla() {
        var registry = new LootTypeRegistry();

        // LootPoolEntries：9 种条目
        registry.vanillaEntry(new LootEntryType<>(Key.key("empty"), EntryCodecs::empty));
        registry.vanillaEntry(new LootEntryType<>(Key.key("item"), EntryCodecs::item));
        registry.vanillaEntry(new LootEntryType<>(Key.key("loot_table"), EntryCodecs::lootTable));
        registry.vanillaEntry(new LootEntryType<>(Key.key("dynamic"), EntryCodecs::dynamic));
        registry.vanillaEntry(new LootEntryType<>(Key.key("tag"), EntryCodecs::tag));
        registry.vanillaEntry(new LootEntryType<>(Key.key("slots"), EntryCodecs::slots));
        registry.vanillaEntry(new LootEntryType<>(Key.key("alternatives"), EntryCodecs::alternatives));
        registry.vanillaEntry(new LootEntryType<>(Key.key("sequence"), EntryCodecs::sequence));
        registry.vanillaEntry(new LootEntryType<>(Key.key("group"), EntryCodecs::group));

        // LootItemConditions：20 种条件
        registry.vanillaCondition(new LootConditionType<>(Key.key("inverted"), ConditionCodecs::inverted));
        registry.vanillaCondition(new LootConditionType<>(Key.key("any_of"), ConditionCodecs::anyOf));
        registry.vanillaCondition(new LootConditionType<>(Key.key("all_of"), ConditionCodecs::allOf));
        registry.vanillaCondition(new LootConditionType<>(Key.key("random_chance"), ConditionCodecs::randomChance));
        registry.vanillaCondition(new LootConditionType<>(Key.key("random_chance_with_enchanted_bonus"),
                ConditionCodecs::randomChanceWithEnchantedBonus));
        registry.vanillaCondition(new LootConditionType<>(Key.key("entity_properties"), ConditionCodecs::entityProperties));
        registry.vanillaCondition(new LootConditionType<>(Key.key("killed_by_player"), ConditionCodecs::killedByPlayer));
        registry.vanillaCondition(new LootConditionType<>(Key.key("entity_scores"), ConditionCodecs::entityScores));
        registry.vanillaCondition(new LootConditionType<>(Key.key("block_state_property"), ConditionCodecs::blockStateProperty));
        registry.vanillaCondition(new LootConditionType<>(Key.key("match_tool"), ConditionCodecs::matchTool));
        registry.vanillaCondition(new LootConditionType<>(Key.key("table_bonus"), ConditionCodecs::tableBonus));
        registry.vanillaCondition(new LootConditionType<>(Key.key("survives_explosion"), ConditionCodecs::survivesExplosion));
        registry.vanillaCondition(new LootConditionType<>(Key.key("damage_source_properties"), ConditionCodecs::damageSourceProperties));
        registry.vanillaCondition(new LootConditionType<>(Key.key("location_check"), ConditionCodecs::locationCheck));
        registry.vanillaCondition(new LootConditionType<>(Key.key("weather_check"), ConditionCodecs::weatherCheck));
        registry.vanillaCondition(new LootConditionType<>(Key.key("reference"), ConditionCodecs::reference));
        registry.vanillaCondition(new LootConditionType<>(Key.key("time_check"), ConditionCodecs::timeCheck));
        registry.vanillaCondition(new LootConditionType<>(Key.key("value_check"), ConditionCodecs::valueCheck));
        registry.vanillaCondition(new LootConditionType<>(Key.key("enchantment_active_check"), ConditionCodecs::enchantmentActiveCheck));
        registry.vanillaCondition(new LootConditionType<>(Key.key("environment_attribute_check"),
                ConditionCodecs::environmentAttributeCheck));

        // LootItemFunctions：43 种修饰器
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_count"), FunctionCodecs::setCount));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_item"), FunctionCodecs::setItem));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("enchant_with_levels"), FunctionCodecs::enchantWithLevels));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("enchant_randomly"), FunctionCodecs::enchantRandomly));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_enchantments"), FunctionCodecs::setEnchantments));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_custom_data"), FunctionCodecs::setCustomData));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_components"), FunctionCodecs::setComponents));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("furnace_smelt"), FunctionCodecs::furnaceSmelt));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("enchanted_count_increase"), FunctionCodecs::enchantedCountIncrease));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_damage"), FunctionCodecs::setDamage));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_attributes"), FunctionCodecs::setAttributes));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_name"), FunctionCodecs::setName));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("exploration_map"), FunctionCodecs::explorationMap));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_stew_effect"), FunctionCodecs::setStewEffect));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("copy_name"), FunctionCodecs::copyName));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_contents"), FunctionCodecs::setContents));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("modify_contents"), FunctionCodecs::modifyContents));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("filtered"), FunctionCodecs::filtered));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("limit_count"), FunctionCodecs::limitCount));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("apply_bonus"), FunctionCodecs::applyBonus));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_loot_table"), FunctionCodecs::setLootTable));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("explosion_decay"), FunctionCodecs::explosionDecay));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_lore"), FunctionCodecs::setLore));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("fill_player_head"), FunctionCodecs::fillPlayerHead));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("copy_custom_data"), FunctionCodecs::copyCustomData));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("copy_state"), FunctionCodecs::copyState));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_banner_pattern"), FunctionCodecs::setBannerPattern));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_potion"), FunctionCodecs::setPotion));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_random_dyes"), FunctionCodecs::setRandomDyes));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_random_potion"), FunctionCodecs::setRandomPotion));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_instrument"), FunctionCodecs::setInstrument));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("reference"), FunctionCodecs::reference));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("sequence"), FunctionCodecs::sequence));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("copy_components"), FunctionCodecs::copyComponents));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_fireworks"), FunctionCodecs::setFireworks));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_firework_explosion"), FunctionCodecs::setFireworkExplosion));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_book_cover"), FunctionCodecs::setBookCover));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_written_book_pages"), FunctionCodecs::setWrittenBookPages));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_writable_book_pages"), FunctionCodecs::setWritableBookPages));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("toggle_tooltips"), FunctionCodecs::toggleTooltips));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_ominous_bottle_amplifier"), FunctionCodecs::setOminousBottleAmplifier));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("set_custom_model_data"), FunctionCodecs::setCustomModelData));
        registry.vanillaFunction(new LootFunctionType<>(Key.key("discard"), FunctionCodecs::discard));

        // NumberProviders：8 种数字提供器
        registry.vanillaNumberProvider(new NumberProviderType<>(Key.key("constant"), NumberCodecs::constant));
        registry.vanillaNumberProvider(new NumberProviderType<>(Key.key("uniform"), NumberCodecs::uniform));
        registry.vanillaNumberProvider(new NumberProviderType<>(Key.key("binomial"), NumberCodecs::binomial));
        registry.vanillaNumberProvider(new NumberProviderType<>(Key.key("score"), NumberCodecs::score));
        registry.vanillaNumberProvider(new NumberProviderType<>(Key.key("storage"), NumberCodecs::storage));
        registry.vanillaNumberProvider(new NumberProviderType<>(Key.key("sum"), NumberCodecs::sum));
        registry.vanillaNumberProvider(new NumberProviderType<>(Key.key("enchantment_level"), NumberCodecs::enchantmentLevel));
        registry.vanillaNumberProvider(new NumberProviderType<>(Key.key("environment_attribute"), NumberCodecs::environmentAttribute));

        return registry;
    }

    private void vanillaEntry(LootEntryType<?> type) {
        entries.put(type.id(), type);
    }

    private void vanillaCondition(LootConditionType<?> type) {
        conditions.put(type.id(), type);
    }

    private void vanillaFunction(LootFunctionType<?> type) {
        functions.put(type.id(), type);
    }

    private void vanillaNumberProvider(NumberProviderType<?> type) {
        numberProviders.put(type.id(), type);
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
        var type = (LootEntryType<? extends LootEntry>) entries.get(id);
        return Optional.ofNullable(type);
    }

    public Optional<LootConditionType<? extends LootCondition>> condition(Key id) {
        var type = (LootConditionType<? extends LootCondition>) conditions.get(id);
        return Optional.ofNullable(type);
    }

    public Optional<LootFunctionType<? extends LootFunction>> function(Key id) {
        var type = (LootFunctionType<? extends LootFunction>) functions.get(id);
        return Optional.ofNullable(type);
    }

    public Optional<NumberProviderType<? extends NumberProvider>> numberProvider(Key id) {
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
}
