package io.ib67.yuanban.bot.observe;

import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 窗口中一个槽位的物品改变，包括打开的窗口中玩家物品栏部分的修改同步到窗口 0 的那一条。空物品为 null。
 */
public record SlotChanged(int windowId, int slot, @Nullable ItemStack old, @Nullable ItemStack now) implements Observation {
}
