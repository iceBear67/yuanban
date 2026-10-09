package io.ib67.yuanban.agent.bot;

import com.google.gson.JsonObject;

/**
 * 一条低成本信号。
 *
 * @param topic {@code moved}、{@code teleported}、{@code health}、{@code damage}、{@code chat}、{@code world_entered}、
 *              {@code player_died}、{@code chunks}、{@code loaded}、{@code disconnected}
 * @param tick  产生时 bot 已经执行的 tick 数
 */
public record Signal(String topic, JsonObject data, long tick) {
    public Signal {
        data = data.deepCopy();
    }

    @Override
    public JsonObject data() {
        return data.deepCopy();
    }
}
