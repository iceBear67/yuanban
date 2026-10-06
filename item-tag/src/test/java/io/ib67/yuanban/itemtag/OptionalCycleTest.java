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
class OptionalCycleTest {
    @TempDir
    Path tempDir;

    /**
     * 原版 {@code DependencySorter} 按 {@code Identifier} 的哈希顺序遍历：{@code minecraft:b} 在 {@code minecraft:a}
     * 之前，于是先加入边 {@code b → a}，{@code a → b} 成环被丢弃；先展开 {@code a}（此时 {@code b} 尚未展开，
     * 可选引用被跳过），再展开 {@code b}。
     */
    @Test
    void optionalCycleFollowsVanillaTraversal(Env env) {
        var world = new TagWorld(tempDir)
                .vanillaTag("a", """
                        {"values": ["minecraft:stone", {"id": "#minecraft:b", "required": false}]}
                        """)
                .vanillaTag("b", """
                        {"values": ["minecraft:dirt", {"id": "#minecraft:a", "required": false}]}
                        """);
        var tags = world.load(env.process());

        assertEquals(List.of(Material.STONE), tags.find(Key.key("a")).orElseThrow());
        assertEquals(List.of(Material.DIRT, Material.STONE), tags.find(Key.key("b")).orElseThrow());
    }
}
