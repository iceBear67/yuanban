package io.ib67.yuanban.codec;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minestom.server.registry.Registries;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 测试中解码、编码 JSON 的辅助方法。期望的错误文本取自原版 26.2 服务端对同一输入的解码结果。
 */
final class CodecTestSupport {
    private CodecTestSupport() {
    }

    static JsonElement json(String json) {
        return JsonParser.parseString(json);
    }

    /**
     * 带有 {@code registries} 中全部注册表的 {@link RegistryOps}。
     */
    static DynamicOps<JsonElement> registryOps(Registries registries) {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryLookup.builder(registries).build());
    }

    static <T> T decode(Codec<T> codec, String json) {
        return decode(codec, JsonOps.INSTANCE, json);
    }

    static <T> T decode(Codec<T> codec, DynamicOps<JsonElement> ops, String json) {
        DataResult<T> result = codec.parse(ops, json(json));
        if (result.isError()) {
            fail("Expected " + json + " to decode, got: " + result.error().orElseThrow().message());
        }
        return result.getOrThrow();
    }

    static String error(Codec<?> codec, String json) {
        return error(codec, JsonOps.INSTANCE, json);
    }

    static String error(Codec<?> codec, DynamicOps<JsonElement> ops, String json) {
        DataResult<?> result = codec.parse(ops, json(json));
        assertTrue(result.isError(), () -> "Expected " + json + " to be rejected, got: " + result.getOrThrow());
        return result.error().orElseThrow().message();
    }

    static <T> String encode(Codec<T> codec, T value) {
        return encode(codec, JsonOps.INSTANCE, value);
    }

    static <T> String encode(Codec<T> codec, DynamicOps<JsonElement> ops, T value) {
        return codec.encodeStart(ops, value).getOrThrow().toString();
    }
}
