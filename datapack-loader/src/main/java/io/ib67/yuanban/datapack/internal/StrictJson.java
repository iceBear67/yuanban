package io.ib67.yuanban.datapack.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.Reader;

/**
 * 和原版 {@code StrictJsonParser} 相同的 JSON 解析：{@link Strictness#STRICT}，并且要求读完整个文档。
 */
@ApiStatus.Internal
public final class StrictJson {
    private StrictJson() {
    }

    public static JsonElement parse(Reader reader) {
        try {
            var jsonReader = new JsonReader(reader);
            jsonReader.setStrictness(Strictness.STRICT);
            var element = JsonParser.parseReader(jsonReader);
            if (!element.isJsonNull() && jsonReader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonSyntaxException("Did not consume the entire document.");
            }
            return element;
        } catch (MalformedJsonException | NumberFormatException e) {
            throw new JsonSyntaxException(e);
        } catch (IOException e) {
            throw new JsonIOException(e);
        }
    }
}
