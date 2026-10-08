package io.ib67.yuanban.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * 原版 {@code net.minecraft.core.UUIDUtil} 的编解码器。
 */
public final class UUIDUtil {
    /**
     * 原版 {@code UUIDUtil.CODEC}：四个 int 组成的数组，高位在前；个数不对时为 {@code Input is not a list of 4 ints}。
     */
    public static final Codec<UUID> CODEC = Codec.INT_STREAM.comapFlatMap(
            stream -> fixedSize(stream, 4).map(UUIDUtil::uuidFromIntArray),
            uuid -> Arrays.stream(uuidToIntArray(uuid)));
    /**
     * 原版 {@code UUIDUtil.STRING_CODEC}：带连字符的字符串，错误为 {@code Invalid UUID <s>: <原因>}。
     */
    public static final Codec<UUID> STRING_CODEC = Codec.STRING.comapFlatMap(string -> {
        try {
            return DataResult.success(UUID.fromString(string), Lifecycle.stable());
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Invalid UUID " + string + ": " + e.getMessage());
        }
    }, UUID::toString);
    /**
     * 原版 {@code UUIDUtil.LENIENT_CODEC}：先按 {@link #CODEC}，失败时按 {@link #STRING_CODEC}。
     */
    public static final Codec<UUID> LENIENT_CODEC = Codec.withAlternative(CODEC, STRING_CODEC);

    private UUIDUtil() {
    }

    /**
     * 原版 {@code UUIDUtil.uuidFromIntArray}。
     */
    public static UUID uuidFromIntArray(int[] intArray) {
        return new UUID((long) intArray[0] << 32 | intArray[1] & 0xFFFFFFFFL, (long) intArray[2] << 32 | intArray[3] & 0xFFFFFFFFL);
    }

    /**
     * 原版 {@code UUIDUtil.uuidToIntArray}。
     */
    public static int[] uuidToIntArray(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return new int[]{(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
    }

    /**
     * 原版 {@code Util.fixedSize(IntStream, int)}：元素过多时保留前 {@code size} 个作为部分结果。
     */
    private static DataResult<int[]> fixedSize(IntStream stream, int size) {
        int[] ints = stream.limit(size + 1).toArray();
        if (ints.length != size) {
            Supplier<String> message = () -> "Input is not a list of " + size + " ints";
            return ints.length >= size ? DataResult.error(message, Arrays.copyOf(ints, size)) : DataResult.error(message);
        }
        return DataResult.success(ints);
    }
}
