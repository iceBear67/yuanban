package io.ib67.yuanban.loot.function;

/**
 * 原版 {@code replace_all}：清空列表后填入新值。原版是单例。
 */
public record ReplaceAllOperation() implements ListOperation {
    public static final ReplaceAllOperation INSTANCE = new ReplaceAllOperation();
}
