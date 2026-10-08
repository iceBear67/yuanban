package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.codec.HolderSet;
import net.kyori.adventure.key.Key;
import net.minestom.server.registry.Registry;
import net.minestom.server.registry.RegistryKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 求值时按 Minestom 的注册表解析 {@link HolderSet}：标签在求值时才查找，同一次加载中更新的标签在提交之后生效。
 */
final class Holders {
    private Holders() {
    }

    /**
     * 原版 {@code HolderSet#contains}。
     */
    static <T> boolean contains(HolderSet<T> set, Key id, Registry<T> registry) {
        return set.contains(id, registry);
    }

    /**
     * Minestom 没有注册表（因此没有标签）的元素类型：列表形式按 ID 比较，标签形式无法解析。
     *
     * @param registryName 元素所在的原版注册表名，用于错误信息
     */
    static <T> boolean containsWithoutTags(HolderSet<T> set, Key id, String registryName) {
        return switch (set) {
            case HolderSet.Direct<T> direct -> direct.keys().stream().anyMatch(key -> key.key().equals(id));
            // TODO(minestom): Minestom 没有这个原版注册表，也就没有它的标签
            case HolderSet.Named<T> named -> throw LootEvaluation.unsupported("tag #" + named.tag().key().asString()
                    + " of the registry " + registryName);
        };
    }

    /**
     * 集合的元素，按原版 {@code HolderSet#stream} 的顺序：列表按书写顺序，标签按标签内容的顺序；找不到标签时为空。
     */
    static <T> List<RegistryKey<T>> members(HolderSet<T> set, Registry<T> registry) {
        return switch (set) {
            case HolderSet.Direct<T> direct -> direct.keys();
            case HolderSet.Named<T> named -> {
                var tag = registry.getTag(named.tag());
                var result = new ArrayList<RegistryKey<T>>();
                if (tag != null) {
                    tag.forEach(result::add);
                }
                yield result;
            }
        };
    }

    /**
     * 原版 {@code HolderSet#getRandomElement}：{@code Util.getRandomSafe}，集合为空时为空。
     */
    static <T> Optional<RegistryKey<T>> randomElement(HolderSet<T> set, Registry<T> registry, Random random) {
        var members = members(set, registry);
        return members.isEmpty() ? Optional.empty() : Optional.of(members.get(random.nextInt(members.size())));
    }

    /**
     * 原版 {@code Registry#getRandom}：按注册顺序的全部元素中随机取一个，注册表为空时为空。
     */
    static <T> Optional<RegistryKey<T>> randomElement(Registry<T> registry, Random random) {
        int size = registry.size();
        return size == 0 ? Optional.empty() : Optional.of(registry.getKey(random.nextInt(size)));
    }
}
