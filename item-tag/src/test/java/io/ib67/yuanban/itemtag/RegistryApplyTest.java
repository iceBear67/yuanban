package io.ib67.yuanban.itemtag;

import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.TagKey;
import net.minestom.testing.Env;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@EnvTest
@ExtendWith(ResetItemTags.class)
class RegistryApplyTest {
    @TempDir
    Path tempDir;

    @Test
    void appliesToMinestomRegistry(Env env) {
        var registry = Material.staticRegistry();
        var logs = registry.getTag(TagKey.of("minecraft:logs"));
        assertNotNull(logs);
        assertNotNull(registry.getTag(TagKey.of("minecraft:planks")));

        new TagWorld(tempDir)
                .vanillaTag("logs", """
                        {"values": ["minecraft:spruce_log", "minecraft:oak_log"]}
                        """)
                .vanillaTag("custom", """
                        {"values": ["minecraft:diamond"]}
                        """)
                .load(env.process());

        assertSame(logs, registry.getTag(TagKey.of("minecraft:logs")));
        assertEquals(List.of(Key.key("spruce_log"), Key.key("oak_log")), keys(logs));
        assertEquals(List.of(Key.key("diamond")), keys(registry.getTag(TagKey.of("minecraft:custom"))));
        assertNull(registry.getTag(TagKey.of("minecraft:planks")));
        assertEquals(2, registry.tags().size());
    }

    private static List<Key> keys(Iterable<RegistryKey<Material>> tag) {
        var keys = new ArrayList<Key>();
        tag.forEach(key -> keys.add(key.key()));
        return keys;
    }
}
