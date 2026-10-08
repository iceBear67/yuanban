package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.item.instrument.Instrument;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code set_instrument}：从乐器集合中随机选一个写入山羊角。JSON 字段 {@code options}
 * （必填，乐器集合）。
 */
public record SetInstrumentFunction(List<LootCondition> conditions,
                                    HolderSet<Instrument> options) implements LootFunction {
    public SetInstrumentFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(options, "options");
    }
}
