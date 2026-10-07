package io.ib67.yuanban.crafting.internal.menu;

import io.ib67.yuanban.crafting.recipe.CraftingRecipe;
import io.ib67.yuanban.crafting.recipe.RecipeHolder;
import io.ib67.yuanban.crafting.recipe.RecipeManager;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryItemChangeEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.inventory.click.ClickType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.client.play.ClientPlaceRecipePacket;
import net.minestom.server.network.packet.server.play.PlaceGhostRecipePacket;
import net.minestom.server.tag.Tag;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 合成模块的事件监听：打开工作台、检查工作台是否有效、背包 2×2 合成格的点击和结果计算、关闭背包、离线、配方书放置、
 * 玩家首次出生时发送配方数据。
 */
@ApiStatus.Internal
public final class CraftingEvents {
    private static final Tag<PlayerCraftingMenu> INVENTORY_MENU = Tag.Transient("yuanban:crafting_inventory_menu");

    private final RecipeManager recipes;

    private CraftingEvents(RecipeManager recipes) {
        this.recipes = recipes;
    }

    public static EventNode<Event> create(RecipeManager recipes) {
        var events = new CraftingEvents(recipes);
        var node = EventNode.all("yuanban:crafting");
        node.addListener(PlayerSpawnEvent.class, events::onSpawn);
        node.addListener(PlayerBlockInteractEvent.class, events::onBlockInteract);
        node.addListener(PlayerTickEvent.class, events::onTick);
        node.addListener(InventoryPreClickEvent.class, events::onPreClick);
        node.addListener(InventoryItemChangeEvent.class, events::onItemChange);
        node.addListener(InventoryCloseEvent.class, events::onClose);
        node.addListener(PlayerDisconnectEvent.class, events::onDisconnect);
        node.addListener(PlayerPacketEvent.class, events::onPacket);
        return node;
    }

    /**
     * 背包菜单（窗口 0）的状态，每个玩家一份。
     */
    private PlayerCraftingMenu inventoryMenu(Player player) {
        var menu = player.getTag(INVENTORY_MENU);
        if (menu == null) {
            menu = new PlayerCraftingMenu(player, recipes);
            player.setTag(INVENTORY_MENU, menu);
        }
        return menu;
    }

    /**
     * Minestom 在 {@code Player.UNSAFE_init} 中发送自己（空的）{@code RecipeManager} 的数据，早于出生事件，这里的数据包会覆盖它们。
     */
    private void onSpawn(PlayerSpawnEvent event) {
        if (event.isFirstSpawn()) {
            recipes.sendRecipes(event.getPlayer());
        }
    }

    /**
     * 原版 {@code ServerPlayerGameMode.useItemOn} 与 {@code CraftingTableBlock.useWithoutItem}。
     */
    private void onBlockInteract(PlayerBlockInteractEvent event) {
        if (!event.getBlock().compare(Block.CRAFTING_TABLE)) {
            return;
        }
        var player = event.getPlayer();
        if (player.getGameMode() != GameMode.SPECTATOR) {
            boolean holdingItems = !player.getItemInMainHand().isAir() || !player.getItemInOffHand().isAir();
            if (player.isSneaking() && holdingItems || event.getHand() != PlayerHand.MAIN) {
                return;
            }
        }
        event.setBlockingItemUse(true);
        player.openInventory(new CraftingTableInventory(player, event.getInstance(), event.getBlockPosition(), recipes));
        if (player.getGameMode() != GameMode.SPECTATOR) {
            CraftingStats.interactWithCraftingTable(player);
        }
    }

    /**
     * 原版 {@code ServerPlayer.tick}：菜单失效时关闭。
     */
    private void onTick(PlayerTickEvent event) {
        var player = event.getPlayer();
        if (player.getOpenInventory() instanceof CraftingTableInventory table && !table.stillValid()) {
            player.closeInventory();
        }
    }

    /**
     * 背包（窗口 0）中涉及合成格的点击取消之后按原版处理，Minestom 随后重新发送玩家背包。
     */
    private void onPreClick(InventoryPreClickEvent event) {
        var player = event.getPlayer();
        if (!(event.getInventory() instanceof PlayerInventory inventory) || inventory != player.getInventory() || player.getOpenInventory() != null) {
            return;
        }
        var menu = inventoryMenu(player);
        var handled = intercept(menu, event.getClick());
        if (handled == null) {
            return;
        }
        event.setCancelled(true);
        // 原版 handleContainerClick：旁观模式的点击不处理
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        new ClickEvents(menu).run(handled.type(), handled.clickedSlots(), handled.action());
    }

    private record Intercepted(@Nullable ClickType type, @Nullable List<Integer> clickedSlots, Runnable action) {
    }

    private static @Nullable Intercepted intercept(PlayerCraftingMenu menu, Click click) {
        final int result = PlayerInventoryUtils.CRAFT_RESULT;
        return switch (click) {
            case Click.Left(int slot) when slot == result -> new Intercepted(ClickType.LEFT_CLICK, List.of(0), () -> menu.pickup(0, true));
            case Click.Right(int slot) when slot == result -> new Intercepted(ClickType.RIGHT_CLICK, List.of(0), () -> menu.pickup(0, false));
            case Click.Middle(int slot) when slot == result -> new Intercepted(null, List.of(), () -> menu.cloneSlot(0));
            case Click.LeftShift(int slot) when slot == result || isGrid(slot) ->
                    new Intercepted(ClickType.SHIFT_CLICK, null, () -> menu.quickMove(PlayerCraftingMenu.menuIndex(slot)));
            case Click.RightShift(int slot) when slot == result || isGrid(slot) ->
                    new Intercepted(ClickType.SHIFT_CLICK, null, () -> menu.quickMove(PlayerCraftingMenu.menuIndex(slot)));
            case Click.Double(int slot) when slot == result || gridCanSupply(menu) ->
                    new Intercepted(ClickType.DOUBLE_CLICK, null, () -> menu.pickupAll(PlayerCraftingMenu.menuIndex(slot), true));
            case Click.DropSlot(int slot, boolean all) when slot == result -> new Intercepted(ClickType.DROP, List.of(0), () -> menu.throwSlot(0, all));
            case Click.HotbarSwap(int hotbarSlot, int slot) when slot == result ->
                    new Intercepted(ClickType.CHANGE_HELD, List.of(0), () -> menu.swap(0, hotbarSlot));
            case Click.OffhandSwap(int slot) when slot == result ->
                    new Intercepted(ClickType.CHANGE_HELD, List.of(0), () -> menu.swap(0, PlayerInventoryUtils.OFFHAND_SLOT));
            case Click.LeftDrag(List<Integer> slots) when slots.contains(result) -> drag(menu, slots, 0, ClickType.LEFT_DRAGGING);
            case Click.RightDrag(List<Integer> slots) when slots.contains(result) -> drag(menu, slots, 1, ClickType.RIGHT_DRAGGING);
            case Click.MiddleDrag(List<Integer> slots) when slots.contains(result) -> drag(menu, slots, 2, null);
            default -> null;
        };
    }

    private static Intercepted drag(PlayerCraftingMenu menu, List<Integer> slots, int type, @Nullable ClickType clickType) {
        var indices = slots.stream().map(PlayerCraftingMenu::menuIndex).toList();
        return new Intercepted(clickType, null, () -> menu.quickcraft(indices, type));
    }

    private static boolean isGrid(int slot) {
        return slot >= PlayerInventoryUtils.CRAFT_SLOT_1 && slot <= PlayerInventoryUtils.CRAFT_SLOT_4;
    }

    /**
     * 原版双击会从合成格收集物品，Minestom 不会；合成格中有可以收集的物品时由本模块处理。
     */
    private static boolean gridCanSupply(PlayerCraftingMenu menu) {
        var carried = menu.getCarried();
        if (carried.isAir()) {
            return false;
        }
        for (var slot : menu.inputGridSlots()) {
            if (slot.hasItem() && CraftingMenu.canItemQuickReplace(slot, carried, true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 背包合成格变化之后重新计算结果，对应原版 {@code InventoryMenu.slotsChanged}。
     */
    private void onItemChange(InventoryItemChangeEvent event) {
        if (!(event.getInventory() instanceof PlayerInventory inventory) || !isGrid(event.getSlot())) {
            return;
        }
        for (var viewer : inventory.getViewers()) {
            if (viewer.getInventory() == inventory) {
                inventoryMenu(viewer).gridChanged();
                return;
            }
        }
    }

    /**
     * 原版 {@code InventoryMenu.removed}：关闭背包时光标物品放回背包，清空结果格，合成格的物品逐个放回。
     */
    private void onClose(InventoryCloseEvent event) {
        var player = event.getPlayer();
        if (event.getInventory() != player.getInventory() || player.getOpenInventory() != null) {
            return;
        }
        var menu = inventoryMenu(player);
        // FIXME(Minestom M16): 关闭窗口 0 时 Minestom 不处理光标物品，原版放回背包，见 MINESTOM-BUG-REPORT.md
        menu.removeCarried(false);
        menu.withoutGridUpdates(() -> player.getInventory().setItemStack(PlayerInventoryUtils.CRAFT_RESULT, ItemStack.AIR));
        menu.clearGrid(false);
    }

    /**
     * 原版 {@code Player.remove}：先处理背包菜单，再关闭打开的菜单；玩家离线，物品全部丢出。
     * 光标物品属于当前打开的菜单。Minestom 之后调用打开的背包的 {@code removeViewer}，那时格子已经为空。
     */
    private void onDisconnect(PlayerDisconnectEvent event) {
        var player = event.getPlayer();
        var menu = inventoryMenu(player);
        var open = player.getOpenInventory();
        if (open == null) {
            menu.removeCarried(true);
        }
        menu.withoutGridUpdates(() -> player.getInventory().setItemStack(PlayerInventoryUtils.CRAFT_RESULT, ItemStack.AIR));
        menu.clearGrid(true);
        if (open instanceof CraftingTableInventory table) {
            table.dropContents();
        }
    }

    private void onPacket(PlayerPacketEvent event) {
        if (event.getPacket() instanceof ClientPlaceRecipePacket packet) {
            // Minestom 的 RecipeListener 查询的是它自己的 RecipeManager，本模块不使用它
            event.setCancelled(true);
            placeRecipe(event.getPlayer(), packet);
        }
    }

    /**
     * 原版 {@code ServerGamePacketListenerImpl.handlePlaceRecipe}。原版客户端只对合成配方发送放置请求；
     * 原版服务端收到非合成配方时会抛出 {@code ClassCastException}，这里忽略。
     */
    @SuppressWarnings("unchecked")
    private void placeRecipe(Player player, ClientPlaceRecipePacket packet) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        var open = player.getOpenInventory();
        CraftingMenu menu;
        if (open instanceof CraftingTableInventory table && table.getWindowId() == packet.windowId()) {
            if (!table.stillValid()) {
                return;
            }
            menu = table.menu();
        } else if (open == null && packet.windowId() == 0) {
            menu = inventoryMenu(player);
        } else {
            return;
        }
        var info = recipes.displayInfo(packet.recipeDisplayId()).orElse(null);
        if (info == null || !(info.parent().value() instanceof CraftingRecipe recipe) || recipe.placementInfo().isImpossibleToPlace()) {
            return;
        }
        var action = RecipePlacer.placeRecipe(menu, (RecipeHolder<CraftingRecipe>) info.parent(), packet.makeAll(),
                player.getGameMode() == GameMode.CREATIVE);
        if (action == RecipePlacer.PostPlaceAction.PLACE_GHOST_RECIPE) {
            player.sendPacket(new PlaceGhostRecipePacket(packet.windowId(), info.display()));
        }
        // Minestom 不记录客户端的格子状态，放置之后重新发送全部内容
        player.getInventory().update();
        if (open instanceof CraftingTableInventory table) {
            table.update(player);
        }
    }
}
