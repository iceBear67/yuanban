package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.view.BossBarSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Boss Bar。
 *
 * @param now 变化之后的状态；移除时为 null
 */
public record BossBarChanged(UUID id, @Nullable BossBarSnapshot now) implements Observation {
}
