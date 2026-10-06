package io.ib67.yuanban.datapack;

import org.jetbrains.annotations.Nullable;

/**
 * 框架抛出的全部异常的父类型。
 */
public class DataPackException extends RuntimeException {
    public DataPackException(String message) {
        super(message);
    }

    public DataPackException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
