package io.ib67.yuanban.bot.transport;

import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBundlePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketDirectionTest {
    @Test
    void packetsAreCheckedAgainstEveryProtocolState() {
        // 只在一个阶段注册的数据包
        assertDoesNotThrow(() -> PacketDirection.requireServerbound(ClientIntentionPacket.class));
        assertDoesNotThrow(() -> PacketDirection.requireClientbound(ClientboundLoginFinishedPacket.class));
        assertDoesNotThrow(() -> PacketDirection.requireClientbound(ClientboundLevelChunkWithLightPacket.class));
        assertDoesNotThrow(() -> PacketDirection.requireServerbound(ServerboundSwingPacket.class));
        // 配置与游戏阶段都有的数据包
        assertDoesNotThrow(() -> PacketDirection.requireClientbound(ClientboundKeepAlivePacket.class));
        assertDoesNotThrow(() -> PacketDirection.requireServerbound(ServerboundKeepAlivePacket.class));
    }

    @Test
    void theWrongDirectionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PacketDirection.requireServerbound(ClientboundKeepAlivePacket.class));
        assertThrows(IllegalArgumentException.class, () -> PacketDirection.requireClientbound(ServerboundKeepAlivePacket.class));
        assertThrows(IllegalArgumentException.class, () -> PacketDirection.requireClientbound(ClientIntentionPacket.class));
    }

    @Test
    void unregisteredClassesAreRejectedAndTheBaseTypeIsAccepted() {
        // Bundle 由分隔符表示，MCProtocolLib 的编解码器中没有注册这个类
        assertThrows(IllegalArgumentException.class, () -> PacketDirection.requireClientbound(ClientboundBundlePacket.class));
        assertThrows(IllegalArgumentException.class, () -> PacketDirection.requireServerbound(ClientboundBundlePacket.class));
        assertDoesNotThrow(() -> PacketDirection.requireClientbound(MinecraftPacket.class));
        assertDoesNotThrow(() -> PacketDirection.requireServerbound(MinecraftPacket.class));
    }
}
