package io.ib67.yuanban.agent.kernel;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentIntent;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.model.ToolDefinition;
import io.ib67.yuanban.agent.plan.BundleSpec;
import io.ib67.yuanban.agent.plan.PlanActor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * AgentKernel 的模式：模型看到的固定前缀、查询函数、决策 schema、目录与上下文，本模式特有的决策，以及 kernel 生命周期中由模式决定的动作。
 * 全部方法在 kernel 线程上调用。
 */
interface KernelMode {
    enum ContextKind {
        /**
         * 每个模型回合的输入
         */
        TURN,
        /**
         * compaction 摘要
         */
        SUMMARY,
        /**
         * 上下文接近窗口上限时的精简输入
         */
        EMERGENCY
    }

    /**
     * 固定前缀：system prompt、参考文档与 profile
     */
    String pinned();

    /**
     * 模型可以调用的查询函数与 {@code submit_decision}
     */
    List<ToolDefinition> tools();

    JsonSchema decisionSchema();

    /**
     * 上下文中的目录，每个回合开始时与 workspace 变化时重新读取
     */
    String catalog();

    JsonObject context(ContextKind kind);

    /**
     * 应用本模式特有的决策（ask 与 say 由 kernel 处理）
     *
     * @return 不能执行时的说明；执行时为 null
     */
    @Nullable String apply(String type, JsonObject decision, String reason);

    /**
     * 正在运行的 background plan
     */
    default List<PlanActor> background() {
        return List.of();
    }

    /**
     * 用户提交了新目标
     */
    default void goalSubmitted(AgentIntent intent) {
    }

    /**
     * bot 第一次加载
     */
    default void loaded() {
    }

    /**
     * 用户停止或暂停、预算耗尽、玩家死亡
     *
     * @param stopReason 需要在这里停止 background plan 时的原因；用户停止时为 null（由 {@link #stopping} 停止）
     */
    default void hold(@Nullable String stopReason) {
    }

    /**
     * 用户恢复
     *
     * @param idle 没有前台 bundle 并且 bot 已经加载
     */
    default void resumed(boolean idle) {
    }

    default void foregroundStarted(BundleSpec spec) {
    }

    /**
     * 前台 bundle 结束，没有排队等待启动的 bundle
     */
    default void foregroundEnded() {
    }

    /**
     * 停止全部计划：在取消前台 bundle 之后、停止其余计划之前调用
     */
    default void stopping(String reason) {
    }

    /**
     * 一条需要回应的事件（question、attention、fault、interest）进入了上下文
     */
    default void expectResponse(String messageId) {
    }

    /**
     * 一条事件得到了回应或被确认
     */
    default void responded(String messageId) {
    }

    /**
     * 计划写下了检查点
     */
    default void checkpointed(String planId) {
    }
}
