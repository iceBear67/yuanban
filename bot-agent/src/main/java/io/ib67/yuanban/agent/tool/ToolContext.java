package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.bot.ActionSpec;
import io.ib67.yuanban.agent.bot.BotFacade;
import io.ib67.yuanban.agent.bot.PendingAction;
import io.ib67.yuanban.agent.bot.PendingWait;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.view.GameView;
import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.ChunkNotLoadedException;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.function.Function;

/**
 * 工具执行时的上下文：参数、取消与 bot，以及把 bot 的结果转换为 {@link ToolResult} 的方法。
 */
public final class ToolContext {
    private final BotFacade facade;
    private final ToolInvocation invocation;
    private final CancelToken cancel;
    private final AgentConfigurator configurator;
    private final ToolPolicy policy;

    public ToolContext(BotFacade facade, ToolInvocation invocation, CancelToken cancel, AgentConfigurator configurator,
                       ToolPolicy policy) {
        this.facade = facade;
        this.invocation = invocation;
        this.cancel = cancel;
        this.configurator = configurator;
        this.policy = policy;
    }

    public ToolPolicy policy() {
        return policy;
    }

    public JsonObject args() {
        return invocation.args();
    }

    public Duration timeout() {
        return invocation.timeout();
    }

    public BotFacade facade() {
        return facade;
    }

    public AgentConfigurator configurator() {
        return configurator;
    }

    /**
     * 提交动作（调用方给出 observer 时带上它）并等到结束
     */
    public ToolResult action(ActionSpec action) {
        PendingAction pending;
        try {
            pending = facade.start(action, invocation.observer());
        } catch (BotDisconnectedException e) {
            return ToolResult.of(ResultKind.DISCONNECTED, e.getMessage());
        } catch (BotFailureException e) {
            return ToolResult.of(ResultKind.BOT_FAILURE, String.valueOf(e.getCause()));
        } catch (IllegalArgumentException e) {
            return ToolResult.of(ResultKind.INVALID_ARGUMENT, e.getMessage());
        }
        cancel.onCancel(pending::cancel);
        var outcome = pending.await(timeout());
        var observation = outcome.observation();
        var result = switch (outcome.status()) {
            case COMPLETED -> ToolResult.ok(observation == null ? Json.object("completed", true) : observation);
            case CANCELLED -> cancel.cancelled()
                    ? ToolResult.of(ResultKind.CANCELLED, cancel.reason(), observation)
                    : ToolResult.of(ResultKind.ACTION_CANCELLED, outcome.cancelReason(), observation);
            case DISCONNECTED -> ToolResult.of(ResultKind.DISCONNECTED, message(outcome.error()));
            case FAILED -> ToolResult.of(ResultKind.BOT_FAILURE, message(outcome.error()));
            case TIMEOUT -> ToolResult.of(ResultKind.TIMEOUT, "no result within " + timeout(), observation);
        };
        return result.withObservation(playerObservation());
    }

    /**
     * 读取状态。未加载的区块为 CHUNK_NOT_LOADED，不在世界中为 NOT_IN_WORLD
     */
    public ToolResult read(Function<GameView, ToolResult> reader) {
        try {
            return facade.read(reader);
        } catch (ChunkNotLoadedException e) {
            return ToolResult.of(ResultKind.CHUNK_NOT_LOADED, e.getMessage());
        } catch (IllegalStateException e) {
            return ToolResult.of(ResultKind.NOT_IN_WORLD, e.getMessage());
        } catch (BotFailureException e) {
            return ToolResult.of(ResultKind.BOT_FAILURE, String.valueOf(e.getCause()));
        }
    }

    public ToolResult readValue(Function<GameView, @Nullable JsonElement> reader) {
        return read(view -> ToolResult.ok(reader.apply(view)));
    }

    /**
     * 等到条件成立；超时为 WAIT_TIMEOUT
     */
    public ToolResult waitFor(Condition condition) {
        PendingWait pending;
        try {
            pending = facade.startWait(condition);
        } catch (BotDisconnectedException e) {
            return ToolResult.of(ResultKind.DISCONNECTED, e.getMessage());
        } catch (BotFailureException e) {
            return ToolResult.of(ResultKind.BOT_FAILURE, String.valueOf(e.getCause()));
        }
        cancel.onCancel(pending::cancel);
        var outcome = pending.await(timeout());
        return switch (outcome.status()) {
            case MET -> ToolResult.ok(Json.object("met", true));
            case TIMEOUT -> ToolResult.of(ResultKind.WAIT_TIMEOUT, condition.describe());
            case CANCELLED -> ToolResult.of(ResultKind.CANCELLED, cancel.reason());
            case DISCONNECTED -> ToolResult.of(ResultKind.DISCONNECTED, message(outcome.error()));
            case FAILED -> ToolResult.of(ResultKind.BOT_FAILURE, message(outcome.error()));
        };
    }

    private @Nullable JsonObject playerObservation() {
        if (facade.ended()) {
            return null;
        }
        try {
            return facade.read(view -> {
                var player = view.player();
                return player == null ? null : Json.object("position", player.position(), "onGround", player.onGround(),
                        "health", player.health(), "dimension", player.dimension());
            });
        } catch (BotFailureException e) {
            return null;
        }
    }

    private static @Nullable String message(@Nullable Throwable error) {
        return error == null ? null : error.getMessage();
    }
}
