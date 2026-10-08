package io.ib67.yuanban.codec;

import net.kyori.adventure.key.Key;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 一个注册表的 ID，对应原版 {@code ResourceKey<Registry<T>>}。{@link #toString()} 与原版相同，
 * 例如 {@code ResourceKey[minecraft:root / minecraft:item]}，原版错误文本中的注册表就是这样打印的。
 *
 * @param key 注册表 ID，例如 {@code minecraft:item}、{@code minecraft:worldgen/biome}
 * @param <T> 注册表元素的类型；只用于编译期检查
 */
public record RegistryId<T>(Key key) {
    private static final Key ROOT = Key.key("root");

    public RegistryId {
        Objects.requireNonNull(key, "key");
    }

    public static <T> RegistryId<T> of(String key) {
        return new RegistryId<>(Key.key(key));
    }

    /**
     * 原版 {@code ResourceKey.toString()} 对注册表中某个元素的打印，例如
     * {@code ResourceKey[minecraft:trim_pattern / minecraft:sentry]}。
     */
    public String element(Key id) {
        return "ResourceKey[" + key.asString() + " / " + id.asString() + "]";
    }

    /**
     * 原版 {@code Holder.Reference.toString()} 对注册表中某个元素的打印的前半部分，与 {@link #element(Key)} 相同。
     */
    public String element(RegistryKey<T> id) {
        return element(id.key());
    }

    @Override
    public @NotNull String toString() {
        return "ResourceKey[" + ROOT.asString() + " / " + key.asString() + "]";
    }
}
