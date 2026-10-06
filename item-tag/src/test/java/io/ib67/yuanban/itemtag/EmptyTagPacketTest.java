package io.ib67.yuanban.itemtag;

import io.ib67.yuanban.datapack.ReloadRequest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.packet.server.common.TagsPacket;
import net.minestom.testing.Env;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 只删除或新建空标签的提交也要让缓存的标签数据包失效。空标签不会触发任何条目的增删，
 * Minestom 的 {@code removeTag} / {@code getOrCreateTag} 本身也不会让缓存失效。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class EmptyTagPacketTest {
    @TempDir
    Path tempDir;

    @Test
    void emptyTagChangesReachPlayers(Env env) {
        var world = new TagWorld(tempDir)
                .vanillaTag("custom", """
                        {"values": ["minecraft:diamond"]}
                        """)
                .packTag("temporary", "minecraft", "temporary", """
                        {"values": []}
                        """);
        var session = world.loader().subscribe(ItemTagSubscriber.subscription(env.process())).build()
                .start(TagWorld.createWorld());

        var connections = env.process().connection();
        var connection = env.createConnection();
        var player = connection.connect(env.process().instance().createInstanceContainer(), new Pos(0, 40, 0));
        // 让 ConnectionManager 生成并缓存当前的标签数据包
        connections.sendRegistryTags(player);
        var tracker = connection.trackIncoming(TagsPacket.class);

        // 只删除一个空标签
        session.reload(new ReloadRequest.Select(List.of("vanilla")));
        tracker.assertSingle(packet -> assertEquals(Set.of("minecraft:custom"), itemTags(packet)));

        // 只新建一个空标签：重新发现数据包，file/temporary 保持禁用，新出现的 file/fresh 自动启用
        world.packTag("fresh", "minecraft", "fresh", """
                {"values": []}
                """);
        var fresh = connection.trackIncoming(TagsPacket.class);
        session.reload(new ReloadRequest.Rescan());
        fresh.assertSingle(packet -> assertEquals(Set.of("minecraft:custom", "minecraft:fresh"), itemTags(packet)));
        session.close();
    }

    private static Set<String> itemTags(TagsPacket packet) {
        return packet.registries().stream()
                .filter(registry -> registry.registry().equals("minecraft:item"))
                .findFirst().orElseThrow()
                .tags().stream()
                .map(TagsPacket.Tag::identifier)
                .collect(Collectors.toSet());
    }
}
