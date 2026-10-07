package io.ib67.yuanban.loot.context;

import net.kyori.adventure.key.Key;

/**
 * 上下文参数：一次取用环境中的一项取值的键，对应原版 {@code net.minecraft.util.context.ContextKey}。
 * 本类的常量沿用原版 {@code LootContextParams} 的名字与 ID（{@code minecraft:this_entity} 等）。
 */
public record ContextKey<T>(Key id) {
    public static final ContextKey<Object> THIS_ENTITY = of("this_entity");
    public static final ContextKey<Object> INTERACTING_ENTITY = of("interacting_entity");
    public static final ContextKey<Object> TARGET_ENTITY = of("target_entity");
    public static final ContextKey<Object> LAST_DAMAGE_PLAYER = of("last_damage_player");
    public static final ContextKey<Object> DAMAGE_SOURCE = of("damage_source");
    public static final ContextKey<Object> ATTACKING_ENTITY = of("attacking_entity");
    public static final ContextKey<Object> DIRECT_ATTACKING_ENTITY = of("direct_attacking_entity");
    public static final ContextKey<Object> ORIGIN = of("origin");
    public static final ContextKey<Object> BLOCK_STATE = of("block_state");
    public static final ContextKey<Object> BLOCK_ENTITY = of("block_entity");
    public static final ContextKey<Object> TOOL = of("tool");
    public static final ContextKey<Object> EXPLOSION_RADIUS = of("explosion_radius");
    public static final ContextKey<Object> ENCHANTMENT_LEVEL = of("enchantment_level");
    public static final ContextKey<Object> ENCHANTMENT_ACTIVE = of("enchantment_active");
    public static final ContextKey<Object> ADDITIONAL_COST_COMPONENT_ALLOWED =
            of("additional_cost_component_allowed");

    private static ContextKey<Object> of(String id) {
        return new ContextKey<>(Key.key("minecraft", id));
    }
}
