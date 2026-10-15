package io.ib67.yuanban.bot.player;

import java.util.concurrent.TimeUnit;

/**
 * 判断何时发送 Player Loaded（原版 {@code LevelLoadTracker}）：Login (play) 或 Respawn 时开始等待；收到 Game Event 13 之后，
 * 每个 tick 检查玩家所在区块已加载、玩家在维度高度范围之外、玩家为旁观模式、玩家已死亡中的任意一条（wiki FAQ），
 * 满足时，或者自开始等待起超过 30 秒（墙钟时间）时，这个 tick 发送 Player Loaded。没有收到 Game Event 13 时一直等待，不会超时。
 */
public final class PlayerLoadTracker {
    /**
     * 原版 {@code LevelLoadTracker.CLIENT_WAIT_TIMEOUT_MS}
     */
    public static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);

    private State state = State.IDLE;
    private long deadlineNanos;

    /**
     * Login (play)、Respawn：开始等待（原版 {@code startClientLoad}）
     *
     * @param nowNanos 当前的 {@link System#nanoTime()}
     */
    public void start(long nowNanos) {
        state = State.WAITING_FOR_SERVER;
        deadlineNanos = nowNanos + TIMEOUT_NANOS;
    }

    /**
     * 离开世界（Start Configuration、Transfer）：不再等待，也不算已经加载
     */
    public void reset() {
        state = State.IDLE;
    }

    /**
     * Game Event 13（{@code LEVEL_CHUNKS_LOAD_START}）：只在开始等待之后、收到它之前生效，其余时候被忽略（原版客户端行为）
     */
    public void chunksLoadStarted() {
        if (state == State.WAITING_FOR_SERVER) {
            state = State.WAITING_FOR_PLAYER_CHUNK;
        }
    }

    public boolean loaded() {
        return state == State.LOADED;
    }

    /**
     * 每个 tick 判断加载条件
     *
     * @param ready    加载条件中「任意一条」的部分是否满足
     * @param nowNanos 当前的 {@link System#nanoTime()}
     * @return 这个 tick 是否发送 Player Loaded
     */
    public boolean tick(boolean ready, long nowNanos) {
        if (state != State.WAITING_FOR_PLAYER_CHUNK) {
            return false;
        }
        if (nowNanos - deadlineNanos > 0 || ready) {
            state = State.LOADED;
            return true;
        }
        return false;
    }

    private enum State {
        /**
         * 不在任何世界中
         */
        IDLE,
        /**
         * 等待 Game Event 13
         */
        WAITING_FOR_SERVER,
        /**
         * 等待加载条件或者超时
         */
        WAITING_FOR_PLAYER_CHUNK,
        /**
         * 已经发送 Player Loaded
         */
        LOADED
    }
}
