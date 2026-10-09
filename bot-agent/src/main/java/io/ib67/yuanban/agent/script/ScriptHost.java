package io.ib67.yuanban.agent.script;

import org.graalvm.polyglot.HostAccess;

/**
 * 交给脚本的唯一主机对象，前导脚本取走之后从全局作用域删除。方法没有重载，参数与返回值只有字符串、数字与布尔值。
 * 异步方法返回调用编号，结果由 {@link ScriptRunner} 在脚本线程上结算。
 */
public final class ScriptHost {
    private final ScriptApi api;
    private final ScriptRunner runner;

    ScriptHost(ScriptApi api, ScriptRunner runner) {
        this.api = api;
        this.runner = runner;
    }

    /**
     * @param observerJson 没有 observer 时为空字符串
     */
    @HostAccess.Export
    public String invoke(String tool, String argsJson, String observerJson) {
        return runner.track(api.invoke(tool, argsJson, observerJson.isEmpty() ? null : observerJson));
    }

    @HostAccess.Export
    public String inboxNext(String filterJson) {
        return runner.track(api.inboxNext(filterJson));
    }

    @HostAccess.Export
    public String inboxPoll(String filterJson) {
        return api.inboxPoll(filterJson);
    }

    @HostAccess.Export
    public String emit(String type, String specJson) {
        return api.emit(type, specJson);
    }

    @HostAccess.Export
    public String notify(String kind, String payloadJson) {
        return api.notify(kind, payloadJson);
    }

    @HostAccess.Export
    public String checkpoint(String name, String dataJson) {
        return api.checkpoint(name, dataJson);
    }

    @HostAccess.Export
    public String budget(String limitsJson) {
        return api.budget(limitsJson);
    }

    @HostAccess.Export
    public String sleep(double millis) {
        return runner.track(api.sleep((long) millis));
    }

    @HostAccess.Export
    public String workspace(String op, String argsJson) {
        return runner.track(api.workspace(op, argsJson));
    }

    @HostAccess.Export
    public String bundle(String op, String argsJson) {
        return runner.track(api.bundle(op, argsJson));
    }

    @HostAccess.Export
    public String ledger(String op, String key, String dataJson) {
        return api.ledger(op, key, dataJson);
    }

    @HostAccess.Export
    public boolean cancelled() {
        return api.cancelled();
    }

    @HostAccess.Export
    public void log(String level, String message) {
        api.log(level, message);
    }

    @HostAccess.Export
    public void finish(String resultJson) {
        runner.finish(resultJson);
    }

    @HostAccess.Export
    public void fail(String error) {
        runner.fail(error);
    }
}
