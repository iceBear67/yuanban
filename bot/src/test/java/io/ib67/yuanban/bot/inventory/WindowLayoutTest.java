package io.ib67.yuanban.bot.inventory;

import io.ib67.yuanban.bot.registry.VanillaReference;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowLayoutTest {
    @BeforeAll
    static void bindMaterialComponents() {
        // Material 的组件绑定到原版注册表之后才能读取
        VanillaReference.get();
    }

    /**
     * wiki Java Edition protocol/Inventory（修订 3201379）：按菜单类型编号排列的第一个主物品栏槽位；讲台不包含玩家物品栏
     */
    private static final Map<InventoryType, Integer> WIKI_MAIN_START = Map.ofEntries(
            Map.entry(InventoryType.CHEST_1_ROW, 9),
            Map.entry(InventoryType.CHEST_2_ROW, 18),
            Map.entry(InventoryType.CHEST_3_ROW, 27),
            Map.entry(InventoryType.CHEST_4_ROW, 36),
            Map.entry(InventoryType.CHEST_5_ROW, 45),
            Map.entry(InventoryType.CHEST_6_ROW, 54),
            Map.entry(InventoryType.WINDOW_3X3, 9),
            Map.entry(InventoryType.CRAFTER_3X3, 9),
            Map.entry(InventoryType.ANVIL, 3),
            Map.entry(InventoryType.BEACON, 1),
            Map.entry(InventoryType.BLAST_FURNACE, 3),
            Map.entry(InventoryType.BREWING_STAND, 5),
            Map.entry(InventoryType.CRAFTING, 10),
            Map.entry(InventoryType.ENCHANTMENT, 2),
            Map.entry(InventoryType.FURNACE, 3),
            Map.entry(InventoryType.GRINDSTONE, 3),
            Map.entry(InventoryType.HOPPER, 5),
            Map.entry(InventoryType.LOOM, 4),
            Map.entry(InventoryType.MERCHANT, 3),
            Map.entry(InventoryType.SHULKER_BOX, 27),
            Map.entry(InventoryType.SMITHING, 4),
            Map.entry(InventoryType.SMOKER, 3),
            Map.entry(InventoryType.CARTOGRAPHY, 3),
            Map.entry(InventoryType.STONE_CUTTER, 2)
    );

    @Test
    void uniqueSlotCountsMatchWiki() {
        for (var type : InventoryType.values()) {
            if (type == InventoryType.LECTERN) {
                // 讲台只有书一格
                assertEquals(1, type.getSize());
                assertEquals(1, Window.container(1, type, Component.empty()).size());
                continue;
            }
            assertEquals(WIKI_MAIN_START.get(type), type.getSize(), type.name());
            var window = Window.container(1, type, Component.empty());
            assertEquals(9, window.toPlayerInventory(type.getSize()), type + " first main slot");
            assertEquals(0, window.toPlayerInventory(type.getSize() + 27), type + " first hotbar slot");
        }
        // 合成器的结果槽位在快捷栏之后
        var crafter = Window.container(1, InventoryType.CRAFTER_3X3, Component.empty());
        assertEquals(46, crafter.size());
        assertEquals(-1, crafter.toPlayerInventory(45));
    }

    @Test
    void openWindowPlayerPartIsWrittenToWindowZero() {
        var model = new InventoryModel();
        var changes = new ArrayList<String>();
        var sink = new InventoryModel.SlotChangeSink() {
            @Override
            public void slotChanged(int windowId, int slot, ItemStack old, ItemStack now) {
                changes.add(windowId + ":" + slot);
            }

            @Override
            public void cursorChanged(ItemStack old, ItemStack now) {
                changes.add("cursor");
            }

            @Override
            public void equipmentChanged(EquipmentSlot slot, ItemStack old, ItemStack now) {
                changes.add(slot.name());
            }
        };
        model.open(3, InventoryType.CHEST_3_ROW, Component.text("chest"), sink);
        var stone = ItemStack.of(Material.STONE);
        // 箱子窗口的槽位 27 是主物品栏第一格，对应窗口 0 的槽位 9；槽位 54 是快捷栏第一格，对应窗口 0 的槽位 36
        model.setSlot(3, 5, 27, stone, sink);
        model.setSlot(3, 6, 54, stone, sink);
        assertEquals(List.of("3:27", "0:9", "3:54", "0:36"), changes);
        assertEquals(stone, model.player().get(9));
        assertEquals(stone, model.player().get(36));
        assertEquals(6, model.stateId());

        // 窗口 0 的修改同时写入打开的窗口
        changes.clear();
        model.setPlayerInventorySlot(1, stone, sink);
        assertEquals(List.of("0:37", "3:55"), changes);
        // 盔甲与副手只在窗口 0 中
        changes.clear();
        model.setPlayerInventorySlot(39, stone, sink);
        model.setPlayerInventorySlot(40, stone, sink);
        assertEquals(List.of("0:5", "0:45"), changes);
        // 原版 Inventory.setItem：41、42 为身体护甲与鞍，窗口 0 中没有对应的槽位；大于 42 的下标被忽略
        changes.clear();
        model.setPlayerInventorySlot(41, stone, sink);
        model.setPlayerInventorySlot(42, stone, sink);
        model.setPlayerInventorySlot(43, stone, sink);
        assertEquals(List.of("BODY", "SADDLE"), changes);
        assertEquals(stone, model.equipment(EquipmentSlot.BODY));
        assertEquals(stone, model.equipment(EquipmentSlot.SADDLE));
        assertThrows(IllegalArgumentException.class, () -> model.equipment(EquipmentSlot.HELMET));
        // 下标为负数时原版写入物品列表越界，客户端断开连接
        assertThrows(IndexOutOfBoundsException.class, () -> model.setPlayerInventorySlot(-1, stone, sink));
    }
}
