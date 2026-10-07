package io.ib67.yuanban.crafting;

import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static io.ib67.yuanban.crafting.CraftingTableTest.crafted;
import static io.ib67.yuanban.crafting.CraftingTableTest.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 玩家背包的 2×2 合成格（窗口 0），全部通过客户端数据包操作。窗口格子编号：0 结果、1–4 合成格、5–8 盔甲、9–35 背包、
 * 36–44 快捷栏、45 副手；对应 Minestom 玩家背包的 36、37–40、41–44、9–35、0–8、45 号格。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class InventoryCraftingTest {
    private static final int RESULT = 36;
    private static final ItemStack AIR = ItemStack.AIR;

    /**
     * Minestom 处理合成格上的普通点击，合成格变化之后本模块重新计算结果；取走结果时消耗原料。
     */
    @Test
    void gridClicksUpdateResult(Env env) {
        var setup = setup(env);
        var inventory = setup.player().getInventory();
        var client = setup.client();
        inventory.setItemStack(0, ItemStack.of(Material.OAK_PLANKS, 2));
        client.leftClick(0, 36);
        client.rightClick(0, 1);
        client.rightClick(0, 3);
        assertEquals(ItemStack.of(Material.STICK, 4), inventory.getItemStack(RESULT));

        client.leftClick(0, 0);
        assertEquals(ItemStack.of(Material.STICK, 4), inventory.getCursorItem());
        assertEquals(AIR, inventory.getItemStack(37));
        assertEquals(AIR, inventory.getItemStack(39));
        assertEquals(AIR, inventory.getItemStack(RESULT));
        assertEquals(4, crafted(setup.player(), Material.STICK));
    }

    /**
     * 原版 {@code InventoryMenu.quickMoveStack}：按住 Shift 点击结果格合成到原料用完，从快捷栏最右侧开始放入。
     */
    @Test
    void shiftClickResultCraftsAll(Env env) {
        var setup = setup(env);
        var inventory = setup.player().getInventory();
        inventory.setItemStack(37, ItemStack.of(Material.OAK_PLANKS, 2));
        inventory.setItemStack(39, ItemStack.of(Material.OAK_PLANKS, 2));
        setup.client().shiftClick(0, 0);
        assertEquals(ItemStack.of(Material.STICK, 8), inventory.getItemStack(8));
        assertEquals(AIR, inventory.getItemStack(37));
        assertEquals(AIR, inventory.getItemStack(RESULT));
    }

    /**
     * 原版 {@code moveItemStackTo}：先在背包和快捷栏中合并，再找空格。快捷栏中的同种物品优先于背包中的空格。
     */
    @Test
    void shiftClickGridMergesFirst(Env env) {
        var setup = setup(env);
        var inventory = setup.player().getInventory();
        inventory.setItemStack(3, ItemStack.of(Material.OAK_PLANKS, 10));
        inventory.setItemStack(37, ItemStack.of(Material.OAK_PLANKS, 3));
        setup.client().shiftClick(0, 1);
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 13), inventory.getItemStack(3));
        assertEquals(AIR, inventory.getItemStack(9));
        assertEquals(AIR, inventory.getItemStack(37));
    }

    @Test
    void dragSkipsResult(Env env) {
        var setup = setup(env);
        var inventory = setup.player().getInventory();
        inventory.setCursorItem(ItemStack.of(Material.DIRT, 4));
        setup.client().drag(0, 0, 0, 1, 2);
        assertEquals(ItemStack.of(Material.DIRT, 2), inventory.getItemStack(37));
        assertEquals(ItemStack.of(Material.DIRT, 2), inventory.getItemStack(38));
        assertEquals(AIR, inventory.getCursorItem());
        assertEquals(AIR, inventory.getItemStack(RESULT));
    }

    /**
     * 原版双击按菜单顺序收集，合成格在背包之前。
     */
    @Test
    void doubleClickCollectsFromGrid(Env env) {
        var setup = setup(env);
        var inventory = setup.player().getInventory();
        inventory.setItemStack(37, ItemStack.of(Material.STICK, 3));
        inventory.setCursorItem(ItemStack.of(Material.STICK, 1));
        setup.client().doubleClick(0, 9);
        assertEquals(ItemStack.of(Material.STICK, 4), inventory.getCursorItem());
        assertEquals(AIR, inventory.getItemStack(37));
    }

    @Test
    void resultRejectsItems(Env env) {
        var setup = setup(env);
        var inventory = setup.player().getInventory();
        inventory.setCursorItem(ItemStack.of(Material.DIRT));
        setup.client().leftClick(0, 0);
        assertEquals(ItemStack.of(Material.DIRT), inventory.getCursorItem());
        assertEquals(AIR, inventory.getItemStack(RESULT));
    }

    /**
     * 原版 {@code InventoryMenu.removed}：关闭背包时光标物品和合成格的物品放回背包，结果格清空。
     */
    @Test
    void closingReturnsItems(Env env) {
        var setup = setup(env);
        var inventory = setup.player().getInventory();
        inventory.setItemStack(37, ItemStack.of(Material.OAK_PLANKS, 2));
        inventory.setItemStack(39, ItemStack.of(Material.OAK_PLANKS, 1));
        inventory.setCursorItem(ItemStack.of(Material.DIRT, 3));
        setup.client().close(0);
        assertEquals(AIR, inventory.getCursorItem());
        assertEquals(ItemStack.of(Material.DIRT, 3), inventory.getItemStack(0));
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 3), inventory.getItemStack(1));
        assertEquals(AIR, inventory.getItemStack(37));
        assertEquals(AIR, inventory.getItemStack(RESULT));
    }

    @Test
    void disconnectDropsGrid(Env env) {
        var setup = setup(env);
        var inventory = setup.player().getInventory();
        inventory.setItemStack(37, ItemStack.of(Material.OAK_PLANKS, 2));
        inventory.setCursorItem(ItemStack.of(Material.DIRT, 3));
        var drops = env.trackEvent(ItemDropEvent.class, EventFilter.PLAYER, setup.player());
        setup.player().remove();
        assertEquals(List.of(ItemStack.of(Material.DIRT, 3), ItemStack.of(Material.OAK_PLANKS, 2)),
                drops.collect().stream().map(ItemDropEvent::getItemStack).toList());
        assertEquals(AIR, inventory.getItemStack(37));
        assertEquals(AIR, inventory.getItemStack(RESULT));
    }
}
