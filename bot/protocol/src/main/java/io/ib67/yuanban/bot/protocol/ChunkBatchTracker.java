package io.ib67.yuanban.bot.protocol;

/**
 * Chunk Batch Received 的数值（原版 {@code ChunkBatchSizeCalculator}）：Chunk Batch Start 时记录时间，Chunk Batch Finished 时
 * 用耗时除以批次中的区块数得到每区块纳秒数，按指数加权平均合并到已有的估计值（新样本钳制在估计值的 1/3 到 3 倍之间，
 * 权重上限 49），回应 {@code 7000000 / 平均每区块纳秒数}。空批次不更新估计值。
 */
public final class ChunkBatchTracker {
    /**
     * 构造时的估计值：每区块 2 毫秒（原版 {@code aggregatedNanosPerChunk} 的初值）
     */
    static final double INITIAL_NANOS_PER_CHUNK = 2_000_000;
    static final int MAX_OLD_SAMPLES_WEIGHT = 49;
    static final double CLAMP_COEFFICIENT = 3;
    /**
     * 回应的分子：7 毫秒，即原版 {@code getDesiredChunksPerTick} 的 {@code 7000000 / aggregatedNanosPerChunk}
     */
    private static final double NANOS_PER_TICK = 7_000_000;

    private double aggregatedNanosPerChunk;
    private int oldSamplesWeight;
    /**
     * Chunk Batch Start 的时刻
     */
    private long startNanos;

    public ChunkBatchTracker() {
        reset(0);
    }

    /**
     * 新连接：估计值与起始时间回到构造时的状态（原版每个 {@code ClientPacketListener} 新建一个计数器）
     */
    public void reset(long nowNanos) {
        aggregatedNanosPerChunk = INITIAL_NANOS_PER_CHUNK;
        oldSamplesWeight = 1;
        startNanos = nowNanos;
    }

    public void start(long nowNanos) {
        startNanos = nowNanos;
    }

    /**
     * @return 期望的每 tick 区块数
     */
    public float finish(long nowNanos, int batchSize) {
        if (batchSize > 0) {
            var nanosPerChunk = (nowNanos - startNanos) / (double) batchSize;
            var clamped = Math.clamp(nanosPerChunk, aggregatedNanosPerChunk / CLAMP_COEFFICIENT,
                    aggregatedNanosPerChunk * CLAMP_COEFFICIENT);
            aggregatedNanosPerChunk = (aggregatedNanosPerChunk * oldSamplesWeight + clamped) / (oldSamplesWeight + 1);
            oldSamplesWeight = Math.min(MAX_OLD_SAMPLES_WEIGHT, oldSamplesWeight + 1);
        }
        return (float) (NANOS_PER_TICK / aggregatedNanosPerChunk);
    }
}
