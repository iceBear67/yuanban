package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * {@code interact}：先看向目标，服务端收到这个朝向之后（{@link HandController#aimed}）发送 Interact
 * （目标偏移取包围盒中心；Sneak Key Pressed 取当前移动动作是否为潜行），数据包写出即执行完毕。
 * 开始时与发送时目标实体不在模型中都取消。
 */
public final class InteractTask extends HandAction {
    private final int entityId;
    private final Hand hand;
    private @Nullable Rotation rotation;
    private boolean sent;

    public InteractTask(ActionImpl handle, int entityId, Hand hand) {
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
        var offset = Vector3d.from(0, target.snapshot().boundingBox().height() / 2, 0);
        state.outbox().send(new ServerboundInteractPacket(entityId, hand, offset, controller.sneaking()));
        state.outbox().completeWhenWritten(handle);
    }

    @Override
    @Nullable Rotation lookRotation(HandController controller) {
        return rotation;
    }
}
