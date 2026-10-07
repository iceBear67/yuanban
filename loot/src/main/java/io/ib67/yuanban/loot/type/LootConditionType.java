package io.ib67.yuanban.loot.type;

import com.mojang.serialization.Codec;
import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 战利品条件类型：一个条件 record 的 ID 和解码描述，对应原版 {@code LootItemConditionType}。
 * 类型 ID 例如 {@code minecraft:random_chance}；JSON 里条件对象的 {@code condition} 字段按 ID 在这里查到本类型。
 */
public record LootConditionType<T extends LootCondition>(Key id, @Nullable Codec<T> codec) {
    /**
     * @param codec 条件的解码器；codec 在 loot 数据类型模块装配时填入，装配前为 null
     */
    public LootConditionType {
        Objects.requireNonNull(id, "id");
    }
}
