package io.ib67.yuanban.loot;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 命名空间 ID 或内联对象的二选一：原版用 {@code Either} 表达。本模块用此 record 避免引入
 * datafixerupper 依赖到公开签名。
 */
public record EitherId<T>(Optional<net.kyori.adventure.key.Key> left, Optional<T> right) {

    public EitherId {
        if (left == null) throw new IllegalArgumentException("left == null");
        if (right == null) throw new IllegalArgumentException("right == null");
        if (left.isPresent() == right.isPresent()) {
            throw new IllegalArgumentException("exactly one of left and right must be present");
        }
    }

    public static <T> EitherId<T> ofLeft(net.kyori.adventure.key.Key id) {
        return new EitherId<>(Optional.of(id), Optional.empty());
    }

    public static <T> EitherId<T> ofRight(T value) {
        return new EitherId<>(Optional.empty(), Optional.of(value));
    }

    public boolean isLeft() {
        return left.isPresent();
    }

    public boolean isRight() {
        return right.isPresent();
    }

    public EitherId<T> ifLeft(Consumer<net.kyori.adventure.key.Key> consumer) {
        left.ifPresent(consumer);
        return this;
    }

    public EitherId<T> ifRight(Consumer<T> consumer) {
        right.ifPresent(consumer);
        return this;
    }
}
