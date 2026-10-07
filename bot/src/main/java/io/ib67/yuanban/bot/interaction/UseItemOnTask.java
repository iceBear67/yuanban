package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.network.packet.client.play.ClientAnimationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * {@code useItemOn} 与 {@code placeBlock}：先看向光标位置，服务端收到这个朝向之后（{@link HandController#aimed}）
 * 发送 Use Item On（带序号；World Border Hit 为 false），然后发送 Swing Arm（本库不预测交互结果，所以总是发送）。收到确认即执行完毕。
 */
public final class UseItemOnTask extends HandAction {
    private static final BoundingBox POINT = new BoundingBox(1.0E-7, 1.0E-7, 1.0E-7);

    private final Point position;
    private final BlockFace face;
    private final PlayerHand hand;
    private final Point cursor;
    private @Nullable Rotation rotation;
    private boolean sent;

    /**
     * @param cursor 光标在方块中的位置，各分量在 [0, 1] 之间
     */
    public UseItemOnTask(ActionImpl handle, Point position, BlockFace face, PlayerHand hand, Point cursor) {
        super(handle);
        this.position = new Vec(position.blockX(), position.blockY(), position.blockZ());
        this.face = face;
        this.hand = hand;
        this.cursor = cursor;
    }

    /**
     * 该面的中心作为光标
     */
    public static Point faceCenter(BlockFace face) {
        var direction = face.toDirection();
        return new Vec(0.5 + direction.normalX() * 0.5, 0.5 + direction.normalY() * 0.5, 0.5 + direction.normalZ() * 0.5);
    }

    @Override
    void start(HandController controller) {
        rotation = Rotation.lookAt(controller.state().player().eyePosition(), position.add(cursor));
        tick(controller);
    }

    @Override
    void tick(HandController controller) {
        if (sent || !controller.aimed(Objects.requireNonNull(rotation))) {
            return;
        }
        sent = true;
        var state = controller.state();
        var eye = state.player().eyePosition();
        var block = state.world().getBlock(position, Block.Getter.Condition.TYPE);
        // Inside block：眼睛是否在方块碰撞箱内
        var insideBlock = block.collisionShape().intersectBox(eye.sub(position), POINT);
        var sequence = controller.sequence().next();
        state.outbox().send(new ClientPlayerBlockPlacementPacket(hand, position, face,
                (float) cursor.x(), (float) cursor.y(), (float) cursor.z(), insideBlock, false, sequence));
        state.outbox().send(new ClientAnimationPacket(hand));
        controller.sequence().await(sequence, handle::succeed);
    }

    @Override
    @Nullable Rotation lookRotation(HandController controller) {
        return rotation;
    }
}
