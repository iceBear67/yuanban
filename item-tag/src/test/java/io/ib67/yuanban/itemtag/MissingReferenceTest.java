package io.ib67.yuanban.itemtag;

import io.ib67.yuanban.datapack.LoadFailedException;
import net.minestom.server.registry.TagKey;
import net.minestom.server.item.Material;
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

@EnvTest
@ExtendWith(ResetItemTags.class)
class MissingReferenceTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsAllMissingReferences(Env env) {
        var world = new TagWorld(tempDir)
                .vanillaTag("broken", """
                        {"values": ["minecraft:stone", "example:unknown", "#minecraft:absent"]}
                        """)
                .vanillaTag("dependent", """
                        {"values": ["#minecraft:broken"]}
                        """)
                .vanillaTag("cycle_a", """
                        {"values": ["#minecraft:cycle_b"]}
                        """)
                .vanillaTag("cycle_b", """
                        {"values": ["#minecraft:cycle_a"]}
                        """);
        var loader = world.loader().subscribe(ItemTagSubscriber.subscription(env.process())).build();

        var exception = assertInstanceOf(ItemTagException.class,
                assertThrows(LoadFailedException.class, () -> loader.start(TagWorld.createWorld())).getCause());
        assertEquals(List.of(
                "Couldn't load tag minecraft:broken as it is missing following references: example:unknown (from vanilla), #minecraft:absent (from vanilla)",
                "Couldn't load tag minecraft:cycle_a as it is missing following references: #minecraft:cycle_b (from vanilla)",
                "Couldn't load tag minecraft:cycle_b as it is missing following references: #minecraft:cycle_a (from vanilla)",
                "Couldn't load tag minecraft:dependent as it is missing following references: #minecraft:broken (from vanilla)"
        ), Arrays.asList(exception.getMessage().split("\n")));
        // 加载失败的时候没有调用 apply，Minestom 自带的标签保持不变
        assertNotNull(Material.staticRegistry().getTag(TagKey.of("minecraft:planks")));
    }
}
