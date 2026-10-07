package io.ib67.yuanban.bot.inventory;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.InventoryActions;
import io.ib67.yuanban.bot.observe.CursorChanged;
import io.ib67.yuanban.bot.observe.WindowClosed;
import io.ib67.yuanban.bot.scheduler.BotInstance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.client.play.ClientClickWindowButtonPacket;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.network.packet.client.play.ClientCloseWindowPacket;

import java.util.Map;

/**
 * 物品栏操作：全部为一次性请求，作用于执行时打开的窗口（没有打开窗口时为窗口 0）。
 * 点击包的变化槽位为空，光标声明为 {@link ItemStack.Hash#AIR}：服务端把点击改变的每个槽位都发回（wiki Click Container）。
 */
public final class InventoryActionsImpl implements InventoryActions {
    private final BotInstance bot;

    public InventoryActionsImpl(BotInstance bot) {
        this.bot = bot;
    }

    @Override
    public Action click(int slot, int button, ClientClickWindowPacket.ClickType mode) {
        return bot.oneShot("click " + slot + " " + button + " " + mode, _ -> {
            var inventory = bot.state().inventory();
            bot.state().outbox().send(new ClientClickWindowPacket(inventory.currentWindowId(), inventory.stateId(),
                    (short) slot, (byte) button, mode, Map.of(), ItemStack.Hash.AIR));
        });
    }

    @Override
    public Action leftClick(int slot) {
        return click(slot, 0, ClientClickWindowPacket.ClickType.PICKUP);
    }

    @Override
    public Action rightClick(int slot) {
        return click(slot, 1, ClientClickWindowPacket.ClickType.PICKUP);
    }

    @Override
    public Action shiftClick(int slot) {
        return click(slot, 0, ClientClickWindowPacket.ClickType.QUICK_MOVE);
    }

    @Override
    public Action swapWithHotbar(int slot, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            throw new IllegalArgumentException("hotbar slot must be within [0, 8], got " + hotbarSlot);
        }
        return click(slot, hotbarSlot, ClientClickWindowPacket.ClickType.SWAP);
    }

    @Override
    public Action dropSlot(int slot, boolean wholeStack) {
        return click(slot, wholeStack ? 1 : 0, ClientClickWindowPacket.ClickType.THROW);
    }

    @Override
    public Action clickButton(int buttonId) {
        return bot.oneShot("clickButton " + buttonId, _ -> bot.state().outbox().send(
                new ClientClickWindowButtonPacket(bot.state().inventory().currentWindowId(), buttonId)));
    }

    /**
     * 发送 Close Container 并在本地关闭打开的窗口（原版客户端行为）；当前的光标随之变为窗口 0 的光标
     */
    @Override
    public Action close() {
        return bot.oneShot("closeWindow", _ -> {
            var state = bot.state();
            var windowId = state.inventory().currentWindowId();
            state.outbox().send(new ClientCloseWindowPacket(windowId));
            var closed = state.inventory().closeOpen((old, now) -> {
                if (state.observations().wants(CursorChanged.class)) {
                    state.observations().emit(new CursorChanged(old, now));
                }
            });
            if (closed != null && state.observations().wants(WindowClosed.class)) {
                state.observations().emit(new WindowClosed(closed));
            }
        });
    }
}
