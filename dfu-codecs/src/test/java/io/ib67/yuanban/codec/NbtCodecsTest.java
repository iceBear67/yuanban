package io.ib67.yuanban.codec;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.ShortBinaryTag;
import org.junit.jupiter.api.Test;

import static io.ib67.yuanban.codec.CodecTestSupport.decode;
import static io.ib67.yuanban.codec.CodecTestSupport.encode;
import static io.ib67.yuanban.codec.CodecTestSupport.error;
import static io.ib67.yuanban.codec.CodecTestSupport.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NbtCodecs} 与 {@link TagParser}：错误文本取自原版 26.2 服务端。
 */
class NbtCodecsTest {
    @Test
    void compoundTagFromJson() {
        CompoundBinaryTag tag = decode(NbtCodecs.COMPOUND_TAG,
                "{\"a\": 1, \"b\": [1, 2], \"c\": [1, \"x\"], \"d\": 1.5, \"e\": 2147483648, \"f\": \"s\", \"g\": true}");
        assertEquals("{a:1b,b:[1b,2b],c:[1b,\"x\"],d:1.5f,e:2147483648L,f:\"s\",g:1b}", StringTagVisitor.toString(tag));
        assertEquals(json("{\"a\":1,\"b\":[1,2],\"c\":[1,\"x\"],\"d\":1.5,\"e\":2147483648,\"f\":\"s\",\"g\":1}"),
                json(encode(NbtCodecs.COMPOUND_TAG, tag)));
        assertEquals("Not a compound tag: [1b]", error(NbtCodecs.COMPOUND_TAG, "[1]"));
        assertEquals("Not a compound tag: \"x\"", error(NbtCodecs.COMPOUND_TAG, "\"x\""));
        assertEquals("Not a compound tag: END", error(NbtCodecs.COMPOUND_TAG, "null"));
        assertEquals(tag, NbtCodecs.COMPOUND_TAG.parse(NbtOps.INSTANCE, tag).getOrThrow());
    }

    @Test
    void lenientCompoundTag() {
        CompoundBinaryTag snbt = decode(NbtCodecs.LENIENT_COMPOUND_TAG, "\"{a:1b,b:[1,2]}\"");
        assertEquals(CompoundBinaryTag.builder().putByte("a", (byte) 1)
                .put("b", ListBinaryTag.builder().add(IntBinaryTag.intBinaryTag(1)).add(IntBinaryTag.intBinaryTag(2)).build()).build(), snbt);
        assertEquals("\"{a:1b,b:[1,2]}\"", encode(NbtCodecs.LENIENT_COMPOUND_TAG, snbt));
        CompoundBinaryTag structured = decode(NbtCodecs.LENIENT_COMPOUND_TAG, "{\"a\": 1}");
        assertEquals(CompoundBinaryTag.builder().putByte("a", (byte) 1).build(), structured);
        assertEquals("\"{a:1b}\"", encode(NbtCodecs.LENIENT_COMPOUND_TAG, structured));
        assertEquals("Failed to parse either. First: Expected compound tag, got [1]; Second: Not a compound tag: \"[1]\"",
                error(NbtCodecs.LENIENT_COMPOUND_TAG, "\"[1]\""));
        assertEquals("Failed to parse either. First: Unexpected trailing data at position 6: {a:1} <--[HERE]; Second: Not a compound tag: \"{a:1} x\"",
                error(NbtCodecs.LENIENT_COMPOUND_TAG, "\"{a:1} x\""));
        assertEquals("Failed to parse either. First: Not a string: 5; Second: Not a compound tag: 5b", error(NbtCodecs.LENIENT_COMPOUND_TAG, "5"));
        // 语法错误的文本来自 adventure 的 TagStringIO，位置报告为开始解析的位置
        String syntax = error(NbtCodecs.LENIENT_COMPOUND_TAG, "\"{a:1\"");
        assertTrue(syntax.startsWith("Failed to parse either. First: ") && syntax.endsWith(" at position 0: <--[HERE]; Second: Not a compound tag: \"{a:1\""),
                syntax);
    }

    @Test
    void nbtConverter() {
        assertEquals("[1b,2b]", StringTagVisitor.toString(decode(NbtCodecs.NBT, "[1, 2]")));
        assertEquals("[1b,300s]", StringTagVisitor.toString(decode(NbtCodecs.NBT, "[1, 300]")));
        assertEquals("[[1b],{a:1b}]", StringTagVisitor.toString(decode(NbtCodecs.NBT, "[[1], {\"a\": 1}]")));
        assertEquals("[1,300]", encode(NbtCodecs.NBT, ListBinaryTag.heterogeneousListBinaryTag()
                .add(ByteBinaryTag.byteBinaryTag((byte) 1)).add(ShortBinaryTag.shortBinaryTag((short) 300)).build()));
    }

    @Test
    void tagParser() throws CommandSyntaxException {
        assertEquals("{a:1b,b:[1,2],c:[I;1,2],d:\"x\"}", StringTagVisitor.toString(TagParser.parseFully("{a:1b,b:[1,2],c:[I;1,2],d:\"x\"}")));
        assertEquals("{\"a b\":1}", StringTagVisitor.toString(TagParser.parseFully("{\"a b\":1}")));
        assertEquals("[1,\"x\"]", StringTagVisitor.toString(TagParser.parseFully("[1,\"x\"]")));
        assertEquals("Unexpected trailing data at position 6: {a:1} <--[HERE]",
                assertThrows(CommandSyntaxException.class, () -> TagParser.parseFully("{a:1} x")).getMessage());
        assertEquals("Expected compound tag at position 3: [1]<--[HERE]",
                assertThrows(CommandSyntaxException.class, () -> TagParser.parseCompoundFully("[1]")).getMessage());
        var reader = new StringReader("{a:1}]rest");
        BinaryTag tag = TagParser.parseCompoundAsArgument(reader);
        assertEquals(CompoundBinaryTag.builder().putInt("a", 1).build(), tag);
        assertEquals(5, reader.getCursor());
    }
}
