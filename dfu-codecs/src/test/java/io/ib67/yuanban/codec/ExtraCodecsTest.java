package io.ib67.yuanban.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.ib67.yuanban.codec.CodecTestSupport.decode;
import static io.ib67.yuanban.codec.CodecTestSupport.encode;
import static io.ib67.yuanban.codec.CodecTestSupport.error;
import static io.ib67.yuanban.codec.CodecTestSupport.json;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ExtraCodecs}、{@link InclusiveRange}、{@link UUIDUtil}：错误文本取自原版 26.2 服务端。
 */
class ExtraCodecsTest {
    @Test
    void intRanges() {
        Codec<Integer> range = ExtraCodecs.intRange(1, 8);
        assertEquals(8, decode(range, "8"));
        assertEquals("Value must be within range [1;8]: 9", error(range, "9"));
        assertEquals("Not a number: \"x\"", error(range, "\"x\""));
        assertEquals("Value must be non-negative: -1", error(ExtraCodecs.NON_NEGATIVE_INT, "-1"));
        assertEquals("Value must be positive: 0", error(ExtraCodecs.POSITIVE_INT, "0"));
        assertEquals("Value must be within range [0.0;1.0]: 1.5", error(ExtraCodecs.floatRange(0.0F, 1.0F), "1.5"));
    }

    @Test
    void unsignedByte() {
        assertEquals(44, decode(ExtraCodecs.UNSIGNED_BYTE, "300"));
        assertEquals(255, decode(ExtraCodecs.UNSIGNED_BYTE, "-1"));
        assertEquals("-1", encode(ExtraCodecs.UNSIGNED_BYTE, 255));
        assertEquals("Unsigned byte was too large: 256 > 255", ExtraCodecs.UNSIGNED_BYTE.encodeStart(JsonOps.INSTANCE, 256)
                .error().orElseThrow().message());
    }

    @Test
    void nonEmptyCollections() {
        Codec<List<Integer>> list = ExtraCodecs.nonEmptyList(Codec.INT.listOf());
        assertEquals("List must have contents", error(list, "[]"));
        assertEquals(List.of(1), decode(list, "[1]"));
        assertEquals("Map must have contents", error(ExtraCodecs.nonEmptyMap(Codec.unboundedMap(Codec.STRING, Codec.INT)), "{}"));
        assertEquals("Map is too long: 2, expected range [0-1]",
                error(ExtraCodecs.sizeLimitedMap(Codec.unboundedMap(Codec.STRING, Codec.INT), 1), "{\"a\":1,\"b\":2}"));
    }

    @Test
    void compactList() {
        Codec<List<Integer>> compact = ExtraCodecs.compactListCodec(Codec.INT);
        assertEquals(List.of(5), decode(compact, "5"));
        assertEquals(List.of(1, 2), decode(compact, "[1, 2]"));
        assertEquals("5", encode(compact, List.of(5)));
        assertEquals("[1,2]", encode(compact, List.of(1, 2)));
        assertEquals("Not a number: \"x\"", error(compact, "[1, \"x\"]"));
    }

    @Test
    void strictUnboundedMapKeepsOrderAndStopsAtFirstError() {
        var codec = ExtraCodecs.strictUnboundedMap(Identifiers.CODEC, Codec.INT);
        Map<Key, Integer> map = decode(codec, "{\"b\": 1, \"a\": 2, \"c\": 3}");
        assertEquals(List.of(Key.key("b"), Key.key("a"), Key.key("c")), List.copyOf(map.keySet()));
        assertEquals("Map entry 'minecraft:c' : Not a number: \"x\"", error(codec, "{\"a:b\": 1, \"c\": \"x\", \"Bad\": 2}"));
        assertEquals("Not a valid resource location: Bad Non [a-z0-9/._-] character in path of location: minecraft:Bad",
                error(codec, "{\"Bad\": 2, \"c\": \"x\"}"));
    }

    @Test
    void pattern() {
        assertEquals("a+", decode(ExtraCodecs.PATTERN, "\"a+\"").pattern());
        assertEquals("Invalid regex pattern '[a': Unclosed character class near index 1\n[a\n ^", error(ExtraCodecs.PATTERN, "\"[a\""));
    }

    @Test
    void colors() {
        assertEquals(-33024, decode(ExtraCodecs.RGB_COLOR_CODEC, "[1.0, 0.5, 0.0]"));
        assertEquals(123, decode(ExtraCodecs.RGB_COLOR_CODEC, "123"));
        assertEquals("Failed to parse either. First: Not a number: [1.0,0.0]; Second: Input is not a list of 3 elements",
                error(ExtraCodecs.RGB_COLOR_CODEC, "[1.0, 0.0]"));
        assertEquals(2147450624, decode(ExtraCodecs.ARGB_COLOR_CODEC, "[1.0, 0.5, 0.0, 0.5]"));
        assertEquals("Failed to parse either. First: Not a number: [1.0,0.5,0.0]; Second: Input is not a list of 4 elements",
                error(ExtraCodecs.ARGB_COLOR_CODEC, "[1.0, 0.5, 0.0]"));
        assertEquals("2147450624", encode(ExtraCodecs.ARGB_COLOR_CODEC, 2147450624));
    }

    @Test
    void tagOrElementId() {
        assertEquals(new ExtraCodecs.TagOrElementLocation(Key.key("foo:bar"), true), decode(ExtraCodecs.TAG_OR_ELEMENT_ID, "\"#foo:bar\""));
        assertEquals(new ExtraCodecs.TagOrElementLocation(Key.key("foo:bar"), false), decode(ExtraCodecs.TAG_OR_ELEMENT_ID, "\"foo:bar\""));
        assertEquals("Not a valid resource location: Foo Non [a-z0-9/._-] character in path of location: minecraft:Foo",
                error(ExtraCodecs.TAG_OR_ELEMENT_ID, "\"#Foo\""));
        assertEquals("\"#foo:bar\"", encode(ExtraCodecs.TAG_OR_ELEMENT_ID, new ExtraCodecs.TagOrElementLocation(Key.key("foo:bar"), true)));
    }

    @Test
    void inclusiveRange() {
        assertEquals(new InclusiveRange<>(1, 2), decode(InclusiveRange.INT, "[1, 2]"));
        assertEquals(new InclusiveRange<>(3, 3), decode(InclusiveRange.INT, "3"));
        assertEquals(new InclusiveRange<>(1, 4), decode(InclusiveRange.INT, "{\"min_inclusive\": 1, \"max_inclusive\": 4}"));
        assertEquals("Failed to parse either. First: Not a number: [3,1]; Second: Failed to parse either. "
                + "First: min_inclusive must be less than or equal to max_inclusive; Second: Not a JSON object: [3,1]", error(InclusiveRange.INT, "[3, 1]"));
        assertEquals("Input is not a list of 2 elements", error(InclusiveRange.INT, "[1, 2, 3]"));
        assertEquals("Failed to parse either. First: Not a number: {\"min_inclusive\":1}; Second: Failed to parse either. "
                        + "First: Not a json array: {\"min_inclusive\":1}; Second: No key max_inclusive in MapLike[{\"min_inclusive\":1}]",
                error(InclusiveRange.INT, "{\"min_inclusive\": 1}"));
        Codec<InclusiveRange<Integer>> limited = InclusiveRange.codec(Codec.INT, 0, 10);
        assertEquals("Range limit too low, expected at least 0 [-1-5]", error(limited, "[-1, 5]"));
        assertEquals("Range limit too high, expected at most 10 [1-11]", error(limited, "[1, 11]"));
        assertEquals("3", encode(InclusiveRange.INT, new InclusiveRange<>(3, 3)));
        assertEquals("[1,2]", encode(InclusiveRange.INT, new InclusiveRange<>(1, 2)));
    }

    @Test
    void uuids() {
        UUID uuid = new UUID(1L << 32 | 2, 3L << 32 | 4);
        assertEquals(uuid, decode(UUIDUtil.LENIENT_CODEC, "[1, 2, 3, 4]"));
        assertEquals(uuid, decode(UUIDUtil.LENIENT_CODEC, "\"00000001-0000-0002-0000-000300000004\""));
        assertEquals("[1,2,3,4]", encode(UUIDUtil.LENIENT_CODEC, uuid));
        assertEquals("Failed to parse either. First: Not a json array: \"not-a-uuid\"; Second: Invalid UUID not-a-uuid: "
                + "Invalid UUID string: not-a-uuid", error(UUIDUtil.LENIENT_CODEC, "\"not-a-uuid\""));
        assertEquals("Input is not a list of 4 ints", error(UUIDUtil.LENIENT_CODEC, "[1, 2, 3, 4, 5]"));
        assertEquals(uuid, UUIDUtil.LENIENT_CODEC.parse(JsonOps.INSTANCE, json("[1, 2, 3, 4, 5]")).resultOrPartial().orElseThrow());
    }

    @Test
    void strings() {
        assertEquals("Disallowed chat character: '\u0007'", error(ExtraCodecs.CHAT_STRING, "\"a\\u0007\""));
        assertEquals("Disallowed chat character: '§'", error(ExtraCodecs.CHAT_STRING, "\"§a\""));
        assertEquals("/say hi", decode(ExtraCodecs.CHAT_STRING, "\"/say hi\""));
        assertEquals(URI.create("https://example.com"), decode(ExtraCodecs.UNTRUSTED_URI, "\"https://example.com\""));
        assertEquals("Expected closing bracket for IPv6 address at index 11: http://[::1", error(ExtraCodecs.UNTRUSTED_URI, "\"http://[::1\""));
        assertEquals("Missing protocol in URI: example.com: example.com", error(ExtraCodecs.UNTRUSTED_URI, "\"example.com\""));
        assertEquals("Unsupported protocol in URI: ftp://x: ftp://x", error(ExtraCodecs.UNTRUSTED_URI, "\"ftp://x\""));
        assertEquals("String \"aaaaaaaaaaaaaaaaa\" is too long: 17, expected range [0-16]", error(ExtraCodecs.PLAYER_NAME, "\"aaaaaaaaaaaaaaaaa\""));
        assertEquals("Player name contained disallowed characters: 'a b'", error(ExtraCodecs.PLAYER_NAME, "\"a b\""));
        assertEquals("Expected non-empty string", error(ExtraCodecs.NON_EMPTY_STRING, "\"\""));
    }

    @Test
    void javaConverter() {
        assertEquals((byte) 1, decode(ExtraCodecs.JAVA, "1"));
        assertEquals(1.5F, decode(ExtraCodecs.JAVA, "1.5"));
        assertEquals(List.of("a", true), decode(ExtraCodecs.JAVA, "[\"a\", true]"));
        assertEquals("{\"a\":1}", encode(ExtraCodecs.JAVA, Map.of("a", 1)));
    }

    @Test
    void optionalAlwaysPresentField() {
        MapCodec<Integer> field = ExtraCodecs.optionalAlwaysPresentFieldOf(Codec.INT, "count", 1);
        assertEquals(1, decode(field.codec(), "{}"));
        assertEquals("{\"count\":1}", encode(field.codec(), 1));
    }

    @Test
    void dispatchOptionalValue() {
        Codec<Long> unit = MapCodec.unit(7L).codec();
        var types = new ExtraCodecs.LateBoundIdMapper<String, Codec<? extends Number>>()
                .put("int", Codec.INT)
                .put("map", unit);
        Codec<Number> codec = ExtraCodecs.<Codec<? extends Number>, Number>dispatchOptionalValue("type", "value",
                types.codec(Codec.STRING), number -> number instanceof Integer ? Codec.INT : unit, c -> c).codec();
        assertEquals(5, decode(codec, "{\"type\": \"int\", \"value\": 5}"));
        // 值缺失时按空对象解码
        assertEquals(7L, decode(codec, "{\"type\": \"map\"}"));
        assertEquals("Missing \"type\" in: MapLike[{\"value\":5}]", error(codec, "{\"value\": 5}"));
        assertEquals("Unknown element id: nope", error(codec, "{\"type\": \"nope\"}"));
    }
}
