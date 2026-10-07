package io.ib67.yuanban.enchant;

import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * 一个附魔提供器的解码结果：类型 ID（{@code single}、{@code by_cost} 或者
 * {@code by_cost_with_difficulty} 的短名）加上完整的 JSON 定义。
 */
public record ProviderEntry(String type, JsonObject definition) {
    public ProviderEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(definition, "definition");
        definition = definition.deepCopy();
    }
}
