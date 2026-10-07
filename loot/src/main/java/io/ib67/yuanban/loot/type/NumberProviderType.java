package io.ib67.yuanban.loot.type;

import com.mojang.serialization.Codec;
import io.ib67.yuanban.loot.number.NumberProvider;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 数字提供器类型：一个数字提供器 record 的 ID 和解码描述，对应原版 {@code NumberProviderType}。
 * 类型 ID 例如 {@code minecraft:uniform}；JSON 里提供器对象的 {@code type} 字段按 ID 在这里查到本类型。
 */
public record NumberProviderType<T extends NumberProvider>(Key id, @Nullable Codec<T> codec) {
    /**
     * @param codec 提供器的解码器；codec 在 loot 数据类型模块装配时填入，装配前为 null
     */
    public NumberProviderType {
        Objects.requireNonNull(id, "id");
    }
}
