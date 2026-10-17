package io.ib67.yuanban.bot.inventory;

import io.ib67.yuanban.bot.data.ItemData;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowLayoutTest {
    /**
     * wiki Java Edition protocol/Inventory（修订 3201379）：按菜单类型编号排列的第一个主物品栏槽位；讲台不包含玩家物品栏
     */
    private static final Map<ContainerType, Integer> WIKI_MAIN_START = Map.ofEntries(
            Map.entry(ContainerType.GENERIC_9X1, 9),
            Map.entry(ContainerType.GENERIC_9X2, 18),
            Map.entry(ContainerType.GENERIC_9X3, 27),
            Map.entry(ContainerType.GENERIC_9X4, 36),
            Map.entry(ContainerType.GENERIC_9X5, 45),
            Map.entry(ContainerType.GENERIC_9X6, 54),
            Map.entry(ContainerType.GENERIC_3X3, 9),
            Map.entry(ContainerType.CRAFTER_3x3, 9),
            Map.entry(ContainerType.ANVIL, 3),
            Map.entry(ContainerType.BEACON, 1),
            Map.entry(ContainerType.BLAST_FURNACE, 3),
            Map.entry(ContainerType.BREWING_STAND, 5),
            Map.entry(ContainerType.CRAFTING, 10),
            Map.entry(ContainerType.ENCHANTMENT, 2),
            Map.entry(ContainerType.FURNACE, 3),
            Map.entry(ContainerType.GRINDSTONE, 3),
            Map.entry(ContainerType.HOPPER, 5),
            Map.entry(ContainerType.LOOM, 4),
            Map.entry(ContainerType.MERCHANT, 3),
            Map.entry(ContainerType.SHULKER_BOX, 27),
            Map.entry(ContainerType.SMITHING, 4),
            Map.entry(ContainerType.SMOKER, 3),
            Map.entry(ContainerType.CARTOGRAPHY, 3),
            Map.entry(ContainerType.STONECUTTER, 2)
    );

    @Test
    void uniqueSlotCountsMatchWiki() {
        for (var type : ContainerType.values()) {
            if (type == ContainerType.LECTERN) {
                // 讲台只有书一格
                assertEquals(1, ContainerSlots.unique(type));
                assertEquals(1, Window.container(1, type, Component.empty()).size());
                continue;
            }
            var unique = ContainerSlots.unique(type);
            assertEquals(WIKI_MAIN_START.get(type), unique, type.name());
            var window = Window.container(1, type, Component.empty());
            assertEquals(9, window.toPlayerInventory(unique), type + " first main slot");
            assertEquals(0, window.toPlayerInventory(unique + 27), type + " first hotbar slot");
        }
        // 合成器的结果槽位在快捷栏之后
        var crafter = Window.container(1, ContainerType.CRAFTER_3x3, Component.empty());
        assertEquals(46, crafter.size());
        assertEquals(-1, crafter.toPlayerInventory(45));
    }

    @Test
    void openWindowPlayerPartIsWrittenToWindowZero() {
        var model = new InventoryModel();
        var changes = new ArrayList<String>();
        var sink = new InventoryModel.SlotChangeSink() {
            @Override
            public void slotChanged(int windowId, int slot, @Nullable ItemStack old, @Nullable ItemStack now) {
                changes.add(windowId + ":" + slot);
            }

            @Override
            public void cursorChanged(@Nullable ItemStack old, @Nullable ItemStack now) {
                changes.add("cursor");
            }

            @Override
            public void equipmentChanged(EquipmentSlot slot, @Nullable ItemStack old, @Nullable ItemStack now) {
                changes.add(slot.name());
            }
        };
        model.open(3, ContainerType.GENERIC_9X3, Component.text("chest"), sink);
        var stone = new ItemStack(ItemData.id(Key.key("stone")));
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
