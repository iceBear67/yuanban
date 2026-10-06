package io.ib67.yuanban.itemtag;

import io.ib67.yuanban.datapack.DataPackLoader;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.datapack.provider.FolderPackProvider;
import io.ib67.yuanban.datapack.selection.DataPackConfig;
import io.ib67.yuanban.datapack.selection.SelectionMode;
import io.ib67.yuanban.datapack.selection.SelectionRequest;
import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import net.minestom.server.ServerProcess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 磁盘上的一份原版数据目录和一个世界的 {@code datapacks/} 目录。
 * <p>
 * 提交阶段会删除 Minestom 自带的物品标签，使用这个类的测试需要加上
 * {@code @ExtendWith(ResetItemTags.class)}，见 {@link io.ib67.yuanban.itemtag.testing.ResetItemTags}。
 */
final class TagWorld {
    final Path vanillaRoot;
    final Path datapacks;

    TagWorld(Path tempDir) {
        this.vanillaRoot = tempDir.resolve("vanilla");
        this.datapacks = tempDir.resolve("world").resolve("datapacks");
        try {
            Files.createDirectories(vanillaRoot.resolve("data").resolve("minecraft"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 在原版数据中写入 {@code data/minecraft/tags/item/<path>.json}。
     */
    TagWorld vanillaTag(String path, String content) {
        write(vanillaRoot.resolve("data/minecraft/tags/item/" + path + ".json"), content);
        return this;
    }

    /**
     * 在世界目录下的数据包 {@code name} 中写入 {@code data/<namespace>/tags/item/<path>.json}。
     */
    TagWorld packTag(String name, String namespace, String path, String content) {
        var pack = datapacks.resolve(name);
        write(pack.resolve("pack.mcmeta"), """
                {"pack": {"description": "test", "min_format": 107, "max_format": 107}}
                """);
        write(pack.resolve("data/" + namespace + "/tags/item/" + path + ".json"), content);
        return this;
    }

    DataPackLoader.Builder loader() {
        return DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
                .provider(new BuiltInPackProvider(vanillaRoot, "26.2"))
                .provider(new FolderPackProvider(datapacks, PackOrigin.WORLD, path -> false))
                .executor(ForkJoinPool.commonPool());
    }

    /**
     * 以新建世界启动一次，通过依赖 {@link ItemTagSubscriber} 的订阅者取得准备结果。
     */
    ItemTags load(ServerProcess process) {
        var result = new AtomicReference<ItemTags>();
        SubscriberKey<String> key = SubscriberKey.of("test:capture");
        var capture = new Subscriber<String>() {
            @Override
            public String prepare(PrepareContext context) {
                result.set(context.dependency(ItemTagSubscriber.KEY));
                return "";
            }

            @Override
            public void apply(String prepared) {
            }
        };
        loader()
                .subscribe(ItemTagSubscriber.subscription(process))
                .subscribe(Subscription.builder(key, capture).lifecycle(Lifecycle.RELOADABLE).after(ItemTagSubscriber.KEY).build())
                .build()
                .start(createWorld())
                .close();
        return result.get();
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
