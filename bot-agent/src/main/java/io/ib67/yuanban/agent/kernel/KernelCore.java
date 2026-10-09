package io.ib67.yuanban.agent.kernel;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentStatus;
import io.ib67.yuanban.agent.plan.BundleSpec;
import io.ib67.yuanban.agent.plan.PlanBundle;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * AgentKernel 交给 {@link KernelMode} 的状态与操作。除 {@link #post} 之外只在 kernel 线程上调用。
 */
interface KernelCore {
    KernelServices services();

    AgentStatus status();

    /**
     * 当前运行的编号；还没有用户目标时为 null
     */
    @Nullable String run();

    @Nullable String userGoal();

    @Nullable PlanBundle foreground();

    /**
     * 还没有确认的输入，按加入的顺序（只读）
     */
    Map<String, JsonObject> unacked();

    /**
     * 等待用户回答的问题
     */
    List<JsonObject> questions();

    /**
     * 每个计划的进度计数与最后一条进度（只读）
     */
    Map<String, JsonObject> progress();

    /**
     * 服务端发来的最近聊天
     */
    List<JsonObject> recentChat();

    /**
     * 加入一条未确认的输入
     *
     * @param wakeModel 是否唤醒模型
     */
    void addHot(String id, JsonObject item, boolean wakeModel);

    /**
     * 确认一条输入：移出未确认的输入，并交给模式的 {@link KernelMode#responded}
     */
    void acknowledge(String id);

    /**
     * 启动前台 bundle
     *
     * @return 不能启动时的说明；启动时为 null
     */
    @Nullable String startForeground(BundleSpec spec);

    /**
     * 取消当前的前台 bundle，它结束之后启动 spec
     */
    void replaceForeground(BundleSpec spec, String reason);

    /**
     * 以 COMPLETED 结束当前运行
     */
    void finishGoal(String summary);

    /**
     * 重新读取模式的目录放进上下文
     */
    void catalogChanged();

    /**
     * 把任务交给 kernel 线程执行（异步结果回到 kernel）；可以在任何线程上调用
     */
    void post(Runnable task);
}
