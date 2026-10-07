package io.ib67.yuanban.crafting;

import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.PlaceGhostRecipePacket;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.ib67.yuanban.crafting.CraftingTableTest.open;
import static io.ib67.yuanban.crafting.CraftingTableTest.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 配方书的放置请求（原版 {@code ServerPlaceRecipe} 与 {@code PlaceRecipeHelper}），通过客户端数据包操作。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class RecipePlacementTest {
    private static final ItemStack AIR = ItemStack.AIR;

    static int displayId(Crafting crafting, Key recipe) {
        for (int id = 0; ; id++) {
            var info = crafting.recipes().displayInfo(id).orElseThrow(() -> new AssertionError("no display for " + recipe));
            if (info.parent().id().equals(recipe)) {
                return id;
            }
        }
    }

    /**
     * 木棍的图案只有一列，比 3×3 合成格窄一半以上，居中放在第 2 列，即工作台的 2 号格和 5 号格。
     */
    @Test
    void placesShapedRecipeCentered(Env env) {
        var setup = setup(env);
        var table = open(setup);
        var inventory = setup.player().getInventory();
        inventory.setItemStack(9, ItemStack.of(Material.OAK_PLANKS, 10));
        int stick = displayId(setup.crafting(), Key.key("stick"));

        setup.client().placeRecipe(table.getWindowId(), stick, false);
        assertEquals(ItemStack.of(Material.OAK_PLANKS), table.getItemStack(2));
        assertEquals(ItemStack.of(Material.OAK_PLANKS), table.getItemStack(5));
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 8), inventory.getItemStack(9));
        assertEquals(ItemStack.of(Material.STICK, 4), table.getItemStack(0));

        // 已经摆好这个配方时，在最少的一格基础上多放一份
        setup.client().placeRecipe(table.getWindowId(), stick, false);
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 2), table.getItemStack(2));
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 2), table.getItemStack(5));
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 6), inventory.getItemStack(9));
    }

    /**
     * 全部放置：64 个木板最多合成 32 份，每格放 32 个。
     */
    @Test
    void makeAllPlacesBiggestCraftableStack(Env env) {
        var setup = setup(env);
        var table = open(setup);
        var inventory = setup.player().getInventory();
        inventory.setItemStack(9, ItemStack.of(Material.OAK_PLANKS, 64));
        setup.client().placeRecipe(table.getWindowId(), displayId(setup.crafting(), Key.key("stick")), true);
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 32), table.getItemStack(2));
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 32), table.getItemStack(5));
        assertEquals(AIR, inventory.getItemStack(9));
    }

    /**
     * 材料不足时清空合成格（物品放回背包）并回复虚影配方。
     */
    @Test
    void missingMaterialsPlaceGhostRecipe(Env env) {
        var setup = setup(env);
        var table = open(setup);
        table.setItemStack(1, ItemStack.of(Material.DIRT, 3));
        int stick = displayId(setup.crafting(), Key.key("stick"));
        var ghosts = setup.connection().trackIncoming(PlaceGhostRecipePacket.class);
        setup.client().placeRecipe(table.getWindowId(), stick, false);
        assertEquals(AIR, table.getItemStack(1));
        assertEquals(ItemStack.of(Material.DIRT, 3), setup.player().getInventory().getItemStack(0));
        ghosts.assertSingle(packet -> {
            assertEquals(table.getWindowId(), packet.windowId());
            assertEquals(setup.crafting().recipes().displayInfo(stick).orElseThrow().display(), packet.recipe());
        });
    }

    /**
     * 原版 {@code ServerPlaceRecipe.testClearGrid}：合成格的物品放不回背包时不处理（非创造模式）。
     */
    @Test
    void fullInventoryKeepsGrid(Env env) {
        var setup = setup(env);
        var table = open(setup);
        var inventory = setup.player().getInventory();
        for (int slot = 0; slot < 36; slot++) {
            inventory.setItemStack(slot, ItemStack.of(Material.DIRT, 64));
        }
        table.setItemStack(1, ItemStack.of(Material.STONE));
        setup.client().placeRecipe(table.getWindowId(), displayId(setup.crafting(), Key.key("stick")), false);
        assertEquals(ItemStack.of(Material.STONE), table.getItemStack(1));
    }

    /**
     * 背包的 2×2 合成格（窗口 0）。
     */
    @Test
    void placesIntoInventoryGrid(Env env) {
        var setup = setup(env);
        var inventory = setup.player().getInventory();
        inventory.setItemStack(9, ItemStack.of(Material.OAK_PLANKS, 4));
        setup.client().placeRecipe(0, displayId(setup.crafting(), Key.key("crafting_table")), false);
        for (int slot = 37; slot <= 40; slot++) {
            assertEquals(ItemStack.of(Material.OAK_PLANKS), inventory.getItemStack(slot));
        }
        assertEquals(ItemStack.of(Material.CRAFTING_TABLE), inventory.getItemStack(36));
        assertEquals(AIR, inventory.getItemStack(9));
    }
}
