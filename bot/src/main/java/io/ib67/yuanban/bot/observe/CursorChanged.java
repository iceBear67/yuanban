package io.ib67.yuanban.bot.observe;

import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 光标上的物品改变；空物品为 null。
 */
public record CursorChanged(@Nullable ItemStack old, @Nullable ItemStack now) implements Observation {
}
