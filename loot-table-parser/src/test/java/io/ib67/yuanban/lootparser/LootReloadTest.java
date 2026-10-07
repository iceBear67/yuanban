package io.ib67.yuanban.lootparser;

import io.ib67.yuanban.datapack.ReloadRequest;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootTypeRegistry;
import net.kyori.adventure.key.Key;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
@ExtendWith(ResetItemTags.class)
class LootReloadTest {
    @TempDir
    Path tempDir;

    @Test
    void reloadReplacesTables(Env env) {
        var world = new LootTestWorld(tempDir)
                .vanilla("loot_table/alpha.json",
                        "{\"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:item\", \"name\": \"minecraft:stone\"}]}]}")
                .vanilla("loot_table/beta.json",
                        "{\"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:item\", \"name\": \"minecraft:dirt\"}]}]}");
        var manager = new LootManager(LootTypeRegistry.vanilla());
        var subscriber = new LootTableSubscriber(env.process(), manager);
        var session = world.loader(env.process(), subscriber).start(LootTestWorld.createWorld());
        assertEquals(Set.of(Key.key("alpha"), Key.key("beta")), subscriber.current().tables().keySet());
        assertEquals(Set.of(Key.key("alpha"), Key.key("beta")), manager.tables().keySet());

        // 移除 alpha、加入 example:gamma 后重载：旧 ID 在新快照里取不到，beta 保留
        try {
            java.nio.file.Files.delete(tempDir.resolve("vanilla/data/minecraft/loot_table/alpha.json"));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        world.pack("gold", "example", "loot_table/gamma.json",
                "{\"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:item\", \"name\": \"minecraft:gold_ingot\"}]}]}");
        session.reload(new ReloadRequest.Rescan());

        assertEquals(Set.of(Key.key("beta"), Key.key("example:gamma")),
                subscriber.current().tables().keySet());
        assertEquals(subscriber.current().tables(), manager.tables());
        assertTrue(manager.get(Key.key("alpha")).isEmpty());
        assertTrue(manager.get(Key.key("beta")).isPresent());
        assertTrue(manager.get(Key.key("example:gamma")).isPresent());
        session.close();
    }
}
