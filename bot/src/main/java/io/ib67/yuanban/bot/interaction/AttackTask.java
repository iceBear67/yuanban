package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.network.packet.client.play.ClientAnimationPacket;
import net.minestom.server.network.packet.client.play.ClientAttackPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * {@code attack}：先看向目标实体包围盒中心，服务端收到这个朝向之后（{@link HandController#aimed}）发送 Attack 与 Swing Arm，
 * 数据包写出即执行完毕。开始时与发送时目标实体不在模型中都取消。
 */
public final class AttackTask extends HandAction {
    private final int entityId;
    private @Nullable Rotation rotation;
    private boolean sent;

    public AttackTask(ActionImpl handle, int entityId) {
        super(handle);
        this.entityId = entityId;
    }

    @Override
    void start(HandController controller) {
        var state = controller.state();
        var target = state.world().entities().get(entityId);
        if (target == null) {
            handle.cancelled(ActionCancelledException.NO_ENTITY);
            return;
        }
        rotation = Rotation.lookAt(state.player().eyePosition(), target.snapshot().center());
        tick(controller);
    }

    @Override
    void tick(HandController controller) {
        if (sent || !controller.aimed(Objects.requireNonNull(rotation))) {
            return;
        }
        var state = controller.state();
        if (state.world().entities().get(entityId) == null) {
            handle.cancelled(ActionCancelledException.NO_ENTITY);
            return;
        }
        sent = true;
        state.outbox().send(new ClientAttackPacket(entityId));
        state.outbox().send(new ClientAnimationPacket(PlayerHand.MAIN));
        state.outbox().completeWhenWritten(handle);
    }

    @Override
    @Nullable Rotation lookRotation(HandController controller) {
        return rotation;
    }
}
