package io.ib67.yuanban.datapack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在磁盘上写出真实的数据包目录或 zip 文件。
 */
final class TestPack {
    private final Map<String, String> files = new LinkedHashMap<>();

    private TestPack() {
    }

    /**
     * 带有 {@code min_format = max_format = 107} 的 pack.mcmeta，{@code extraSections} 追加到根对象中。
     */
    static TestPack create(String extraSections) {
        var pack = new TestPack();
        var extra = extraSections.isBlank() ? "" : "," + extraSections;
        pack.files.put("pack.mcmeta", """
                {"pack": {"description": "test", "min_format": 107, "max_format": 107}%s}
                """.formatted(extra));
        return pack;
    }

    static TestPack create() {
        return create("");
    }

    static TestPack withMcmeta(String mcmeta) {
        var pack = new TestPack();
        pack.files.put("pack.mcmeta", mcmeta);
        return pack;
    }

    static TestPack withoutMcmeta() {
        return new TestPack();
    }

    TestPack file(String path, String content) {
        files.put(path, content);
        return this;
    }

    /**
     * {@code data/<namespace>/<path>}。
     */
    TestPack data(String namespace, String path, String content) {
        return file("data/" + namespace + "/" + path, content);
    }

    Path writeDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            for (var entry : files.entrySet()) {
                var target = directory.resolve(entry.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
            }
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Path writeZip(Path zipFile) {
        try {
            Files.createDirectories(zipFile.getParent());
            var uri = URI.create("jar:" + zipFile.toUri());
            try (var fileSystem = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
                for (var entry : files.entrySet()) {
                    var target = fileSystem.getPath("/" + entry.getKey());
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
                }
            }
            return zipFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 按布局写到 {@code parent} 下，目录名为 {@code name}，zip 文件名为 {@code name.zip}。
     *
     * @return 数据包在 {@link io.ib67.yuanban.datapack.provider.FolderPackProvider} 中的 ID
     */
    String write(Layout layout, Path parent, String name) {
        return switch (layout) {
            case DIRECTORY -> {
                writeDirectory(parent.resolve(name));
                yield "file/" + name;
            }
            case ZIP -> {
                writeZip(parent.resolve(name + ".zip"));
                yield "file/" + name + ".zip";
            }
        };
    }

    enum Layout {
        DIRECTORY,
        ZIP
    }
}
