package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.inventory.Window;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.jetbrains.annotations.Nullable;

/**
 * {@code useItem}：发送 Use Item（带序号与当前朝向）。未指定保持时间时收到确认即执行完毕；
 * 指定时收到确认后再保持使用状态这么多 tick，然后发送 Player Action「Shoot arrow / finish eating」，数据包写出即执行完毕。
 * <p>
 * 指定了保持时间时，从发送 Use Item 到发送「Shoot arrow / finish eating」视为正在使用这个物品（{@link #usingItem}），
 * 移动按原版 {@code LocalPlayer.modifyInput} 减速，按 {@code isSlowDueToUsingItem} 不开始疾跑。
 * 原版只对能够使用的物品进入使用状态，本库不预测物品能否使用。
 */
public final class UseItemTask extends HandAction {
    private final Hand hand;
    private final int holdTicks;
    private int remaining = -1;
    private int pendingSequence;
    private boolean using;
    private @Nullable ItemStack usingItem;

    /**
     * @param holdTicks 收到确认后保持使用的 tick 数；为 0 时收到确认即执行完毕
     */
    public UseItemTask(ActionImpl handle, Hand hand, int holdTicks) {
        super(handle);
        this.hand = hand;
        this.holdTicks = holdTicks;
    }

    @Override
    void start(HandController controller) {
        var state = controller.state();
        var rotation = state.player().position();
        var sequence = controller.sequence().next();
        pendingSequence = sequence;
        if (holdTicks > 0) {
            using = true;
            usingItem = state.inventory().player().get(hand == Hand.MAIN_HAND
                    ? Window.PLAYER_HOTBAR_START + state.player().heldSlot() : Window.PLAYER_OFFHAND);
        }
        state.outbox().send(new ServerboundUseItemPacket(hand, sequence, rotation.yaw(), rotation.pitch()));
        controller.sequence().await(sequence, () -> {
            pendingSequence = 0;
            if (holdTicks == 0) {
                handle.succeed();
            } else {
                remaining = holdTicks;
            }
        });
    }

    @Override
    void tick(HandController controller) {
        if (remaining < 0) {
            return;
        }
        remaining--;
        if (remaining == 0) {
            remaining = -1;
            release(controller);
            controller.state().outbox().completeWhenWritten(handle);
        }
    }

    /**
     * 保持使用期间被取消时停止使用物品
     */
    @Override
    void cleanup(HandController controller) {
        if (pendingSequence != 0) {
            controller.sequence().forget(pendingSequence);
        }
        if (remaining > 0) {
            release(controller);
        }
        using = false;
        usingItem = null;
    }

    @Override
    boolean usingItem() {
        return using;
    }

    @Override
    @Nullable ItemStack itemInUse() {
        return usingItem;
    }

    private void release(HandController controller) {
        using = false;
        usingItem = null;
        controller.state().outbox().send(new ServerboundPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM, Vector3i.ZERO,
                Direction.DOWN, 0));
    }
}
