package io.ib67.yuanban.crafting;

import io.ib67.yuanban.crafting.testing.InMemoryMaps;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.statistic.StatisticCategory;
import net.minestom.server.statistic.StatisticType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 工作台：打开、点击、关闭和离线，全部通过客户端数据包操作。窗口格子编号：0 结果、1–9 合成格、10–36 背包、37–45 快捷栏。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class CraftingTableTest {
    static final BlockVec TABLE = new BlockVec(2, 40, 0);
    private static final ItemStack AIR = ItemStack.AIR;

    record Setup(Crafting crafting, Instance instance, TestConnection connection, Player player, Client client) {
    }

    static Setup setup(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        instance.setBlock(TABLE, Block.CRAFTING_TABLE);
        var connection = env.createConnection();
        var player = connection.connect(instance, new Pos(0.5, 40, 0.5));
        return new Setup(crafting, instance, connection, player, new Client(player));
    }

    static AbstractInventory open(Setup setup) {
        setup.client().useBlock(TABLE, PlayerHand.MAIN);
        return assertInstanceOf(AbstractInventory.class, setup.player().getOpenInventory());
    }

    /**
     * 玩家背包格子（Minestom 编号）在工作台窗口中的编号。
     */
    static int windowSlot(int playerSlot) {
        return playerSlot < 9 ? 37 + playerSlot : playerSlot + 1;
    }

    static int statistic(Player player, StatisticCategory category, int id) {
        for (var entry : player.getStatisticValueMap().entrySet()) {
            if (entry.getKey().getCategory() == category && entry.getKey().getStatisticId() == id) {
                return entry.getValue();
            }
        }
        return 0;
    }

    static int crafted(Player player, Material material) {
        return statistic(player, StatisticCategory.CRAFTED, material.id());
    }

    private static void sticksInGrid(AbstractInventory table, int planksPerSlot) {
        table.setItemStack(1, ItemStack.of(Material.OAK_PLANKS, planksPerSlot));
        table.setItemStack(4, ItemStack.of(Material.OAK_PLANKS, planksPerSlot));
    }

    /**
     * 原版 {@code ServerPlayerGameMode.useItemOn}：主手交互打开工作台并增加统计；潜行并且手里有物品、副手交互时不打开。
     */
    @Test
    void opensWithMainHand(Env env) {
        var setup = setup(env);
        var player = setup.player();
        player.setSneaking(true);
        player.setItemInMainHand(ItemStack.of(Material.STONE));
        setup.client().useBlock(TABLE, PlayerHand.MAIN);
        assertNull(player.getOpenInventory());
        player.setSneaking(false);
        setup.client().useBlock(TABLE, PlayerHand.OFF);
        assertNull(player.getOpenInventory());

        var table = assertInstanceOf(Inventory.class, open(setup));
        assertEquals(InventoryType.CRAFTING, table.getInventoryType());
        assertEquals(1, statistic(player, StatisticCategory.CUSTOM, StatisticType.INTERACT_WITH_CRAFTING_TABLE.id()));
    }

    @Test
    void spectatorOpensWithoutStatistic(Env env) {
        var setup = setup(env);
        setup.player().setGameMode(GameMode.SPECTATOR);
        setup.player().setItemInMainHand(ItemStack.of(Material.STONE));
        setup.player().setSneaking(true);
        open(setup);
        assertEquals(0, statistic(setup.player(), StatisticCategory.CUSTOM, StatisticType.INTERACT_WITH_CRAFTING_TABLE.id()));
    }

    /**
     * 放入原料后出现结果；单击结果格取走整组，每格原料减一，并按取走的数量增加统计。
     */
    @Test
    void takingResultConsumesIngredients(Env env) {
        var setup = setup(env);
        var player = setup.player();
        player.getInventory().setItemStack(0, ItemStack.of(Material.OAK_PLANKS, 3));
        var table = open(setup);
        int window = table.getWindowId();
        var client = setup.client();
        client.leftClick(window, windowSlot(0));
        client.rightClick(window, 1);
        client.rightClick(window, 4);
        assertEquals(ItemStack.of(Material.STICK, 4), table.getItemStack(0));
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 1), player.getInventory().getCursorItem());

        // 光标中的木板与结果不同，结果格不能放入，什么也不发生
        client.rightClick(window, 0);
        assertEquals(ItemStack.of(Material.STICK, 4), table.getItemStack(0));
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 1), player.getInventory().getCursorItem());
        client.leftClick(window, windowSlot(0));

        client.leftClick(window, 0);
        assertEquals(ItemStack.of(Material.STICK, 4), player.getInventory().getCursorItem());
        assertEquals(AIR, table.getItemStack(1));
        assertEquals(AIR, table.getItemStack(4));
        assertEquals(AIR, table.getItemStack(0));
        assertEquals(4, crafted(player, Material.STICK));
    }

    /**
     * 原版 {@code CraftingMenu.quickMoveStack}：按住 Shift 点击结果格合成到原料用完，结果从快捷栏最右侧开始放入。
     */
    @Test
    void shiftClickResultCraftsAll(Env env) {
        var setup = setup(env);
        var table = open(setup);
        sticksInGrid(table, 3);
        setup.client().shiftClick(table.getWindowId(), 0);
        assertEquals(ItemStack.of(Material.STICK, 12), setup.player().getInventory().getItemStack(8));
        assertEquals(AIR, table.getItemStack(1));
        assertEquals(AIR, table.getItemStack(0));
        assertEquals(12, crafted(setup.player(), Material.STICK));
    }

    /**
     * 原版 {@code CraftingTableBlock} 的配方中牛奶桶留下桶。
     */
    @Test
    void remaindersStayInGrid(Env env) {
        var setup = setup(env);
        var table = open(setup);
        for (int slot = 1; slot <= 3; slot++) {
            table.setItemStack(slot, ItemStack.of(Material.MILK_BUCKET));
        }
        table.setItemStack(4, ItemStack.of(Material.SUGAR));
        table.setItemStack(5, ItemStack.of(Material.EGG));
        table.setItemStack(6, ItemStack.of(Material.SUGAR));
        for (int slot = 7; slot <= 9; slot++) {
            table.setItemStack(slot, ItemStack.of(Material.WHEAT));
        }
        assertEquals(ItemStack.of(Material.CAKE), table.getItemStack(0));
        setup.client().leftClick(table.getWindowId(), 0);
        assertEquals(ItemStack.of(Material.CAKE), setup.player().getInventory().getCursorItem());
        for (int slot = 1; slot <= 3; slot++) {
            assertEquals(ItemStack.of(Material.BUCKET), table.getItemStack(slot));
        }
        for (int slot = 4; slot <= 9; slot++) {
            assertEquals(AIR, table.getItemStack(slot));
        }
    }

    /**
     * 原版 {@code doClick} 的拖动：结果格不能放入，被排除；剩下的两格平分光标物品。
     */
    @Test
    void dragSkipsResult(Env env) {
        var setup = setup(env);
        var table = open(setup);
        setup.player().getInventory().setCursorItem(ItemStack.of(Material.DIRT, 5));
        setup.client().drag(table.getWindowId(), 0, 0, 2, 3);
        assertEquals(ItemStack.of(Material.DIRT, 2), table.getItemStack(2));
        assertEquals(ItemStack.of(Material.DIRT, 2), table.getItemStack(3));
        assertEquals(ItemStack.of(Material.DIRT, 1), setup.player().getInventory().getCursorItem());
        assertEquals(AIR, table.getItemStack(0));
    }

    /**
     * 原版 {@code CraftingMenu.canTakeItemForPickAll}：双击不从结果格取物。
     */
    @Test
    void doubleClickSkipsResult(Env env) {
        var setup = setup(env);
        var table = open(setup);
        sticksInGrid(table, 1);
        setup.player().getInventory().setItemStack(9, ItemStack.of(Material.STICK, 3));
        setup.player().getInventory().setCursorItem(ItemStack.of(Material.STICK, 1));
        setup.client().doubleClick(table.getWindowId(), 7);
        assertEquals(ItemStack.of(Material.STICK, 4), setup.player().getInventory().getCursorItem());
        assertEquals(ItemStack.of(Material.STICK, 4), table.getItemStack(0));
        assertEquals(AIR, setup.player().getInventory().getItemStack(9));
    }

    /**
     * 原版 {@code doClick} 的 {@code THROW}：丢出结果格时取走整组；统计按请求的数量（1）增加。
     */
    @Test
    void throwingResultDropsWholeStack(Env env) {
        var setup = setup(env);
        var table = open(setup);
        sticksInGrid(table, 1);
        var drops = env.trackEvent(ItemDropEvent.class, EventFilter.PLAYER, setup.player());
        setup.client().drop(table.getWindowId(), 0, false);
        assertEquals(List.of(ItemStack.of(Material.STICK, 4)), drops.collect().stream().map(ItemDropEvent::getItemStack).toList());
        assertEquals(AIR, table.getItemStack(1));
        assertEquals(1, crafted(setup.player(), Material.STICK));
    }

    @Test
    void swappingResultIntoHotbar(Env env) {
        var setup = setup(env);
        var table = open(setup);
        sticksInGrid(table, 1);
        setup.client().swap(table.getWindowId(), 0, 2);
        assertEquals(ItemStack.of(Material.STICK, 4), setup.player().getInventory().getItemStack(2));
        assertEquals(AIR, table.getItemStack(4));
        assertEquals(4, crafted(setup.player(), Material.STICK));
    }

    /**
     * 原版 {@code CraftingMenu.quickMoveStack}：背包中的物品先移入合成格。
     */
    @Test
    void shiftClickInventoryFillsGrid(Env env) {
        var setup = setup(env);
        var table = open(setup);
        setup.player().getInventory().setItemStack(9, ItemStack.of(Material.OAK_PLANKS, 5));
        setup.client().shiftClick(table.getWindowId(), windowSlot(9));
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 5), table.getItemStack(1));
        assertEquals(AIR, setup.player().getInventory().getItemStack(9));
        assertEquals(ItemStack.of(Material.OAK_BUTTON), table.getItemStack(0));
    }

    /**
     * 原版 {@code CraftingMenu.removed}：关闭时光标物品和合成格的物品按 {@code placeItemBackInInventory} 放回背包。
     */
    @Test
    void closingReturnsItems(Env env) {
        var setup = setup(env);
        var table = open(setup);
        var inventory = setup.player().getInventory();
        table.setItemStack(1, ItemStack.of(Material.OAK_PLANKS, 3));
        table.setItemStack(5, ItemStack.of(Material.OAK_PLANKS, 2));
        inventory.setCursorItem(ItemStack.of(Material.STICK, 2));
        setup.client().close(table.getWindowId());
        assertNull(setup.player().getOpenInventory());
        assertEquals(AIR, inventory.getCursorItem());
        assertEquals(ItemStack.of(Material.STICK, 2), inventory.getItemStack(0));
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 5), inventory.getItemStack(1));
    }

    /**
     * 原版 {@code CraftingMenu.stillValid}：方块不再是工作台，或者玩家走远之后，下一刻关闭。
     */
    @Test
    void closesWhenNoLongerValid(Env env) {
        var setup = setup(env);
        var table = open(setup);
        table.setItemStack(1, ItemStack.of(Material.OAK_PLANKS, 3));
        setup.instance().setBlock(TABLE, Block.AIR);
        env.tick();
        assertNull(setup.player().getOpenInventory());
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 3), setup.player().getInventory().getItemStack(0));

        setup.instance().setBlock(TABLE, Block.CRAFTING_TABLE);
        open(setup);
        env.tick();
        assertInstanceOf(AbstractInventory.class, setup.player().getOpenInventory());
        setup.player().teleport(new Pos(20.5, 40, 0.5)).join();
        env.tick();
        assertNull(setup.player().getOpenInventory());
    }

    /**
     * 原版 {@code Player.remove}：离线时光标物品和合成格的物品丢出。
     */
    @Test
    void disconnectDropsItems(Env env) {
        var setup = setup(env);
        var table = open(setup);
        table.setItemStack(1, ItemStack.of(Material.OAK_PLANKS, 3));
        setup.player().getInventory().setCursorItem(ItemStack.of(Material.STICK, 2));
        var drops = env.trackEvent(ItemDropEvent.class, EventFilter.PLAYER, setup.player());
        setup.player().remove();
        assertEquals(List.of(ItemStack.of(Material.STICK, 2), ItemStack.of(Material.OAK_PLANKS, 3)),
                drops.collect().stream().map(ItemDropEvent::getItemStack).toList());
        assertEquals(AIR, setup.player().getInventory().getItemStack(0));
    }
}
