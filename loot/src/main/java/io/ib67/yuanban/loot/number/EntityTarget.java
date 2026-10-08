package io.ib67.yuanban.loot.number;

import io.ib67.yuanban.loot.context.ContextKey;

/**
 * 条件与数字提供器引用的上下文实体，对应原版 {@code net.minecraft.world.level.storage.loot.LootContext$EntityTarget}。
 * 序列化名字沿用原版：{@code this}、{@code attacker}、{@code direct_attacker}、{@code attacking_player}、
 * {@code target_entity}、{@code interacting_entity}。
 */
public enum EntityTarget {
    THIS("this", ContextKey.THIS_ENTITY),
    ATTACKER("attacker", ContextKey.ATTACKING_ENTITY),
    DIRECT_ATTACKER("direct_attacker", ContextKey.DIRECT_ATTACKING_ENTITY),
    ATTACKING_PLAYER("attacking_player", ContextKey.LAST_DAMAGE_PLAYER),
    TARGET_ENTITY("target_entity", ContextKey.TARGET_ENTITY),
    INTERACTING_ENTITY("interacting_entity", ContextKey.INTERACTING_ENTITY);

    private final String serializedName;
    private final ContextKey<?> param;

    EntityTarget(String serializedName, ContextKey<?> param) {
        this.serializedName = serializedName;
        this.param = param;
    }

    /**
     * 对应原版 {@code EntityTarget#contextParam}：该实体在上下文中读取的参数键。
     */
    public ContextKey<?> contextParam() {
        return param;
    }

    /**
     * 对应原版 {@code EntityTarget#getSerializedName}：JSON 中的名字。
     */
    public String serializedName() {
        return serializedName;
    }
}
