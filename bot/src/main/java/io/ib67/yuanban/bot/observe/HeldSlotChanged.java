package io.ib67.yuanban.bot.observe;

/**
 * 快捷栏选中的槽位改变（0 到 8）。
 */
public record HeldSlotChanged(int old, int now) implements Observation {
}
