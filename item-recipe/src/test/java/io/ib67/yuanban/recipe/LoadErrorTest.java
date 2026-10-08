package io.ib67.yuanban.recipe;

import io.ib67.yuanban.crafting.Crafting;
import io.ib67.yuanban.crafting.testing.InMemoryMaps;
import io.ib67.yuanban.datapack.LoadFailedException;
import net.minestom.server.item.Material;
import net.minestom.server.registry.TagKey;
import net.minestom.testing.Env;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
@ExtendWith(ResetItemTags.class)
class LoadErrorTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsEveryBrokenFile(Env env) {
        var world = new RecipeWorld(tempDir)
                .vanilla("tags/item/planks.json", """
                        {"values": ["minecraft:oak_planks"]}
                        """)
                .vanilla("recipe/good.json", """
                        {"type": "stonecutting", "ingredient": "#minecraft:planks", "result": "minecraft:stick"}
                        """)
                .pack("broken", "example", "recipe/missing_tag.json", """
                        {"type": "stonecutting", "ingredient": "#minecraft:logs", "result": "minecraft:stick"}
                        """)
                .pack("broken", "example", "recipe/unknown_type.json", """
                        {"type": "minecraft:brewing"}
                        """);
        var crafting = new Crafting(env.process(), new InMemoryMaps());
        var subscriber = new RecipeSubscriber(env.process(), crafting.recipes());
        var loader = world.loader(env.process(), subscriber);

        var exception = assertInstanceOf(RecipeException.class,
                assertThrows(LoadFailedException.class, () -> loader.start(RecipeWorld.createWorld())).getCause());
        List<String> lines = Arrays.asList(exception.getMessage().split("\n"));
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("Couldn't parse data file 'example:missing_tag' from [file/broken] data/example/recipe/missing_tag.json: "), lines.get(0));
        assertTrue(lines.get(0).contains("Missing tag: 'minecraft:logs' in 'minecraft:item'"), lines.get(0));
        assertTrue(lines.get(1).startsWith("Couldn't parse data file 'example:unknown_type' from [file/broken] data/example/recipe/unknown_type.json: "), lines.get(1));
        // 加载失败的时候没有调用任何 apply
        assertNotNull(Material.staticRegistry().getTag(TagKey.of("minecraft:logs")));
        assertEquals(RecipeSet.EMPTY, subscriber.current());
        assertEquals(Map.of(), crafting.recipes().recipes());
    }

    /**
     * JSON 语法错误与解码错误一起按配方 ID 汇总。错误信息是 Gson 异常的信息。
     */
    @Test
    void reportsJsonSyntaxErrorsWithDecodingErrors(Env env) {
        var world = new RecipeWorld(tempDir)
                .vanilla("recipe/good.json", """
                        {"type": "stonecutting", "ingredient": "minecraft:stone", "result": "minecraft:stick"}
                        """)
                .pack("broken", "example", "recipe/c_unterminated.json", """
                        {"type": "stonecutting",
                        """)
                .pack("broken", "example", "recipe/b_unknown_type.json", """
                        {"type": "minecraft:brewing"}
                        """)
                .pack("broken", "example", "recipe/a_trailing.json", """
                        {"type": "stonecutting", "ingredient": "minecraft:stone", "result": "minecraft:stick"} {}
                        """);
        var crafting = new Crafting(env.process(), new InMemoryMaps());
        var subscriber = new RecipeSubscriber(env.process(), crafting.recipes());
        var loader = world.loader(env.process(), subscriber);

        var message = assertInstanceOf(RecipeException.class,
                assertThrows(LoadFailedException.class, () -> loader.start(RecipeWorld.createWorld())).getCause()).getMessage();
        int trailing = message.indexOf("Couldn't parse data file 'example:a_trailing' from [file/broken] data/example/recipe/a_trailing.json: ");
        int unknownType = message.indexOf("Couldn't parse data file 'example:b_unknown_type' from [file/broken] data/example/recipe/b_unknown_type.json: "
                + "Unknown registry key in ResourceKey[minecraft:root / minecraft:recipe_serializer]: minecraft:brewing\n");
        int unterminated = message.indexOf("Couldn't parse data file 'example:c_unterminated' from [file/broken] data/example/recipe/c_unterminated.json: ");
        assertEquals(0, trailing, message);
        assertTrue(trailing < unknownType && unknownType < unterminated, message);
        int malformed = message.indexOf("MalformedJsonException");
        assertTrue(trailing < malformed && malformed < unknownType, message);
        assertTrue(message.indexOf("End of input", unterminated) > unterminated, message);
        assertEquals(RecipeSet.EMPTY, subscriber.current());
        assertEquals(Map.of(), crafting.recipes().recipes());
    }
}
