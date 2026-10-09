package io.ib67.yuanban.agent.model;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceReader;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 确定性重放的模型：按顺序返回 trace 中 {@code model_response_meta} 记录的回应。记录用完时以异常结束。
 */
public final class ReplayModelProvider implements ModelProvider {
    private final String name;
    private final int contextWindow;
    private final ArrayDeque<JsonObject> responses = new ArrayDeque<>();

    public ReplayModelProvider(TraceReader trace) {
        var header = trace.records().getFirst();
        var model = Json.objectOrNull(header, "model");
        this.name = "replay:" + (model == null ? "unknown" : Json.string(model, "name"));
        this.contextWindow = model == null ? 128_000 : (int) Json.longOr(model, "contextWindow", 128_000);
        for (var record : trace.ofKind(TraceKind.MODEL_RESPONSE_META)) {
            if (record.has("response")) {
                responses.add(record.getAsJsonObject("response"));
            }
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int contextWindow() {
        return contextWindow;
    }

    @Override
    public synchronized CompletionStage<ModelResponse> complete(ModelRequest request) {
        var next = responses.poll();
        if (next == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("the trace has no more recorded model responses"));
        }
        return CompletableFuture.completedFuture(ModelResponse.fromJson(next));
    }

    public synchronized int remaining() {
        return responses.size();
    }
}
