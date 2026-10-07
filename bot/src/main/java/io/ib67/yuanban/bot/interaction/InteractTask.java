package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * {@code interact}：先看向目标，服务端收到这个朝向之后（{@link HandController#aimed}）发送 Interact
 * （目标偏移取包围盒中心；Sneak Key Pressed 取当前移动动作是否为潜行），数据包写出即执行完毕。
 * 开始时与发送时目标实体不在模型中都取消。
 */
public final class InteractTask extends HandAction {
    private final int entityId;
    private final PlayerHand hand;
    private @Nullable Rotation rotation;
    private boolean sent;

    public InteractTask(ActionImpl handle, int entityId, PlayerHand hand) {
        super(handle);
        this.entityId = entityId;
        this.hand = hand;
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
        var target = state.world().entities().get(entityId);
        if (target == null) {
            handle.cancelled(ActionCancelledException.NO_ENTITY);
            return;
        }
        sent = true;
        var offset = new Vec(0, target.snapshot().boundingBox().height() / 2, 0);
        state.outbox().send(new ClientInteractEntityPacket(entityId, hand, offset, controller.sneaking()));
        state.outbox().completeWhenWritten(handle);
    }

    @Override
    @Nullable Rotation lookRotation(HandController controller) {
        return rotation;
    }
}
