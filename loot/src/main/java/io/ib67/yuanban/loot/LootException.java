package io.ib67.yuanban.loot;

/**
 * 战利品表系统的错误：
 * <ul>
 *   <li>战利品表集合不合要求（引用缺失、递归引用、复合条目展开超限）时由 {@link LootManager#replaceAll} 抛出，
 *       本次替换不做任何修改；</li>
 *   <li>求值失败时由 {@code LootSelector} 抛出，包括上下文缺少参数、参数的值类型不符，以及 Minestom 没有求值所需的
 *       游戏数据的情况。</li>
 * </ul>
 */
public class LootException extends RuntimeException {
    public LootException(String message) {
        super(message);
    }

    public LootException(String message, Throwable cause) {
        super(message, cause);
    }
}
