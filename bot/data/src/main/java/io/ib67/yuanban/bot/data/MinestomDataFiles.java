package io.ib67.yuanban.bot.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minestom.data.MinestomData;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 读取 {@code net.minestom:data} 中的 JSON 文件
 */
final class MinestomDataFiles {
    private MinestomDataFiles() {
    }

    /**
     * @param file 相对于 {@code /net/minestom/data} 的文件名，例如 {@code item.json}
     */
    static JsonObject read(String file) {
        var stream = Objects.requireNonNull(MinestomData.resource(file), () -> "net.minestom:data has no " + file);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file + " from net.minestom:data", e);
        }
    }
}
