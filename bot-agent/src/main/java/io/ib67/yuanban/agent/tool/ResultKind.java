package io.ib67.yuanban.agent.tool;

/**
 * 工具结果的分类。前五种对应 bot 的异常：{@code ActionCancelledException}、{@code BotDisconnectedException}、
 * {@code BotFailureException}、{@code BotTimeoutException}、{@code ChunkNotLoadedException}。
 */
public enum ResultKind {
    OK(true),
    ACTION_CANCELLED(false),
    DISCONNECTED(false),
    BOT_FAILURE(false),
    TIMEOUT(false),
    CHUNK_NOT_LOADED(false),
    /**
     * 不在任何世界中，读不到玩家与方块
     */
    NOT_IN_WORLD(false),
    /**
     * waitFor 超时
     */
    WAIT_TIMEOUT(false),
    /**
     * assertState 的条件不成立
     */
    ASSERTION_FAILED(false),
    /**
     * dry-run：改变世界的工具被拦截，没有执行。ok 为 true，脚本照常继续
     */
    WOULD_EXECUTE(true),
    INVALID_ARGUMENT(false),
    PERMISSION_DENIED(false),
    /**
     * 改变世界的工具需要 capability lease，调用方没有持有
     */
    LEASE_REQUIRED(false),
    BUDGET_EXHAUSTED(false),
    /**
     * 频率或坐标限流
     */
    RATE_LIMITED(false),
    /**
     * 同一个 idempotency key 的副作用已经完成，返回上次的结果，不再执行
     */
    DUPLICATE_SUPPRESSED(true),
    /**
     * 同一个 idempotency key 的副作用开始过但没有记录结束（脚本在副作用中途重启），不能确定是否已经发生
     */
    UNCERTAIN_SIDE_EFFECT(false),
    /**
     * 脚本的并发调用数、调用总数等限制
     */
    LIMIT_EXCEEDED(false),
    RESULT_TOO_LARGE(false),
    /**
     * 调用被网关取消：脚本停止、bundle 取消、primary 暂停、lease 被收回
     */
    CANCELLED(false),
    /**
     * 重放时调用与记录不一致，或者 fixture 没有预设结果
     */
    REPLAY_MISMATCH(false);

    private final boolean ok;

    ResultKind(boolean ok) {
        this.ok = ok;
    }

    public boolean ok() {
        return ok;
    }
}
