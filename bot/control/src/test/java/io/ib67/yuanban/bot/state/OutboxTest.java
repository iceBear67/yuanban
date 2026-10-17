package io.ib67.yuanban.bot.state;

import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxTest {
    /**
     * 记录动作的结束，其余方法抛出异常：Outbox 只负责把句柄交给调用方
     */
    private static final class Action implements ActionHandle {
        @Override
        public boolean isDone() {
            throw new AssertionError("Outbox must not query the state of an action");
        }

        @Override
        public void canceller(Consumer<String> canceller) {
            throw new AssertionError("Outbox must not set a canceller");
        }

        @Override
        public void requestCancel(String reason) {
            throw new AssertionError("Outbox must not cancel an action");
        }

        @Override
        public void succeed() {
            throw new AssertionError("Outbox must not end an action");
        }

        @Override
        public void cancelled(String reason) {
            throw new AssertionError("Outbox must not cancel an action");
        }

        @Override
        public void terminate(RuntimeException exception) {
            throw new AssertionError("Outbox must not terminate an action");
        }
    }

    @Test
    void packetsStayUntilTheBatchIsDrained() {
        var outbox = new Outbox();
        assertTrue(outbox.isEmpty());
        MinecraftPacket first = new ClientboundKeepAlivePacket(1);
        MinecraftPacket second = new ServerboundKeepAlivePacket(2);
        outbox.send(first);
        outbox.send(second);
        assertFalse(outbox.isEmpty());
        // packets() 不取出，交给连接之前可以多次读取
        assertEquals(List.of(first, second), outbox.packets());
        assertEquals(List.of(first, second), outbox.packets());

        var batch = outbox.drain();
        assertEquals(List.of(first, second), batch.packets());
        assertTrue(batch.completeOnWrite().isEmpty());
        assertTrue(outbox.isEmpty());
        assertEquals(List.of(), outbox.packets());
    }

    @Test
    void actionsWaitForTheBatchOfThePacketsWrittenBeforeThem() {
        var outbox = new Outbox();
        var first = new Action();
        var second = new Action();
        outbox.send(new ClientboundKeepAlivePacket(1));
        outbox.completeWhenWritten(first);
        // 动作写入之后才加入的数据包属于同一批
        outbox.send(new ClientboundKeepAlivePacket(2));
        outbox.completeWhenWritten(second);

        var batch = outbox.drain();
        assertEquals(List.of(new ClientboundKeepAlivePacket(1), new ClientboundKeepAlivePacket(2)), batch.packets());
        assertEquals(List.of(first, second), batch.completeOnWrite());
        assertTrue(outbox.isEmpty());
    }

    @Test
    void anActionWithoutPacketsIsStillDrained() {
        var outbox = new Outbox();
        var action = new Action();
        outbox.completeWhenWritten(action);
        assertFalse(outbox.isEmpty());
        var batch = outbox.drain();
        assertEquals(List.of(), batch.packets());
        assertEquals(List.of(action), batch.completeOnWrite());
        assertTrue(outbox.isEmpty());
    }

    @Test
    void drainedListsAreCopies() {
        var outbox = new Outbox();
        outbox.send(new ClientboundKeepAlivePacket(1));
        outbox.completeWhenWritten(new Action());
        var packets = outbox.packets();
        var batch = outbox.drain();
        // 交出的列表不随之后的写入变化
        assertEquals(1, packets.size());
        assertEquals(1, batch.packets().size());
        outbox.send(new ClientboundKeepAlivePacket(2));
        assertEquals(1, packets.size());
        assertEquals(1, batch.packets().size());
    }
}
