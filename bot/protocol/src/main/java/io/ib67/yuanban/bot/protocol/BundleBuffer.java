package io.ib67.yuanban.bot.protocol;

import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDelimiterPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 在两个 Bundle Delimiter 之间缓存数据包，收到结束分隔符时整批交出（wiki Bundle Delimiter）。
 */
public final class BundleBuffer {
    /**
     * 原版客户端不接受超过这个数量的 bundle
     */
    public static final int MAX_BUNDLE_SIZE = 4096;

    private @Nullable List<InboundPacket> open;

    /**
     * @return 可以处理的一批数据包：bundle 之外的数据包单独成批，bundle 在结束分隔符处整批交出；正在缓存时为 null
     * @throws IllegalStateException bundle 超过 {@link #MAX_BUNDLE_SIZE} 个数据包
     */
    public @Nullable List<InboundPacket> accept(InboundPacket packet) {
        if (packet.packet() instanceof ClientboundDelimiterPacket) {
            if (open == null) {
                open = new ArrayList<>();
                return null;
            }
            var bundle = open;
            open = null;
            return bundle;
        }
        if (open == null) {
            return List.of(packet);
        }
        if (open.size() >= MAX_BUNDLE_SIZE) {
            throw new IllegalStateException("bundle exceeds " + MAX_BUNDLE_SIZE + " packets");
        }
        open.add(packet);
        return null;
    }
}
