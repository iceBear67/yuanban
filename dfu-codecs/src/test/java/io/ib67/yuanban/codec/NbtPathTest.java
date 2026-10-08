package io.ib67.yuanban.codec;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.ib67.yuanban.codec.CodecTestSupport.decode;
import static io.ib67.yuanban.codec.CodecTestSupport.error;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link NbtPath}：结果按原版 {@code Tag.toString()} 打印，期望值取自原版 26.2 服务端对同一路径和 NBT 的执行结果。
 */
class NbtPathTest {
    private static BinaryTag tag(String snbt) throws CommandSyntaxException {
        return TagParser.parseFully(snbt);
    }

    private static String get(String path, String snbt) throws CommandSyntaxException {
        return NbtPath.of(path).get(tag(snbt)).stream().map(StringTagVisitor::toString).toList().toString();
    }

    private static String getError(String path, String snbt) throws CommandSyntaxException {
        BinaryTag root = tag(snbt);
        NbtPath nbtPath = NbtPath.of(path);
        return assertThrows(CommandSyntaxException.class, () -> nbtPath.get(root)).getMessage();
    }

    private static String modified(NbtPath.Modified result) {
        return result.count() + " " + StringTagVisitor.toString(result.root());
    }

    private static String set(String path, String snbt, String value) throws CommandSyntaxException {
        return modified(NbtPath.of(path).set(tag(snbt), tag(value)));
    }

    private static String insert(String path, String snbt, int index, String value) throws CommandSyntaxException {
        return modified(NbtPath.of(path).insert(index, (CompoundBinaryTag) tag(snbt), List.of(tag(value))));
    }

    private static String remove(String path, String snbt) throws CommandSyntaxException {
        return modified(NbtPath.of(path).remove(tag(snbt)));
    }

    private static String merge(String path, String snbt, String source) throws CommandSyntaxException {
        CompoundBinaryTag sourceTag = (CompoundBinaryTag) tag(source);
        return StringTagVisitor.toString(NbtPath.of(path).getOrCreate(tag(snbt), CompoundBinaryTag::empty,
                target -> target instanceof CompoundBinaryTag compound ? NbtUtils.merge(compound, sourceTag) : target));
    }

    @Test
    void codec() {
        assertEquals("a.b[0]", decode(NbtPath.CODEC, "\"a.b[0]\"").asString());
        assertEquals("Failed to parse path a..b: Invalid NBT path element at position 2: a.<--[HERE]", error(NbtPath.CODEC, "\"a..b\""));
        // 与原版相同，空格之后的内容不报错
        assertEquals("a", decode(NbtPath.CODEC, "\"a b\"").asString());
        assertEquals("Failed to parse path a[{c:1}: Expected ']' at position 7: a[{c:1}<--[HERE]", error(NbtPath.CODEC, "\"a[{c:1}\""));
        assertEquals("", decode(NbtPath.CODEC, "\"\"").asString());
        assertEquals("{a:1}.b{c:2}[1][]", decode(NbtPath.CODEC, "\"{a:1}.b{c:2}[1][]\"").asString());
        assertEquals("Failed to parse path a{b:1}{c:2}: Invalid NBT path element at position 6: a{b:1}<--[HERE]",
                error(NbtPath.CODEC, "\"a{b:1}{c:2}\""));
        assertEquals("\"quo ted\".x", decode(NbtPath.CODEC, "\"\\\"quo ted\\\".x\"").asString());
        assertEquals("a[-1]", decode(NbtPath.CODEC, "\"a[-1]\"").asString());
        assertEquals("[0]", decode(NbtPath.CODEC, "\"[0]\"").toString());
        assertEquals("\"a[0]\"", CodecTestSupport.encode(NbtPath.CODEC, decode(NbtPath.CODEC, "\"a[0]\"")));
    }

    @Test
    void get() throws CommandSyntaxException {
        assertEquals("[1]", get("a.b", "{a:{b:1}}"));
        assertEquals("Found no elements matching a.c", getError("a.c", "{a:{b:1}}"));
        assertEquals("[1, 2, 3]", get("a[]", "{a:[1,2,3]}"));
        assertEquals("[3]", get("a[-1]", "{a:[1,2,3]}"));
        assertEquals("Found no elements matching a[5]", getError("a[5]", "{a:[1,2,3]}"));
        assertEquals("[{x:1,y:2}, {x:1}]", get("a[{x:1}]", "{a:[{x:1,y:2},{x:2},{x:1}]}"));
        assertEquals("[{x:1,y:2}]", get("a{x:1}", "{a:{x:1,y:2}}"));
        assertEquals("[{a:1,b:2}]", get("{a:1}", "{a:1,b:2}"));
        assertEquals("Found no elements matching {a:2}", getError("{a:2}.b", "{a:1,b:2}"));
        assertEquals("[1, 3]", get("a[].b", "{a:[{b:1},{c:2},{b:3}]}"));
        assertEquals("[1b]", get("a[0]", "{a:[B;1b,2b]}"));
        assertEquals("[5L, 6L]", get("a[]", "{a:[L;5L,6L]}"));
        assertEquals("[7]", get("\"q k\".x", "{\"q k\":{x:7}}"));
        assertEquals(3, NbtPath.of("a[]").countMatching(tag("{a:[1,2,3]}")));
        assertEquals(0, NbtPath.of("a.z").countMatching(tag("{a:[1,2,3]}")));
    }

    @Test
    void set() throws CommandSyntaxException {
        assertEquals("1 {a:{b:5}}", set("a.b", "{}", "5"));
        assertEquals("1 {a:[9,2]}", set("a[0]", "{a:[1,2]}", "9"));
        assertEquals("0 {a:[1,2]}", set("a[0]", "{a:[1,2]}", "1"));
        assertEquals("1 {a:[1,1,1]}", set("a[]", "{a:[1,2,1]}", "1"));
        assertEquals("1 {a:[7]}", set("a[]", "{a:[]}", "7"));
        assertEquals("1 {a:[{x:1,y:3},{x:2}]}", set("a[{x:1}]", "{a:[{x:1},{x:2}]}", "{x:1,y:3}"));
        assertEquals("1 {a:[{x:9}]}", set("a[{x:1}]", "{a:[]}", "{x:9}"));
        assertEquals("1 {a:{x:1,z:0}}", set("a{x:1}", "{a:{x:1}}", "{x:1,z:0}"));
        assertEquals("0 {a:{b:[1,2]}}", set("a.b", "{a:{b:[1,2]}}", "[1,2]"));
        assertEquals("1 [5,2]", set("[0]", "[1,2]", "5"));
        assertEquals("1 {a:[\"x\",\"y\"]}", set("a[1]", "{a:[\"x\",2]}", "\"y\""));
        assertEquals("Found no elements matching a.b",
                assertThrows(CommandSyntaxException.class, () -> set("a.b.c", "{a:5}", "1")).getMessage());
        assertEquals("Found no elements matching a[0]",
                assertThrows(CommandSyntaxException.class, () -> set("a[0].b", "{}", "1")).getMessage());
    }

    @Test
    void setOnArrays() throws CommandSyntaxException {
        assertEquals("0 {a:[I;1,2]}", set("a[0]", "{a:[I;1,2]}", "\"s\""));
        assertEquals("1 {a:[I;5,2]}", set("a[0]", "{a:[I;1,2]}", "5b"));
        assertEquals("1 {a:[I;2,2]}", set("a[0]", "{a:[I;1,2]}", "2.7f"));
        // 原版先清空数组，之后字符串无法写入整数数组
        assertEquals("0 {a:[I;]}", set("a[]", "{a:[I;1,2]}", "\"s\""));
        assertEquals("2 {a:[I;3,3]}", set("a[]", "{a:[I;1,2]}", "3"));
    }

    @Test
    void insert() throws CommandSyntaxException {
        assertEquals("1 {a:[1,2,3]}", insert("a", "{a:[1,2]}", -1, "3"));
        assertEquals("1 {a:[3,1,2]}", insert("a", "{a:[1,2]}", 0, "3"));
        assertEquals("1 {a:[3]}", insert("a", "{}", 0, "3"));
        assertEquals("1 {a:[\"x\",{c:1}]}", insert("a", "{a:[\"x\"]}", 1, "{c:1}"));
        assertEquals("0 {a:[B;1B]}", insert("a", "{a:[B;1b]}", -1, "\"x\""));
        assertEquals("1 {a:[B;1B,44B]}", insert("a", "{a:[B;1b]}", -1, "300"));
        assertEquals("1 {a:[B;1B,-2B]}", insert("a", "{a:[B;1b]}", -1, "-1.5d"));
        assertEquals("Invalid list index: 5", assertThrows(CommandSyntaxException.class, () -> insert("a", "{a:[1,2]}", 5, "3")).getMessage());
        assertEquals("Expected a list: got 5", assertThrows(CommandSyntaxException.class, () -> insert("a", "{a:5}", 0, "3")).getMessage());
    }

    @Test
    void remove() throws CommandSyntaxException {
        assertEquals("1 {a:{c:2}}", remove("a.b", "{a:{b:1,c:2}}"));
        assertEquals("3 {a:[]}", remove("a[]", "{a:[1,2,3]}"));
        assertEquals("2 {a:[{x:2}]}", remove("a[{x:1}]", "{a:[{x:1},{x:2},{x:1}]}"));
        assertEquals("1 {a:[L;6L]}", remove("a[0]", "{a:[L;5L,6L]}"));
        assertEquals("0 {a:1}", remove("{a:1}", "{a:1}"));
    }

    @Test
    void getOrCreateAndMerge() throws CommandSyntaxException {
        assertEquals("{a:{b:{x:1}}}", merge("a.b", "{}", "{x:1}"));
        assertEquals("{a:{n:{p:1,q:2},x:1,y:2}}", merge("a", "{a:{x:1,n:{p:1}}}", "{y:2,n:{q:2}}"));
        assertEquals("{a:[{x:1,z:3},5,{y:1,z:3}]}", merge("a[]", "{a:[{x:1},5,{y:1}]}", "{z:3}"));
        assertEquals("{a:[{z:3}]}", merge("a[]", "{a:[]}", "{z:3}"));
        assertEquals("{a:[I;1,2]}", merge("a[]", "{a:[I;1,2]}", "{z:3}"));
    }

    @Test
    void tooDeep() throws CommandSyntaxException {
        BinaryTag deep = CompoundBinaryTag.empty();
        for (int i = 0; i < 512; i++) {
            deep = CompoundBinaryTag.builder().put("a", deep).build();
        }
        BinaryTag value = deep;
        NbtPath path = NbtPath.of("x");
        assertEquals("Resulting NBT too deeply nested",
                assertThrows(CommandSyntaxException.class, () -> path.set(CompoundBinaryTag.empty(), value)).getMessage());
    }
}
