package io.ib67.yuanban.datapack;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * {@code pack.mcmeta} 的内容不合法。
 */
@Getter
public class PackMetadataException extends DataPackException {
    private final String packId;

    /**
     * @param reason 原因，解码失败时是编解码器给出的错误文本
     */
    public PackMetadataException(String packId, String reason) {
        this(packId, reason, null);
    }

    public PackMetadataException(String packId, String reason, @Nullable Throwable cause) {
        super("数据包 " + packId + " 的 pack.mcmeta 不合法：" + reason, cause);
        this.packId = packId;
    }
}
