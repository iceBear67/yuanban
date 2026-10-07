package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.scheduler.ActionImpl;
import net.minestom.server.network.packet.client.ClientPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 本轮调度中待发送的数据包，以及在这些数据包交给连接之后执行完毕的动作。
 * 协议状态机与各控制器只把数据包写入这里，由调度循环一次交给连接。
 */
public final class Outbox {
    private final List<ClientPacket> packets = new ArrayList<>();
    private final List<ActionImpl> completeOnWrite = new ArrayList<>();

    public void send(ClientPacket packet) {
        packets.add(packet);
    }

    /**
     * 当前及之前写入的数据包交给连接之后，动作执行完毕
     */
    public void completeWhenWritten(ActionImpl action) {
        completeOnWrite.add(action);
    }

    /**
     * 待发送的数据包（不取出）
     */
    public List<ClientPacket> packets() {
        return List.copyOf(packets);
    }

    public boolean isEmpty() {
        return packets.isEmpty() && completeOnWrite.isEmpty();
    }

    /**
     * 取出全部待发送的数据包与动作
     */
    public Batch drain() {
        var batch = new Batch(List.copyOf(packets), List.copyOf(completeOnWrite));
        packets.clear();
        completeOnWrite.clear();
        return batch;
    }

    public record Batch(List<ClientPacket> packets, List<ActionImpl> completeOnWrite) {
    }
}
