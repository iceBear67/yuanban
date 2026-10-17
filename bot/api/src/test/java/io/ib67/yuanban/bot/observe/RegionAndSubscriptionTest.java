package io.ib67.yuanban.bot.observe;

import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBundlePacket;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionAndSubscriptionTest {
    @Test
    void regionContainsBothEnds() {
        var region = new Region(new Vec(0, 0, 0), new Vec(4, 2, 6));
        assertTrue(region.contains(new Vec(0, 0, 0)));
        assertTrue(region.contains(new Vec(4, 2, 6)));
        assertTrue(region.contains(new Vec(2, 1, 3)));
        // 超出任意一个轴都不包含
        assertFalse(region.contains(new Vec(-0.0001, 0, 0)));
        assertFalse(region.contains(new Vec(4.0001, 0, 0)));
        assertFalse(region.contains(new Vec(0, 2.0001, 0)));
        assertFalse(region.contains(new Vec(0, 0, 6.0001)));
    }

    @Test
    void regionNormalizesTheCorners() {
        // min 与 max 给反了也是同一个区域
        var region = new Region(new Vec(4, 2, 6), new Vec(0, 0, 0));
        assertEquals(new Vec(0, 0, 0), region.min());
        assertEquals(new Vec(4, 2, 6), region.max());
        assertTrue(region.contains(new Vec(1, 1, 1)));
    }

    @Test
    void aroundExtendsTheRadiusInEveryDirection() {
        var region = Region.around(new Vec(10, 64, 10), 3);
        assertEquals(new Vec(7, 61, 7), region.min());
        assertEquals(new Vec(13, 67, 13), region.max());
        assertTrue(region.contains(new Vec(10, 64, 10)));
        assertFalse(region.contains(new Vec(14, 64, 10)));
    }

    @Test
    void aSubscriptionRejectsPacketObservationTypes() {
        assertThrows(IllegalArgumentException.class, () -> Subscription.of(Observation.PacketReceived.class));
        assertThrows(IllegalArgumentException.class, () -> Subscription.of(Observation.PacketSent.class));
        // 模型层的观察记录照常接受
        assertEquals(Set.of(Observation.BlockChanged.class), Subscription.of(Observation.BlockChanged.class).observations());
    }

    @Test
    void packetClassesMustMatchTheDirection() {
        // 服务端发往客户端的数据包只能放在 received 中
        assertThrows(IllegalArgumentException.class, () -> Subscription.NONE.received(ServerboundKeepAlivePacket.class));
        // 客户端发往服务端的数据包只能放在 sent 中
        assertThrows(IllegalArgumentException.class, () -> Subscription.NONE.sent(ClientboundKeepAlivePacket.class));
        assertTrue(Subscription.NONE.received(ClientboundKeepAlivePacket.class).received()
                .contains(ClientboundKeepAlivePacket.class));
        assertTrue(Subscription.NONE.sent(ServerboundKeepAlivePacket.class).sent().contains(ServerboundKeepAlivePacket.class));
        // 只在一个协议阶段注册的数据包照常按方向判断
        assertTrue(Subscription.NONE.sent(ClientIntentionPacket.class).sent().contains(ClientIntentionPacket.class));
    }

    @Test
    void theBasePacketTypeIsAcceptedInBothDirections() {
        // MinecraftPacket.class 表示这一方向的全部数据包
        assertTrue(Subscription.NONE.received(MinecraftPacket.class).received().contains(MinecraftPacket.class));
        assertTrue(Subscription.NONE.sent(MinecraftPacket.class).sent().contains(MinecraftPacket.class));
    }

    @Test
    void unregisteredPacketClassesAreRejected() {
        // Bundle 由分隔符表示，MCProtocolLib 的编解码器中没有注册这个类
        assertThrows(IllegalArgumentException.class, () -> Subscription.NONE.received(ClientboundBundlePacket.class));
    }

    @Test
    void combiningSubscriptionsKeepsEveryEntry() {
        var a = Subscription.of(Observation.BlockChanged.class).received(ClientboundKeepAlivePacket.class);
        var b = Subscription.of(Observation.ChatReceived.class).sent(ServerboundKeepAlivePacket.class);
        var combined = a.and(b);
        assertEquals(Set.of(Observation.BlockChanged.class, Observation.ChatReceived.class), combined.observations());
        assertEquals(Set.of(ClientboundKeepAlivePacket.class), combined.received());
        assertEquals(Set.of(ServerboundKeepAlivePacket.class), combined.sent());
        // 原来的订阅不变
        assertEquals(Set.of(Observation.BlockChanged.class), a.observations());
        assertTrue(a.sent().isEmpty());
    }

    @Test
    void checkingASingleDirectionLeavesTheOtherOneEmpty() {
        var received = Subscription.NONE.received(ClientboundKeepAlivePacket.class);
        assertTrue(received.sent().isEmpty());
        assertTrue(received.observations().isEmpty());
        var sent = received.sent(ServerboundKeepAlivePacket.class);
        assertEquals(received.received(), sent.received());
        assertTrue(Subscription.NONE.observations().isEmpty());
        assertTrue(Subscription.NONE.received().isEmpty());
        assertTrue(Subscription.NONE.sent().isEmpty());
    }
}
