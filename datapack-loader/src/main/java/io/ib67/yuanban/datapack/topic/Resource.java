package io.ib67.yuanban.datapack.topic;

import com.google.gson.JsonElement;
import io.ib67.yuanban.datapack.internal.StrictJson;
import io.ib67.yuanban.datapack.pack.PackInfo;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 数据包中属于某个 topic 的一个文件。在所属批次关闭之前都可以读取。
 */
public final class Resource {
    private final PackInfo pack;
    private final @Nullable String overlay;
    private final Key location;
    private final Key id;
    private final Path file;

    @ApiStatus.Internal
    public Resource(PackInfo pack, @Nullable String overlay, Key location, Key id, Path file) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.overlay = overlay;
        this.location = Objects.requireNonNull(location, "location");
        this.id = Objects.requireNonNull(id, "id");
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * 所属数据包。
     */
    public PackInfo pack() {
        return pack;
    }

    /**
     * 所在 overlay 目录，根目录为 {@code null}。
     */
    public @Nullable String overlay() {
        return overlay;
    }

    /**
     * 文件位置，例如 {@code minecraft:recipe/stick.json}。
     */
    public Key location() {
        return location;
    }

    /**
     * 资源 ID，例如 {@code minecraft:stick}。
     */
    public Key id() {
        return id;
    }

    public InputStream open() throws IOException {
        return Files.newInputStream(file);
    }

    public byte[] readAllBytes() throws IOException {
        return Files.readAllBytes(file);
    }

    /**
     * 以 UTF-8 读取。
     */
    public BufferedReader openReader() throws IOException {
        return Files.newBufferedReader(file, StandardCharsets.UTF_8);
    }

    /**
     * 用 Gson {@code STRICT} 模式解析，要求读完整个文档，和原版 {@code StrictJsonParser} 相同。
     */
    public JsonElement readJson() throws IOException {
        try (var reader = openReader()) {
            return StrictJson.parse(reader);
        }
    }

    @Override
    public String toString() {
        return "[" + pack.id() + "] "
                + (overlay == null ? "" : overlay + "/")
                + "data/" + location.namespace() + "/" + location.value();
    }
}
