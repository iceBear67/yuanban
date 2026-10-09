package io.ib67.yuanban.agent.view;

/**
 * 槽位中的物品。
 *
 * @param slot 窗口槽位
 * @param item 物品名称，例如 {@code minecraft:dirt}
 */
public record ItemView(int slot, String item, int count) {
}
