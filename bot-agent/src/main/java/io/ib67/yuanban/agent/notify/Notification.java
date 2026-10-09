package io.ib67.yuanban.agent.notify;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;

/**
 * 一条通知。
 *
 * @param id      {@code n-<序号>}
 * @param source  发出者，格式同 envelope 的 source
 * @param text    给人看的短文本：TUI 显示它，游戏内只发送它
 * @param payload 完整的结构化内容，写入 trace，不进入游戏聊天
 * @param at      epoch 毫秒
 */
public record Notification(String id, NotifyKind kind, String source, String text, JsonObject payload, long at) {
    public Notification {
        payload = payload.deepCopy();
    }

    @Override
    public JsonObject payload() {
        return payload.deepCopy();
    }

    public JsonObject toJson() {
        return Json.object("id", id, "kind", kind.wire(), "source", source, "text", text, "payload", payload.deepCopy(), "at", at);
    }
}
