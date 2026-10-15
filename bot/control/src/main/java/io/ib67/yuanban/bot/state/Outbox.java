package io.ib67.yuanban.bot.state;

import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 本轮调度中待发送的数据包，以及在这些数据包交给连接之后执行完毕的动作。
 * 协议状态机与各控制器只把数据包写入这里，由调度循环一次交给连接。
 */
public final class Outbox {
    private final List<MinecraftPacket> packets = new ArrayList<>();
    private final List<ActionHandle> completeOnWrite = new ArrayList<>();

    public void send(MinecraftPacket packet) {
        packets.add(packet);
    }

    /**
     * 当前及之前写入的数据包交给连接之后，动作执行完毕
     */
    public void completeWhenWritten(ActionHandle action) {
        completeOnWrite.add(action);
    }

    /**
     * 待发送的数据包（不取出）
     */
    public List<MinecraftPacket> packets() {
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

    public record Batch(List<MinecraftPacket> packets, List<ActionHandle> completeOnWrite) {
    }
}
