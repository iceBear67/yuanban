package io.ib67.yuanban.codec;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.registry.RegistryTranscoder;

/**
 * 把 Minestom 的编解码器包装成 DataFixerUpper 的编解码器。
 * <p>
 * 只用于结果类型属于 Minestom、原版逻辑庞大的叶子类型，例如组件补丁和附魔效果组件。JSON 按
 * {@link StrictJsonTranscoder} 读取；ops 是 {@link RegistryOps} 时，Minestom 编解码器通过
 * {@link RegistryTranscoder} 看到 {@link RegistryLookup#registries()}。被包装的部分内部仍然只报告第一个错误，
 * 错误文本也是 Minestom 的。
 */
public final class MinestomCodecs {
    private MinestomCodecs() {
    }

    public static <A> Codec<A> adapt(net.minestom.server.codec.Codec<A> codec) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json = asJson(ops, input);
                return switch (codec.decode(transcoder(ops), json)) {
                    case Result.Ok<A>(var value) -> DataResult.success(Pair.of(value, ops.empty()));
                    case Result.Error<A>(var message) -> DataResult.error(() -> message);
                };
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return switch (codec.encode(transcoder(ops), input)) {
                    case Result.Ok<JsonElement>(var json) -> ops.mergeToPrimitive(prefix, JsonOps.INSTANCE.convertTo(ops, json));
                    case Result.Error<JsonElement>(var message) -> DataResult.error(() -> message);
                };
            }

            @Override
            public String toString() {
                return "Minestom[" + codec + "]";
            }
        };
    }

    private static <T> Transcoder<JsonElement> transcoder(DynamicOps<T> ops) {
        if (ops instanceof RegistryOps<T> registryOps) {
            return new RegistryTranscoder<>(StrictJsonTranscoder.INSTANCE, registryOps.lookup().registries());
        }
        return StrictJsonTranscoder.INSTANCE;
    }

    /**
     * 下层 ops 是 {@link JsonOps} 时直接使用输入，否则转换成 JSON。
     */
    private static <T> JsonElement asJson(DynamicOps<T> ops, T input) {
        DynamicOps<T> base = ops;
        while (base instanceof DelegatingOps<T> delegating) {
            base = delegating.delegate();
        }
        if (base instanceof JsonOps) {
            return (JsonElement) input;
        }
        return ops.convertTo(JsonOps.INSTANCE, input);
    }
}
