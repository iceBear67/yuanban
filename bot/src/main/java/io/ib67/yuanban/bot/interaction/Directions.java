package io.ib67.yuanban.bot.interaction;

import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;

/**
 * 方块面的法向量（原版 {@code Direction.getUnitVec3}）
 */
public final class Directions {
    private Directions() {
    }

    public static Vec normal(Direction direction) {
        return switch (direction) {
            case DOWN -> new Vec(0, -1, 0);
            case UP -> new Vec(0, 1, 0);
            case NORTH -> new Vec(0, 0, -1);
            case SOUTH -> new Vec(0, 0, 1);
            case WEST -> new Vec(-1, 0, 0);
            case EAST -> new Vec(1, 0, 0);
        };
    }
}
