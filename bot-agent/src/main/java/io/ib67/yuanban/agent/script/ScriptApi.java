package io.ib67.yuanban.agent.script;

import java.util.concurrent.CompletableFuture;

/**
 * 脚本主机方法的实现方（自治计划）。全部参数与返回值都是 JSON 文本：不把 Java 对象交给脚本。
 * 同步方法返回 {@code {"ok": true, "value": ...}} 或 {@code {"ok": false, "error": "..."}}，由前导脚本转换为返回值或 JS 异常；
 * 异步方法返回的 future 以交给脚本的结果 JSON 完成，在脚本线程上结算。
 */
public interface ScriptApi {
    /**
     * @param observerJson 没有 observer 时为 null
     * @return 以 ToolResult 的 JSON 完成
     */
    CompletableFuture<String> invoke(String tool, String argsJson, String observerJson);

    /**
     * @return 以消息 JSON 完成；超时或信箱关闭时以 {@code null} 完成
     */
    CompletableFuture<String> inboxNext(String filterJson);

    String inboxPoll(String filterJson);

    String emit(String type, String specJson);

    String notify(String kind, String payloadJson);

    String checkpoint(String name, String dataJson);

    String budget(String limitsJson);

    CompletableFuture<String> sleep(long millis);

    CompletableFuture<String> workspace(String op, String argsJson);

    CompletableFuture<String> bundle(String op, String argsJson);

    String ledger(String op, String key, String dataJson);

    boolean cancelled();

    void log(String level, String message);
}
