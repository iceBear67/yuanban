package io.ib67.yuanban.agent.tool;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次工具调用的取消：网关在脚本停止、bundle 取消、primary 暂停或 lease 被收回时取消，执行方注册的回调取消 bot 动作或等待。
 */
public final class CancelToken {
    private final List<Runnable> hooks = new ArrayList<>();
    private @Nullable String reason;

    /**
     * 第一次调用生效；之后的调用不做任何事
     */
    public void cancel(String reason) {
        List<Runnable> run;
        synchronized (this) {
            if (this.reason != null) {
                return;
            }
            this.reason = reason;
            run = List.copyOf(hooks);
            hooks.clear();
        }
        run.forEach(Runnable::run);
    }

    /**
     * 已经取消时立即在调用线程上执行
     */
    public void onCancel(Runnable hook) {
        synchronized (this) {
            if (reason == null) {
                hooks.add(hook);
                return;
            }
        }
        hook.run();
    }

    public synchronized @Nullable String reason() {
        return reason;
    }

    public synchronized boolean cancelled() {
        return reason != null;
    }
}
