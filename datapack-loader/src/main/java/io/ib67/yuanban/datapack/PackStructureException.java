package io.ib67.yuanban.datapack;

import org.jetbrains.annotations.Nullable;

/**
 * 数据包的发现或者目录结构出现问题，例如 ID 重复、符号链接不在白名单中、文件名不合法。
 */
public class PackStructureException extends DataPackException {
    public PackStructureException(String message) {
        super(message);
    }

    public PackStructureException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
