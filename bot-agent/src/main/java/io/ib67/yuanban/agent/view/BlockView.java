package io.ib67.yuanban.agent.view;

import java.util.Map;

/**
 * 一个方块。
 *
 * @param name         方块名称，例如 {@code minecraft:stone}
 * @param properties   方块状态属性
 * @param solid        原版的 solid 分类（不等于有碰撞）
 * @param blocksMotion 原版的阻挡移动分类：可以站在上面、会挡住移动
 * @param liquid       液体方块（水、熔岩），不含含水方块
 */
public record BlockView(BlockPos pos, String name, Map<String, String> properties, boolean air, boolean solid,
                        boolean blocksMotion, boolean liquid) {
    public BlockView {
        properties = Map.copyOf(properties);
    }
}
