package io.ib67.yuanban.agent.view;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 玩家物品栏（窗口 0）与打开的窗口。
 *
 * @param items      窗口 0 中不为空的槽位
 * @param mainHand   主手物品（槽位 36 + heldSlot）；为空时为 null
 * @param offHand    副手物品（槽位 45）；为空时为 null
 * @param cursor     光标上的物品；为空时为 null
 * @param openWindow 打开的窗口；没有时为 null
 */
public record InventoryView(int heldSlot, List<ItemView> items, @Nullable ItemView mainHand, @Nullable ItemView offHand,
                            @Nullable ItemView cursor, @Nullable WindowView openWindow) {
    public InventoryView {
        items = List.copyOf(items);
    }

    /**
     * @param type  窗口类型名称；马与鹦鹉螺的物品栏为 null
     * @param items 不为空的槽位
     */
    public record WindowView(int windowId, @Nullable String type, String title, int slotCount, List<ItemView> items) {
        public WindowView {
            items = List.copyOf(items);
        }
    }
}
