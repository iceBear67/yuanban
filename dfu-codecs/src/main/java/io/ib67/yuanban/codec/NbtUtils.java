package io.ib67.yuanban.codec;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import org.jetbrains.annotations.Nullable;

/**
 * 原版 {@code net.minecraft.nbt.NbtUtils} 中与 NBT 比较有关的部分。
 */
public final class NbtUtils {
    private NbtUtils() {
    }

    /**
     * 原版 {@code NbtUtils.compareNbt}：{@code expected} 中的每一项都要在 {@code actual} 中出现。
     * <ul>
     *   <li>{@code expected} 为 {@code null} 时总是成立，{@code actual} 为 {@code null} 时不成立；</li>
     *   <li>两者类型不同时不成立；</li>
     *   <li>复合标签：{@code expected} 的每个键在 {@code actual} 中都有满足比较的值；</li>
     *   <li>{@code partialListMatches} 为 {@code true} 时，列表 {@code expected} 的每个元素在 {@code actual}
     *   中都有满足比较的元素，空列表只匹配空列表；</li>
     *   <li>其它情况按相等比较。</li>
     * </ul>
     */
    public static boolean compareNbt(@Nullable BinaryTag expected, @Nullable BinaryTag actual, boolean partialListMatches) {
        if (expected == actual) {
            return true;
        }
        if (expected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (expected.type() != actual.type()) {
            return false;
        }
        if (expected instanceof CompoundBinaryTag expectedCompound) {
            var actualCompound = (CompoundBinaryTag) actual;
            if (actualCompound.size() < expectedCompound.size()) {
                return false;
            }
            for (var entry : expectedCompound) {
                if (!compareNbt(entry.getValue(), actualCompound.get(entry.getKey()), partialListMatches)) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof ListBinaryTag expectedList && partialListMatches) {
            var actualList = (ListBinaryTag) actual;
            if (expectedList.isEmpty()) {
                return actualList.isEmpty();
            }
            if (actualList.size() < expectedList.size()) {
                return false;
            }
            for (BinaryTag expectedElement : expectedList) {
                boolean found = false;
                for (BinaryTag actualElement : actualList) {
                    if (compareNbt(expectedElement, actualElement, partialListMatches)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
        return expected.equals(actual);
    }

    /**
     * 原版 {@code CompoundTag.merge}：把 {@code other} 的每一项放入 {@code target}；两边同一个键都是复合标签时递归合并，
     * 否则以 {@code other} 的值为准。原版直接修改 {@code target}，这里返回合并之后的新标签。
     */
    public static CompoundBinaryTag merge(CompoundBinaryTag target, CompoundBinaryTag other) {
        var builder = CompoundBinaryTag.builder().put(target);
        for (var entry : other) {
            String key = entry.getKey();
            BinaryTag value = entry.getValue();
            if (value instanceof CompoundBinaryTag otherCompound && target.get(key) instanceof CompoundBinaryTag selfCompound) {
                builder.put(key, merge(selfCompound, otherCompound));
            } else {
                builder.put(key, value);
            }
        }
        return builder.build();
    }
}
