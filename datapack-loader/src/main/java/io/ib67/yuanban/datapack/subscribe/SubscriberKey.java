package io.ib67.yuanban.datapack.subscribe;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 订阅者的名字，同时标明它的准备结果类型。
 *
 * @param <R> 准备结果的类型
 */
public record SubscriberKey<R>(Key name) {
    public SubscriberKey {
        Objects.requireNonNull(name, "name");
    }

    public static <R> SubscriberKey<R> of(String name) {
        return new SubscriberKey<>(Key.key(name));
    }

    @Override
    public @NotNull String toString() {
        return name.asString();
    }
}
