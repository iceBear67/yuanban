package io.ib67.yuanban.bot.observe;

import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.item.ItemStack;

/**
 * 实体一个装备槽位的物品改变：Set Equipment 改变模型中的实体；Set Player Inventory Slot 的下标 41、42 改变本地玩家的身体护甲与鞍，
 * 这两个槽位在窗口 0 中没有对应的槽位，{@code entityId} 为本地玩家的实体编号。
 */
public record EntityEquipmentChanged(int entityId, EquipmentSlot slot, ItemStack old, ItemStack now) implements Observation {
}
