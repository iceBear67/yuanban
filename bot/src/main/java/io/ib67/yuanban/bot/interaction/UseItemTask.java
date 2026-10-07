package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.inventory.Window;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientUseItemPacket;
import org.jetbrains.annotations.Nullable;

/**
 * {@code useItem}：发送 Use Item（带序号与当前朝向）。未指定保持时间时收到确认即执行完毕；
 * 指定时收到确认后再保持使用状态这么多 tick，然后发送 Player Action「Shoot arrow / finish eating」，数据包写出即执行完毕。
 * <p>
 * 指定了保持时间时，从发送 Use Item 到发送「Shoot arrow / finish eating」视为正在使用这个物品（{@link #itemInUse}），
 * 移动按原版 {@code LocalPlayer.modifyInput} 减速，按 {@code isSlowDueToUsingItem} 不开始疾跑。
 * 原版只对能够使用的物品进入使用状态，本库不预测物品能否使用。
 */
public final class UseItemTask extends HandAction {
    private final PlayerHand hand;
    private final int holdTicks;
    private int remaining = -1;
    private int pendingSequence;
    private @Nullable ItemStack using;

    /**
     * @param holdTicks 收到确认后保持使用的 tick 数；为 0 时收到确认即执行完毕
     */
    public UseItemTask(ActionImpl handle, PlayerHand hand, int holdTicks) {
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
            using = state.inventory().player().get(hand == PlayerHand.MAIN
                    ? Window.PLAYER_HOTBAR_START + state.player().heldSlot() : Window.PLAYER_OFFHAND);
        }
        state.outbox().send(new ClientUseItemPacket(hand, sequence, rotation.yaw(), rotation.pitch()));
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
        using = null;
    }

    @Override
    @Nullable ItemStack itemInUse() {
        return using;
    }

    private void release(HandController controller) {
        using = null;
        controller.state().outbox().send(new ClientPlayerActionPacket(ClientPlayerActionPacket.Status.UPDATE_ITEM_STATE,
                Vec.ZERO, BlockFace.BOTTOM, 0));
    }
}
