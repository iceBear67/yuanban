package io.ib67.yuanban.datapack.internal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.PackMetadataException;
import io.ib67.yuanban.datapack.PackStructureException;
import io.ib67.yuanban.datapack.pack.PackMetadata;
import io.ib67.yuanban.datapack.pack.PackRoot;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 打开数据包，读取并解析根目录中的 {@code pack.mcmeta}，读完立即关闭。
 */
@ApiStatus.Internal
public final class PackMetadataReader {
    public static final String FILE_NAME = "pack.mcmeta";
    /**
     * 和原版 {@code GsonHelper.parse(Reader)} 相同，根元素用 Gson 的 {@link JsonObject} 适配器读取，
     * 根元素不是对象的时候 Gson 抛出语法错误。
     */
    private static final TypeAdapter<JsonObject> JSON_OBJECT = new Gson().getAdapter(JsonObject.class);

    private PackMetadataReader() {
    }

    public static PackMetadata read(String packId, PackRoot root, LoaderSettings settings) {
        try (var opened = OpenedPack.open(packId, root)) {
            var file = opened.root().resolve(FILE_NAME);
            if (!Files.isRegularFile(file)) {
                throw new PackStructureException("数据包 " + packId + " 缺少 " + FILE_NAME);
            }
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                var json = JSON_OBJECT.fromJsonTree(StrictJson.parse(reader));
                return PackMetadataParser.parse(packId, json, settings);
            } catch (JsonParseException e) {
                throw new PackMetadataException(packId, "JSON 语法错误：" + e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("无法读取数据包 " + packId + " 的 " + FILE_NAME, e);
        }
    }
}
