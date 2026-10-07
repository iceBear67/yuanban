package io.ib67.yuanban.loot.type;

import com.mojang.serialization.Codec;
import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 物品修饰器类型：一个修饰器 record 的 ID 和解码描述，对应原版 {@code LootItemFunctionType}。
 * 类型 ID 例如 {@code minecraft:set_count}；JSON 里修饰器对象的 {@code function} 字段按 ID 在这里查到本类型。
 */
public record LootFunctionType<T extends LootFunction>(Key id, @Nullable Codec<T> codec) {
    /**
     * @param codec 修饰器的解码器；codec 在 loot 数据类型模块装配时填入，装配前为 null
     */
    public LootFunctionType {
        Objects.requireNonNull(id, "id");
    }
}
