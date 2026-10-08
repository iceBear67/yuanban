package io.ib67.yuanban.loot;

import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Optional;

/**
 * 战利品表：一段战利品配置，对应原版 {@code net.minecraft.world.level.storage.loot.LootTable}。
 * <p>
 * {@link #type()} 的取值来自原版 {@code LootContextParamSets} 注册的 26 种上下文类型
 * （{@code minecraft:generic}、{@code minecraft:chest} 等）。原版按 {@code lenientOptionalFieldOf} 解码这个字段，
 * 无法识别的取值按缺省的 {@code minecraft:generic} 处理，不报错。
 * 本模块的 {@link LootManager} 只保管表本身；谓词和物品修饰器文件由其他订阅者加载，{@code reference} 类型只记 ID。
 *
 * @param randomSequence 稳定随机序列的 ID，对应原版字段 {@code random_sequence}
 */
public record LootTable(Key type,
                        List<LootFunction> functions,
                        List<LootPool> pools,
                        Optional<Key> randomSequence) {
    /**
     * 默认上下文类型 {@code minecraft:generic}，对应原版 {@code LootTable.DEFAULT_PARAM_SET}。
     */
    public static final Key DEFAULT_TYPE = Key.key("generic");

    /**
     * {@code type} 为 null 时填 {@link #DEFAULT_TYPE}，{@code randomSequence} 为 null 时填
     * {@link Optional#empty()}；各列表按不可变副本保存。
     */
    public LootTable {
        type = type == null ? DEFAULT_TYPE : type;
        randomSequence = randomSequence == null ? Optional.empty() : randomSequence;
        functions = List.copyOf(functions);
        pools = List.copyOf(pools);
    }

    /**
     * {@code type} 是原版 {@code LootContextParamSets} 注册的 26 种上下文类型之一。
     */
    public static boolean isKnownType(Key type) {
        return switch (type.asString()) {
            case "minecraft:empty", "minecraft:chest", "minecraft:command", "minecraft:selector",
                 "minecraft:villager_trade", "minecraft:fishing", "minecraft:entity", "minecraft:equipment",
                 "minecraft:archaeology", "minecraft:gift", "minecraft:barter", "minecraft:vault",
                 "minecraft:advancement_reward", "minecraft:advancement_entity", "minecraft:advancement_location",
                 "minecraft:block_use", "minecraft:generic", "minecraft:block", "minecraft:shearing",
                 "minecraft:entity_interact", "minecraft:block_interact", "minecraft:enchanted_damage",
                 "minecraft:enchanted_item", "minecraft:enchanted_location", "minecraft:enchanted_entity",
                 "minecraft:hit_block" -> true;
            default -> false;
        };
    }
}
