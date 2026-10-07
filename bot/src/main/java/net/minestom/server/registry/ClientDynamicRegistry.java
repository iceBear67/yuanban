package net.minestom.server.registry;

import net.kyori.adventure.key.Key;
import net.minestom.server.gamedata.DataPack;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端按服务端编号逐个加入条目的动态注册表：先登记全部条目的名称（{@link #declare}），再按同样的顺序给出条目的值（{@link #define}）。
 * 登记之后名称立即可以解析（{@code getKey}），所以解码条目时可以引用排在后面的条目（原版 {@code RegistryDataLoader} 允许任意顺序的引用）。
 */
// FIXME(Minestom M8): DynamicRegistry 是 sealed 接口，唯一的实现 DynamicRegistryImpl 只能通过 register 加入条目，
//  而同一 JVM 中的服务端启动之后 register 抛出 UnsupportedOperationException；另外 DynamicRegistry.fromMap
//  在全部条目解码之后才创建注册表，引用本注册表标签的条目（附魔的 exclusive_set）无法解码；引用条目的编解码器解码时
//  按名称查找，getKey(Key) 只认得已经有值的名称，排在后面的条目无法被引用。
//  这里借用同名包调用 DynamicRegistryImpl 包内可见的构造方法，并直接写入传给它的集合（先登记名称，再给出值），
//  要求 Minestom 位于 classpath 上，见 MINESTOM-BUG-REPORT.md
@ApiStatus.Internal
public final class ClientDynamicRegistry<T> {
    private final List<T> idToValue = new ArrayList<>();
    private final Map<RegistryKey<T>, Integer> keyToId = new HashMap<>();
    private final List<RegistryKey<T>> idToKey = new ArrayList<>();
    private final Map<Key, T> keyToValue = new HashMap<>();
    private final Map<T, RegistryKey<T>> valueToKey = new HashMap<>();
    private final List<DataPack> packById = new ArrayList<>();
    private final DynamicRegistry<T> registry;

    public ClientDynamicRegistry(Key key) {
        this.registry = new DynamicRegistryImpl<>(key, null, idToValue, keyToId, idToKey, keyToValue, valueToKey,
                packById, new ConcurrentHashMap<>());
    }

    /**
     * 正在构建的注册表；加入的条目立即可以通过它读取
     */
    public DynamicRegistry<T> registry() {
        return registry;
    }

    /**
     * 登记一个条目的名称，编号为登记之前的条目数；之后 {@code getKey} 可以解析这个名称，{@code get} 在给出值之前返回 null
     *
     * @throws IllegalArgumentException 名称重复
     */
    public void declare(Key key) {
        var registryKey = new RegistryKeyImpl<T>(key);
        if (keyToId.containsKey(registryKey)) {
            throw new IllegalArgumentException("duplicate entry " + key + " in registry " + registry.key());
        }
        keyToId.put(registryKey, idToKey.size());
        idToKey.add(registryKey);
        keyToValue.put(key, null);
    }

    /**
     * 按登记的顺序给出下一个条目的值
     *
     * @throws IllegalStateException 名称不是下一个还没有值的条目
     */
    public void define(Key key, T value) {
        var registryKey = new RegistryKeyImpl<T>(key);
        var id = idToValue.size();
        if (id >= idToKey.size() || !idToKey.get(id).equals(registryKey)) {
            throw new IllegalStateException("entry " + key + " is not the next declared entry of registry " + registry.key());
        }
        idToValue.add(value);
        keyToValue.put(key, value);
        valueToKey.put(value, registryKey);
        packById.add(DataPack.MINESTOM_UNNAMED);
    }
}
