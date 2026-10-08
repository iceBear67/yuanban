package io.ib67.yuanban.loot;

import io.ib67.yuanban.loot.codec.ConditionCodecs;
import io.ib67.yuanban.loot.codec.EntryCodecs;
import io.ib67.yuanban.loot.codec.FunctionCodecs;
import io.ib67.yuanban.loot.codec.NumberCodecs;
import io.ib67.yuanban.loot.type.LootConditionType;
import io.ib67.yuanban.loot.type.LootEntryType;
import io.ib67.yuanban.loot.type.LootFunctionType;
import io.ib67.yuanban.loot.type.NumberProviderType;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LootTypeRegistry} 的原版覆盖和注册规则。
 */
class LootTypeRegistryTest {
    private static final String[] VANILLA_ENTRIES = {
            "empty", "item", "loot_table", "dynamic", "tag", "slots", "alternatives", "sequence", "group"
    };
    private static final String[] VANILLA_CONDITIONS = {
            "inverted", "any_of", "all_of", "random_chance", "random_chance_with_enchanted_bonus",
            "entity_properties", "killed_by_player", "entity_scores", "block_state_property",
            "match_tool", "table_bonus", "survives_explosion", "damage_source_properties",
            "location_check", "weather_check", "reference", "time_check", "value_check",
            "enchantment_active_check", "environment_attribute_check"
    };
    private static final String[] VANILLA_FUNCTIONS = {
            "set_count", "set_item", "enchant_with_levels", "enchant_randomly", "set_enchantments",
            "set_custom_data", "set_components", "furnace_smelt", "enchanted_count_increase",
            "set_damage", "set_attributes", "set_name", "exploration_map", "set_stew_effect",
            "copy_name", "set_contents", "modify_contents", "filtered", "limit_count",
            "apply_bonus", "set_loot_table", "explosion_decay", "set_lore", "fill_player_head",
            "copy_custom_data", "copy_state", "set_banner_pattern", "set_potion", "set_random_dyes",
            "set_random_potion", "set_instrument", "reference", "sequence", "copy_components",
            "set_fireworks", "set_firework_explosion", "set_book_cover", "set_written_book_pages",
            "set_writable_book_pages", "toggle_tooltips", "set_ominous_bottle_amplifier",
            "set_custom_model_data", "discard"
    };
    private static final String[] VANILLA_NUMBER_PROVIDERS = {
            "constant", "uniform", "binomial", "score", "storage", "sum",
            "enchantment_level", "environment_attribute"
    };

    @Test
    void vanilla覆盖原版全部80种类型ID() {
        var registry = LootTypeRegistry.vanilla();
        for (var id : VANILLA_ENTRIES) {
            assertTrue(registry.entry(Key.key(id)).isPresent(), "entry minecraft:" + id);
        }
        for (var id : VANILLA_CONDITIONS) {
            assertTrue(registry.condition(Key.key(id)).isPresent(), "condition minecraft:" + id);
        }
        for (var id : VANILLA_FUNCTIONS) {
            assertTrue(registry.function(Key.key(id)).isPresent(), "function minecraft:" + id);
        }
        for (var id : VANILLA_NUMBER_PROVIDERS) {
            assertTrue(registry.numberProvider(Key.key(id)).isPresent(), "numberProvider minecraft:" + id);
        }
        assertEquals(9, VANILLA_ENTRIES.length);
        assertEquals(20, VANILLA_CONDITIONS.length);
        assertEquals(43, VANILLA_FUNCTIONS.length);
        assertEquals(8, VANILLA_NUMBER_PROVIDERS.length);
        var total = Stream.of(VANILLA_ENTRIES, VANILLA_CONDITIONS, VANILLA_FUNCTIONS, VANILLA_NUMBER_PROVIDERS)
                .mapToInt(a -> a.length).sum();
        assertEquals(80, total);
    }

    @Test
    void vanilla没有非原版的function修饰器() {
        assertFalse(LootTypeRegistry.vanilla().function(Key.key("function")).isPresent());
    }

    @Test
    void 原版类型的ID与登记的键相同() {
        var registry = LootTypeRegistry.vanilla();
        for (var id : VANILLA_FUNCTIONS) {
            assertEquals(Key.key(id), registry.function(Key.key(id)).orElseThrow().id());
        }
    }

    @Test
    void 类型记录不接受空的编解码器() {
        var id = Key.key("example", "nothing");
        assertThrows(NullPointerException.class, () -> new LootEntryType<>(id, null));
    }

    @Test
    void 重复注册同一ID抛IllegalArgumentException() {
        var registry = LootTypeRegistry.vanilla();
        var id = Key.key("example", "my_entry");
        registry.registerEntry(id, new LootEntryType<>(id, EntryCodecs::empty));
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> registry.registerEntry(id, new LootEntryType<>(id, EntryCodecs::empty)));
        assertTrue(thrown.getMessage().contains(id.asString()));
    }

    @Test
    void 用minecraft命名空间注册自定义类型抛IllegalArgumentException() {
        var registry = LootTypeRegistry.vanilla();
        var id = Key.key("minecraft", "foo");
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerFunction(id, new LootFunctionType<>(id, FunctionCodecs::discard)));
    }

    @Test
    void 自定义命名空间的类型注册成功且能查到() {
        var registry = LootTypeRegistry.vanilla();
        var conditionId = Key.key("example", "my_condition");
        var conditionType = new LootConditionType<>(conditionId, ConditionCodecs::killedByPlayer);
        registry.registerCondition(conditionId, conditionType);
        assertTrue(registry.condition(conditionId).isPresent());
        assertEquals(conditionType, registry.condition(conditionId).orElseThrow());

        var providerId = Key.key("example", "my_number");
        registry.registerNumberProvider(providerId, new NumberProviderType<>(providerId, NumberCodecs::constant));
        assertTrue(registry.numberProvider(providerId).isPresent());
    }
}
