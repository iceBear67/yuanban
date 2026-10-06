package io.ib67.yuanban.itemtag;

import io.ib67.yuanban.datapack.ReloadRequest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.common.TagsPacket;
import net.minestom.testing.Env;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
@ExtendWith(ResetItemTags.class)
class ReloadTagsPacketTest {
    @TempDir
    Path tempDir;

    @Test
    void reloadSendsTagsToOnlinePlayers(Env env) {
        var world = new TagWorld(tempDir)
                .vanillaTag("custom", """
                        {"values": ["minecraft:diamond"]}
                        """);
        var session = world.loader().subscribe(ItemTagSubscriber.subscription(env.process())).build()
                .start(TagWorld.createWorld());

        var instance = env.process().instance().createInstanceContainer();
        var connection = env.createConnection();
        connection.connect(instance, new Pos(0, 40, 0));
        var tracker = connection.trackIncoming(TagsPacket.class);

        world.packTag("extra", "minecraft", "custom", """
                {"values": ["minecraft:emerald"]}
                """);
        session.reload(new ReloadRequest.Rescan());

        tracker.assertSingle(packet -> {
            var items = packet.registries().stream()
                    .filter(registry -> registry.registry().equals("minecraft:item"))
                    .findFirst().orElseThrow();
            assertEquals(1, items.tags().size());
            var tag = items.tags().getFirst();
            assertEquals("minecraft:custom", tag.identifier());
            assertArrayEquals(new int[]{Material.DIAMOND.id(), Material.EMERALD.id()}, tag.entries());
        });
        session.close();
    }
}
