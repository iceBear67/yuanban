package io.ib67.yuanban.loot.context;

import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * 原版 26.2 {@code LootContextParamSets} 注册的 26 种上下文类型的快捷构造，
 * 必需/可选参数的划分逐条来自原版 {@code <clinit>} 的字节码（见 {@link ParamSets}）。
 * <p>
 * 最后一个参数固定是 {@link LootEnvironment}；原版由调用方以 {@code LootParams.Builder}
 * 逐项提供的可选项（玩家、方块实体等）通过各工厂方法的可空参数传入。构造出的
 * {@link LootContext} 的运气按原版 {@code LootParams$Builder#getLuck} 的规则由
 * {@link LootEnvironment#luckOf} 在 {@link LootContextImpl} 构造时计算。
 * 宿主需要其他组合时直接 new {@link LootContextImpl}。
 */
public final class LootContexts {
    private LootContexts() {
    }

    /**
     * {@code minecraft:empty}：原版常量 {@code LootContextParamSets.EMPTY}，无参数。
     */
    public static LootContext empty(LootEnvironment env) {
        return build(env, "empty", Map.of(), null, null);
    }

    /**
     * {@code minecraft:chest}：必需 {@code ORIGIN}，可选 {@code THIS_ENTITY}。
     */
    public static LootContext chest(Object origin, Object thisEntityOrNull, LootEnvironment env) {
        return build(env, "chest", of(ContextKey.ORIGIN, origin, ContextKey.THIS_ENTITY, thisEntityOrNull), null, null);
    }

    /**
     * {@code minecraft:command}：必需 {@code ORIGIN}，可选 {@code THIS_ENTITY}。
     */
    public static LootContext command(Object origin, Object thisEntityOrNull, LootEnvironment env) {
        return build(env, "command", of(ContextKey.ORIGIN, origin, ContextKey.THIS_ENTITY, thisEntityOrNull), null, null);
    }

    /**
     * {@code minecraft:selector}：必需 {@code ORIGIN}、{@code THIS_ENTITY}。
     */
    public static LootContext selector(Object origin, Object thisEntity, LootEnvironment env) {
        return build(env, "selector", of(ContextKey.ORIGIN, origin, ContextKey.THIS_ENTITY, thisEntity), null, null);
    }

    /**
     * {@code minecraft:villager_trade}：必需 {@code ORIGIN}、{@code THIS_ENTITY}、
     * {@code ADDITIONAL_COST_COMPONENT_ALLOWED}。
     */
    public static LootContext villagerTrade(Object origin, Object thisEntity,
                                            boolean additionalCostComponentAllowed, LootEnvironment env) {
        var params = of(ContextKey.ORIGIN, origin, ContextKey.THIS_ENTITY, thisEntity);
        params.put(ContextKey.ADDITIONAL_COST_COMPONENT_ALLOWED, additionalCostComponentAllowed);
        return build(env, "villager_trade", params, null, null);
    }

    /**
     * {@code minecraft:fishing}：必需 {@code TOOL}、{@code ORIGIN}，可选 {@code THIS_ENTITY}。
     * 运气按原版钓竿规则计入 {@code fishing_luck_bonus}（由环境实现）。
     */
    public static LootContext fishing(ItemStack tool, Object origin, Object thisEntityOrNull, LootEnvironment env) {
        Objects.requireNonNull(tool, "tool");
        return build(env, "fishing", of(ContextKey.ORIGIN, origin, ContextKey.TOOL, tool,
                        ContextKey.THIS_ENTITY, thisEntityOrNull),
                thisEntityOrNull instanceof Player player ? player : null, tool);
    }

    /**
     * {@code minecraft:entity}：必需 {@code THIS_ENTITY}、{@code ORIGIN}、{@code DAMAGE_SOURCE}；
     * 可选 {@code ATTACKING_ENTITY} / {@code DIRECT_ATTACKING_ENTITY} / {@code LAST_DAMAGE_PLAYER}。
     */
    public static LootContext entity(Object thisEntity, Object origin, Object damageSource,
                                     Object attackingEntityOrNull, Object directAttackingEntityOrNull,
                                     Object lastDamagePlayerOrNull, LootEnvironment env) {
        var params = of(ContextKey.THIS_ENTITY, thisEntity, ContextKey.ORIGIN, origin,
                ContextKey.DAMAGE_SOURCE, damageSource,
                ContextKey.ATTACKING_ENTITY, attackingEntityOrNull,
                ContextKey.DIRECT_ATTACKING_ENTITY, directAttackingEntityOrNull,
                ContextKey.LAST_DAMAGE_PLAYER, lastDamagePlayerOrNull);
        return build(env, "entity", params,
                lastDamagePlayerOrNull instanceof Player player ? player : null, null);
    }

    /**
     * {@code minecraft:equipment}：必需 {@code ORIGIN}、{@code THIS_ENTITY}。
     */
    public static LootContext equipment(Object origin, Object thisEntity, LootEnvironment env) {
        return build(env, "equipment", of(ContextKey.ORIGIN, origin, ContextKey.THIS_ENTITY, thisEntity), null, null);
    }

    /**
     * {@code minecraft:archaeology}：必需 {@code ORIGIN}、{@code THIS_ENTITY}、{@code TOOL}。
     */
    public static LootContext archaeology(Object origin, Object thisEntity, ItemStack tool, LootEnvironment env) {
        Objects.requireNonNull(tool, "tool");
        return build(env, "archaeology", of(ContextKey.ORIGIN, origin, ContextKey.THIS_ENTITY, thisEntity,
                        ContextKey.TOOL, tool),
                thisEntity instanceof Player player ? player : null, tool);
    }

    /**
     * {@code minecraft:gift}：必需 {@code ORIGIN}、{@code THIS_ENTITY}。
     */
    public static LootContext gift(Object origin, Object thisEntity, LootEnvironment env) {
        return build(env, "gift", of(ContextKey.ORIGIN, origin, ContextKey.THIS_ENTITY, thisEntity), null, null);
    }

    /**
     * {@code minecraft:barter}：必需 {@code THIS_ENTITY}。
     */
    public static LootContext barter(Object thisEntity, LootEnvironment env) {
        return build(env, "barter", of(ContextKey.THIS_ENTITY, thisEntity), null, null);
    }

    /**
     * {@code minecraft:vault}：必需 {@code ORIGIN}，可选 {@code THIS_ENTITY} / {@code TOOL}。
     */
    public static LootContext vault(Object origin, Object thisEntityOrNull, @Nullable ItemStack tool,
                                    LootEnvironment env) {
        return build(env, "vault", of(ContextKey.ORIGIN, origin, ContextKey.THIS_ENTITY, thisEntityOrNull,
                        ContextKey.TOOL, tool),
                thisEntityOrNull instanceof Player player ? player : null, tool);
    }

    /**
     * {@code minecraft:advancement_reward}：必需 {@code THIS_ENTITY}、{@code ORIGIN}。
     */
    public static LootContext advancementReward(Object thisEntity, Object origin, LootEnvironment env) {
        return build(env, "advancement_reward", of(ContextKey.THIS_ENTITY, thisEntity, ContextKey.ORIGIN, origin),
                null, null);
    }

    /**
     * {@code minecraft:advancement_entity}：必需 {@code THIS_ENTITY}、{@code ORIGIN}。
     */
    public static LootContext advancementEntity(Object thisEntity, Object origin, LootEnvironment env) {
        return build(env, "advancement_entity", of(ContextKey.THIS_ENTITY, thisEntity, ContextKey.ORIGIN, origin),
                null, null);
    }

    /**
     * {@code minecraft:advancement_location}：必需 {@code THIS_ENTITY}、{@code ORIGIN}、{@code TOOL}、
     * {@code BLOCK_STATE}。
     */
    public static LootContext advancementLocation(Object thisEntity, Object origin, ItemStack tool,
                                                  Object blockState, LootEnvironment env) {
        Objects.requireNonNull(tool, "tool");
        var params = of(ContextKey.THIS_ENTITY, thisEntity, ContextKey.ORIGIN, origin,
                ContextKey.TOOL, tool, ContextKey.BLOCK_STATE, blockState);
        return build(env, "advancement_location", params,
                thisEntity instanceof Player player ? player : null, tool);
    }

    /**
     * {@code minecraft:block_use}：必需 {@code THIS_ENTITY}、{@code ORIGIN}、{@code BLOCK_STATE}。
     */
    public static LootContext blockUse(Object thisEntity, Object origin, Object blockState, LootEnvironment env) {
        return build(env, "block_use", of(ContextKey.THIS_ENTITY, thisEntity, ContextKey.ORIGIN, origin,
                ContextKey.BLOCK_STATE, blockState), null, null);
    }

    /**
     * {@code minecraft:generic}：原版 {@code ALL_PARAMS}，11 个参数全部必需；
     * 战利品表缺省上下文类型（{@code LootTable#DEFAULT_TYPE}）。宿主不满足全集时应当用更具体的工厂。
     */
    public static LootContext generic(Object thisEntity, Object lastDamagePlayer, Object damageSource,
                                      Object attackingEntity, Object directAttackingEntity, Object origin,
                                      Object blockState, Object blockEntity, ItemStack tool,
                                      Object explosionRadius, boolean additionalCostComponentAllowed,
                                      LootEnvironment env) {
        Objects.requireNonNull(tool, "tool");
        var params = of(ContextKey.THIS_ENTITY, thisEntity, ContextKey.LAST_DAMAGE_PLAYER, lastDamagePlayer,
                ContextKey.DAMAGE_SOURCE, damageSource, ContextKey.ATTACKING_ENTITY, attackingEntity,
                ContextKey.DIRECT_ATTACKING_ENTITY, directAttackingEntity, ContextKey.ORIGIN, origin,
                ContextKey.BLOCK_STATE, blockState, ContextKey.BLOCK_ENTITY, blockEntity,
                ContextKey.TOOL, tool, ContextKey.EXPLOSION_RADIUS, explosionRadius);
        params.put(ContextKey.ADDITIONAL_COST_COMPONENT_ALLOWED, additionalCostComponentAllowed);
        return build(env, "generic", params,
                lastDamagePlayer instanceof Player player ? player : null, tool);
    }

    /**
     * {@code minecraft:block}：必需 {@code BLOCK_STATE}、{@code ORIGIN}、{@code TOOL}；可选
     * {@code THIS_ENTITY} / {@code BLOCK_ENTITY} / {@code EXPLOSION_RADIUS}。
     */
    public static LootContext block(Object blockState, Object origin, ItemStack tool,
                                    Object thisEntityOrNull, Object blockEntityOrNull, Object explosionRadiusOrNull,
                                    LootEnvironment env) {
        Objects.requireNonNull(tool, "tool");
        var params = of(ContextKey.BLOCK_STATE, blockState, ContextKey.ORIGIN, origin,
                ContextKey.TOOL, tool, ContextKey.THIS_ENTITY, thisEntityOrNull,
                ContextKey.BLOCK_ENTITY, blockEntityOrNull,
                ContextKey.EXPLOSION_RADIUS, explosionRadiusOrNull);
        return build(env, "block", params,
                thisEntityOrNull instanceof Player player ? player : null, tool);
    }

    /**
     * {@code minecraft:shearing}：必需 {@code ORIGIN}、{@code THIS_ENTITY}、{@code TOOL}。
     */
    public static LootContext shearing(Object origin, Object thisEntity, ItemStack tool, LootEnvironment env) {
        Objects.requireNonNull(tool, "tool");
        return build(env, "shearing", of(ContextKey.ORIGIN, origin, ContextKey.THIS_ENTITY, thisEntity,
                        ContextKey.TOOL, tool),
                thisEntity instanceof Player player ? player : null, tool);
    }

    /**
     * {@code minecraft:entity_interact}：必需 {@code TARGET_ENTITY}、{@code TOOL}；可选 {@code INTERACTING_ENTITY}。
     */
    public static LootContext entityInteract(Object targetEntity, ItemStack tool, Object interactingEntityOrNull,
                                             LootEnvironment env) {
        Objects.requireNonNull(tool, "tool");
        return build(env, "entity_interact", of(ContextKey.TARGET_ENTITY, targetEntity,
                        ContextKey.TOOL, tool, ContextKey.INTERACTING_ENTITY, interactingEntityOrNull),
                interactingEntityOrNull instanceof Player player ? player : null, tool);
    }

    /**
     * {@code minecraft:block_interact}：必需 {@code BLOCK_STATE}；可选 {@code BLOCK_ENTITY} /
     * {@code INTERACTING_ENTITY} / {@code TOOL}。
     */
    public static LootContext blockInteract(Object blockState, Object blockEntityOrNull,
                                            Object interactingEntityOrNull, @Nullable ItemStack tool,
                                            LootEnvironment env) {
        return build(env, "block_interact", of(ContextKey.BLOCK_STATE, blockState,
                        ContextKey.BLOCK_ENTITY, blockEntityOrNull,
                        ContextKey.INTERACTING_ENTITY, interactingEntityOrNull, ContextKey.TOOL, tool),
                interactingEntityOrNull instanceof Player player ? player : null, tool);
    }

    /**
     * {@code minecraft:enchanted_damage}：必需 {@code THIS_ENTITY}、{@code ENCHANTMENT_LEVEL}、
     * {@code ORIGIN}、{@code DAMAGE_SOURCE}；可选 {@code DIRECT_ATTACKING_ENTITY} / {@code ATTACKING_ENTITY}。
     */
    public static LootContext enchantedDamage(Object thisEntity, int enchantmentLevel, Object origin,
                                              Object damageSource, Object directAttackingEntityOrNull,
                                              Object attackingEntityOrNull, LootEnvironment env) {
        var params = of(ContextKey.THIS_ENTITY, thisEntity, ContextKey.ORIGIN, origin,
                ContextKey.DAMAGE_SOURCE, damageSource,
                ContextKey.DIRECT_ATTACKING_ENTITY, directAttackingEntityOrNull,
                ContextKey.ATTACKING_ENTITY, attackingEntityOrNull);
        params.put(ContextKey.ENCHANTMENT_LEVEL, enchantmentLevel);
        return build(env, "enchanted_damage", params, null, null);
    }

    /**
     * {@code minecraft:enchanted_item}：必需 {@code TOOL}、{@code ENCHANTMENT_LEVEL}。
     */
    public static LootContext enchantedItem(ItemStack tool, int enchantmentLevel, LootEnvironment env) {
        Objects.requireNonNull(tool, "tool");
        var params = of(ContextKey.TOOL, tool);
        params.put(ContextKey.ENCHANTMENT_LEVEL, enchantmentLevel);
        return build(env, "enchanted_item", params, null, tool);
    }

    /**
     * {@code minecraft:enchanted_location}：必需 {@code THIS_ENTITY}、{@code ENCHANTMENT_LEVEL}、
     * {@code ORIGIN}、{@code ENCHANTMENT_ACTIVE}。
     */
    public static LootContext enchantedLocation(Object thisEntity, int enchantmentLevel, Object origin,
                                                boolean enchantmentActive, LootEnvironment env) {
        var params = of(ContextKey.THIS_ENTITY, thisEntity, ContextKey.ORIGIN, origin);
        params.put(ContextKey.ENCHANTMENT_LEVEL, enchantmentLevel);
        params.put(ContextKey.ENCHANTMENT_ACTIVE, enchantmentActive);
        return build(env, "enchanted_location", params, null, null);
    }

    /**
     * {@code minecraft:enchanted_entity}：必需 {@code THIS_ENTITY}、{@code ENCHANTMENT_LEVEL}、{@code ORIGIN}。
     */
    public static LootContext enchantedEntity(Object thisEntity, int enchantmentLevel, Object origin,
                                              LootEnvironment env) {
        var params = of(ContextKey.THIS_ENTITY, thisEntity, ContextKey.ORIGIN, origin);
        params.put(ContextKey.ENCHANTMENT_LEVEL, enchantmentLevel);
        return build(env, "enchanted_entity", params, null, null);
    }

    /**
     * {@code minecraft:hit_block}：必需 {@code THIS_ENTITY}、{@code ENCHANTMENT_LEVEL}、{@code ORIGIN}、
     * {@code BLOCK_STATE}。
     */
    public static LootContext hitBlock(Object thisEntity, int enchantmentLevel, Object origin, Object blockState,
                                       LootEnvironment env) {
        var params = of(ContextKey.THIS_ENTITY, thisEntity, ContextKey.ORIGIN, origin,
                ContextKey.BLOCK_STATE, blockState);
        params.put(ContextKey.ENCHANTMENT_LEVEL, enchantmentLevel);
        return build(env, "hit_block", params, null, null);
    }

    private static LootContext build(LootEnvironment env, String type, Map<ContextKey<?>, Object> params,
                                     @Nullable Player luckPlayer, @Nullable ItemStack tool) {
        var id = net.kyori.adventure.key.Key.key("minecraft", type);
        var set = ParamSets.of(id);
        assert set != null;
        return new LootContextImpl(env, id, params, luckPlayer, tool, new Random());
    }

    /**
     * 按序装配参数；{@code null} 值直接跳过（对应原版 {@code Builder.optional(...)} 未提供的语义）。
     */
    private static Map<ContextKey<?>, Object> of(Object... pairs) {
        var params = new IdentityHashMap<ContextKey<?>, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            var value = pairs[i + 1];
            if (value != null) {
                params.put((ContextKey<?>) pairs[i], value);
            }
        }
        return params;
    }
}
