package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.datapack.provider.FolderPackProvider;
import io.ib67.yuanban.datapack.selection.DataPackConfig;
import io.ib67.yuanban.datapack.selection.SelectionMode;
import io.ib67.yuanban.datapack.selection.SelectionRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

/**
 * 磁盘上的一份原版数据目录和一个世界的 {@code datapacks/} 目录。
 */
final class TestWorld {
    final Path vanillaRoot;
    final Path datapacks;

    TestWorld(Path tempDir) {
        this.vanillaRoot = tempDir.resolve("vanilla");
        this.datapacks = tempDir.resolve("world").resolve("datapacks");
        try {
            Files.createDirectories(vanillaRoot.resolve("data").resolve("minecraft"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 在原版数据中写入 {@code data/<namespace>/<path>}。
     */
    TestWorld vanilla(String namespace, String path, String content) {
        try {
            var target = vanillaRoot.resolve("data").resolve(namespace).resolve(path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 写入原版自带的特性数据包 {@code data/minecraft/datapacks/<name>}，声明一个特性。
     */
    TestWorld featurePack(String name, String feature) {
        TestPack.create("\"features\": {\"enabled\": [\"" + feature + "\"]}")
                .writeDirectory(vanillaRoot.resolve("data").resolve("minecraft").resolve("datapacks").resolve(name));
        return this;
    }

    String worldPack(TestPack pack, TestPack.Layout layout, String name) {
        return pack.write(layout, datapacks, name);
    }

    DataPackLoader.Builder loader() {
        return loader(path -> false);
    }

    DataPackLoader.Builder loader(PathMatcher symlinkAllowList) {
        return DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
                .provider(new BuiltInPackProvider(vanillaRoot, "26.2"))
                .provider(new FolderPackProvider(datapacks, PackOrigin.WORLD, symlinkAllowList))
                .executor(ForkJoinPool.commonPool());
    }

    static SelectionRequest createWorld() {
        return new SelectionRequest(new DataPackConfig(List.of("vanilla"), List.of()), Set.of(), SelectionMode.CREATE_WORLD, false);
    }
}
