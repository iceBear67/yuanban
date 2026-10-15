package io.ib67.yuanban.bot.data;

import net.minestom.server.instance.block.Block;

/**
 * 方块状态的总数。区块数据中全局调色板的位数由它决定（MCProtocolLib 解码区段时需要）
 */
public final class BlockStates {
    public static final int REGISTRY_SIZE = Block.statesCount();

    private BlockStates() {
    }
}
