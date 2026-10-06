package io.ib67.yuanban.datapack;

import org.jetbrains.annotations.Nullable;

/**
 * 标签文件的格式错误。
 */
public class TagFileException extends DataPackException {
    public TagFileException(String message) {
        super(message);
    }

    public TagFileException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
