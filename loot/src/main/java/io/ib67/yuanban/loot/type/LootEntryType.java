package io.ib67.yuanban.loot.type;

import com.mojang.serialization.MapCodec;
import io.ib67.yuanban.loot.codec.LootCodecs;
import io.ib67.yuanban.loot.entry.LootEntry;
import net.kyori.adventure.key.Key;

import java.util.Objects;
import java.util.function.Function;

/**
 * 战利品条目类型：一个条目 record 的 ID 和编解码器，对应原版 {@code LootPoolEntryType} 注册表中的一项。
 * 类型 ID 例如 {@code minecraft:item}；JSON 里条目对象的 {@code type} 字段按 ID 在注册表中查到本类型。
 *
 * @param codec 由分派编解码器构造本类型的 {@link MapCodec}；参数是正在解码的 {@link LootCodecs}，
 *              嵌套的条目、条件、修饰器、数字提供器经它解码，宿主注册的类型同样可以出现在嵌套的位置
 */
public record LootEntryType<T extends LootEntry>(Key id, Function<LootCodecs, MapCodec<T>> codec) {
    public LootEntryType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
    }
}
