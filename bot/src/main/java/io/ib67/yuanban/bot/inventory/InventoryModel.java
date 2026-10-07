package io.ib67.yuanban.bot.inventory;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.TradeListPacket;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 物品栏：窗口 0 与打开的窗口，以及窗口 0 中没有槽位的身体护甲与鞍。物品栏内容只由服务端数据更新；状态编号与光标物品由每个窗口各自记录
 * （原版每个 {@code AbstractContainerMenu} 各有 {@code stateId} 与 {@code carried}），当前的光标为当前窗口的光标。
 * <p>
 * 打开的窗口中玩家物品栏部分与窗口 0 对应同一份物品（原版客户端的容器共用玩家的 Inventory）：
 * 服务端对其中一边的修改同时写入另一边（wiki Set Container Slot：服务端不会针对窗口 0 重发）。
 * <p>
 * 当前的光标（{@link #cursor()}）改变时通知 {@link CursorSink}，包括打开与关闭窗口使当前窗口改变的情况。
 */
public final class InventoryModel {
    private final Window player = Window.player();
    private @Nullable Window open;
    /**
     * 窗口 0 中没有槽位的玩家装备：身体护甲与鞍
     */
    private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(Map.of(
            EquipmentSlot.BODY, ItemStack.AIR, EquipmentSlot.SADDLE, ItemStack.AIR));

    public Window player() {
        return player;
    }

    public @Nullable Window open() {
        return open;
    }

    /**
     * 当前窗口（打开的窗口，没有打开窗口时为窗口 0）最近收到的状态编号，点击时发送
     * （原版 {@code handleContainerInput} 发送 {@code containerMenu.getStateId()}，每个菜单各有一个状态编号）
     */
    public int stateId() {
        return current().stateId();
    }

    /**
     * 当前窗口（打开的窗口，没有打开窗口时为窗口 0）的光标物品
     */
    public ItemStack cursor() {
        return current().carried();
    }

    private Window current() {
        return open != null ? open : player;
    }

    /**
     * 当前窗口编号：打开的窗口，没有打开窗口时为 0
     */
    public int currentWindowId() {
        return open != null ? open.id() : 0;
    }

    /**
     * Open Screen
     */
    public void open(int windowId, InventoryType type, Component title, CursorSink sink) {
        switchTo(Window.container(windowId, type, title), sink);
    }

    /**
     * Open Horse Screen：马的窗口，或者鹦鹉螺的窗口（{@code columns} 为 0）
     *
     * @param columns 物品栏列数
     */
    public void openHorse(int windowId, int columns, CursorSink sink) {
        switchTo(Window.horse(windowId, columns), sink);
    }

    /**
     * 关闭打开的窗口，与窗口编号无关（原版 {@code handleContainerClose} 调用 {@code clientSideCloseContainer}，
     * {@code inventory().close()} 同样关闭当前窗口）；当前窗口回到窗口 0，光标随之变为窗口 0 的光标
     *
     * @return 被关闭的窗口编号；没有打开的窗口时为 null
     */
    public @Nullable Integer closeOpen(CursorSink sink) {
        if (open == null) {
            return null;
        }
        var closed = open.id();
        switchTo(null, sink);
        return closed;
    }

    private void switchTo(@Nullable Window window, CursorSink sink) {
        var old = cursor();
        open = window;
        cursorChanged(old, sink);
    }

    private void cursorChanged(ItemStack old, CursorSink sink) {
        var now = cursor();
        if (!old.equals(now)) {
            sink.cursorChanged(old, now);
        }
    }

    /**
     * Set Container Content：光标物品写入这个窗口（原版 {@code initializeContents} 设置这个菜单的 {@code carried}）；
     * 窗口编号与窗口 0 或打开的窗口都不同时忽略（原版客户端行为）
     *
     * @throws IndexOutOfBoundsException 物品数多于窗口的槽位数（原版 {@code AbstractContainerMenu.getSlot} 越界抛出异常，客户端断开连接）
     */
    public void windowItems(int windowId, int stateId, List<ItemStack> items, ItemStack carried, SlotChangeSink sink) {
        var window = window(windowId);
        if (window == null) {
            return;
        }
        Objects.checkFromIndexSize(0, items.size(), window.size());
        window.stateId(stateId);
        for (int slot = 0; slot < items.size(); slot++) {
            setAndPropagate(window, slot, items.get(slot), sink);
        }
        var old = cursor();
        window.carried(carried);
        cursorChanged(old, sink);
    }

    /**
     * Set Container Slot；窗口编号与窗口 0 或打开的窗口都不同时忽略（原版客户端行为）
     *
     * @throws IndexOutOfBoundsException 槽位不在窗口中（原版 {@code AbstractContainerMenu.getSlot} 越界抛出异常，客户端断开连接）
     */
    public void setSlot(int windowId, int stateId, int slot, ItemStack item, SlotChangeSink sink) {
        var window = window(windowId);
        if (window == null) {
            return;
        }
        Objects.checkIndex(slot, window.size());
        window.stateId(stateId);
        setAndPropagate(window, slot, item, sink);
    }

    /**
     * Set Player Inventory Slot（原版 {@code Inventory.setItem}）：下标为玩家物品栏下标（0 到 8 快捷栏，9 到 35 主物品栏，
     * 36 到 39 依次为脚、腿、胸、头，40 副手，41 身体护甲，42 鞍）；身体护甲与鞍在窗口 0 中没有槽位，见 {@link #equipment}。
     * 大于 42 的下标被忽略（原版客户端行为）
     *
     * @throws IndexOutOfBoundsException 下标为负数（原版写入物品列表时越界抛出异常，客户端断开连接）
     */
    public void setPlayerInventorySlot(int index, ItemStack item, SlotChangeSink sink) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("player inventory index " + index + " is negative");
        }
        if (index < 36) {
            setAndPropagate(player, player.fromPlayerInventory(index), item, sink);
            return;
        }
        if (index == 41 || index == 42) {
            var slot = index == 41 ? EquipmentSlot.BODY : EquipmentSlot.SADDLE;
            var old = equipment.put(slot, item);
            if (!item.equals(old)) {
                sink.equipmentChanged(slot, old, item);
            }
            return;
        }
        var slot = switch (index) {
            case 36 -> 8;
            case 37 -> 7;
            case 38 -> 6;
            case 39 -> 5;
            case 40 -> Window.PLAYER_OFFHAND;
            default -> -1;
        };
        if (slot >= 0) {
            set(player, slot, item, sink);
        }
    }

    /**
     * 窗口 0 中没有槽位的玩家装备：身体护甲与鞍（Set Player Inventory Slot 的下标 41、42，原版 {@code Inventory.EQUIPMENT_SLOT_MAPPING}）；
     * 没有时为空气
     *
     * @param slot {@link EquipmentSlot#BODY} 或 {@link EquipmentSlot#SADDLE}
     * @throws IllegalArgumentException 其它装备槽位：它们在窗口 0 中
     */
    public ItemStack equipment(EquipmentSlot slot) {
        var item = equipment.get(slot);
        if (item == null) {
            throw new IllegalArgumentException(slot + " is a slot of window 0");
        }
        return item;
    }

    /**
     * Set Cursor Item：写入当前窗口的光标（原版 {@code containerMenu.setCarried}）
     */
    public void cursor(ItemStack item, CursorSink sink) {
        var old = cursor();
        current().carried(item);
        cursorChanged(old, sink);
    }

    public void property(int windowId, int property, int value) {
        var window = window(windowId);
        if (window != null) {
            window.property(property, value);
        }
    }

    /**
     * Merchant Offers：窗口编号与打开的窗口不同时忽略
     */
    public void trades(TradeListPacket trades) {
        if (open != null && open.id() == trades.windowId()) {
            open.trades(trades);
        }
    }

    private @Nullable Window window(int windowId) {
        if (windowId == 0) {
            return player;
        }
        return open != null && open.id() == windowId ? open : null;
    }

    private void setAndPropagate(Window window, int slot, ItemStack item, SlotChangeSink sink) {
        set(window, slot, item, sink);
        var index = window.toPlayerInventory(slot);
        if (index < 0) {
            return;
        }
        if (window != player) {
            set(player, player.fromPlayerInventory(index), item, sink);
        } else if (open != null && open.fromPlayerInventory(index) >= 0) {
            set(open, open.fromPlayerInventory(index), item, sink);
        }
    }

    private static void set(Window window, int slot, ItemStack item, SlotChangeSink sink) {
        var old = window.set(slot, item);
        if (!old.equals(item)) {
            sink.slotChanged(window.id(), slot, old, item);
        }
    }

    /**
     * 接收槽位变化
     */
    public interface SlotChangeSink extends CursorSink {
        void slotChanged(int windowId, int slot, ItemStack old, ItemStack now);

        /**
         * 窗口 0 中没有槽位的玩家装备（{@link #equipment}）改变
         */
        void equipmentChanged(EquipmentSlot slot, ItemStack old, ItemStack now);
    }

    public interface CursorSink {
        void cursorChanged(ItemStack old, ItemStack now);
    }
}
