package io.ib67.yuanban.enchant;

import com.google.gson.JsonParser;
import io.ib67.yuanban.datapack.DataPackLoader;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.enchant.Enchantment;
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
import java.util.concurrent.ForkJoinPool;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 读取原版 26.2 服务端 jar 中的全部附魔、附魔标签和附魔提供器。jar 由 {@code build.gradle}
 * 的下载任务准备，路径通过系统属性 {@code yuanban.vanilla-server} 传入。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class VanillaDataTest {
    @Test
    void loadsAllVanillaEnchantments(Env env) throws IOException {
        var server = Path.of(System.getProperty("yuanban.vanilla-server"));
        var expectedEnchantmentCount = countJsonFiles(server, "data/minecraft/enchantment/");
        var expectedTagCount = countJsonFiles(server, "data/minecraft/tags/enchantment/");
        var expectedProviderCount = countJsonFiles(server, "data/minecraft/enchantment_provider/");

        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        try (var jar = FileSystems.newFileSystem(URI.create("jar:" + server.toUri()), Map.of());
             var session = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
                     .provider(new BuiltInPackProvider(jar.getPath("/"), "26.2"))
                     .subscribe(ItemTagSubscriber.subscription(process))
                     .subscribe(enchantmentTags.subscription())
                     .subscribe(enchantments.subscription())
                     .subscribe(providers.subscription())
                     .executor(ForkJoinPool.commonPool())
                     .build()
                     .start(EnchantWorld.createWorld())) {

            var data = enchantments.current();
            assertEquals(expectedEnchantmentCount, data.enchantments().size());
            assertEquals(expectedTagCount, enchantmentTags.current().tags().size());
            assertEquals(expectedProviderCount, providers.current().providers().size());

            // 抽查 sharpness 字段和数据包一致
            var sharpness = data.enchantments().get(Key.key("minecraft:sharpness"));
            var sharpnessJson = readJson(server, "data/minecraft/enchantment/sharpness.json").getAsJsonObject();
            assertEquals(sharpnessJson.get("weight").getAsInt(), sharpness.weight());
            assertEquals(sharpnessJson.get("max_level").getAsInt(), sharpness.maxLevel());
            assertEquals(sharpnessJson.get("anvil_cost").getAsInt(), sharpness.anvilCost());
            assertEquals(sharpnessJson.get("min_cost").getAsJsonObject().get("base").getAsInt(), sharpness.minCost().base());
            assertEquals(sharpnessJson.get("min_cost").getAsJsonObject().get("per_level_above_first").getAsInt(), sharpness.minCost().perLevelAboveFirst());
            assertEquals(sharpnessJson.get("max_cost").getAsJsonObject().get("base").getAsInt(), sharpness.maxCost().base());
            assertEquals(sharpnessJson.get("max_cost").getAsJsonObject().get("per_level_above_first").getAsInt(), sharpness.maxCost().perLevelAboveFirst());
            assertEquals(sharpnessJson.get("exclusive_set").getAsString(), "#" + sharpness.exclusiveSet().asString());
            assertEquals(sharpnessJson.get("supported_items").getAsString(), "#" + sharpness.supportedItems().asString());

            // 注册表里的条目和数据包一致
            var registrySharpness = process.enchantment().get(Key.key("minecraft:sharpness"));
            assertEquals(sharpness.weight(), registrySharpness.weight());
            assertEquals(sharpness.maxLevel(), registrySharpness.maxLevel());
            assertEquals(sharpness.anvilCost(), registrySharpness.anvilCost());
            assertEquals(sharpness.minCost(), registrySharpness.minCost());
            assertEquals(sharpness.maxCost(), registrySharpness.maxCost());

            // 43 个附魔全部注册进注册表
            var registrySize = process.enchantment().values().size();
            assertEquals(expectedEnchantmentCount, registrySize);

            // 7 个提供器全部成为结构化数据，抽查字段和数据包一致
            var providerData = providers.current().providers();
            assertEquals(7, providerData.size());
            assertVanillaProvider(providerData.get(Key.key("minecraft:enderman_loot_drop")),
                    "minecraft:silk_touch", 1);
            assertVanillaProvider(providerData.get(Key.key("minecraft:pillager_spawn_crossbow")),
                    "minecraft:piercing", 1);
            assertVanillaProvider(providerData.get(Key.key("minecraft:raid/pillager_post_wave_3")),
                    "minecraft:quick_charge", 1);
            assertVanillaProvider(providerData.get(Key.key("minecraft:raid/pillager_post_wave_5")),
                    "minecraft:quick_charge", 2);
            assertVanillaProvider(providerData.get(Key.key("minecraft:raid/vindicator")),
                    "minecraft:sharpness", 1);
            assertVanillaProvider(providerData.get(Key.key("minecraft:raid/vindicator_post_wave_5")),
                    "minecraft:sharpness", 2);
            var mobSpawnByCost = assertInstanceOf(ProviderEntry.ByCostWithDifficulty.class,
                    providerData.get(Key.key("minecraft:mob_spawn_equipment")));
            assertEquals(Key.key("minecraft:on_mob_spawn_equipment"), mobSpawnByCost.enchantments());
            assertEquals(5, mobSpawnByCost.minCost());
            assertEquals(17, mobSpawnByCost.maxCostSpan());
        }
    }

    private static void assertVanillaProvider(ProviderEntry entry, String enchantment, int level) {
        var single = assertInstanceOf(ProviderEntry.Single.class, entry);
        assertEquals(Key.key(enchantment), single.enchantment());
        assertEquals(new ProviderEntry.IntProvider.ConstantInt(level), single.level());
        assertEquals(level, single.level().sample());
    }

    private static int countJsonFiles(Path server, String prefix) throws IOException {
        var count = 0;
        try (var zip = new ZipFile(server.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().startsWith(prefix) && entry.getName().endsWith(".json")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static com.google.gson.JsonElement readJson(Path server, String name) throws IOException {
        try (var zip = new ZipFile(server.toFile())) {
            var entry = zip.getEntry(name);
            try (var reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        }
    }
}
