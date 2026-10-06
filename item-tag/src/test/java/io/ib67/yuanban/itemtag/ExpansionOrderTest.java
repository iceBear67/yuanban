package io.ib67.yuanban.itemtag;

import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
@ExtendWith(ResetItemTags.class)
class ExpansionOrderTest {
    @TempDir
    Path tempDir;

    @Test
    void expandsInVanillaOrder(Env env) {
        var world = new TagWorld(tempDir)
                .vanillaTag("logs", """
                        {"values": ["minecraft:oak_log", "minecraft:birch_log"]}
                        """)
                .vanillaTag("fuel", """
                        {"values": [
                          "minecraft:coal",
                          "#minecraft:logs",
                          "minecraft:oak_log",
                          {"id": "example:missing", "required": false},
                          {"id": "#minecraft:missing", "required": false}
                        ]}
                        """)
                .vanillaTag("replaced", """
                        {"values": ["minecraft:dirt"]}
                        """)
                .packTag("extra", "minecraft", "logs", """
                        {"values": ["minecraft:spruce_log", "minecraft:oak_log"]}
                        """)
                .packTag("replace", "minecraft", "replaced", """
                        {"replace": true, "values": ["minecraft:stone"]}
                        """);
        var tags = world.load(env.process());

        assertEquals(List.of(Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG), tags.find(Key.key("logs")).orElseThrow());
        assertEquals(List.of(Material.COAL, Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG), tags.find(Key.key("fuel")).orElseThrow());
        assertEquals(List.of(Material.STONE), tags.find(Key.key("replaced")).orElseThrow());
    }
}
