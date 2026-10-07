package io.ib67.yuanban.lootparser.internal;

import io.ib67.yuanban.loot.LootTypeRegistry;
import net.minestom.server.ServerProcess;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * 把原版 26.2 的 10 + 20 + 44 + 8 种战利品类型的 {@code Codec} 装配进 {@link LootTypeRegistry}。
 * 实际的编解码器在 {@link LootCodecs} 内组装；这个类只是装配入口。
 * <p>
 * 装配过来的注册表后续可以直接用 {@code types.entry(id).codec()} 读到非空编解码器；
 * 覆盖已经装配过的注册表允许再次装配，新的 codec 替换旧的。
 */
@ApiStatus.Internal
public final class CodecWiring {

    private CodecWiring() {
    }

    /**
     * 把原版全部 81 种类型的编解码器装配进 {@code types}。
     * 装配完之后 {@code types.entry(id).codec().isPresent()} / 其它三个族必然为非空。
     */
    public static void wire(LootTypeRegistry types, ServerProcess process) {
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(process, "process");
        // LootCodecs.of 在构造中通过 LootTypeRegistry.wire*Codec 把所有 codec 装进去。
        LootCodecs.of(process, types);
    }
}
