package io.ib67.yuanban.bot.protocol;

import java.util.ArrayDeque;

/**
 * Chunk Batch Received 的数值（wiki Chunk Batch Finished）：Chunk Batch Start 时记录时间，Chunk Batch Finished 时
 * 用耗时除以批次中的区块数得到每区块毫秒数，保留最近 15 批，回应 {@code 25 / 平均每区块毫秒数}。
 */
public final class ChunkBatchTracker {
    public static final int SAMPLES = 15;

    private final ArrayDeque<Double> millisPerChunk = new ArrayDeque<>();
    private long startNanos;

    public void start(long nowNanos) {
        startNanos = nowNanos;
    }

    /**
     * @return 期望的每 tick 区块数
     */
    public float finish(long nowNanos, int batchSize) {
        if (batchSize > 0) {
            millisPerChunk.addLast((nowNanos - startNanos) / 1_000_000.0 / batchSize);
            while (millisPerChunk.size() > SAMPLES) {
                millisPerChunk.removeFirst();
            }
        }
        var average = millisPerChunk.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return (float) (25 / average);
    }
}
