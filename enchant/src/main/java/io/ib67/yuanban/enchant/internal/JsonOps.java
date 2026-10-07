package io.ib67.yuanban.enchant.internal;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryTranscoder;
import org.jetbrains.annotations.ApiStatus;

/**
 * 用 Minestom 的编解码器读取数据包 JSON 的入口。和原版 {@code RegistryOps} 的作用相同，
 * 通过 {@link RegistryTranscoder} 让编解码器在解码注册表引用和联合类型字段的时候能看到进程的注册表。
 */
@ApiStatus.Internal
public final class JsonOps {
    private JsonOps() {
    }

    /**
     * 按 {@code codec} 解码 {@code json}。
     */
    public static <T> Result<T> decode(Codec<T> codec, Registries registries, JsonElement json) {
        return codec.decode(new RegistryTranscoder<>(Transcoder.JSON, registries), json);
    }
}
