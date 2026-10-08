package io.ib67.yuanban.recipe;

import com.google.gson.JsonParser;
import io.ib67.yuanban.crafting.Crafting;
import io.ib67.yuanban.crafting.recipe.Recipe;
import io.ib67.yuanban.crafting.recipe.kind.cooking.CookingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.DyeRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ImbueRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ShapedRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ShapelessRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.TransmuteRecipe;
import io.ib67.yuanban.crafting.recipe.kind.smithing.SmithingTransformRecipe;
import io.ib67.yuanban.crafting.recipe.kind.smithing.SmithingTrimRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.BannerDuplicateRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.BookCloningRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.DecoratedPotRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.FireworkRocketRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.FireworkStarFadeRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.FireworkStarRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.MapExtendingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.RepairItemRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.ShieldDecorationRecipe;
import io.ib67.yuanban.crafting.recipe.kind.stonecutter.StonecutterRecipe;
import io.ib67.yuanban.crafting.testing.InMemoryMaps;
import io.ib67.yuanban.datapack.DataPackLoader;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.item.Material;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.play.DeclareRecipesPacket;
import net.minestom.server.network.packet.server.play.RecipeBookAddPacket;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 读取原版 26.2 服务端 jar 中的全部物品标签和配方。jar 由根项目的 {@code extractVanillaServer} 任务准备，
 * 路径通过系统属性 {@code yuanban.vanilla-server} 传入。
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
        var crafting = new Crafting(process, new InMemoryMaps());
        process.eventHandler().addChild(crafting.eventNode());
        var subscriber = new RecipeSubscriber(process, crafting.recipes());
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
        assertEquals(expectedTypes.values().stream().mapToInt(Integer::intValue).sum(), crafting.recipes().recipes().size());

        // 两份独立来源：Minestom 自带的标签数据和原版 jar 中的标签文件，展开结果应当相同
        assertEquals(builtInTags, snapshotTags());

        // 玩家首次出生时收到的配方数据：Minestom 自己的（空的）数据包在前，crafting 的数据包在后
        var connection = env.createConnection();
        var declared = connection.trackIncoming(DeclareRecipesPacket.class);
        var book = connection.trackIncoming(RecipeBookAddPacket.class);
        connection.connect(process.instance().createInstanceContainer(), new Pos(0, 40, 0));
        var declare = declared.collect().getLast();
        // 原版 26.2 的切石机配方都可以放置，全部出现在数据包中
        assertEquals(expectedTypes.get("minecraft:stonecutting"), declare.stonecutterRecipes().size());
        NetworkBuffer.resizableBuffer(process).write(DeclareRecipesPacket.SERIALIZER, declare);
        var entries = book.collect().getLast();
        assertTrue(entries.replace());
        assertEquals(crafting.recipes().recipes().values().stream().mapToInt(recipe -> recipe.display().size()).sum(), entries.entries().size());
        NetworkBuffer.resizableBuffer(process).write(RecipeBookAddPacket.SERIALIZER, entries);
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

    private static String typeOf(Recipe<?> recipe) {
        return switch (recipe) {
            case ShapedRecipe _ -> "minecraft:crafting_shaped";
            case ShapelessRecipe _ -> "minecraft:crafting_shapeless";
            case TransmuteRecipe _ -> "minecraft:crafting_transmute";
            case DyeRecipe _ -> "minecraft:crafting_dye";
            case ImbueRecipe _ -> "minecraft:crafting_imbue";
            case DecoratedPotRecipe _ -> "minecraft:crafting_decorated_pot";
            case BookCloningRecipe _ -> "minecraft:crafting_special_bookcloning";
            case MapExtendingRecipe _ -> "minecraft:crafting_special_mapextending";
            case FireworkRocketRecipe _ -> "minecraft:crafting_special_firework_rocket";
            case FireworkStarRecipe _ -> "minecraft:crafting_special_firework_star";
            case FireworkStarFadeRecipe _ -> "minecraft:crafting_special_firework_star_fade";
            case BannerDuplicateRecipe _ -> "minecraft:crafting_special_bannerduplicate";
            case ShieldDecorationRecipe _ -> "minecraft:crafting_special_shielddecoration";
            case RepairItemRecipe _ -> "minecraft:crafting_special_repairitem";
            case CookingRecipe cooking -> "minecraft:" + cooking.kind().type();
            case StonecutterRecipe _ -> "minecraft:stonecutting";
            case SmithingTransformRecipe _ -> "minecraft:smithing_transform";
            case SmithingTrimRecipe _ -> "minecraft:smithing_trim";
            default -> throw new IllegalStateException("Unknown recipe type: " + recipe.getClass());
        };
    }
}
