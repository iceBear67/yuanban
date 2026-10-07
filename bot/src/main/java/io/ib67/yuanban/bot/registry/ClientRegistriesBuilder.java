package io.ib67.yuanban.bot.registry;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.network.packet.server.configuration.RegistryDataPacket;
import net.minestom.server.registry.RegistryTranscoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 累积配置阶段的 Registry Data，在收到 Finish Configuration 时构建 {@link ClientRegistries}。在入站线程上使用。
 */
public final class ClientRegistriesBuilder {
    private final Map<Key, List<RegistryDataPacket.Entry>> pending = new LinkedHashMap<>();
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
    public boolean accept(RegistryDataPacket packet) {
        var cleared = !receivedInPhase;
        if (cleared) {
            pending.clear();
            receivedInPhase = true;
        }
        pending.computeIfAbsent(Key.key(packet.registryId()), _ -> new ArrayList<>()).addAll(packet.entries());
        return cleared;
    }

    /**
     * 当前累积的同步注册表的名称
     */
    public Set<String> registryNames() {
        return pending.keySet().stream().map(Key::asString).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 建立全部同步注册表：先创建标签，再为每个注册表按服务端顺序登记全部条目的名称，最后按 {@link ClientRegistries#SYNCED}
     * 的顺序逐个注册表、按服务端顺序解码条目。名称都登记之后才解码，所以条目可以引用同一注册表中排在后面的条目，
     * 也可以引用排在后面的注册表的条目（原版 {@code RegistryDataLoader} 先为全部注册表建立查找上下文）。
     * 条目带 NBT 时用该类型的编解码器解码，不带 NBT 时按名称取 {@link VanillaReference} 中的值。
     *
     * @throws IllegalStateException 条目解码失败（包括引用了不存在的条目），或省略了 NBT 的条目在原版注册表中不存在
     */
    public ClientRegistries build(TagTable tags) {
        var vanilla = VanillaReference.get();
        var known = ClientRegistries.SYNCED.stream().map(spec -> spec.key().key()).collect(Collectors.toSet());
        var unsupported = new HashMap<Key, List<Key>>();
        pending.forEach((registry, entries) -> {
            if (!known.contains(registry)) {
                unsupported.put(registry, entries.stream().map(entry -> Key.key(entry.id())).toList());
            }
        });
        var registries = new ClientRegistries(vanilla, unsupported);
        registries.createTags(tags);
        var transcoder = new RegistryTranscoder<>(Transcoder.NBT, registries);
        for (var spec : ClientRegistries.SYNCED) {
            var writer = registries.writer(spec.key());
            for (var entry : pending.getOrDefault(spec.key().key(), List.of())) {
                writer.declare(Key.key(entry.id()));
            }
        }
        for (var spec : ClientRegistries.SYNCED) {
            fill(spec, registries, transcoder);
        }
        return registries;
    }

    private <T> void fill(ClientRegistries.Spec<T> spec, ClientRegistries registries,
                          RegistryTranscoder<BinaryTag> transcoder) {
        var writer = registries.writer(spec.key());
        var reference = spec.select().apply(VanillaReference.get());
        for (var entry : pending.getOrDefault(spec.key().key(), List.of())) {
            var key = Key.key(entry.id());
            var data = entry.data();
            T value;
            if (data == null) {
                value = reference.get(key);
                if (value == null) {
                    throw new IllegalStateException("registry " + spec.key().key() + " entry " + key
                            + " has no data and does not exist in the vanilla registry");
                }
            } else {
                var result = spec.codec().decode(transcoder, data);
                if (!(result instanceof Result.Ok<T>(T decoded))) {
                    throw new IllegalStateException("failed to decode registry " + spec.key().key() + " entry " + key
                            + ": " + result);
                }
                value = decoded;
            }
            writer.define(key, value);
        }
    }
}
