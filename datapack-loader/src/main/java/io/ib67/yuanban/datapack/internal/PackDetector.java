package io.ib67.yuanban.datapack.internal;

import io.ib67.yuanban.datapack.PackStructureException;
import io.ib67.yuanban.datapack.pack.PackRoot;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 判断目录中的一个条目是否是数据包，规则和原版 {@code PackDetector} 相同：
 * 含有 {@code pack.mcmeta} 的目录、文件名以 {@code .zip} 结尾的普通文件是数据包，其余条目不是。
 * 符号链接必须匹配白名单，否则抛出异常。
 */
@ApiStatus.Internal
public final class PackDetector {
    public static final String ZIP_SUFFIX = ".zip";

    private PackDetector() {
    }

    public static @Nullable PackRoot detect(Path entry, PathMatcher symlinkAllowList) throws IOException {
        var target = entry;
        var attributes = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink()) {
            target = validateSymlink(entry, symlinkAllowList);
            attributes = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        if (attributes.isDirectory()) {
            validateDirectory(target, symlinkAllowList);
            if (!Files.isRegularFile(target.resolve(PackMetadataReader.FILE_NAME))) {
                return null;
            }
            return target.getFileSystem() == FileSystems.getDefault()
                    ? new PackRoot.Directory(target)
                    : new PackRoot.Embedded(target);
        }
        if (attributes.isRegularFile() && target.getFileName().toString().endsWith(ZIP_SUFFIX)) {
            if (target.getFileSystem() != FileSystems.getDefault()) {
                throw new PackStructureException("无法打开位于非默认文件系统中的 zip 数据包 " + target.toUri());
            }
            return new PackRoot.Zip(target);
        }
        return null;
    }

    private static Path validateSymlink(Path link, PathMatcher symlinkAllowList) throws IOException {
        var target = Files.readSymbolicLink(link);
        if (!symlinkAllowList.matches(target)) {
            throw new PackStructureException("符号链接 " + link + " 指向 " + target + "，目标不在白名单中");
        }
        return link.resolveSibling(target);
    }

    private static void validateDirectory(Path directory, PathMatcher symlinkAllowList) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                check(dir, attrs);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                check(file, attrs);
                return FileVisitResult.CONTINUE;
            }

            private void check(Path path, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) {
                    validateSymlink(path, symlinkAllowList);
                }
            }
        });
    }
}
