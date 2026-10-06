package io.ib67.yuanban.itemtag;

import io.ib67.yuanban.datapack.DataPackException;

/**
 * 物品标签引用了不存在的物品或标签。
 */
public final class ItemTagException extends DataPackException {
    public ItemTagException(String message) {
        super(message);
    }
}
