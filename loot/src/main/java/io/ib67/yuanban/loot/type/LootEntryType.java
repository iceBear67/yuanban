package io.ib67.yuanban.loot.type;

import com.mojang.serialization.Codec;
import io.ib67.yuanban.loot.entry.LootEntry;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 战利品条目类型：一个条目 record 的 ID 和解码描述，对应原版 {@code LootPoolEntryType}。
 * 类型 ID 例如 {@code minecraft:item}；JSON 里条目对象的 {@code type} 字段按 ID 在这里查到本类型。
 */
public record LootEntryType<T extends LootEntry>(Key id, @Nullable Codec<T> codec) {
    /**
     * @param codec 条目的解码器；codec 在 loot 数据类型模块装配时填入，装配前为 null
     */
    public LootEntryType {
        Objects.requireNonNull(id, "id");
    }
}
