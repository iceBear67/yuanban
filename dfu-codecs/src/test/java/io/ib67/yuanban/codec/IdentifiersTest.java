package io.ib67.yuanban.codec;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import static io.ib67.yuanban.codec.CodecTestSupport.decode;
import static io.ib67.yuanban.codec.CodecTestSupport.encode;
import static io.ib67.yuanban.codec.CodecTestSupport.error;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentifiersTest {
    @Test
    void codecAcceptsVanillaIdentifiers() {
        assertEquals(Key.key("minecraft", "bar"), decode(Identifiers.CODEC, "\":bar\""));
        assertEquals(Key.key("minecraft", "bar"), decode(Identifiers.CODEC, "\"bar\""));
        assertEquals(Key.key("foo", "a/b.c-d_e"), decode(Identifiers.CODEC, "\"foo:a/b.c-d_e\""));
        assertEquals("\"foo:bar\"", encode(Identifiers.CODEC, Key.key("foo:bar")));
    }

    @Test
    void codecRejectsInvalidCharacters() {
        assertEquals("Not a valid resource location: Foo:bar Non [a-z0-9_.-] character in namespace of identifier: Foo:bar",
                error(Identifiers.CODEC, "\"Foo:bar\""));
        assertEquals("Not a valid resource location: foo:Bar Non [a-z0-9/._-] character in path of location: foo:Bar",
                error(Identifiers.CODEC, "\"foo:Bar\""));
        assertEquals("Not a valid resource location: a:b:c Non [a-z0-9/._-] character in path of location: a:b:c",
                error(Identifiers.CODEC, "\"a:b:c\""));
        assertEquals("Not a valid resource location: ..:a Non [a-z0-9_.-] character in namespace of identifier: ..:a",
                error(Identifiers.CODEC, "\"..:a\""));
        assertEquals("Not a string: 5", error(Identifiers.CODEC, "5"));
    }

    @Test
    void readFromCommand() throws CommandSyntaxException {
        var reader = new StringReader("foo:bar baz");
        assertEquals(Key.key("foo:bar"), Identifiers.read(reader));
        assertEquals(7, reader.getCursor());
        // 原版只读取允许的字符：大写字母之前的部分为空，解析为 minecraft: 加空路径
        var upper = new StringReader("Foo");
        assertEquals("minecraft:", Identifiers.read(upper).asString());
        assertEquals(0, upper.getCursor());
        var invalid = new StringReader("a:b:c");
        assertEquals("Invalid ID at position 0: <--[HERE]", assertThrows(CommandSyntaxException.class, () -> Identifiers.read(invalid)).getMessage());
        assertEquals(0, invalid.getCursor());
    }
}
