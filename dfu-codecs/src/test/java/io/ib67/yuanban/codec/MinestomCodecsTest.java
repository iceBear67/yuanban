package io.ib67.yuanban.codec;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.minestom.server.dialog.Dialog;
import net.minestom.server.registry.Holder;
import net.minestom.server.registry.RegistryKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static io.ib67.yuanban.codec.CodecTestSupport.decode;
import static io.ib67.yuanban.codec.CodecTestSupport.encode;
import static io.ib67.yuanban.codec.CodecTestSupport.error;
import static io.ib67.yuanban.codec.CodecTestSupport.registryOps;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
class MinestomCodecsTest {
    @Test
    void decodesWithStrictJson() {
        Codec<String> string = MinestomCodecs.adapt(net.minestom.server.codec.Codec.STRING);
        assertEquals("a", decode(string, "\"a\""));
        // 与 JsonOps 相同，数字不是字符串
        assertEquals("Not a string: 5", error(string, "5"));
        assertEquals("\"a\"", encode(string, "a"));
    }

    @Test
    void convertsOtherOpsToJson() {
        Codec<Integer> integer = MinestomCodecs.adapt(net.minestom.server.codec.Codec.INT);
        assertEquals(5, integer.parse(NbtOps.INSTANCE, IntBinaryTag.intBinaryTag(5)).getOrThrow());
    }

    @Test
    void registryDependentCodecSeesRegistries(Env env) {
        Codec<Holder<Dialog>> dialog = MinestomCodecs.adapt(Dialog.CODEC);
        DynamicOps<JsonElement> ops = registryOps(env.process());
        assertEquals(RegistryKey.<Dialog>of("minecraft:server_links"), decode(dialog, ops, "\"minecraft:server_links\""));
        assertEquals("\"minecraft:server_links\"", encode(dialog, ops, RegistryKey.of("minecraft:server_links")));
        // 没有 RegistryOps 时 Minestom 的编解码器看不到注册表
        error(dialog, JsonOps.INSTANCE, "\"minecraft:server_links\"");
    }
}
