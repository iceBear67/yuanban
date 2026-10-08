package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.view.ActiveEffect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.jetbrains.annotations.Nullable;

/**
 * Entity Effect 或 Remove Entity Effect，目标为自己。
 *
 * @param now  新的效果；移除时为 null
 * @param tick 收到时 bot 已经执行的 tick 数
 */
public record EffectChanged(Effect effect, @Nullable ActiveEffect now, long tick) implements Observation {
}
