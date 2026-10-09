package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonElement;

import java.util.Set;

/**
 * {@code external.configure} 修改的 agent 运行时配置。
 */
public interface AgentConfigurator {
    /**
     * 可以修改的键
     */
    Set<String> keys();

    /**
     * @throws IllegalArgumentException 没有这个键，或者值的类型不对
     */
    void set(String key, JsonElement value);
}
