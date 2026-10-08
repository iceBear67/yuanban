package io.ib67.yuanban.lootparser;

import io.ib67.yuanban.datapack.LoadFailedException;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import io.ib67.yuanban.loot.LootManager;
import net.minestom.server.item.Material;
import net.minestom.server.registry.TagKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
@ExtendWith(ResetItemTags.class)
class LootLoadErrorTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsEveryBrokenFile(Env env) {
        var world = new LootTestWorld(tempDir)
                .vanilla("tags/item/planks.json", """
                        {"values": ["minecraft:oak_planks"]}
                        """)
                .vanilla("loot_table/good.json", """
                        {"pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:stone"}]}]}
                        """)
                .pack("broken", "example", "loot_table/missing_tag.json", """
                        {"pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:stone",
                          "conditions": [{"condition": "minecraft:match_tool", "predicate": {"items": "#minecraft:logs"}}]}]}]}
                        """)
                .pack("broken", "example", "loot_table/unknown_type.json", """
                        {"pools": [{"rolls": 1, "entries": [{"type": "minecraft:no_such_entry", "name": "minecraft:stone"}]}]}
                        """);
        var manager = new LootManager(io.ib67.yuanban.loot.LootTypeRegistry.vanilla());
        var subscriber = new LootTableSubscriber(env.process(), manager);
        var loader = world.loader(env.process(), subscriber);

        var thrown = assertThrows(LoadFailedException.class, () -> loader.start(LootTestWorld.createWorld()));
        var exception = assertInstanceOf(LootException.class, thrown.getCause());
        var lines = Arrays.asList(exception.getMessage().split("\n"));
        if (lines.size() != 2) {
            throw new AssertionError("expected 2 failure lines, got " + lines.size()
                    + " raw=[" + exception.getMessage() + "]");
        }
        // subscriber 按资源 ID 排序拼接失败；missing_tag 的字典序在 unknown_type 之前。
        // 每行前部是 "Couldn't parse data file '<id>' from <resource>"，在此基础上看尾部错误信息。
        var missingTag = lines.stream().filter(l -> l.contains("'example:missing_tag'")).findFirst().orElseThrow();
        var unknownType = lines.stream().filter(l -> l.contains("'example:unknown_type'")).findFirst().orElseThrow();
        assertTrue(missingTag.startsWith("Couldn't parse data file 'example:missing_tag' from [file/broken] data/example/loot_table/missing_tag.json: Missing tag: 'minecraft:logs' in 'minecraft:item'"), missingTag);
        assertTrue(unknownType.startsWith("Couldn't parse data file 'example:unknown_type' from "), unknownType);
        assertTrue(unknownType.contains("minecraft:no_such_entry"), unknownType);
        // ID 字典序：missing_tag 必须出现在 unknown_type 之前
        assertTrue(lines.indexOf(missingTag) < lines.indexOf(unknownType), String.join(" | ", lines));
        // 失败时不调用任何 apply
        assertNotNull(Material.staticRegistry().getTag(TagKey.of("minecraft:logs")));
        assertEquals(LootTableSet.EMPTY, subscriber.current());
        assertEquals(Map.of(), manager.tables());
    }
}
