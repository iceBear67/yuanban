package io.ib67.yuanban.bot.inventory;

import io.ib67.yuanban.bot.view.WindowSnapshot;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.kyori.adventure.text.Component;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.TradeListPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 一个窗口的槽位（wiki Inventory）。一般的窗口排列为：独有槽位，然后主物品栏 27 格，然后快捷栏 9 格；
 * 合成器的结果槽位排在快捷栏之后；讲台不包含玩家物品栏。窗口 0 的排列见 wiki Inventory。
 */
public final class Window {
    /**
     * 窗口 0 的槽位数：合成输出 1、合成格 4、盔甲 4、主物品栏 27、快捷栏 9、副手 1
     */
    public static final int PLAYER_WINDOW_SIZE = 46;
    public static final int PLAYER_MAIN_START = 9;
    public static final int PLAYER_HOTBAR_START = 36;
    public static final int PLAYER_OFFHAND = 45;

    private final int id;
    private final @Nullable InventoryType type;
    private final Component title;
    private final int mainStart;
    private final boolean playerInventory;
    private final ItemStack[] slots;
    private final Map<Integer, Integer> properties = new Int2IntOpenHashMap();
    private @Nullable TradeListPacket trades;
    /**
     * 这个窗口最近收到的状态编号（原版每个 {@code AbstractContainerMenu} 各有一个，新打开的窗口从 0 开始）
     */
    private int stateId;
    /**
     * 这个窗口的光标物品（原版每个 {@code AbstractContainerMenu} 各有一个 {@code carried}，新打开的窗口为空）
     */
    private ItemStack carried = ItemStack.AIR;

    private Window(int id, @Nullable InventoryType type, Component title, int mainStart, boolean playerInventory, int size) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.mainStart = mainStart;
        this.playerInventory = playerInventory;
        this.slots = new ItemStack[size];
        Arrays.fill(slots, ItemStack.AIR);
    }

    public static Window player() {
        return new Window(0, null, Component.empty(), PLAYER_MAIN_START, true, PLAYER_WINDOW_SIZE);
    }

    /**
     * Open Screen 打开的窗口：独有槽位数取 {@link InventoryType#getSize()}
     */
    public static Window container(int id, InventoryType type, Component title) {
        return switch (type) {
            case LECTERN -> new Window(id, type, title, type.getSize(), false, type.getSize());
            // 输入 0 到 8，主物品栏 9 到 35，快捷栏 36 到 44，结果槽位为 45
            case CRAFTER_3X3 -> new Window(id, type, title, type.getSize(), true, type.getSize() + 36 + 1);
            default -> new Window(id, type, title, type.getSize(), true, type.getSize() + 36);
        };
    }

    /**
     * Open Horse Screen 打开的窗口：鞍与身体护甲 2 格，然后每列 3 格（鹦鹉螺的窗口没有这些格子，列数为 0）
     */
    public static Window horse(int id, int columns) {
        var unique = 2 + 3 * columns;
        return new Window(id, null, Component.empty(), unique, true, unique + 36);
    }

    public int id() {
        return id;
    }

    public @Nullable InventoryType type() {
        return type;
    }

    public Component title() {
        return title;
    }

    public int size() {
        return slots.length;
    }

    public int stateId() {
        return stateId;
    }

    /**
     * Set Container Content 或 Set Container Slot 给出的状态编号
     */
    public void stateId(int stateId) {
        this.stateId = stateId;
    }

    public ItemStack carried() {
        return carried;
    }

    /**
     * Set Container Content 的光标物品，或者这是当前窗口时的 Set Cursor Item
     */
    public void carried(ItemStack carried) {
        this.carried = carried;
    }

    public ItemStack get(int slot) {
        return slots[slot];
    }

    /**
     * @return 旧的物品
     */
    public ItemStack set(int slot, ItemStack item) {
        var old = slots[slot];
        slots[slot] = item;
        return old;
    }

    public void property(int property, int value) {
        properties.put(property, value);
    }

    /**
     * Merchant Offers
     */
    public void trades(TradeListPacket trades) {
        this.trades = trades;
    }

    /**
     * 窗口槽位对应的玩家物品栏下标（0 到 8 为快捷栏，9 到 35 为主物品栏）；不属于玩家物品栏时为 -1
     */
    public int toPlayerInventory(int slot) {
        if (!playerInventory) {
            return -1;
        }
        var relative = slot - mainStart;
        if (relative < 0 || relative >= 36) {
            return -1;
        }
        return relative < 27 ? relative + 9 : relative - 27;
    }

    /**
     * 玩家物品栏下标（0 到 35）对应的窗口槽位；窗口不包含玩家物品栏时为 -1
     */
    public int fromPlayerInventory(int index) {
        if (!playerInventory) {
            return -1;
        }
        return index < 9 ? mainStart + 27 + index : mainStart + index - 9;
    }

    public WindowSnapshot snapshot() {
        return new WindowSnapshot(id, type, title, stateId, List.of(slots), properties, trades);
    }
}
