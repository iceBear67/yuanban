package io.ib67.yuanban.crafting.internal.menu;

import io.ib67.yuanban.crafting.recipe.RecipeManager;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.click.ClickType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;

import java.util.List;

/**
 * 一个玩家打开的工作台界面，对应原版为每次打开新建的 {@code CraftingMenu}。0 号格是结果格，1–9 号格是合成格。
 * <p>
 * 结果格上的点击、按住 Shift 的点击、双击、经过结果格的拖动由本模块按原版处理；其他格子上的单击、交换、丢弃、中键点击
 * 交给 Minestom。合成格变化之后重新计算结果。
 */
public final class CraftingTableInventory extends Inventory {
    private static final double INTERACTION_RANGE_BUFFER = 4.0;

    private final Player owner;
    private final Instance instance;
    private final BlockVec position;
    private final TableMenu menu;
    private final ClickEvents clickEvents;

    CraftingTableInventory(Player owner, Instance instance, BlockVec position, RecipeManager recipes) {
        super(InventoryType.CRAFTING, Component.translatable("container.crafting"));
        this.owner = owner;
        this.instance = instance;
        this.position = position;
        this.menu = new TableMenu(owner, recipes, this);
        this.clickEvents = new ClickEvents(menu);
    }

    TableMenu menu() {
        return menu;
    }

    /**
     * 原版 {@code CraftingMenu.stillValid}：方块仍是工作台，并且玩家的眼睛到方块的距离小于方块交互距离加 4。
     * 区块没有加载时视为不是工作台。
     */
    boolean stillValid() {
        if (!instance.isChunkLoaded(position) || !instance.getBlock(position).compare(Block.CRAFTING_TABLE)) {
            return false;
        }
        double maxRange = owner.getAttributeValue(Attribute.BLOCK_INTERACTION_RANGE) + INTERACTION_RANGE_BUFFER;
        var eye = owner.getPosition().add(0, owner.getEyeHeight(), 0);
        double dx = Math.max(Math.max(position.x() - eye.x(), eye.x() - (position.x() + 1)), 0);
        double dy = Math.max(Math.max(position.y() - eye.y(), eye.y() - (position.y() + 1)), 0);
        double dz = Math.max(Math.max(position.z() - eye.z(), eye.z() - (position.z() + 1)), 0);
        return dx * dx + dy * dy + dz * dz < maxRange * maxRange;
    }

    /**
     * 原版 {@code handleContainerClick}：旁观模式和失效的菜单不处理点击。
     */
    private boolean accepts(Player player) {
        return player == owner && player.getGameMode() != GameMode.SPECTATOR && stillValid();
    }

    /**
     * Minestom 的点击格子编号对应的菜单序号：0–9 是这个背包，之后是玩家背包的 0–35 号格。
     */
    private int menuIndex(int slot) {
        if (slot < getSize()) {
            return slot;
        }
        int playerSlot = slot - getSize();
        return playerSlot < 9 ? TableMenu.INVENTORY_END + playerSlot : playerSlot + 1;
    }

    @Override
    public boolean leftClick(Player player, int slot) {
        if (!accepts(player)) {
            return false;
        }
        if (slot != menu.resultIndex) {
            return super.leftClick(player, slot);
        }
        clickEvents.run(ClickType.LEFT_CLICK, List.of(0), () -> menu.pickup(0, true));
        return true;
    }

    @Override
    public boolean rightClick(Player player, int slot) {
        if (!accepts(player)) {
            return false;
        }
        if (slot != menu.resultIndex) {
            return super.rightClick(player, slot);
        }
        clickEvents.run(ClickType.RIGHT_CLICK, List.of(0), () -> menu.pickup(0, false));
        return true;
    }

    @Override
    public boolean shiftClick(Player player, int slot, int button) {
        if (!accepts(player)) {
            return false;
        }
        int index = menuIndex(slot);
        clickEvents.run(ClickType.SHIFT_CLICK, null, () -> menu.quickMove(index));
        return true;
    }

    @Override
    public boolean changeHeld(Player player, int slot, int key) {
        if (!accepts(player)) {
            return false;
        }
        if (slot != menu.resultIndex) {
            return super.changeHeld(player, slot, key);
        }
        int inventorySlot = key == 40 || key == PlayerInventoryUtils.OFFHAND_SLOT ? PlayerInventoryUtils.OFFHAND_SLOT : key;
        clickEvents.run(ClickType.CHANGE_HELD, List.of(0), () -> menu.swap(0, inventorySlot));
        return true;
    }

    @Override
    public boolean middleClick(Player player, int slot) {
        if (!accepts(player)) {
            return false;
        }
        if (slot != menu.resultIndex) {
            return super.middleClick(player, slot);
        }
        clickEvents.run(null, List.of(), () -> menu.cloneSlot(0));
        return true;
    }

    @Override
    public boolean drop(Player player, boolean all, int slot) {
        if (!accepts(player)) {
            return false;
        }
        if (slot != menu.resultIndex) {
            return super.drop(player, all, slot);
        }
        clickEvents.run(ClickType.DROP, List.of(0), () -> menu.throwSlot(0, all));
        return true;
    }

    @Override
    public boolean dragging(Player player, List<Integer> slots, int button) {
        if (!accepts(player)) {
            return false;
        }
        if (!slots.contains(menu.resultIndex)) {
            return super.dragging(player, slots, button);
        }
        var indices = slots.stream().map(this::menuIndex).toList();
        int type = ClickEvents.dragType(button);
        clickEvents.run(type == 1 ? ClickType.RIGHT_DRAGGING : ClickType.LEFT_DRAGGING, null, () -> menu.quickcraft(indices, type));
        return true;
    }

    @Override
    public boolean doubleClick(Player player, int slot) {
        if (!accepts(player)) {
            return false;
        }
        int index = menuIndex(slot);
        clickEvents.run(ClickType.DOUBLE_CLICK, null, () -> menu.pickupAll(index, true));
        return true;
    }

    /**
     * 合成格变化之后重新计算结果，对应原版 {@code TransientCraftingContainer} 调用的 {@code slotsChanged}。
     */
    @Override
    public void setItemStack(int slot, ItemStack itemStack, boolean sendPacket) {
        var previous = getItemStack(slot);
        super.setItemStack(slot, itemStack, sendPacket);
        if (slot >= menu.gridStart && slot < menu.gridStart + menu.width * menu.height && !previous.equals(itemStack)) {
            menu.gridChanged();
        }
    }

    /**
     * 原版 {@code CraftingMenu.removed}：光标物品和合成格的物品放回背包。
     */
    @Override
    public boolean removeViewer(Player player) {
        if (!getViewers().contains(player)) {
            return super.removeViewer(player);
        }
        // FIXME(Minestom M15): AbstractInventory.removeViewer 丢出光标物品，原版放回背包，这里先处理光标物品，见 MINESTOM-BUG-REPORT.md
        menu.removeCarried(false);
        boolean removed = super.removeViewer(player);
        menu.clearGrid(false);
        return removed;
    }

    /**
     * 玩家离线时按原版 {@code Player.remove} 丢出光标物品和合成格的物品。
     */
    void dropContents() {
        menu.removeCarried(true);
        menu.clearGrid(true);
    }
}
