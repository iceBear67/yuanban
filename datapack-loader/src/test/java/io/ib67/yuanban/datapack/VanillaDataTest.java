package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Topic;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 用原版 26.2 服务端 jar 中的数据走一次真实的加载流程。jar 由根项目的 {@code extractVanillaServer} 任务准备，
 * 路径通过系统属性 {@code yuanban.vanilla-server} 传入。
 */
class VanillaDataTest {
    private static final Topic.Single RECIPE = Topic.json("recipe");
    private static final Topic.Single LOOT_TABLE = Topic.json("loot_table");
    private static final Topic.Tags BLOCK_TAGS = Topic.tags("block");
    private static final Topic.Single BIOME = Topic.json("worldgen/biome");

    @Test
    void loadsVanillaData() throws IOException {
        var server = Path.of(System.getProperty("yuanban.vanilla-server"));
        var subscriber = new TestSubscriber<Map<String, Integer>>(context -> Map.of(
                "recipe", context.contents(RECIPE).files().size(),
                "loot_table", context.contents(LOOT_TABLE).files().size(),
                "tags/block", context.contents(BLOCK_TAGS).tags().size(),
                "worldgen/biome", context.contents(BIOME).files().size()));
        try (var jar = FileSystems.newFileSystem(URI.create("jar:" + server.toUri()), Map.of());
             var session = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
                     .provider(new BuiltInPackProvider(jar.getPath("/"), "26.2"))
                     .subscribe(Subscription.builder(SubscriberKey.<Map<String, Integer>>of("test:vanilla"), subscriber)
                             .lifecycle(Lifecycle.RELOADABLE)
                             .topic(RECIPE)
                             .topic(LOOT_TABLE)
                             .topic(BLOCK_TAGS)
                             .topic(BIOME)
                             .build())
                     .executor(ForkJoinPool.commonPool())
                     .build()
                     .start(TestWorld.createWorld())) {
            assertEquals(Set.of("vanilla", "trade_rebalance", "redstone_experiments", "minecart_improvements"),
                    session.available().keySet());
            assertEquals(Map.of(
                    "recipe", countJsonFiles(server, "data/minecraft/recipe/"),
                    "loot_table", countJsonFiles(server, "data/minecraft/loot_table/"),
                    "tags/block", countJsonFiles(server, "data/minecraft/tags/block/"),
                    "worldgen/biome", countJsonFiles(server, "data/minecraft/worldgen/biome/")), subscriber.last());
        }
    }

    /**
     * 直接用 {@link ZipFile} 统计 jar 中 {@code prefix} 之下的 JSON 文件数量。
     */
    private static int countJsonFiles(Path jar, String prefix) throws IOException {
        try (var zip = new ZipFile(jar.toFile())) {
            return (int) zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(java.util.zip.ZipEntry::getName)
                    .filter(name -> name.startsWith(prefix) && name.endsWith(".json"))
                    .count();
        }
    }
}
