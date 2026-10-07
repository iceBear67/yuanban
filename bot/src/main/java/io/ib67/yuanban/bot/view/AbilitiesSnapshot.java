package io.ib67.yuanban.bot.view;

/**
 * 本地玩家的能力：游戏模式改变时按原版规则设置，之后由 Player Abilities 覆盖。
 *
 * @param mayBuild 可以建造；Player Abilities 不包含这一项，只由游戏模式决定（冒险模式与旁观模式为 false）
 */
public record AbilitiesSnapshot(boolean invulnerable, boolean flying, boolean allowFlying, boolean instantBreak,
                                boolean mayBuild, float flyingSpeed, float walkingSpeed) {
}
