package io.ib67.yuanban.lootparser;

import io.ib67.yuanban.datapack.DataPackLoader;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.entry.AlternativesEntry;
import net.kyori.adventure.key.Key;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.ZipFile;

import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 读取原版 26.2 服务端 jar 中的全部 loot_table 文件。jar 由 {@code build.gradle} 的下载任务准备，路径通过
 * 系统属性 {@code yuanban.vanilla-server} 传入。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class VanillaLootTest {
    @Test
    void loadsAllVanillaLootTables(Env env) throws IOException {
        var server = Path.of(System.getProperty("yuanban.vanilla-server"));
        var expectedCount = countLootTables(server);
        var process = env.process();
        var manager = new LootManager(LootTypeRegistry.vanilla());
        var subscriber = new LootTableSubscriber(process, manager);
        try (var jar = FileSystems.newFileSystem(URI.create("jar:" + server.toUri()), Map.of())) {
            var loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
                    .provider(new BuiltInPackProvider(jar.getPath("/"), "26.2"))
                    .subscribe(ItemTagSubscriber.subscription(process))
                    .subscribe(subscriber.subscription())
                    .executor(ForkJoinPool.commonPool())
                    .build();
            loader.start(LootTestWorld.createWorld()).close();
        }

        assertEquals(expectedCount, subscriber.current().tables().size());
        assertEquals(expectedCount, manager.tables().size());
        spotCheck(manager);
    }

    /** 对照原版 JSON 抽查四个表。 */
    private static void spotCheck(LootManager manager) {
        var grassBlock = manager.get(Key.key("blocks/grass_block")).orElseThrow();
        assertEquals(Key.key("minecraft:block"), grassBlock.type());
        assertEquals(1, grassBlock.pools().size());
        var grassPool = grassBlock.pools().getFirst();
        assertEquals(1, grassPool.entries().size());
        assertInstanceOf(AlternativesEntry.class, grassPool.entries().getFirst());

        var zombie = manager.get(Key.key("entities/zombie")).orElseThrow();
        assertEquals(Key.key("minecraft:entity"), zombie.type());
        assertEquals(4, zombie.pools().size());

        var piglinBartering = manager.get(Key.key("gameplay/piglin_bartering")).orElseThrow();
        assertEquals(Key.key("minecraft:barter"), piglinBartering.type());
        assertEquals(1, piglinBartering.pools().size());
        assertEquals(19, piglinBartering.pools().getFirst().entries().size());

        var simpleDungeon = manager.get(Key.key("chests/simple_dungeon")).orElseThrow();
        assertEquals(Key.key("minecraft:chest"), simpleDungeon.type());
        assertEquals(3, simpleDungeon.pools().size());
    }

    private static int countLootTables(Path server) throws IOException {
        try (var zip = new ZipFile(server.toFile())) {
            var entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var name = entry.getName();
                if (name.startsWith("data/minecraft/loot_table/") && name.endsWith(".json")) {
                    count++;
                }
            }
            return count;
        }
    }
}
