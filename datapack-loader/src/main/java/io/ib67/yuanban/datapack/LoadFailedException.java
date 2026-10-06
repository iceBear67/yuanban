package io.ib67.yuanban.datapack;

import org.jetbrains.annotations.Nullable;

/**
 * 加载过程中的索引、视图或者准备任务失败。抛出这个异常的时候没有调用过任何 {@code apply}。
 * 第一个失败作为 cause，其余失败作为 suppressed。
 */
public class LoadFailedException extends DataPackException {
    public LoadFailedException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
