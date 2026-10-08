package io.ib67.yuanban.codec;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.ByteArrayBinaryTag;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.EndBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.LongArrayBinaryTag;
import net.kyori.adventure.nbt.LongBinaryTag;
import net.kyori.adventure.nbt.ShortBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.ib67.yuanban.codec.CodecTestSupport.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link NbtOps} 与 {@link StringTagVisitor}：行为和错误文本与原版 {@code NbtOps} 相同。
 */
class NbtOpsTest {
    private static final NbtOps OPS = NbtOps.INSTANCE;

    private static ListBinaryTag list(BinaryTag... tags) {
        var builder = ListBinaryTag.heterogeneousListBinaryTag();
        for (BinaryTag tag : tags) {
            builder.add(tag);
        }
        return builder.build();
    }

    @Test
    void primitives() {
        assertEquals(ByteBinaryTag.byteBinaryTag((byte) 1), OPS.createBoolean(true));
        assertEquals(true, OPS.getBooleanValue(ShortBinaryTag.shortBinaryTag((short) 2)).getOrThrow());
        assertEquals(false, OPS.getBooleanValue(DoubleBinaryTag.doubleBinaryTag(0.0)).getOrThrow());
        assertEquals("Not a number", OPS.getBooleanValue(StringBinaryTag.stringBinaryTag("true")).error().orElseThrow().message());
        assertEquals((short) 3, OPS.getNumberValue(ShortBinaryTag.shortBinaryTag((short) 3)).getOrThrow());
        assertEquals("Not a number", OPS.getNumberValue(StringBinaryTag.stringBinaryTag("3")).error().orElseThrow().message());
        assertEquals("Not a string", OPS.getStringValue(IntBinaryTag.intBinaryTag(3)).error().orElseThrow().message());
        assertEquals(DoubleBinaryTag.doubleBinaryTag(5.0), OPS.createNumeric(5));
        assertEquals(EndBinaryTag.endBinaryTag(), OPS.empty());
    }

    @Test
    void lists() {
        assertEquals(list(ByteBinaryTag.byteBinaryTag((byte) 1), StringBinaryTag.stringBinaryTag("x")),
                OPS.createList(Stream.of(ByteBinaryTag.byteBinaryTag((byte) 1), StringBinaryTag.stringBinaryTag("x"))));
        // 数组只接受同类型的元素，其它元素使数组变成列表
        assertEquals(ByteArrayBinaryTag.byteArrayBinaryTag((byte) 1, (byte) 2),
                OPS.mergeToList(ByteArrayBinaryTag.byteArrayBinaryTag((byte) 1), ByteBinaryTag.byteBinaryTag((byte) 2)).getOrThrow());
        assertEquals(list(ByteBinaryTag.byteBinaryTag((byte) 1), IntBinaryTag.intBinaryTag(2)),
                OPS.mergeToList(ByteArrayBinaryTag.byteArrayBinaryTag((byte) 1), IntBinaryTag.intBinaryTag(2)).getOrThrow());
        assertEquals(IntArrayBinaryTag.intArrayBinaryTag(1, 2),
                OPS.mergeToList(IntArrayBinaryTag.intArrayBinaryTag(1), List.of(IntBinaryTag.intBinaryTag(2))).getOrThrow());
        assertEquals(LongArrayBinaryTag.longArrayBinaryTag(1, 2),
                OPS.mergeToList(LongArrayBinaryTag.longArrayBinaryTag(1), LongBinaryTag.longBinaryTag(2)).getOrThrow());
        // 空数组与 END 按列表处理
        assertEquals(list(IntBinaryTag.intBinaryTag(2)),
                OPS.mergeToList(IntArrayBinaryTag.intArrayBinaryTag(), IntBinaryTag.intBinaryTag(2)).getOrThrow());
        assertEquals(list(IntBinaryTag.intBinaryTag(2)), OPS.mergeToList(OPS.empty(), IntBinaryTag.intBinaryTag(2)).getOrThrow());
        DataResult<BinaryTag> notList = OPS.mergeToList(IntBinaryTag.intBinaryTag(5), IntBinaryTag.intBinaryTag(2));
        assertEquals("mergeToList called with not a list: 5", notList.error().orElseThrow().message());
        assertEquals(IntBinaryTag.intBinaryTag(5), notList.resultOrPartial().orElseThrow());
        assertEquals(List.of(ByteBinaryTag.byteBinaryTag((byte) 7)),
                OPS.getStream(ByteArrayBinaryTag.byteArrayBinaryTag((byte) 7)).getOrThrow().toList());
        assertEquals("Not a list", OPS.getStream(IntBinaryTag.intBinaryTag(5)).error().orElseThrow().message());
        assertEquals("Not a list: \"s\"", OPS.getList(StringBinaryTag.stringBinaryTag("s")).error().orElseThrow().message());
    }

    @Test
    void maps() {
        var compound = CompoundBinaryTag.builder().putInt("a", 1).build();
        assertEquals(CompoundBinaryTag.builder().putInt("a", 1).putInt("b", 2).build(),
                OPS.mergeToMap(compound, OPS.createString("b"), IntBinaryTag.intBinaryTag(2)).getOrThrow());
        assertEquals("mergeToMap called with not a map: 5",
                OPS.mergeToMap(IntBinaryTag.intBinaryTag(5), OPS.createString("b"), IntBinaryTag.intBinaryTag(2)).error().orElseThrow().message());
        assertEquals("key is not a string: 1",
                OPS.mergeToMap(compound, IntBinaryTag.intBinaryTag(1), IntBinaryTag.intBinaryTag(2)).error().orElseThrow().message());
        assertEquals(CompoundBinaryTag.empty(), OPS.mergeToMap(OPS.empty(), Map.of()).getOrThrow());
        DataResult<BinaryTag> partial = OPS.mergeToMap(compound, Map.of(IntBinaryTag.intBinaryTag(1), IntBinaryTag.intBinaryTag(2)));
        assertEquals("some keys are not strings: [1]", partial.error().orElseThrow().message());
        assertEquals("Not a map: [B;1B]", OPS.getMap(ByteArrayBinaryTag.byteArrayBinaryTag((byte) 1)).error().orElseThrow().message());
        assertEquals("MapLike[{a:1}]", OPS.getMap(compound).getOrThrow().toString());
        assertThrows(UnsupportedOperationException.class, () -> OPS.getMap(compound).getOrThrow().get(IntBinaryTag.intBinaryTag(1)));
        assertThrows(UnsupportedOperationException.class, () -> OPS.createMap(Stream.of(Pair.of(IntBinaryTag.intBinaryTag(1), IntBinaryTag.intBinaryTag(2)))));
        assertEquals(CompoundBinaryTag.empty(), OPS.remove(compound, "a"));
        assertEquals(IntBinaryTag.intBinaryTag(1), OPS.remove(IntBinaryTag.intBinaryTag(1), "a"));
    }

    @Test
    void recordBuilder() {
        record Point(int x, String y) {
        }
        Codec<Point> codec = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Point::x),
                Codec.STRING.fieldOf("y").forGetter(Point::y)
        ).apply(i, Point::new));
        BinaryTag encoded = codec.encodeStart(OPS, new Point(1, "s")).getOrThrow();
        assertEquals(CompoundBinaryTag.builder().putInt("x", 1).putString("y", "s").build(), encoded);
        assertEquals(new Point(1, "s"), codec.parse(OPS, encoded).getOrThrow());
        assertEquals("mergeToMap called with not a map: 5",
                codec.encode(new Point(1, "s"), OPS, IntBinaryTag.intBinaryTag(5)).error().orElseThrow().message());
        assertEquals(CompoundBinaryTag.builder().putInt("x", 1).putString("y", "s").putInt("z", 3).build(),
                codec.encode(new Point(1, "s"), OPS, CompoundBinaryTag.builder().putInt("z", 3).putInt("x", 9).build()).getOrThrow());
    }

    @Test
    void convertsToAndFromJson() {
        JsonElement json = json("{\"a\": 1, \"b\": [1, 2], \"c\": [1, \"x\"], \"d\": 1.5, \"e\": 2147483648, \"f\": \"s\", \"g\": true}");
        BinaryTag tag = JsonOps.INSTANCE.convertTo(OPS, json);
        assertEquals("{a:1b,b:[1b,2b],c:[1b,\"x\"],d:1.5f,e:2147483648L,f:\"s\",g:1b}", StringTagVisitor.toString(tag));
        assertEquals(json("{\"a\":1,\"b\":[1,2],\"c\":[1,\"x\"],\"d\":1.5,\"e\":2147483648,\"f\":\"s\",\"g\":1}"), OPS.convertTo(JsonOps.INSTANCE, tag));
        assertEquals(json("[1,2]"), OPS.convertTo(JsonOps.INSTANCE, IntArrayBinaryTag.intArrayBinaryTag(1, 2)));
        assertEquals(json("[1,2]"), OPS.convertTo(JsonOps.INSTANCE, ByteArrayBinaryTag.byteArrayBinaryTag((byte) 1, (byte) 2)));
    }

    @Test
    void printsLikeVanilla() {
        var tag = CompoundBinaryTag.builder()
                .putString("b", "it's")
                .putString("a b", "say \"hi\"")
                .putByteArray("c", new byte[]{1, -2})
                .putIntArray("d", new int[]{1, 2})
                .putLongArray("e", new long[]{3})
                .putFloat("f", 1.5F)
                .putDouble("g", 2.0)
                .putShort("h", (short) 4)
                .putLong("i", 5)
                .putInt("true", 1)
                .put("l", ListBinaryTag.empty())
                .putString("ctl", "a\nb\u0001")
                .build();
        assertEquals("{\"a b\":'say \"hi\"',b:\"it's\",c:[B;1B,-2B],ctl:\"a\\nb\\x01\",d:[I;1,2],e:[L;3L],f:1.5f,g:2.0d,h:4s,i:5L,l:[],"
                + "\"true\":1}", StringTagVisitor.toString(tag));
        assertEquals("END", StringTagVisitor.toString(EndBinaryTag.endBinaryTag()));
        assertEquals("\"a\\\\b\"", StringTagVisitor.quoteAndEscape("a\\b"));
        assertEquals(FloatBinaryTag.floatBinaryTag(1.5F), JsonOps.INSTANCE.convertTo(OPS, json("1.5")));
    }
}
