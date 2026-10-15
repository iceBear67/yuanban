package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.state.Outbox;
import net.minestom.server.coordinate.Pos;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;

/**
 * 在每次模拟之后发送移动包：位置变化发送位置，朝向变化发送朝向，两者都变化时发送 Set Player Position and Rotation；
 * 只有着地或顶住墙壁标记变化时发送 Set Player Movement Flags；静止时每 20 tick 至少发送一次位置。
 * 位置变化的阈值为位移平方大于 {@code (2.0E-4)²}（原版客户端行为）。
 */
public final class MovementSender {
    public static final double POSITION_THRESHOLD_SQUARED = 2.0E-4 * 2.0E-4;
    public static final int POSITION_REMINDER_TICKS = 20;

    private Pos lastPosition = Pos.ZERO;
    private boolean lastOnGround;
    private boolean lastHorizontalCollision;
    private int positionReminder;

    public void send(Outbox outbox, Pos position, boolean onGround, boolean horizontalCollision) {
        positionReminder++;
        var dx = position.x() - lastPosition.x();
        var dy = position.y() - lastPosition.y();
        var dz = position.z() - lastPosition.z();
        var positionChanged = dx * dx + dy * dy + dz * dz > POSITION_THRESHOLD_SQUARED
                || positionReminder >= POSITION_REMINDER_TICKS;
        var rotationChanged = position.yaw() != lastPosition.yaw() || position.pitch() != lastPosition.pitch();
        if (positionChanged && rotationChanged) {
            outbox.send(new ServerboundMovePlayerPosRotPacket(onGround, horizontalCollision, position.x(), position.y(),
                    position.z(), position.yaw(), position.pitch()));
        } else if (positionChanged) {
            outbox.send(new ServerboundMovePlayerPosPacket(onGround, horizontalCollision, position.x(), position.y(), position.z()));
        } else if (rotationChanged) {
            outbox.send(new ServerboundMovePlayerRotPacket(onGround, horizontalCollision, position.yaw(), position.pitch()));
        } else if (onGround != lastOnGround || horizontalCollision != lastHorizontalCollision) {
            outbox.send(new ServerboundMovePlayerStatusOnlyPacket(onGround, horizontalCollision));
        }
        if (positionChanged) {
            lastPosition = lastPosition.withCoord(position);
            positionReminder = 0;
        }
        if (rotationChanged) {
            lastPosition = lastPosition.withView(position.yaw(), position.pitch());
        }
        lastOnGround = onGround;
        lastHorizontalCollision = horizontalCollision;
    }
}
