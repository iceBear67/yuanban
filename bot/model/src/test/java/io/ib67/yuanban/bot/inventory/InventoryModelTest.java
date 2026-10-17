package io.ib67.yuanban.bot.inventory;

import io.ib67.yuanban.bot.data.ItemData;
import io.ib67.yuanban.bot.view.WindowSnapshot;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 窗口 0 与打开的窗口之间的同步，以及 Set Player Inventory Slot 的下标映射。
 */
class InventoryModelTest {
    private static final int DIRT = ItemData.id(Key.key("dirt"));
    private static final int STONE = ItemData.id(Key.key("stone"));
    private static final int DIAMOND = ItemData.id(Key.key("diamond"));

    private static ItemStack item(int id) {
        return new ItemStack(id, 1);
    }

    /**
     * 记录全部变化
     */
    private static final class Recorder implements InventoryModel.SlotChangeSink {
        final List<String> changes = new ArrayList<>();
        final List<String> cursors = new ArrayList<>();
        final List<String> equipment = new ArrayList<>();

        @Override
        public void slotChanged(int windowId, int slot, @Nullable ItemStack old, @Nullable ItemStack now) {
            changes.add(windowId + ":" + slot + " " + describe(old) + " -> " + describe(now));
        }

        @Override
        public void cursorChanged(@Nullable ItemStack old, @Nullable ItemStack now) {
            cursors.add(describe(old) + " -> " + describe(now));
        }

        @Override
        public void equipmentChanged(EquipmentSlot slot, @Nullable ItemStack old, @Nullable ItemStack now) {
            equipment.add(slot + " " + describe(old) + " -> " + describe(now));
        }

        private static String describe(@Nullable ItemStack item) {
            return item == null ? "empty" : String.valueOf(item.getId());
        }
    }

    private final InventoryModel inventory = new InventoryModel();
    private final Recorder recorder = new Recorder();

    @Test
    void anOpenedWindowSharesThePlayerInventory() {
        inventory.setSlot(0, 0, Window.PLAYER_HOTBAR_START, item(DIRT), recorder);
        recorder.changes.clear();
        inventory.open(7, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        // 打开时取窗口 0 的物品，物品没有改变，所以不产生槽位变化
        assertTrue(recorder.changes.isEmpty());
        assertEquals(item(DIRT), inventory.open().get(27 + 27));
        // 打开窗口后当前窗口的光标与状态编号来自新窗口
        assertEquals(7, inventory.currentWindowId());
        assertEquals(0, inventory.stateId());
        assertNull(inventory.cursor());
    }

    @Test
    void changesToTheOpenWindowReachWindowZero() {
        inventory.open(7, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        // 打开窗口的槽位 27 是主物品栏第一格（玩家物品栏下标 9），与窗口 0 的槽位 9 是同一份物品
        inventory.setSlot(7, 1, 27, item(STONE), recorder);
        assertEquals(List.of("7:27 empty -> " + STONE, "0:9 empty -> " + STONE), recorder.changes);
        assertEquals(item(STONE), inventory.player().get(Window.PLAYER_MAIN_START));

        recorder.changes.clear();
        // 窗口 0 的快捷栏与打开窗口的槽位 54 对应（快捷栏第一格为玩家物品栏下标 0）
        inventory.setSlot(0, 2, Window.PLAYER_HOTBAR_START, item(DIAMOND), recorder);
        assertEquals(List.of("0:36 empty -> " + DIAMOND, "7:54 empty -> " + DIAMOND), recorder.changes);
        assertEquals(item(DIAMOND), inventory.open().get(54));
    }

    @Test
    void theContainerPartOfAnOpenWindowIsNotShared() {
        inventory.open(7, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        // 槽位 0 是容器自己的槽位，不属于窗口 0
        inventory.setSlot(7, 1, 0, item(DIRT), recorder);
        assertEquals(List.of("7:0 empty -> " + DIRT), recorder.changes);
        assertNull(inventory.player().get(Window.PLAYER_MAIN_START));
    }

    @Test
    void anUnknownWindowIsIgnored() {
        inventory.open(7, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        recorder.changes.clear();
        inventory.setSlot(9, 1, 0, item(DIRT), recorder);
        inventory.windowItems(9, 1, List.of(item(DIRT)), null, recorder);
        // 不属于窗口 0 与打开的窗口：整包忽略
        assertTrue(recorder.changes.isEmpty());
        assertNull(inventory.open().get(0));
    }

    @Test
    void windowZeroIsAlwaysAccepted() {
        inventory.open(7, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        inventory.setSlot(0, 5, Window.PLAYER_OFFHAND, item(DIRT), recorder);
        assertEquals(item(DIRT), inventory.player().get(Window.PLAYER_OFFHAND));
        // 副手不属于打开的窗口的玩家物品栏部分
        assertNull(inventory.open().get(27 + 36 - 1));
    }

    @Test
    void outOfRangeSlotsAndItemsFail() {
        inventory.open(7, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        // 原版 AbstractContainerMenu.getSlot 越界抛出异常：客户端断开连接
        assertThrows(IndexOutOfBoundsException.class, () -> inventory.setSlot(7, 1, 63, item(DIRT), recorder));
        assertThrows(IndexOutOfBoundsException.class, () -> {
            var items = new ArrayList<ItemStack>();
            for (int i = 0; i <= 63; i++) {
                items.add(null);
            }
            inventory.windowItems(7, 1, items, null, recorder);
        });
        assertThrows(IndexOutOfBoundsException.class, () -> inventory.setPlayerInventorySlot(-1, item(DIRT), recorder));
    }

    @Test
    void closingAWindowReturnsToWindowZero() {
        inventory.setSlot(0, 0, Window.PLAYER_HOTBAR_START, item(DIRT), recorder);
        inventory.open(7, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        assertEquals(7, inventory.closeOpen(recorder));
        assertEquals(0, inventory.currentWindowId());
        assertNull(inventory.open());
        // 关闭没有打开的窗口时什么都不做
        assertNull(inventory.closeOpen(recorder));
        // 关闭时按窗口编号关闭，不看参数
        inventory.open(7, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        inventory.open(8, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        assertEquals(8, inventory.closeOpen(recorder));
    }

    @Test
    void theCursorBelongsToEachWindow() {
        inventory.cursor(item(DIRT), recorder);
        assertEquals(List.of("empty -> " + DIRT), recorder.cursors);
        inventory.open(7, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        // 新窗口的光标从空开始
        assertEquals(List.of("empty -> " + DIRT, DIRT + " -> empty"), recorder.cursors);
        assertNull(inventory.cursor());
        inventory.cursor(item(STONE), recorder);
        // 关闭窗口之后回到窗口 0 的光标
        inventory.closeOpen(recorder);
        assertEquals(item(DIRT), inventory.cursor());
        assertEquals(STONE + " -> " + DIRT, recorder.cursors.getLast());
    }

    @Test
    void setPlayerInventorySlotMapsEveryIndex() {
        // 快捷栏与主物品栏
        inventory.setPlayerInventorySlot(0, item(DIRT), recorder);
        assertEquals(item(DIRT), inventory.player().get(Window.PLAYER_HOTBAR_START));
        inventory.setPlayerInventorySlot(9, item(STONE), recorder);
        assertEquals(item(STONE), inventory.player().get(Window.PLAYER_MAIN_START));
        // 脚、腿、胸、头依次为 36 到 39
        inventory.setPlayerInventorySlot(36, item(DIRT), recorder);
        inventory.setPlayerInventorySlot(39, item(STONE), recorder);
        assertEquals(item(DIRT), inventory.player().get(8));
        assertEquals(item(STONE), inventory.player().get(5));
        // 副手
        inventory.setPlayerInventorySlot(40, item(DIAMOND), recorder);
        assertEquals(item(DIAMOND), inventory.player().get(Window.PLAYER_OFFHAND));
        // 身体护甲与鞍不在窗口 0 中
        inventory.setPlayerInventorySlot(41, item(DIRT), recorder);
        inventory.setPlayerInventorySlot(42, item(STONE), recorder);
        assertEquals(item(DIRT), inventory.equipment(EquipmentSlot.BODY));
        assertEquals(item(STONE), inventory.equipment(EquipmentSlot.SADDLE));
        assertEquals(List.of("BODY empty -> " + DIRT, "SADDLE empty -> " + STONE), recorder.equipment);
        // 大于 42 的下标被忽略
        inventory.setPlayerInventorySlot(43, item(DIAMOND), recorder);
        assertEquals(item(DIRT), inventory.equipment(EquipmentSlot.BODY));
        // 其余装备在窗口 0 中，读取时抛出异常
        assertThrows(IllegalArgumentException.class, () -> inventory.equipment(EquipmentSlot.HELMET));
    }

    @Test
    void equipmentOutsideBodyAndSaddleIsRejected() {
        // 其余装备在窗口 0 中
        assertThrows(IllegalArgumentException.class, () -> inventory.equipment(EquipmentSlot.HELMET));
        assertThrows(IllegalArgumentException.class, () -> inventory.equipment(EquipmentSlot.MAIN_HAND));
    }

    @Test
    void aHorseWindowHasItsOwnSlots() {
        inventory.openHorse(5, 3, recorder);
        // 鞍与身体护甲 2 格，然后每列 3 格
        assertEquals(2 + 3 * 3 + 36, inventory.open().size());
        // 鹦鹉螺的窗口没有这些格子，只有鞍与身体护甲
        inventory.openHorse(6, 0, recorder);
        assertEquals(2 + 36, inventory.open().size());
    }

    @Test
    void windowItemsSetsTheStateIdAndTheCursor() {
        inventory.open(7, ContainerType.GENERIC_9X3, Component.text("chest"), recorder);
        var items = new ArrayList<ItemStack>();
        for (int i = 0; i < 54; i++) {
            items.add(i == 0 ? item(DIRT) : null);
        }
        inventory.windowItems(7, 4, items, item(STONE), recorder);
        assertEquals(4, inventory.stateId());
        assertEquals(item(STONE), inventory.cursor());
        assertEquals(item(DIRT), inventory.open().get(0));
    }

    @Test
    void windowZeroContentSetsTheSnapshotsStateId() {
        inventory.windowItems(0, 3, List.of(), null, recorder);
        WindowSnapshot snapshot = inventory.player().snapshot();
        assertEquals(3, snapshot.stateId());
        assertEquals(Window.PLAYER_WINDOW_SIZE, snapshot.slots().size());
    }

    @Test
    void propertiesAndTradesOnlyApplyToTheOpenWindow() {
        inventory.open(7, ContainerType.MERCHANT, Component.text("trade"), recorder);
        inventory.property(7, 1, 42);
        // 不属于打开的窗口的属性被忽略
        inventory.property(8, 1, 42);
        // 窗口 0 也有属性表，不抛出异常
        inventory.property(0, 0, 5);
        assertEquals(42, inventory.open().snapshot().properties().get(1));
        assertNull(inventory.open().snapshot().trades());
    }
}
