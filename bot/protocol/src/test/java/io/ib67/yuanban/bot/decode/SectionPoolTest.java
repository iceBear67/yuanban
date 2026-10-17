package io.ib67.yuanban.bot.decode;

import io.ib67.yuanban.bot.world.SharedSection;
import net.minestom.server.instance.block.Block;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 区段按原始字节去重，解码函数只在未命中时执行。
 */
class SectionPoolTest {
    private static SharedSection section(Block block) {
        return new SharedSection(DataPalette.createForBlockState(block.stateId(), Block.statesCount()),
                DataPalette.createForBiome(0, 1), block.air() ? 0 : 4096, 0);
    }

    @Test
    void identicalBytesShareOneSection() {
        var pool = new SectionPool();
        var decodes = new AtomicInteger();
        var bytes = new byte[]{1, 2, 3};
        var first = pool.get(bytes, 1, () -> {
            decodes.incrementAndGet();
            return section(Block.STONE);
        });
        var second = pool.get(bytes, 1, () -> {
            decodes.incrementAndGet();
            return section(Block.STONE);
        });
        assertSame(first, second);
        assertEquals(1, decodes.get());
        assertEquals(1, pool.size());
    }

    @Test
    void differentBytesOrBiomeCountsAreDifferentSections() {
        var pool = new SectionPool();
        var first = pool.get(new byte[]{1}, 1, () -> section(Block.STONE));
        var second = pool.get(new byte[]{2}, 1, () -> section(Block.STONE));
        // 生物群系数量不同：解码结果不同（决定全局调色板的位数）
        var otherBiomes = pool.get(new byte[]{1}, 2, () -> section(Block.STONE));
        assertNotSame(first, second);
        assertNotSame(first, otherBiomes);
        assertEquals(3, pool.size());
    }

    @Test
    void theSharedPoolIsAvailableToEveryBot() {
        // SHARED 由全部连接共用：同一个区段在不同连接中解码时只保留一份
        var first = SectionPool.SHARED.get(new byte[]{7, 8, 9}, 1, () -> section(Block.GOLD_BLOCK));
        var second = SectionPool.SHARED.get(new byte[]{7, 8, 9}, 1, () -> section(Block.DIAMOND_BLOCK));
        assertSame(first, second);
    }
}
