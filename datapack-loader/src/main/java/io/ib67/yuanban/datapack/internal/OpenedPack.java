package io.ib67.yuanban.datapack.internal;

import io.ib67.yuanban.datapack.PackStructureException;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.pack.PackRoot;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 已经打开的数据包根目录。zip 数据包持有自己新建的 zip 文件系统，关闭的时候一同关闭。
 */
@ApiStatus.Internal
public record OpenedPack(String packId, Path root, @Nullable FileSystem ownedFileSystem) implements AutoCloseable {
    public static OpenedPack open(Pack pack) throws IOException {
        return open(pack.id(), pack.root());
    }

    public static OpenedPack open(String packId, PackRoot root) throws IOException {
        return switch (root) {
            case PackRoot.Directory(Path path) -> new OpenedPack(packId, requireDirectory(packId, path), null);
            case PackRoot.Embedded(Path path) -> new OpenedPack(packId, requireDirectory(packId, path), null);
            case PackRoot.Zip(Path file) -> {
                var fileSystem = FileSystems.newFileSystem(file);
                yield new OpenedPack(packId, fileSystem.getRootDirectories().iterator().next(), fileSystem);
            }
        };
    }

    private static Path requireDirectory(String packId, Path path) {
        if (!Files.isDirectory(path)) {
            throw new PackStructureException("数据包 " + packId + " 的根目录 " + path + " 不存在或者不是目录");
        }
        return path;
    }

    @Override
    public void close() throws IOException {
        if (ownedFileSystem != null) {
            ownedFileSystem.close();
        }
    }
}
