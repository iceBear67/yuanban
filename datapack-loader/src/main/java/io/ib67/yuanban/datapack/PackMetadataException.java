package io.ib67.yuanban.datapack;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * {@code pack.mcmeta} 的内容不合法。
 */
@Getter
public class PackMetadataException extends DataPackException {
    private final String packId;
    private final String field;

    public PackMetadataException(String packId, String field, String message) {
        this(packId, field, message, null);
    }

    public PackMetadataException(String packId, String field, String message, @Nullable Throwable cause) {
        super("数据包 " + packId + " 的 pack.mcmeta 中 " + (field.isEmpty() ? "<根>" : field) + " 不合法：" + message, cause);
        this.packId = packId;
        this.field = field;
    }
}
