package io.ib67.yuanban.bot;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

/**
 * {@link BotRuntime} 的选项。
 */
@Getter
@Accessors(fluent = true)
@Builder
public final class RuntimeOptions {
    public static final RuntimeOptions DEFAULT = builder().build();

    /**
     * 阻塞等待期间执行的推进函数，用于推进手动 tick 的服务端：同一个 runtime 中所有等待的线程共用，任何时刻最多由一个线程执行，
     * 两次执行的开始时间至少相隔 50 毫秒，由正在等待的线程中到时间时先检查到的一个在它自己的线程上执行；
     * 为 null 时等待方法只检查完成状态
     */
    private final @Nullable Runnable waitPump;
}
