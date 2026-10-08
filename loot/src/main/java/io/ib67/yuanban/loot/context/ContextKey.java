package io.ib67.yuanban.loot.context;

import net.kyori.adventure.key.Key;

/**
 * 上下文参数：一次取用环境中的一项取值的键，对应原版 {@code net.minecraft.util.context.ContextKey}。
 * 本类的常量沿用原版 {@code LootContextParams} 的名字与 ID（{@code minecraft:this_entity} 等）。
 * <p>
 * 求值器按下面的类型读取各参数的值，值的类型不符时求值抛出 {@link io.ib67.yuanban.loot.LootException}：
 * <ul>
 *   <li>{@link #THIS_ENTITY}、{@link #INTERACTING_ENTITY}、{@link #TARGET_ENTITY}、{@link #LAST_DAMAGE_PLAYER}、
 *       {@link #ATTACKING_ENTITY}、{@link #DIRECT_ATTACKING_ENTITY}：{@link net.minestom.server.entity.Entity}；
 *   <li>{@link #DAMAGE_SOURCE}：{@link net.minestom.server.entity.damage.Damage}；</li>
 *   <li>{@link #ORIGIN}：{@link net.minestom.server.coordinate.Point}；</li>
 *   <li>{@link #BLOCK_STATE}、{@link #BLOCK_ENTITY}：{@link net.minestom.server.instance.block.Block}，
 *       方块实体参数是带有方块实体类型与 NBT 的方块；</li>
 *   <li>{@link #TOOL}：{@link net.minestom.server.item.ItemStack}；</li>
 *   <li>{@link #EXPLOSION_RADIUS}：{@link Float}；{@link #ENCHANTMENT_LEVEL}：{@link Integer}；
 *       {@link #ENCHANTMENT_ACTIVE}、{@link #ADDITIONAL_COST_COMPONENT_ALLOWED}：{@link Boolean}。</li>
 * </ul>
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
