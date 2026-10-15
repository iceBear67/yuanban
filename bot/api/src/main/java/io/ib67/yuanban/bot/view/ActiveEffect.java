package io.ib67.yuanban.bot.view;

import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;

/**
 * 实体身上的一个药水效果（Entity Effect）。
 *
 * @param amplifier     放大器（等级减一）
 * @param duration      持续时间（tick）；{@link #INFINITE_DURATION} 表示无限
 * @param ambient       来自信标等环境效果
 * @param showParticles 显示粒子
 * @param showIcon      显示图标
 * @param blend         与客户端已有的效果混合
 */
public record ActiveEffect(Effect effect, int amplifier, int duration, boolean ambient, boolean showParticles,
                           boolean showIcon, boolean blend) {
    public static final int INFINITE_DURATION = -1;

    public boolean isInfinite() {
        return duration == INFINITE_DURATION;
    }

    /**
     * 持续时间换成 duration 之后的效果
     */
    public ActiveEffect withDuration(int duration) {
        return new ActiveEffect(effect, amplifier, duration, ambient, showParticles, showIcon, blend);
    }
}
