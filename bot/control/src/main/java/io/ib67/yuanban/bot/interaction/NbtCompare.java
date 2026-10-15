package io.ib67.yuanban.bot.interaction;

import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 原版 {@code NbtUtils.compareNbt(expected, actual, true)}：actual 包含 expected 时匹配。
 * 复合标签按 expected 的每个键递归比较；列表在 expected 为空时要求 actual 也为空，否则要求 expected 的每个元素都能在 actual 中找到匹配的元素；
 * 其余值要求类型与内容相同。
 */
final class NbtCompare {
    private NbtCompare() {
    }

    static boolean contains(@Nullable Object expected, @Nullable Object actual) {
        if (expected == actual || expected == null) {
            return true;
        }
        if (actual == null || !expected.getClass().equals(actual.getClass())) {
            return false;
        }
        if (expected instanceof NbtMap expectedMap) {
            var actualMap = (NbtMap) actual;
            if (actualMap.size() < expectedMap.size()) {
                return false;
            }
            for (var entry : expectedMap.entrySet()) {
                if (!contains(entry.getValue(), actualMap.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof NbtList<?> expectedList) {
            var actualList = (NbtList<?>) actual;
            if (expectedList.isEmpty()) {
                return actualList.isEmpty();
            }
            if (actualList.size() < expectedList.size()) {
                return false;
            }
            for (var element : expectedList) {
                if (actualList.stream().noneMatch(candidate -> contains(element, candidate))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.deepEquals(expected, actual);
    }
}
