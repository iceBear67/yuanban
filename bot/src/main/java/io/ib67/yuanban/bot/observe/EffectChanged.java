package io.ib67.yuanban.bot.observe;

import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import org.jetbrains.annotations.Nullable;

/**
 * Entity Effect 或 Remove Entity Effect，目标为自己。
 *
 * @param now 新的效果，{@link TimedPotion#startingTicks()} 为收到时 bot 已经执行的 tick 数；移除时为 null
 */
public record EffectChanged(PotionEffect effect, @Nullable TimedPotion now) implements Observation {
}
