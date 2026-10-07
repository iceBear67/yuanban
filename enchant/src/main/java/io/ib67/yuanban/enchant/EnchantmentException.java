package io.ib67.yuanban.enchant;

import io.ib67.yuanban.datapack.DataPackException;

/**
 * 附魔、附魔标签或者附魔提供器的数据无法解码。
 */
public final class EnchantmentException extends DataPackException {
    public EnchantmentException(String message) {
        super(message);
    }
}
