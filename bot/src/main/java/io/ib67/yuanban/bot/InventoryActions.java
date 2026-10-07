package io.ib67.yuanban.bot;

import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;

/**
 * 物品栏操作，全部为一次性请求，作用于执行时打开的窗口（没有打开窗口时为窗口 0）。
 * 物品栏内容只由服务端数据更新，本库不预测点击结果。
 */
public interface InventoryActions {
    /**
     * 发送 Click Container
     *
     * @param slot   窗口槽位
     * @param button 按键，含义随模式变化，见 wiki Click Container
     * @param mode   模式
     */
    Action click(int slot, int button, ClientClickWindowPacket.ClickType mode);

    /**
     * 左键点击：模式 PICKUP，按键 0
     */
    Action leftClick(int slot);

    /**
     * 右键点击：模式 PICKUP，按键 1
     */
    Action rightClick(int slot);

    /**
     * Shift 左键点击：模式 QUICK_MOVE，按键 0
     */
    Action shiftClick(int slot);

    /**
     * 与快捷栏交换：模式 SWAP，按键为快捷栏槽位（0 到 8）
     */
    Action swapWithHotbar(int slot, int hotbarSlot);

    /**
     * 丢出槽位中的物品：模式 THROW，按键 0 丢出一个，1 丢出整组
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
