package io.ib67.yuanban.agent.view;

import java.util.List;

/**
 * 本地玩家。
 *
 * @param position  脚部位置
 * @param velocity  每 tick 的速度
 * @param gameMode  游戏模式名称，例如 {@code SURVIVAL}
 * @param heldSlot  快捷栏选中的槽位（0 到 8）
 * @param dimension 维度名称
 */
public record PlayerView(int entityId, String uuid, String username, Vec3 position, float yaw, float pitch,
                         Vec3 velocity, boolean onGround, float health, int food, float saturation, boolean dead,
                         String gameMode, boolean flying, int heldSlot, int level, List<EffectView> effects,
                         String dimension) {
    public PlayerView {
        effects = List.copyOf(effects);
    }

    /**
     * @param effect    效果名称，例如 {@code minecraft:speed}
     * @param amplifier 放大器（等级减一）
     * @param duration  剩余 tick 数；-1 表示无限
     */
    public record EffectView(String effect, int amplifier, int duration) {
    }
}
