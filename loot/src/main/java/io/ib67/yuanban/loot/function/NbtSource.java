package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContextArg;
import net.kyori.adventure.key.Key;

import java.util.Objects;
import java.util.Set;

/**
 * NBT 来源：原版 {@code providers.nbt} 下的两种。JSON 是实体目标名或 {@code block_entity}（上下文来源），
 * 或者 {@code {type: "context", target}}、{@code {type: "storage", source}}。
 */
public sealed interface NbtSource {

    /**
     * 求值时要求上下文提供的参数，对应原版 {@code NbtProvider#getReferencedContextParams}。
     */
    Set<ContextKey<?>> referencedParams();

    /**
     * 原版 {@code ContextNbtProvider}：从上下文实体或方块实体取 NBT，{@code target} 只会是实体或方块实体。
     */
    record Context(LootContextArg target) implements NbtSource {
        public Context {
            Objects.requireNonNull(target, "target");
        }

        @Override
        public Set<ContextKey<?>> referencedParams() {
            return Set.of(target.contextParam());
        }
    }

    /**
     * 原版 {@code StorageNbtProvider}：从命令存储取整个根标签。JSON 字段 {@code source}（命名空间 ID）。
     */
    record Storage(Key storage) implements NbtSource {
        public Storage {
            Objects.requireNonNull(storage, "storage");
        }

        @Override
        public Set<ContextKey<?>> referencedParams() {
            return Set.of();
        }
    }
}
