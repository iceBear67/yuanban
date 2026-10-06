package io.ib67.yuanban.datapack.provider;

import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.internal.PackDetector;
import io.ib67.yuanban.datapack.internal.PackMetadataReader;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.pack.PackPosition;
import io.ib67.yuanban.datapack.pack.PackSelectionConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 扫描一个目录（通常是世界的 {@code datapacks/}）中的数据包，对应原版 {@code FolderRepositorySource}。
 * 数据包 ID 是 {@code file/<条目名>}，zip 保留 {@code .zip} 后缀。目录不存在的时候创建它。
 */
public final class FolderPackProvider implements PackProvider {
    public static final String ID_PREFIX = "file/";
    private static final PackSelectionConfig SELECTION_CONFIG = new PackSelectionConfig(false, PackPosition.TOP, false);

    private final Path directory;
    private final PackOrigin origin;
    private final PathMatcher symlinkAllowList;

    /**
     * @param directory        扫描的目录
     * @param origin           发现的数据包的类别，世界目录使用 {@link PackOrigin#WORLD}
     * @param symlinkAllowList 符号链接目标的白名单，不允许任何符号链接的时候传入总是返回 {@code false} 的匹配器
     */
    public FolderPackProvider(Path directory, PackOrigin origin, PathMatcher symlinkAllowList) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.symlinkAllowList = Objects.requireNonNull(symlinkAllowList, "symlinkAllowList");
    }

    @Override
    public void discover(LoaderSettings settings, Consumer<Pack> output) {
        try {
            Files.createDirectories(directory);
            try (var entries = Files.newDirectoryStream(directory)) {
                for (var entry : entries) {
                    var root = PackDetector.detect(entry, symlinkAllowList);
                    if (root == null) {
                        continue;
                    }
                    var id = ID_PREFIX + entry.getFileName().toString();
                    var metadata = PackMetadataReader.read(id, root, settings);
                    output.accept(Pack.create(settings, id, origin, null, SELECTION_CONFIG, metadata, root));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("无法扫描数据包目录 " + directory, e);
        }
    }
}
