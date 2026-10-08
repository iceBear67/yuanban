package io.ib67.yuanban.loot.type;

import com.mojang.serialization.MapCodec;
import io.ib67.yuanban.loot.codec.LootCodecs;
import io.ib67.yuanban.loot.number.NumberProvider;
import net.kyori.adventure.key.Key;

import java.util.Objects;
import java.util.function.Function;

/**
 * 数字提供器类型：一个数字提供器 record 的 ID 和编解码器，对应原版 {@code LootNumberProviderType} 注册表中的一项。
 * 类型 ID 例如 {@code minecraft:uniform}；JSON 里提供器对象的 {@code type} 字段按 ID 在注册表中查到本类型。
 *
 * @param codec 由分派编解码器构造本类型的 {@link MapCodec}，见 {@link LootEntryType#codec()}
 */
public record NumberProviderType<T extends NumberProvider>(Key id, Function<LootCodecs, MapCodec<T>> codec) {
    public NumberProviderType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
    }
}
