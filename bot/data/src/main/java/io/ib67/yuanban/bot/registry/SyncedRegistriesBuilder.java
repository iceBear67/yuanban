package io.ib67.yuanban.bot.registry;

import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 累积配置阶段的 Registry Data，在收到 Finish Configuration 时构建 {@link SyncedRegistries}。
 * 由解码上下文使用（TCP 连接在 Netty 事件循环上）。
 */
public final class SyncedRegistriesBuilder {
    private final Map<Key, List<RegistryEntry>> pending = new LinkedHashMap<>();
    private boolean receivedInPhase;

    /**
     * 进入一个配置阶段
     */
    public void startConfiguration() {
        receivedInPhase = false;
    }

    /**
     * 累积一个 Registry Data。同一个配置阶段中的第一个 Registry Data 清空之前的同步注册表
     *
     * @return 是否清空了之前的同步注册表；这时调用方需要同时清空它们的标签
     */
    public boolean accept(ClientboundRegistryDataPacket packet) {
        var cleared = !receivedInPhase;
        if (cleared) {
            pending.clear();
            receivedInPhase = true;
        }
        pending.computeIfAbsent(packet.getRegistry(), _ -> new ArrayList<>()).addAll(packet.getEntries());
        return cleared;
    }

    /**
     * 当前累积的同步注册表的名称
     */
    public Set<Key> registryNames() {
        return Set.copyOf(pending.keySet());
    }

    /**
     * 按服务端顺序记录全部条目的名称。条目不带数据时（服务端因为 Known Packs 省略），它必须存在于原版的同一注册表中；
     * 维度类型的最低 y 与高度取自条目的数据，省略时取自原版
     *
     * @throws IllegalStateException 省略了数据的条目在原版注册表中不存在
     */
    public SyncedRegistries build() {
        var entries = new HashMap<Key, List<Key>>();
        var dimensionTypes = new ArrayList<DimensionInfo>();
        pending.forEach((registry, list) -> {
            var names = new ArrayList<Key>(list.size());
            for (var entry : list) {
                var data = data(registry, entry);
                if (registry.equals(SyncedRegistries.DIMENSION_TYPE)) {
                    dimensionTypes.add(new DimensionInfo(entry.getId(), data.getInt("min_y"), data.getInt("height")));
                }
                names.add(entry.getId());
            }
            entries.put(registry, List.copyOf(names));
        });
        return new SyncedRegistries(entries, dimensionTypes);
    }

    private static NbtMap data(Key registry, RegistryEntry entry) {
        if (entry.getData() != null) {
            return entry.getData();
        }
        var vanilla = VanillaNetworkCodec.element(registry, entry.getId());
        if (vanilla == null) {
            throw new IllegalStateException("registry " + registry + " entry " + entry.getId()
                    + " has no data and does not exist in the vanilla registry");
        }
        return vanilla;
    }
}
