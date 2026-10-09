package io.ib67.yuanban.agent.workspace;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * 一个模块版本：manifest、源码与测试 fixture。
 */
public record Module(ModuleManifest manifest, String source, List<JsonObject> fixtures) {
    public Module {
        fixtures = fixtures.stream().map(JsonObject::deepCopy).toList();
    }

    public ModuleRef ref() {
        return manifest.ref();
    }
}
