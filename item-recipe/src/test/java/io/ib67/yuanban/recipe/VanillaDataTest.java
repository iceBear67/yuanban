package io.ib67.yuanban.recipe;

import com.google.gson.JsonParser;
import io.ib67.yuanban.datapack.DataPackLoader;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.recipe.definition.RecipeDefinition;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.CachedPacket;
import net.minestom.server.network.packet.server.play.DeclareRecipesPacket;
import net.minestom.server.registry.RegistryTag;
import net.minestom.testing.Env;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 读取原版 26.2 服务端 jar 中的全部物品标签和配方。jar 由 {@code build.gradle} 的下载任务准备，路径通过系统属性
 * {@code yuanban.vanilla-server} 传入。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class VanillaDataTest {
    @Test
    void loadsAllVanillaRecipes(Env env) throws IOException {
        var server = Path.of(System.getProperty("yuanban.vanilla-server"));
        var expectedTypes = countRecipeTypes(server);
        var builtInTags = snapshotTags();

        var process = env.process();
        var subscriber = new RecipeSubscriber(process);
        try (var jar = FileSystems.newFileSystem(URI.create("jar:" + server.toUri()), Map.of())) {
            var loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
                    .provider(new BuiltInPackProvider(jar.getPath("/"), "26.2"))
                    .subscribe(ItemTagSubscriber.subscription(process))
                    .subscribe(subscriber.subscription())
                    .executor(ForkJoinPool.commonPool())
                    .build();
            loader.start(RecipeWorld.createWorld()).close();
        }

        var actualTypes = new TreeMap<String, Integer>();
        subscriber.current().recipes().values().forEach(recipe -> actualTypes.merge(typeOf(recipe), 1, Integer::sum));
        assertEquals(expectedTypes, actualTypes);
        assertEquals(expectedTypes.values().stream().mapToInt(Integer::intValue).sum(), process.recipe().getRecipes().size());

        // 两份独立来源：Minestom 自带的标签数据和原版 jar 中的标签文件，展开结果应当相同
        assertEquals(builtInTags, snapshotTags());

        var manager = process.recipe();
        var declare = (DeclareRecipesPacket) ((CachedPacket) manager.getDeclareRecipesPacket()).packet(ConnectionState.PLAY);
        // 原版 26.2 的切石机配方都只有一个物品原料，全部出现在数据包中
        assertEquals(expectedTypes.get("minecraft:stonecutting"), declare.stonecutterRecipes().size());
        NetworkBuffer.resizableBuffer(process).write(DeclareRecipesPacket.SERIALIZER, declare);
    }

    private static Map<String, Integer> countRecipeTypes(Path server) throws IOException {
        var counts = new TreeMap<String, Integer>();
        try (var zip = new ZipFile(server.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().startsWith("data/minecraft/recipe/") && entry.getName().endsWith(".json")) {
                    try (var reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                        var type = Key.key(JsonParser.parseReader(reader).getAsJsonObject().get("type").getAsString()).asString();
                        counts.merge(type, 1, Integer::sum);
                    }
                }
            }
        }
        return counts;
    }

    private static Map<Key, List<Key>> snapshotTags() {
        var result = new HashMap<Key, List<Key>>();
        for (RegistryTag<Material> tag : Material.staticRegistry().tags()) {
            var keys = new ArrayList<Key>();
            tag.forEach(key -> keys.add(key.key()));
            result.put(tag.key().key(), keys);
        }
        return result;
    }

    private static String typeOf(RecipeDefinition recipe) {
        return switch (recipe) {
            case RecipeDefinition.ShapedRecipe _ -> "minecraft:crafting_shaped";
            case RecipeDefinition.ShapelessRecipe _ -> "minecraft:crafting_shapeless";
            case RecipeDefinition.TransmuteRecipe _ -> "minecraft:crafting_transmute";
            case RecipeDefinition.DyeRecipe _ -> "minecraft:crafting_dye";
            case RecipeDefinition.ImbueRecipe _ -> "minecraft:crafting_imbue";
            case RecipeDefinition.DecoratedPotRecipe _ -> "minecraft:crafting_decorated_pot";
            case RecipeDefinition.BookCloningRecipe _ -> "minecraft:crafting_special_bookcloning";
            case RecipeDefinition.MapExtendingRecipe _ -> "minecraft:crafting_special_mapextending";
            case RecipeDefinition.FireworkRocketRecipe _ -> "minecraft:crafting_special_firework_rocket";
            case RecipeDefinition.FireworkStarRecipe _ -> "minecraft:crafting_special_firework_star";
            case RecipeDefinition.FireworkStarFadeRecipe _ -> "minecraft:crafting_special_firework_star_fade";
            case RecipeDefinition.BannerDuplicateRecipe _ -> "minecraft:crafting_special_bannerduplicate";
            case RecipeDefinition.ShieldDecorationRecipe _ -> "minecraft:crafting_special_shielddecoration";
            case RecipeDefinition.RepairItemRecipe _ -> "minecraft:crafting_special_repairitem";
            case RecipeDefinition.CookingRecipe cooking -> "minecraft:" + cooking.kind().type();
            case RecipeDefinition.StonecutterRecipe _ -> "minecraft:stonecutting";
            case RecipeDefinition.SmithingTransformRecipe _ -> "minecraft:smithing_transform";
            case RecipeDefinition.SmithingTrimRecipe _ -> "minecraft:smithing_trim";
        };
    }
}
