package io.ib67.yuanban.codec;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

/**
 * 原版 {@code net.minecraft.commands.arguments.coordinates.BlockPosArgument} 的解析部分，以及它用到的
 * {@code WorldCoordinates}、{@code WorldCoordinate}、{@code LocalCoordinates}。
 */
final class BlockPosArgument {
    /**
     * 原版 {@code Vec3Argument.ERROR_NOT_COMPLETE}。
     */
    static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = CommandMessages.simple("argument.pos3d.incomplete");
    /**
     * 原版 {@code Vec3Argument.ERROR_MIXED_TYPE}。
     */
    static final SimpleCommandExceptionType ERROR_MIXED_TYPE = CommandMessages.simple("argument.pos.mixed");
    /**
     * 原版 {@code WorldCoordinate.ERROR_EXPECTED_DOUBLE}。
     */
    static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE = CommandMessages.simple("argument.pos.missing.double");
    /**
     * 原版 {@code WorldCoordinate.ERROR_EXPECTED_INT}。
     */
    static final SimpleCommandExceptionType ERROR_EXPECTED_INT = CommandMessages.simple("argument.pos.missing.int");

    private BlockPosArgument() {
    }

    /**
     * 原版 {@code BlockPosArgument.parse}：{@code ^} 开头时为局部坐标，否则为方块的世界坐标。
     */
    static Coordinates parse(StringReader reader) throws CommandSyntaxException {
        return reader.canRead() && reader.peek() == '^' ? parseLocal(reader) : parseWorldInt(reader);
    }

    /**
     * 原版 {@code WorldCoordinates.parseInt}。
     */
    private static WorldCoordinates parseWorldInt(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        WorldCoordinate x = parseIntCoordinate(reader);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate y = parseIntCoordinate(reader);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate z = parseIntCoordinate(reader);
                return new WorldCoordinates(x, y, z);
            }
        }
        reader.setCursor(start);
        throw ERROR_NOT_COMPLETE.createWithContext(reader);
    }

    /**
     * 原版 {@code WorldCoordinate.parseInt}：绝对坐标是整数，{@code ~} 开头的相对坐标是小数，可以省略数值。
     */
    private static WorldCoordinate parseIntCoordinate(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw ERROR_MIXED_TYPE.createWithContext(reader);
        }
        if (!reader.canRead()) {
            throw ERROR_EXPECTED_INT.createWithContext(reader);
        }
        boolean relative = isRelative(reader);
        double value;
        if (reader.canRead() && reader.peek() != ' ') {
            value = relative ? reader.readDouble() : reader.readInt();
        } else {
            value = 0.0;
        }
        return new WorldCoordinate(relative, value);
    }

    private static boolean isRelative(StringReader reader) {
        if (reader.peek() == '~') {
            reader.skip();
            return true;
        }
        return false;
    }

    /**
     * 原版 {@code LocalCoordinates.parse}。
     */
    private static LocalCoordinates parseLocal(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        double left = readLocalDouble(reader, start);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            double up = readLocalDouble(reader, start);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                double forwards = readLocalDouble(reader, start);
                return new LocalCoordinates(left, up, forwards);
            }
        }
        reader.setCursor(start);
        throw ERROR_NOT_COMPLETE.createWithContext(reader);
    }

    private static double readLocalDouble(StringReader reader, int start) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        }
        if (reader.peek() != '^') {
            reader.setCursor(start);
            throw ERROR_MIXED_TYPE.createWithContext(reader);
        }
        reader.skip();
        return reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
    }

    /**
     * 原版 {@code Coordinates}。
     */
    sealed interface Coordinates permits WorldCoordinates, LocalCoordinates {
    }

    /**
     * 原版 {@code WorldCoordinates}。
     */
    record WorldCoordinates(WorldCoordinate x, WorldCoordinate y, WorldCoordinate z) implements Coordinates {
    }

    /**
     * 原版 {@code WorldCoordinate}：{@code relative} 为 {@code true} 时是相对于执行位置的偏移。
     */
    record WorldCoordinate(boolean relative, double value) {
    }

    /**
     * 原版 {@code LocalCoordinates}：相对于执行朝向向左、向上、向前的偏移。
     */
    record LocalCoordinates(double left, double up, double forwards) implements Coordinates {
    }
}
