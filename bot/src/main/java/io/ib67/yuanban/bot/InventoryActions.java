package io.ib67.yuanban.bot;

import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;

/**
 * 物品栏操作，全部为一次性请求，作用于执行时打开的窗口（没有打开窗口时为窗口 0）。
 * 物品栏内容只由服务端数据更新，本库不预测点击结果。
 */
public interface InventoryActions {
    /**
     * 发送 Click Container
     *
     * @param slot   窗口槽位
     * @param type   模式（wiki Click Container 的 Mode）
     * @param action 这个模式下的按键（wiki Click Container 的 Button），类型必须与模式对应：
     *               {@code CLICK_ITEM} 对应 {@code ClickItemAction}，{@code SHIFT_CLICK_ITEM} 对应 {@code ShiftClickItemAction}，
     *               {@code MOVE_TO_HOTBAR_SLOT} 对应 {@code MoveToHotbarAction}，{@code CREATIVE_GRAB_MAX_STACK} 对应
     *               {@code CreativeGrabAction}，{@code DROP_ITEM} 对应 {@code DropItemAction}，{@code SPREAD_ITEM} 对应
     *               {@code SpreadItemAction}，{@code FILL_STACK} 对应 {@code FillStackAction}
     * @throws IllegalArgumentException 按键与模式不对应，或者点击窗口外的两种 {@code DropItemAction} 的槽位不是 -999
     */
    Action click(int slot, ContainerActionType type, ContainerAction action);

    /**
     * 左键点击：模式 CLICK_ITEM，按键 LEFT_CLICK
     */
    Action leftClick(int slot);

    /**
     * 右键点击：模式 CLICK_ITEM，按键 RIGHT_CLICK
     */
    Action rightClick(int slot);

    /**
     * Shift 左键点击：模式 SHIFT_CLICK_ITEM，按键 LEFT_CLICK
     */
    Action shiftClick(int slot);

    /**
     * 与快捷栏交换：模式 MOVE_TO_HOTBAR_SLOT，按键为快捷栏槽位（0 到 8）
     */
    Action swapWithHotbar(int slot, int hotbarSlot);

    /**
     * 丢出槽位中的物品：模式 DROP_ITEM，按键 DROP_FROM_SELECTED 丢出一个，DROP_SELECTED_STACK 丢出整组
     */
    Action dropSlot(int slot, boolean wholeStack);

    /**
     * 发送 Click Container Button
     */
    Action clickButton(int buttonId);

    /**
     * 发送 Close Container；没有打开窗口时窗口编号为 0
     */
    Action close();
}
