package io.ib67.yuanban.agent.workspace;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本可重放的运行检查点。重启只能从最近的检查点继续：不重复账本中已经完成的副作用。
 *
 * @param id              {@code cp-<n>}
 * @param name            脚本给出的名称（循环位置、阶段）
 * @param mailboxCursor   脚本最后取出的消息的序号
 * @param data            脚本的局部状态（目标、已完成的动作、observer 结论、重试次数、下一步）
 * @param inFlightActions 写检查点时还没有结束的工具调用编号
 * @param ledger          写检查点时幂等账本的内容
 */
public record Checkpoint(String id, String planId, String lineage, ModuleRef module, String sourceHash, String name,
                         long mailboxCursor, JsonElement data, List<String> inFlightActions, JsonObject ledger, long at) {
    public Checkpoint {
        data = data.deepCopy();
        inFlightActions = List.copyOf(inFlightActions);
        ledger = ledger.deepCopy();
    }

    public JsonObject toJson() {
        return Json.object("id", id, "planId", planId, "lineage", lineage, "module", module.toString(),
                "sourceHash", sourceHash, "name", name, "mailboxCursor", mailboxCursor, "data", data.deepCopy(),
                "inFlightActions", inFlightActions, "ledger", ledger.deepCopy(), "at", at);
    }

    public static Checkpoint fromJson(JsonObject json) {
        var inFlight = new ArrayList<String>();
        json.getAsJsonArray("inFlightActions").forEach(element -> inFlight.add(element.getAsString()));
        return new Checkpoint(Json.requireString(json, "id"), Json.requireString(json, "planId"),
                Json.requireString(json, "lineage"), ModuleRef.parse(Json.requireString(json, "module")),
                Json.requireString(json, "sourceHash"), Json.requireString(json, "name"),
                json.get("mailboxCursor").getAsLong(), json.get("data"), inFlight, json.getAsJsonObject("ledger"),
                json.get("at").getAsLong());
    }

    /**
     * 给 supervisor 看的摘要（不含局部状态与账本内容）
     */
    public JsonObject summary() {
        return Json.object("id", id, "plan", planId, "module", module.toString(), "name", name,
                "mailboxCursor", mailboxCursor, "inFlightActions", inFlightActions, "ledgerEntries", ledger.size(), "at", at);
    }
}
