package io.ib67.yuanban.bot.observe;

import net.minestom.server.item.ItemStack;

/**
 * 窗口中一个槽位的物品改变，包括打开的窗口中玩家物品栏部分的修改同步到窗口 0 的那一条。
 */
public record SlotChanged(int windowId, int slot, ItemStack old, ItemStack now) implements Observation {
}
