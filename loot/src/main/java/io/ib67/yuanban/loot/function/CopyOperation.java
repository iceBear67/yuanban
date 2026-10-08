package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.codec.NbtPath;

import java.util.Objects;

/**
 * {@code copy_custom_data} 的一次复制动作：原版 {@code CopyCustomDataFunction$CopyOperation}。
 * JSON 字段 {@code source}、{@code target}（NBT 路径）、{@code op}（必填，{@code replace}、{@code append}、{@code merge}）。
 */
public record CopyOperation(NbtPath source, NbtPath target, MergeStrategy op) {
    public CopyOperation {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(op, "op");
    }
}
