package io.ib67.yuanban.bot.observe;

import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 实体一个装备槽位的物品改变：Set Equipment 改变模型中的实体；Set Player Inventory Slot 的下标 41、42 改变本地玩家的身体护甲与鞍，
 * 这两个槽位在窗口 0 中没有对应的槽位，{@code entityId} 为本地玩家的实体编号。空物品为 null。
 */
public record EntityEquipmentChanged(int entityId, EquipmentSlot slot, @Nullable ItemStack old, @Nullable ItemStack now)
        implements Observation {
}
