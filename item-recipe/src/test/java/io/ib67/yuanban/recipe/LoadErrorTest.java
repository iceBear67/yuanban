package io.ib67.yuanban.recipe;

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
        var subscriber = new RecipeSubscriber(env.process());
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
        assertEquals(0, env.process().recipe().getRecipes().size());
    }
}
