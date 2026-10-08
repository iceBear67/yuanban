package io.ib67.yuanban.loot.type;

import com.mojang.serialization.MapCodec;
import io.ib67.yuanban.loot.codec.LootCodecs;
import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.key.Key;

import java.util.Objects;
import java.util.function.Function;

/**
 * 战利品条件类型：一个条件 record 的 ID 和编解码器，对应原版 {@code LootItemConditionType} 注册表中的一项。
 * 类型 ID 例如 {@code minecraft:random_chance}；JSON 里条件对象的 {@code condition} 字段按 ID 在注册表中查到本类型。
 *
 * @param codec 由分派编解码器构造本类型的 {@link MapCodec}，见 {@link LootEntryType#codec()}
 */
public record LootConditionType<T extends LootCondition>(Key id, Function<LootCodecs, MapCodec<T>> codec) {
    public LootConditionType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
    }
}
