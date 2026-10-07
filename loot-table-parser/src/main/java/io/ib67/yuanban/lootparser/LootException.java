package io.ib67.yuanban.lootparser;

import io.ib67.yuanban.datapack.DataPackException;

/**
 * {@code loot_table} 解析或装配失败时抛出；信息是多行错误列表。
 */
public final class LootException extends DataPackException {
    public LootException(String message) {
        super(message);
    }
}
