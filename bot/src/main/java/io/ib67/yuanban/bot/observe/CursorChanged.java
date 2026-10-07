package io.ib67.yuanban.bot.observe;

import net.minestom.server.item.ItemStack;

/**
 * 光标上的物品改变。
 */
public record CursorChanged(ItemStack old, ItemStack now) implements Observation {
}
