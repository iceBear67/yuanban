package io.ib67.yuanban.bot.registry;

import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 原版 26.2 全部同步注册表条目的数据，取自 MCProtocolLib 的 {@link MinecraftProtocol#loadNetworkCodec()}。
 * 服务端因为 Known Packs 省略了条目的数据时，本库从这里读取（wiki Registry Data：客户端与服务端都知道的包中的条目可以省略数据）。
 */
final class VanillaNetworkCodec {
    private static final Map<Key, Map<Key, NbtMap>> ELEMENTS = load();

    private VanillaNetworkCodec() {
    }

    /**
     * @return 原版没有这个条目时为 null
     */
    static @Nullable NbtMap element(Key registry, Key entry) {
        return ELEMENTS.getOrDefault(registry, Map.of()).get(entry);
    }

    private static Map<Key, Map<Key, NbtMap>> load() {
        var result = new HashMap<Key, Map<Key, NbtMap>>();
        var codec = MinecraftProtocol.loadNetworkCodec();
        for (var value : codec.values()) {
            var registry = (NbtMap) value;
            var elements = new HashMap<Key, NbtMap>();
            for (var entry : registry.getList("value", NbtType.COMPOUND)) {
                elements.put(Key.key(entry.getString("name")), entry.getCompound("element"));
            }
            result.put(Key.key(registry.getString("type")), Map.copyOf(elements));
        }
        return Map.copyOf(result);
    }
}
