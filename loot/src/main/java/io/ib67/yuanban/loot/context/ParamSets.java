package io.ib67.yuanban.loot.context;

import net.kyori.adventure.key.Key;

import java.util.Map;
import java.util.Set;

/**
 * 原版 26.2 {@code LootContextParamSets} 注册的 26 种上下文类型（键名 → 必需/可选参数）。
 * 数据逐条来自原版 {@code LootContextParamSets.<clinit>} 的注册字节码。
 * 战利品表的 {@code type} 字段缺省值 {@code minecraft:generic}（原版表默认
 * {@code LootContextParamSets.ALL_PARAMS}）出现在第 19 项。
 */
final class ParamSets {
    private ParamSets() {
    }

    record Entry(Set<ContextKey<?>> required, Set<ContextKey<?>> optional) {
    }

    static final Map<Key, Entry> SETS = Map.ofEntries(
            entry("empty", req(), opt()),
            entry("chest", req(ContextKey.ORIGIN), opt(ContextKey.THIS_ENTITY)),
            entry("command", req(ContextKey.ORIGIN), opt(ContextKey.THIS_ENTITY)),
            entry("selector", req(ContextKey.ORIGIN), req(ContextKey.THIS_ENTITY)),
            entry("villager_trade", req(ContextKey.ORIGIN, ContextKey.THIS_ENTITY,
                    ContextKey.ADDITIONAL_COST_COMPONENT_ALLOWED), opt()),
            entry("fishing", req(ContextKey.ORIGIN, ContextKey.TOOL), opt(ContextKey.THIS_ENTITY)),
            entry("entity", req(ContextKey.THIS_ENTITY, ContextKey.ORIGIN, ContextKey.DAMAGE_SOURCE),
                    opt(ContextKey.ATTACKING_ENTITY, ContextKey.DIRECT_ATTACKING_ENTITY,
                            ContextKey.LAST_DAMAGE_PLAYER)),
            entry("equipment", req(ContextKey.ORIGIN, ContextKey.THIS_ENTITY), opt()),
            entry("archaeology", req(ContextKey.ORIGIN, ContextKey.THIS_ENTITY, ContextKey.TOOL), opt()),
            entry("gift", req(ContextKey.ORIGIN, ContextKey.THIS_ENTITY), opt()),
            entry("barter", req(ContextKey.THIS_ENTITY), opt()),
            entry("vault", req(ContextKey.ORIGIN), opt(ContextKey.THIS_ENTITY, ContextKey.TOOL)),
            entry("advancement_reward", req(ContextKey.THIS_ENTITY, ContextKey.ORIGIN), opt()),
            entry("advancement_entity", req(ContextKey.THIS_ENTITY, ContextKey.ORIGIN), opt()),
            entry("advancement_location", req(ContextKey.THIS_ENTITY, ContextKey.ORIGIN,
                    ContextKey.TOOL, ContextKey.BLOCK_STATE), opt()),
            entry("block_use", req(ContextKey.THIS_ENTITY, ContextKey.ORIGIN, ContextKey.BLOCK_STATE), opt()),
            entry("generic", req(ContextKey.THIS_ENTITY, ContextKey.LAST_DAMAGE_PLAYER,
                    ContextKey.DAMAGE_SOURCE, ContextKey.ATTACKING_ENTITY, ContextKey.DIRECT_ATTACKING_ENTITY,
                    ContextKey.ORIGIN, ContextKey.BLOCK_STATE, ContextKey.BLOCK_ENTITY, ContextKey.TOOL,
                    ContextKey.EXPLOSION_RADIUS, ContextKey.ADDITIONAL_COST_COMPONENT_ALLOWED), opt()),
            entry("block", req(ContextKey.BLOCK_STATE, ContextKey.ORIGIN, ContextKey.TOOL),
                    opt(ContextKey.THIS_ENTITY, ContextKey.BLOCK_ENTITY, ContextKey.EXPLOSION_RADIUS)),
            entry("shearing", req(ContextKey.ORIGIN, ContextKey.THIS_ENTITY, ContextKey.TOOL), opt()),
            entry("entity_interact", req(ContextKey.TARGET_ENTITY, ContextKey.TOOL),
                    opt(ContextKey.INTERACTING_ENTITY)),
            entry("block_interact", req(ContextKey.BLOCK_STATE),
                    opt(ContextKey.BLOCK_ENTITY, ContextKey.INTERACTING_ENTITY, ContextKey.TOOL)),
            entry("enchanted_damage", req(ContextKey.THIS_ENTITY, ContextKey.ENCHANTMENT_LEVEL,
                            ContextKey.ORIGIN, ContextKey.DAMAGE_SOURCE),
                    opt(ContextKey.DIRECT_ATTACKING_ENTITY, ContextKey.ATTACKING_ENTITY)),
            entry("enchanted_item", req(ContextKey.TOOL, ContextKey.ENCHANTMENT_LEVEL), opt()),
            entry("enchanted_location", req(ContextKey.THIS_ENTITY, ContextKey.ENCHANTMENT_LEVEL,
                    ContextKey.ORIGIN, ContextKey.ENCHANTMENT_ACTIVE), opt()),
            entry("enchanted_entity", req(ContextKey.THIS_ENTITY, ContextKey.ENCHANTMENT_LEVEL,
                    ContextKey.ORIGIN), opt()),
            entry("hit_block", req(ContextKey.THIS_ENTITY, ContextKey.ENCHANTMENT_LEVEL,
                    ContextKey.ORIGIN, ContextKey.BLOCK_STATE), opt()));

    private static Map.Entry<Key, Entry> entry(String name, Set<ContextKey<?>> required,
                                               Set<ContextKey<?>> optional) {
        return Map.entry(Key.key("minecraft", name), new Entry(required, optional));
    }

    private static Set<ContextKey<?>> req(ContextKey<?>... keys) {
        return Set.of(keys);
    }

    private static Set<ContextKey<?>> opt(ContextKey<?>... keys) {
        return Set.of(keys);
    }

    static Entry of(Key type) {
        return SETS.get(type);
    }
}
