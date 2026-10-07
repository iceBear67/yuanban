package io.ib67.yuanban.loot.function;

/**
 * {@code copy_custom_data} 的一次复制动作：原版 {@code CopyCustomDataFunction$CopyOperation}。
 * JSON 字段 {@code source}、{@code target}、{@code op}（默认 {@code replace}）。
 */
public record CopyOperation(String source, String target, MergeStrategy op) {
    public CopyOperation {
        op = op == null ? MergeStrategy.REPLACE : op;
    }

    public CopyOperation(String source, String target) {
        this(source, target, MergeStrategy.REPLACE);
    }
}
