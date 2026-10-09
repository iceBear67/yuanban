package io.ib67.yuanban.agent.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonObject;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolChoiceOption;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.completions.CompletionUsage;
import io.ib67.yuanban.agent.json.Json;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 通过 OpenAI 官方 Java SDK 的 Chat Completions 接口调用模型，也适用于兼容这个接口的服务（设置 baseUrl）。
 * 函数调用使用 {@code tools} 与 {@code tool_choice}；token 用量取自 {@code usage}，命中缓存的输入取自 {@code prompt_tokens_details.cached_tokens}。
 */
public final class OpenAiChatProvider implements ModelProvider {
    @Getter
    @Accessors(fluent = true)
    @Builder
    public static final class Options {
        /**
         * 为 null 时使用 OpenAI 的地址
         */
        private final @Nullable String baseUrl;
        @NonNull
        private final String apiKey;
        @NonNull
        private final String model;
        /**
         * 模型的上下文窗口
         */
        @Builder.Default
        private final int contextWindow = 128_000;
        /**
         * 每次回应的最大输出 token 数
         */
        @Builder.Default
        private final int maxOutputTokens = 4096;
        /**
         * 为 null 时不设置
         */
        private final @Nullable Double temperature;
        @NonNull
        @Builder.Default
        private final Duration timeout = Duration.ofMinutes(2);
        @Builder.Default
        private final int maxRetries = 2;
    }

    private final Options options;
    private final OpenAIClientAsync client;

    public OpenAiChatProvider(Options options) {
        this.options = options;
        var builder = OpenAIOkHttpClientAsync.builder()
                .apiKey(options.apiKey())
                .timeout(options.timeout())
                .maxRetries(options.maxRetries());
        if (options.baseUrl() != null) {
            builder.baseUrl(options.baseUrl());
        }
        this.client = builder.build();
    }

    @Override
    public String name() {
        return "openai-chat:" + options.model();
    }

    @Override
    public int contextWindow() {
        return options.contextWindow();
    }

    @Override
    public CompletionStage<ModelResponse> complete(ModelRequest request) {
        var params = ChatCompletionCreateParams.builder()
                .model(options.model())
                .maxCompletionTokens(Math.min(request.maxOutputTokens(), options.maxOutputTokens()));
        if (options.temperature() != null) {
            params.temperature(options.temperature());
        }
        for (var message : request.messages()) {
            switch (message) {
                case ModelMessage.System system -> params.addSystemMessage(system.text());
                case ModelMessage.User user -> params.addUserMessage(user.text());
                case ModelMessage.Assistant assistant -> {
                    var builder = ChatCompletionAssistantMessageParam.builder();
                    if (assistant.content() != null) {
                        builder.content(assistant.content());
                    }
                    for (var call : assistant.toolCalls()) {
                        builder.addToolCall(ChatCompletionMessageFunctionToolCall.builder()
                                .id(call.id())
                                .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                        .name(call.name())
                                        .arguments(call.arguments())
                                        .build())
                                .build());
                    }
                    params.addMessage(builder.build());
                }
                case ModelMessage.ToolResult result -> params.addMessage(ChatCompletionToolMessageParam.builder()
                        .toolCallId(result.callId())
                        .content(result.text())
                        .build());
            }
        }
        for (var tool : request.tools()) {
            params.addTool(ChatCompletionFunctionTool.builder()
                    .function(FunctionDefinition.builder()
                            .name(tool.name())
                            .description(tool.description())
                            .parameters(parameters(tool.parameters()))
                            .build())
                    .build());
        }
        if (!request.tools().isEmpty()) {
            params.toolChoice(ChatCompletionToolChoiceOption.ofAuto(request.requireToolCall()
                    ? ChatCompletionToolChoiceOption.Auto.REQUIRED : ChatCompletionToolChoiceOption.Auto.AUTO));
        }
        return client.chat().completions().create(params.build()).thenApply(OpenAiChatProvider::convert);
    }

    @Override
    public void close() {
        client.close();
    }

    private static ModelResponse convert(ChatCompletion completion) {
        if (completion.choices().isEmpty()) {
            throw new IllegalStateException("the model returned no choices (response " + completion.id() + ")");
        }
        var choice = completion.choices().getFirst();
        var message = choice.message();
        var calls = new ArrayList<ToolCall>();
        for (ChatCompletionMessageToolCall call : message.toolCalls().orElse(List.of())) {
            if (!call.isFunction()) {
                throw new IllegalStateException("the model returned a non-function tool call: " + call);
            }
            var function = call.asFunction();
            calls.add(new ToolCall(function.id(), function.function().name(), function.function().arguments()));
        }
        var usage = completion.usage().map(value -> new ModelResponse.TokenUsage(value.promptTokens(), value.completionTokens(),
                value.promptTokensDetails().flatMap(CompletionUsage.PromptTokensDetails::cachedTokens).orElse(0L)))
                .orElse(new ModelResponse.TokenUsage(0, 0, 0));
        return new ModelResponse(message.content().orElse(null), calls, usage, choice.finishReason().asString(), completion.id());
    }

    private static FunctionParameters parameters(JsonObject schema) {
        var builder = FunctionParameters.builder();
        try {
            var node = ObjectMappers.jsonMapper().readTree(Json.write(schema));
            for (var entry : node.properties()) {
                builder.putAdditionalProperty(entry.getKey(), JsonValue.fromJsonNode(entry.getValue()));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot convert a tool schema", e);
        }
        return builder.build();
    }
}
