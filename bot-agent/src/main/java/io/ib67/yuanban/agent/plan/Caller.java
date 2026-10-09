package io.ib67.yuanban.agent.plan;

import io.ib67.yuanban.agent.tool.BudgetScope;
import io.ib67.yuanban.agent.tool.Capabilities;
import io.ib67.yuanban.agent.workspace.IdempotencyLedger;

/**
 * 调用 ToolGateway 的计划。
 */
public interface Caller {
    /**
     * 计划编号 {@code plan-<n>}
     */
    String planId();

    /**
     * 重放时用于对应记录的稳定键：{@code <模块>@<版本>/<角色>#<同一模块与角色的第几个计划>}
     */
    String planKey();

    /**
     * 幂等账本的归属：计划从检查点重启或在安全点被替换时沿用最初计划的编号
     */
    String lineage();

    /**
     * trace 中标注的来源：{@code foreground:<模块>@<版本>} 或 {@code background:<模块>@<版本>}
     */
    String origin();

    String run();

    PlanRole role();

    Capabilities capabilities();

    BudgetScope budget();

    IdempotencyLedger ledger();
}
