package io.ib67.yuanban.agent.live;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * 测试 JVM 中的一个 Minestom 服务端（离线模式，监听回环地址上的空闲端口）：y 小于 64 为石头，玩家出生在 (0.5, 64, 0.5)。
 */
public final class LiveServer {
    public static final Pos SPAWN = new Pos(0.5, 64, 0.5);
    private static @Nullable LiveServer instance;

    public final InstanceContainer world;
    public final int port;

    private LiveServer() {
        var server = MinecraftServer.init();
        world = MinecraftServer.getInstanceManager().createInstanceContainer();
        world.setGenerator(unit -> unit.modifier().fillHeight(-64, 64, Block.STONE));
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(world);
            event.getPlayer().setRespawnPoint(SPAWN);
        });
        server.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        port = MinecraftServer.getServer().getPort();
    }

    public static synchronized LiveServer get() {
        if (instance == null) {
            instance = new LiveServer();
        }
        return instance;
    }

    /**
     * 服务端中的在线玩家；不在线时为 null
     */
    public @Nullable Player player(String username) {
        return MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(player -> player.getUsername().equals(username)).findFirst().orElse(null);
    }
}
