package io.ib67.yuanban.recipe;

import io.ib67.yuanban.datapack.DataPackLoader;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.datapack.provider.FolderPackProvider;
import io.ib67.yuanban.datapack.selection.DataPackConfig;
import io.ib67.yuanban.datapack.selection.SelectionMode;
import io.ib67.yuanban.datapack.selection.SelectionRequest;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import net.minestom.server.ServerProcess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

/**
 * 磁盘上的一份原版数据目录和一个世界的 {@code datapacks/} 目录。
 * <p>
 * 加载成功之后 {@code item-tag} 会删除 Minestom 自带的物品标签，使用这个类的测试需要加上
 * {@code @ExtendWith(ResetItemTags.class)}，见 {@link io.ib67.yuanban.itemtag.testing.ResetItemTags}。
 */
final class RecipeWorld {
    final Path vanillaRoot;
    final Path datapacks;

    RecipeWorld(Path tempDir) {
        this.vanillaRoot = tempDir.resolve("vanilla");
        this.datapacks = tempDir.resolve("world").resolve("datapacks");
        try {
            Files.createDirectories(vanillaRoot.resolve("data").resolve("minecraft"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 在原版数据中写入 {@code data/minecraft/<path>}。
     */
    RecipeWorld vanilla(String path, String content) {
        write(vanillaRoot.resolve("data/minecraft/" + path), content);
        return this;
    }

    /**
     * 在世界目录下的数据包 {@code name} 中写入 {@code data/<namespace>/<path>}。
     */
    RecipeWorld pack(String name, String namespace, String path, String content) {
        var pack = datapacks.resolve(name);
        write(pack.resolve("pack.mcmeta"), """
                {"pack": {"description": "test", "min_format": 107, "max_format": 107}}
                """);
        write(pack.resolve("data/" + namespace + "/" + path), content);
        return this;
    }

    DataPackLoader loader(ServerProcess process, RecipeSubscriber recipes) {
        return DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
                .provider(new BuiltInPackProvider(vanillaRoot, "26.2"))
                .provider(new FolderPackProvider(datapacks, PackOrigin.WORLD, path -> false))
                .subscribe(ItemTagSubscriber.subscription(process))
                .subscribe(recipes.subscription())
                .executor(ForkJoinPool.commonPool())
                .build();
    }

    static SelectionRequest createWorld() {
        return new SelectionRequest(new DataPackConfig(List.of("vanilla"), List.of()), Set.of(), SelectionMode.CREATE_WORLD, false);
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
