package io.ib67.yuanban.crafting.internal.menu;

import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.inventory.InventoryClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.click.ClickType;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行由本模块处理的点击，之后按 Minestom 自己处理点击时的方式发出 {@link InventoryClickEvent}，并重新发送背包内容。
 */
final class ClickEvents {
    private final CraftingMenu menu;

    ClickEvents(CraftingMenu menu) {
        this.menu = menu;
    }

    /**
     * Minestom 拖动的按键对应原版的拖动类型：2 为左键（0），6 为右键（1），10 为中键（2）。
     */
    static int dragType(int button) {
        return switch (button) {
            case 2 -> 0;
            case 6 -> 1;
            case 10 -> 2;
            default -> throw new IllegalArgumentException("unknown drag button " + button);
        };
    }

    /**
     * @param type          发出的事件类型，为 {@code null} 时不发出事件（Minestom 的点击类型中没有中键复制）
     * @param clickedSlots  单击类操作的菜单序号：为每一格发出一个事件，物品和光标都是操作之前的；为 {@code null} 时为内容变化的
     *                      每一格发出一个事件，物品是操作之后的，光标是操作之前的，与 Minestom 处理按住 Shift 的点击、双击、拖动相同
     */
    void run(@Nullable ClickType type, @Nullable List<Integer> clickedSlots, Runnable action) {
        var cursorBefore = menu.getCarried();
        var before = new ArrayList<ItemStack>(menu.slots.size());
        for (var slot : menu.slots) {
            before.add(slot.getItem());
        }
        action.run();
        if (type != null) {
            if (clickedSlots != null) {
                for (int index : clickedSlots) {
                    var slot = menu.slots.get(index);
                    fire(slot, type, before.get(index), cursorBefore);
                }
            } else {
                for (int i = 0; i < menu.slots.size(); i++) {
                    var slot = menu.slots.get(i);
                    var after = slot.getItem();
                    if (!after.equals(before.get(i))) {
                        fire(slot, type, after, cursorBefore);
                    }
                }
            }
        }
        // Minestom 不记录客户端的格子状态，处理之后重新发送全部内容；窗口 0 的点击已被取消，Minestom 会自己重新发送
        if (menu.gridInventory instanceof Inventory table) {
            menu.player.getInventory().update();
            table.update(menu.player);
        }
    }

    private void fire(MenuSlot slot, ClickType type, ItemStack item, ItemStack cursor) {
        EventDispatcher.call(new InventoryClickEvent(slot.inventory, menu.player, slot.index, type, item, cursor));
    }
}
