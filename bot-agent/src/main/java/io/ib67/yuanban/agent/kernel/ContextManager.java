package io.ib67.yuanban.agent.kernel;

import com.google.gson.JsonObject;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.model.ModelMessage;
import io.ib67.yuanban.agent.model.ToolDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * supervisor 的上下文。只压缩这里的对话，不影响脚本、收件箱与动作。一次请求的消息依次为：
 * <ol>
 *     <li>pinned：system prompt、参考文档、agent profile（稳定的前缀，供应商可以缓存）；</li>
 *     <li>目录：workspace 中的模块（模块变化时才变化）；</li>
 *     <li>压缩摘要（有时）：{@code supervisor-context/1} 结构的摘要与冷数据引用；</li>
 *     <li>之前的回合；</li>
 *     <li>本回合的输入：当前的 {@code supervisor-context/1}。</li>
 * </ol>
 * emergency 时只发送 pinned 与本回合的精简输入。只在 AgentKernel 线程上使用。
 */
final class ContextManager {
    static final double SOFT = 0.60;
    static final double HARD = 0.80;
    static final double EMERGENCY = 0.92;
    /**
     * 压缩时保留的最近回合数
     */
    static final int KEEP_TURNS = 2;

    private static final Encoding ENCODING = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

    enum Pressure {
        NORMAL,
        SOFT,
        HARD,
        EMERGENCY
    }

    /**
     * 一个回合加入对话的消息
     *
     * @param traceFrom 这个回合的第一条 trace 记录
     * @param traceTo   这个回合的最后一条 trace 记录
     */
    record Turn(long number, List<ModelMessage> messages, int tokens, long traceFrom, long traceTo) {
        Turn {
            messages = List.copyOf(messages);
        }
    }

    /**
     * 后台生成的压缩候选
     *
     * @param turns           要折叠的回合数（从最早的开始）
     * @param basedOnEventSeq 生成时 AgentKernel 的事件序号；提交时序号已经变化则丢弃
     */
    record Candidate(JsonObject summary, int turns, long basedOnEventSeq) {
    }

    private final int window;
    private final ModelMessage pinned;
    private final int toolTokens;
    private final List<Turn> turns = new ArrayList<>();
    private ModelMessage catalog = new ModelMessage.System("");
    private @Nullable ModelMessage summary;

    ContextManager(int window, String pinned, List<ToolDefinition> tools) {
        this.window = window;
        this.pinned = new ModelMessage.System(pinned);
        var toolText = new StringBuilder();
        for (var tool : tools) {
            toolText.append(tool.name()).append(tool.description()).append(Json.write(tool.parameters()));
        }
        this.toolTokens = count(toolText.toString());
    }

    static int count(String text) {
        return ENCODING.countTokens(text);
    }

    void catalog(String text) {
        catalog = new ModelMessage.System(text);
    }

    /**
     * 本回合的请求消息
     *
     * @param current 本回合的输入
     */
    List<ModelMessage> messages(String current, boolean emergency) {
        var messages = new ArrayList<ModelMessage>();
        messages.add(pinned);
        if (!emergency) {
            messages.add(catalog);
            if (summary != null) {
                messages.add(summary);
            }
            for (var turn : turns) {
                messages.addAll(turn.messages());
            }
        }
        messages.add(new ModelMessage.User(current));
        return messages;
    }

    int estimate(List<ModelMessage> messages) {
        var total = toolTokens;
        for (var message : messages) {
            total += count(message.text()) + 4;
        }
        return total;
    }

    Pressure pressure(int tokens) {
        var ratio = (double) tokens / window;
        if (ratio >= EMERGENCY) {
            return Pressure.EMERGENCY;
        }
        if (ratio >= HARD) {
            return Pressure.HARD;
        }
        return ratio >= SOFT ? Pressure.SOFT : Pressure.NORMAL;
    }

    void append(Turn turn) {
        turns.add(turn);
    }

    List<Turn> turns() {
        return List.copyOf(turns);
    }

    /**
     * 可以折叠的回合数（保留最近的 {@link #KEEP_TURNS} 个）
     */
    int compactable() {
        return Math.max(0, turns.size() - KEEP_TURNS);
    }

    boolean hasSummary() {
        return summary != null;
    }

    /**
     * 比较并提交：AgentKernel 的事件序号在生成候选之后变化时丢弃候选
     *
     * @return 是否提交
     */
    boolean commit(Candidate candidate, long eventSeq) {
        if (candidate.basedOnEventSeq() != eventSeq || candidate.turns() > turns.size()) {
            return false;
        }
        turns.subList(0, candidate.turns()).clear();
        summary = new ModelMessage.System("【compaction 摘要】之前的回合已经压缩为下面的 supervisor-context/1 摘要；"
                + "完整内容可以用 read_reference 读取 coldRefs。\n" + Json.PRETTY.toJson(candidate.summary()));
        return true;
    }

    int window() {
        return window;
    }
}
