package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.InventoryActions;
import io.ib67.yuanban.bot.observe.Observation.CursorChanged;
import io.ib67.yuanban.bot.observe.Observation.WindowClosed;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.CreativeGrabAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.FillStackAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.SpreadItemAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerButtonClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;

import java.util.Map;

/**
 * 物品栏操作：全部为一次性请求，作用于执行时打开的窗口（没有打开窗口时为窗口 0）。
 * 点击包的变化槽位为空，光标声明为空：服务端把点击改变的每个槽位都发回（wiki Click Container）。
 */
public final class InventoryActionsImpl implements InventoryActions {
    private final BotInstance bot;

    public InventoryActionsImpl(BotInstance bot) {
        this.bot = bot;
    }

    @Override
    public Action click(int slot, ContainerActionType type, ContainerAction action) {
        check(slot, type, action);
        return bot.oneShot("click " + slot + " " + type + " " + action, _ -> {
            var inventory = bot.state().inventory();
            bot.state().outbox().send(new ServerboundContainerClickPacket(inventory.currentWindowId(), inventory.stateId(),
                    slot, type, action, null, Map.of()));
        });
    }

    @Override
    public Action leftClick(int slot) {
        return click(slot, ContainerActionType.CLICK_ITEM, ClickItemAction.LEFT_CLICK);
    }

    @Override
    public Action rightClick(int slot) {
        return click(slot, ContainerActionType.CLICK_ITEM, ClickItemAction.RIGHT_CLICK);
    }

    @Override
    public Action shiftClick(int slot) {
        return click(slot, ContainerActionType.SHIFT_CLICK_ITEM, ShiftClickItemAction.LEFT_CLICK);
    }

    @Override
    public Action swapWithHotbar(int slot, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            throw new IllegalArgumentException("hotbar slot must be within [0, 8], got " + hotbarSlot);
        }
        return click(slot, ContainerActionType.MOVE_TO_HOTBAR_SLOT, MoveToHotbarAction.from(hotbarSlot));
    }

    @Override
    public Action dropSlot(int slot, boolean wholeStack) {
        return click(slot, ContainerActionType.DROP_ITEM,
                wholeStack ? DropItemAction.DROP_SELECTED_STACK : DropItemAction.DROP_FROM_SELECTED);
    }

    @Override
    public Action clickButton(int buttonId) {
        return bot.oneShot("clickButton " + buttonId, _ -> bot.state().outbox().send(
                new ServerboundContainerButtonClickPacket(bot.state().inventory().currentWindowId(), buttonId)));
    }

    /**
     * 发送 Close Container 并在本地关闭打开的窗口（原版客户端行为）；当前的光标随之变为窗口 0 的光标
     */
    @Override
    public Action close() {
        return bot.oneShot("closeWindow", _ -> {
            var state = bot.state();
            var windowId = state.inventory().currentWindowId();
            state.outbox().send(new ServerboundContainerClosePacket(windowId));
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

    /**
     * 在提交请求的线程上检查按键与模式是否对应
     * FIXME: 既然这里 ContainerActionType 跟 ContainerAction 这个枚举强绑定那为什么还需要这个枚举呢？直接把 ContainerAction 做成 sealed interface.
     */
    private static void check(int slot, ContainerActionType type, ContainerAction action) {
        var expected = switch (type) {
            case CLICK_ITEM -> ClickItemAction.class;
            case SHIFT_CLICK_ITEM -> ShiftClickItemAction.class;
            case MOVE_TO_HOTBAR_SLOT -> MoveToHotbarAction.class;
            case CREATIVE_GRAB_MAX_STACK -> CreativeGrabAction.class;
            case DROP_ITEM -> DropItemAction.class;
            case SPREAD_ITEM -> SpreadItemAction.class;
            case FILL_STACK -> FillStackAction.class;
        };
        if (!expected.isInstance(action)) {
            throw new IllegalArgumentException(action + " is not a button of mode " + type);
        }
        if ((action == DropItemAction.LEFT_CLICK_OUTSIDE_NOT_HOLDING || action == DropItemAction.RIGHT_CLICK_OUTSIDE_NOT_HOLDING)
                && slot != ServerboundContainerClickPacket.CLICK_OUTSIDE_NOT_HOLDING_SLOT) {
            throw new IllegalArgumentException(action + " requires slot " + ServerboundContainerClickPacket.CLICK_OUTSIDE_NOT_HOLDING_SLOT);
        }
    }
}
