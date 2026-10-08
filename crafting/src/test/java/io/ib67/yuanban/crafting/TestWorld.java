package io.ib67.yuanban.crafting;

import io.ib67.yuanban.crafting.testing.InMemoryMaps;
import io.ib67.yuanban.datapack.DataPackLoader;
import io.ib67.yuanban.datapack.DataPackSession;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.datapack.provider.PackProvider;
import io.ib67.yuanban.datapack.selection.DataPackConfig;
import io.ib67.yuanban.datapack.selection.SelectionMode;
import io.ib67.yuanban.datapack.selection.SelectionRequest;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.recipe.RecipeSubscriber;
import net.minestom.server.ServerProcess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

/**
 * 测试中的游戏数据：磁盘上的一份原版数据目录，或者原版 26.2 服务端 jar 中的数据。
 * 配方经过 {@code datapack-loader}、{@code item-tag}、{@code item-recipe} 的真实加载流程注册进 {@link Crafting}。
 * <p>
 * 加载成功之后 {@code item-tag} 会删除 Minestom 自带的物品标签，使用这个类的测试需要加上
 * {@code @ExtendWith(ResetItemTags.class)}。
 */
final class TestWorld {
    final Path vanillaRoot;

    TestWorld(Path tempDir) {
        this.vanillaRoot = tempDir.resolve("vanilla");
        try {
            Files.createDirectories(vanillaRoot.resolve("data").resolve("minecraft"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 在原版数据中写入 {@code data/minecraft/<path>}。
     */
    TestWorld vanilla(String path, String content) {
        write(vanillaRoot.resolve("data/minecraft/" + path), content);
        return this;
    }

    /**
     * 新建 {@link Crafting}，把它的事件节点加入进程，再按这份数据启动加载。
     */
    Loaded load(ServerProcess process) {
        var maps = new InMemoryMaps();
        var crafting = install(process, maps);
        var session = start(process, crafting, new BuiltInPackProvider(vanillaRoot, "26.2"));
        return new Loaded(crafting, maps, session);
    }

    record Loaded(Crafting crafting, InMemoryMaps maps, DataPackSession session) {
    }

    /**
     * 读取原版 26.2 服务端 jar 中的全部数据。jar 由根项目的 {@code extractVanillaServer} 任务准备，
     * 路径通过系统属性 {@code yuanban.vanilla-server} 传入。
     */
    static Crafting loadVanilla(ServerProcess process, InMemoryMaps maps) {
        var crafting = install(process, maps);
        var server = Path.of(System.getProperty("yuanban.vanilla-server"));
        try (var jar = FileSystems.newFileSystem(URI.create("jar:" + server.toUri()), Map.of())) {
            start(process, crafting, new BuiltInPackProvider(jar.getPath("/"), "26.2")).close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return crafting;
    }

    private static Crafting install(ServerProcess process, InMemoryMaps maps) {
        var crafting = new Crafting(process, maps);
        process.eventHandler().addChild(crafting.eventNode());
        return crafting;
    }

    private static DataPackSession start(ServerProcess process, Crafting crafting, PackProvider... providers) {
        var builder = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2);
        for (var provider : providers) {
            builder.provider(provider);
        }
        var loader = builder
                .subscribe(ItemTagSubscriber.subscription(process))
                .subscribe(new RecipeSubscriber(process, crafting.recipes()).subscription())
                .executor(ForkJoinPool.commonPool())
                .build();
        return loader.start(new SelectionRequest(new DataPackConfig(List.of("vanilla"), List.of()), Set.of(), SelectionMode.CREATE_WORLD, false));
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
