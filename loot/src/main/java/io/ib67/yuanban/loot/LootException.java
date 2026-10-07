package io.ib67.yuanban.loot;

/**
 * 战利品表集合不合要求：引用缺失、递归引用、复合条目展开超限时由 {@link LootManager#replaceAll} 抛出，本次替换不做任何修改。
 */
public class LootException extends RuntimeException {
    public LootException(String message) {
        super(message);
    }

    public LootException(String message, Throwable cause) {
        super(message, cause);
    }
}
