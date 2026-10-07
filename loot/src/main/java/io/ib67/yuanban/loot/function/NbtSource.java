package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.number.EntityTarget;
import net.kyori.adventure.key.Key;

/**
 * NBT 来源：原版 {@code providers.nbt} 下的两种。JSON 形态由解析器拆开后填进对应的 record。
 */
public sealed interface NbtSource {

    /**
     * 原版 {@code ContextNbtProvider}，从上下文实体或方块实体取 NBT。原版 JSON 用
     * {@code type: "context", target}，其中 target 是实体目标或字面量 {@code block_entity}。
     */
    record Context(EntityTarget target, boolean blockEntity) implements NbtSource {
    }

    /**
     * 原版 {@code StorageNbtProvider}，从命令存储取 NBT。JSON 字段 {@code source}（命名空间 ID）。
     */
    record Storage(Key storage) implements NbtSource {
    }
}
