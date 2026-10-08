package io.ib67.yuanban.codec;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.nbt.BinaryTag;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.ib67.yuanban.codec.CodecTestSupport.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * {@link StrictJsonTranscoder} 与 DataFixerUpper {@link JsonOps} 对同一组输入的结果相同。
 */
class StrictJsonTranscoderTest {
    private static final List<String> INPUTS = List.of("\"a\"", "\"5\"", "\"true\"", "5", "5.5", "-1", "300", "true", "false",
            "null", "[1]", "{\"a\": 1}");

    /**
     * 对每个输入比较 Minestom 编解码器（经 {@link StrictJsonTranscoder}）与对应的 DataFixerUpper 编解码器：
     * 成功与否相同，成功时的值相同。
     */
    private static <T> void assertSameAsJsonOps(net.minestom.server.codec.Codec<T> minestom, Codec<T> dfu) {
        for (String input : INPUTS) {
            JsonElement element = json(input);
            Result<T> strict = minestom.decode(StrictJsonTranscoder.INSTANCE, element);
            DataResult<T> jsonOps = dfu.parse(JsonOps.INSTANCE, element);
            switch (strict) {
                case Result.Ok<T>(T value) -> assertEquals(jsonOps.result().orElse(null), value, input);
                case Result.Error<T>(String message) -> assertEquals(true, jsonOps.isError(), input + " -> " + message);
            }
        }
    }

    @Test
    void stringsOnlyAcceptJsonStrings() {
        assertSameAsJsonOps(net.minestom.server.codec.Codec.STRING, Codec.STRING);
        assertEquals(new Result.Error<>("Not a string: 5"), StrictJsonTranscoder.INSTANCE.getString(json("5")));
        // Minestom 自己的 JSON Transcoder 把任何原始值都读作字符串
        assertEquals(new Result.Ok<>("5"), Transcoder.JSON.getString(json("5")));
    }

    @Test
    void booleansOnlyAcceptJsonBooleans() {
        assertSameAsJsonOps(net.minestom.server.codec.Codec.BOOLEAN, Codec.BOOL);
        assertEquals(new Result.Error<>("Not a boolean: \"true\""), StrictJsonTranscoder.INSTANCE.getBoolean(json("\"true\"")));
        assertEquals(new Result.Ok<>(true), Transcoder.JSON.getBoolean(json("\"true\"")));
    }

    @Test
    void numbersMatchJsonOps() {
        assertSameAsJsonOps(net.minestom.server.codec.Codec.INT, Codec.INT);
        assertSameAsJsonOps(net.minestom.server.codec.Codec.LONG, Codec.LONG);
        assertSameAsJsonOps(net.minestom.server.codec.Codec.DOUBLE, Codec.DOUBLE);
        assertSameAsJsonOps(net.minestom.server.codec.Codec.BYTE, Codec.BYTE);
    }

    private record Holder(int value) {
    }

    @Test
    void nullFieldsAreAbsent() {
        var optional = StructCodec.struct("a", net.minestom.server.codec.Codec.INT.optional(7), Holder::value, Holder::new);
        Codec<Integer> dfuOptional = Codec.INT.optionalFieldOf("a", 7).codec();
        assertEquals(new Result.Ok<>(new Holder(7)), optional.decode(StrictJsonTranscoder.INSTANCE, json("{\"a\": null}")));
        assertEquals(7, dfuOptional.parse(JsonOps.INSTANCE, json("{\"a\": null}")).getOrThrow());
        var required = StructCodec.struct("a", net.minestom.server.codec.Codec.INT, Holder::value, Holder::new);
        assertInstanceOf(Result.Error.class, required.decode(StrictJsonTranscoder.INSTANCE, json("{\"a\": null}")));
        assertEquals(true, Codec.INT.fieldOf("a").codec().parse(JsonOps.INSTANCE, json("{\"a\": null}")).isError());
        assertEquals(List.of("b"), StrictJsonTranscoder.INSTANCE.getMap(json("{\"a\": null, \"b\": 1}")).orElseThrow().keys());
        // Minestom 自己的 JSON Transcoder 把值为 null 的字段作为存在的字段
        assertEquals(true, Transcoder.JSON.getMap(json("{\"a\": null}")).orElseThrow().hasValue("a"));
    }

    @Test
    void convertToNarrowsNumbersLikeJsonOps() {
        for (String input : List.of("1", "300", "70000", "3000000000", "1.5", "0.1", "1e400", "[1, 300, 1.5]",
                "{\"a\": 1, \"b\": {\"c\": 2.5}}", "\"s\"", "true")) {
            JsonElement element = json(input);
            BinaryTag strict = StrictJsonTranscoder.INSTANCE.convertTo(Transcoder.NBT, element).orElseThrow();
            BinaryTag jsonOps = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, element);
            assertEquals(jsonOps, strict, input);
        }
        // Minestom 自己的 JSON Transcoder 把全部数字转换成 double
        assertEquals(net.kyori.adventure.nbt.DoubleBinaryTag.doubleBinaryTag(1),
                Transcoder.JSON.convertTo(Transcoder.NBT, json("1")).orElseThrow());
    }
}
