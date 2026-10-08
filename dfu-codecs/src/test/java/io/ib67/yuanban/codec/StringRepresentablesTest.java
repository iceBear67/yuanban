package io.ib67.yuanban.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static io.ib67.yuanban.codec.CodecTestSupport.decode;
import static io.ib67.yuanban.codec.CodecTestSupport.encode;
import static io.ib67.yuanban.codec.CodecTestSupport.error;
import static io.ib67.yuanban.codec.CodecTestSupport.json;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StringRepresentablesTest {
    private enum Mode {
        REPLACE, APPEND, MERGE
    }

    private static final Codec<Mode> CODEC = StringRepresentables.fromEnum(Mode.values(), mode -> mode.name().toLowerCase(Locale.ROOT));

    @Test
    void decodesByName() {
        assertEquals(Mode.APPEND, decode(CODEC, "\"append\""));
        assertEquals("\"merge\"", encode(CODEC, Mode.MERGE));
        assertEquals("Unknown element name:x", error(CODEC, "\"x\""));
        assertEquals("Not a string: 1", error(CODEC, "1"));
    }

    @Test
    void compressedFormatUsesIndex() {
        assertEquals(Mode.MERGE, CODEC.parse(JsonOps.COMPRESSED, json("2")).getOrThrow());
        assertEquals("Unknown element id: 3", CODEC.parse(JsonOps.COMPRESSED, json("3")).error().orElseThrow().message());
    }

    @Test
    void fromValuesUsesLookupTable() {
        String[] values = new String[20];
        for (int i = 0; i < values.length; i++) {
            values[i] = "v" + i;
        }
        Codec<String> codec = StringRepresentables.fromValues(values, value -> value);
        assertEquals("v17", decode(codec, "\"v17\""));
        assertEquals("Unknown element name:v20", error(codec, "\"v20\""));
    }
}
