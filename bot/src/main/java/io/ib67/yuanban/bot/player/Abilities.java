package io.ib67.yuanban.bot.player;

import io.ib67.yuanban.bot.view.AbilitiesSnapshot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.network.packet.server.play.PlayerAbilitiesPacket;

/**
 * 本地玩家的能力：初始值与原版 {@code Abilities} 相同，游戏模式改变时按 {@link #gameMode} 设置，
 * 之后由 Player Abilities 覆盖。
 */
public final class Abilities {
    private boolean invulnerable;
    private boolean flying;
    private boolean allowFlying;
    private boolean instantBreak;
    private boolean mayBuild = true;
    private float flyingSpeed = 0.05f;
    private float walkingSpeed = 0.1f;

    public void apply(PlayerAbilitiesPacket packet) {
        var flags = packet.flags();
        invulnerable = (flags & 0x01) != 0;
        flying = (flags & 0x02) != 0;
        allowFlying = (flags & 0x04) != 0;
        instantBreak = (flags & 0x08) != 0;
        flyingSpeed = packet.flyingSpeed();
        walkingSpeed = packet.walkingSpeed();
    }

    /**
     * 游戏模式改变（Login (play)、Respawn、Game Event 3）：按原版 {@code GameType.updatePlayerAbilities} 设置。
     * 创造模式可以飞行、瞬间破坏、无敌；旁观模式可以飞行、无敌并且正在飞行；其余模式这几项为 false。
     * 冒险模式与旁观模式不能建造
     */
    public void gameMode(GameMode gameMode) {
        switch (gameMode) {
            case CREATIVE -> {
                allowFlying = true;
                instantBreak = true;
                invulnerable = true;
            }
            case SPECTATOR -> {
                allowFlying = true;
                instantBreak = false;
                invulnerable = true;
                flying = true;
            }
            case SURVIVAL, ADVENTURE -> {
                allowFlying = false;
                instantBreak = false;
                invulnerable = false;
                flying = false;
            }
        }
        mayBuild = gameMode != GameMode.ADVENTURE && gameMode != GameMode.SPECTATOR;
    }

    public boolean flying() {
        return flying;
    }

    /**
     * 原版 {@code LocalPlayer.aiStep}：旁观模式以外飞行中着地时停止飞行
     */
    public void stopFlying() {
        flying = false;
    }

    public float flyingSpeed() {
        return flyingSpeed;
    }

    public boolean allowFlying() {
        return allowFlying;
    }

    public boolean instantBreak() {
        return instantBreak;
    }

    /**
     * 是否可以建造；Player Abilities 不包含这一项，只由游戏模式决定
     */
    public boolean mayBuild() {
        return mayBuild;
    }

    public AbilitiesSnapshot snapshot() {
        return new AbilitiesSnapshot(invulnerable, flying, allowFlying, instantBreak, mayBuild, flyingSpeed, walkingSpeed);
    }
}
