package io.ib67.yuanban.datapack.internal;

import io.ib67.yuanban.datapack.PackStructureException;
import io.ib67.yuanban.datapack.pack.Pack;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 一个数据包中 {@code data/} 下全部文件的索引，已经按 overlay 顺序合并：同一路径保留优先级最高的那一份。
 * 建立索引的时候只遍历一次文件系统。遍历跟随符号链接（数据包内的符号链接在发现阶段已经用白名单校验过），
 * 链接成环或者链接目标不存在的时候抛出异常。数据包只读取部分命名空间的时候，其余条目直接忽略。
 */
@ApiStatus.Internal
public final class PackIndex {
    private static final String DATA_DIRECTORY = "data";

    private final Pack pack;
    private final Map<String, NavigableMap<String, IndexedFile>> namespaces;

    private PackIndex(Pack pack, Map<String, NavigableMap<String, IndexedFile>> namespaces) {
        this.pack = pack;
        this.namespaces = namespaces;
    }

    public static PackIndex build(Pack pack, Path root) throws IOException {
        var namespaces = new TreeMap<String, NavigableMap<String, IndexedFile>>();
        indexLayer(pack, root.resolve(DATA_DIRECTORY), null, namespaces);
        for (var overlay : pack.activeOverlays()) {
            indexLayer(pack, root.resolve(overlay).resolve(DATA_DIRECTORY), overlay, namespaces);
        }
        return new PackIndex(pack, Collections.unmodifiableMap(namespaces));
    }

    private static void indexLayer(Pack pack,
                                   Path dataDirectory,
                                   @Nullable String overlay,
                                   Map<String, NavigableMap<String, IndexedFile>> namespaces) throws IOException {
        if (!Files.isDirectory(dataDirectory)) {
            return;
        }
        var exposed = pack.exposedNamespaces().orElse(null);
        try (var children = Files.newDirectoryStream(dataDirectory)) {
            for (var child : children) {
                var name = fileName(child);
                if (exposed != null && !exposed.contains(name)) {
                    continue;
                }
                if (!Files.isDirectory(child)) {
                    throw new PackStructureException("数据包 " + pack.id() + " 的 " + describe(overlay, name)
                            + " 不是目录，data/ 下只能有命名空间目录");
                }
                if (!Key.parseableNamespace(name)) {
                    throw new PackStructureException("数据包 " + pack.id() + " 的 " + describe(overlay, name)
                            + " 不是合法的命名空间，命名空间只能包含 [a-z0-9_.-]");
                }
                var files = namespaces.computeIfAbsent(name, ignored -> new TreeMap<>());
                Files.walkFileTree(child, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        // 跟随链接的时候仍然拿到链接自身的属性，说明链接目标不存在
                        if (attrs.isSymbolicLink()) {
                            throw new PackStructureException("数据包 " + pack.id() + " 中的符号链接 " + file + " 指向的目标不存在");
                        }
                        if (attrs.isRegularFile()) {
                            files.put(relativePath(child, file), new IndexedFile(file, overlay));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    private static String relativePath(Path base, Path file) {
        var builder = new StringBuilder();
        for (var part : base.relativize(file)) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(fileName(part));
        }
        return builder.toString();
    }

    private static String fileName(Path path) {
        var name = path.getFileName().toString();
        return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    }

    private static String describe(@Nullable String overlay, String name) {
        return (overlay == null ? "" : overlay + "/") + DATA_DIRECTORY + "/" + name;
    }

    public Pack pack() {
        return pack;
    }

    public Map<String, NavigableMap<String, IndexedFile>> namespaces() {
        return namespaces;
    }

    /**
     * 命名空间下位于 {@code directory} 目录中的文件，键是命名空间目录下的完整相对路径。
     */
    public static NavigableMap<String, IndexedFile> filesIn(NavigableMap<String, IndexedFile> namespace, String directory) {
        // '0' 在 ASCII 中紧跟在 '/' 之后
        return namespace.subMap(directory + "/", true, directory + "0", false);
    }

    public record IndexedFile(Path path, @Nullable String overlay) {
    }
}
