package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.protocol.Vectors;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import io.ib67.yuanban.bot.scheduler.BotState;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * {@code useItemOn} 与 {@code placeBlock}：先看向光标位置，服务端收到这个朝向之后（{@link HandController#aimed}）
 * 发送 Use Item On（带序号；World Border Hit 为 false），然后发送 Swing Arm（本库不预测交互结果，所以总是发送）。收到确认即执行完毕。
 * 目标方块在世界边框之外时什么都不发送，直接执行完毕。目标所在区块未加载时，计算 Inside block 读取方块抛出的
 * {@link io.ib67.yuanban.bot.ChunkNotLoadedException} 由 {@link HandController#tick} 处理，什么都不发送。
 */
public final class UseItemOnTask extends HandAction {
    private static final BoundingBox POINT = new BoundingBox(1.0E-7, 1.0E-7, 1.0E-7);

    private final Point position;
    private final Direction face;
    private final Hand hand;
    private final Point cursor;
    private @Nullable Rotation rotation;
    private boolean sent;

    /**
     * @param cursor 光标在方块中的位置，各分量在 [0, 1] 之间
     */
    public UseItemOnTask(ActionImpl handle, Point position, Direction face, Hand hand, Point cursor) {
        super(handle);
        this.position = new Vec(position.blockX(), position.blockY(), position.blockZ());
        this.face = face;
        this.hand = hand;
        this.cursor = cursor;
    }

    /**
     * 该面的中心作为光标
     */
    public static Point faceCenter(Direction face) {
        var normal = Directions.normal(face);
        return new Vec(0.5 + normal.x() * 0.5, 0.5 + normal.y() * 0.5, 0.5 + normal.z() * 0.5);
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
        // 原版 MultiPlayerGameMode.useItemOn：目标方块在世界边框之外时返回 FAIL，什么都不发送，调用方也不挥手
        if (!state.world().border().withinBounds(position.blockX(), position.blockZ())) {
            handle.succeed();
            return;
        }
        var sequence = controller.sequence().next();
        state.outbox().send(new ServerboundUseItemOnPacket(Vectors.blockPos(position), face, hand,
                (float) cursor.x(), (float) cursor.y(), (float) cursor.z(), insideBlock(state), false, sequence));
        state.outbox().send(new ServerboundSwingPacket(hand));
        controller.sequence().await(sequence, handle::succeed);
    }

    /**
     * Inside block（原版准星选取方块时的 {@code VoxelShape.clip}）：准星射线从眼睛出发，沿当前朝向的视线长度为方块与实体交互距离中
     * 较大的一个（原版 {@code LocalPlayer.pick}）；射线上前进全长千分之一的点在目标方块的轮廓形状之内时为 true。
     * 原版选取方块使用轮廓形状（{@code ClipContext.Block.OUTLINE}），wiki 写的「碰撞箱」与原版不同，按原版
     */
    private boolean insideBlock(BotState state) {
        var player = state.player();
        var eye = player.eyePosition();
        var look = player.position();
        var attributes = player.attributes();
        var range = Math.max(attributes.value(AttributeType.Builtin.BLOCK_INTERACTION_RANGE),
                attributes.value(AttributeType.Builtin.ENTITY_INTERACTION_RANGE));
        var to = eye.add(new Rotation(look.yaw(), look.pitch()).viewVector().mul(range));
        var test = eye.add(to.sub(eye).mul(0.001));
        var block = state.world().getBlock(position, Block.Getter.Condition.TYPE);
        return block.outlineShape().intersectBox(test.sub(position), POINT);
    }

    @Override
    @Nullable Rotation lookRotation(HandController controller) {
        return rotation;
    }
}
