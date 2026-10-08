package io.ib67.yuanban.codec;

import net.kyori.adventure.key.Key;
import net.minestom.server.registry.Registry;
import net.minestom.server.registry.TagKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 解码时查询一个注册表的元素和标签，对应原版 {@code HolderGetter}。
 * 解码结果只保存 ID，求值时再到 Minestom 的注册表中取值，所以这里只回答元素是否存在以及标签的内容。
 *
 * @param <T> 注册表元素的类型
 */
public interface RegistryGetter<T> {
    /**
     * @return 注册表中有 ID 为 {@code id} 的元素时为 {@code true}
     */
    boolean containsElement(Key id);

    /**
     * 原版 {@code HolderGetter.get(TagKey)}。
     *
     * @return 标签中全部元素的 ID，按标签展开的顺序；标签不存在时为空
     */
    Optional<List<Key>> tag(Key tag);

    /**
     * @return 注册表中有 ID 为 {@code tag} 的标签时为 {@code true}
     */
    default boolean containsTag(Key tag) {
        return tag(tag).isPresent();
    }

    /**
     * Minestom 注册表及其标签。
     */
    static <T> RegistryGetter<T> of(Registry<T> registry) {
        return of(id -> registry.get(id) != null, tag -> {
            var resolved = registry.getTag(TagKey.of(tag));
            if (resolved == null) {
                return Optional.empty();
            }
            var keys = new ArrayList<Key>(resolved.size());
            resolved.forEach(key -> keys.add(key.key()));
            return Optional.of(List.copyOf(keys));
        });
    }

    /**
     * 按给出的元素集合和标签内容回答。
     */
    static <T> RegistryGetter<T> of(Collection<Key> elements, Map<Key, List<Key>> tags) {
        var elementSet = Set.copyOf(elements);
        var tagMap = Map.copyOf(tags);
        return of(elementSet::contains, tag -> Optional.ofNullable(tagMap.get(tag)));
    }

    /**
     * 按给出的判断和标签内容查询回答。
     */
    static <T> RegistryGetter<T> of(Predicate<Key> elements, Function<Key, Optional<List<Key>>> tags) {
        return new RegistryGetter<>() {
            @Override
            public boolean containsElement(Key id) {
                return elements.test(id);
            }

            @Override
            public Optional<List<Key>> tag(Key tag) {
                return tags.apply(tag);
            }
        };
    }

    /**
     * 任何元素和标签都存在，对应原版加载同一批注册表时的 {@code MappedRegistry.createRegistrationLookup()}：
     * 引用在解码时一律成立，标签的内容在此时还没有确定，作为空列表；全部文件解码之后再检查是否有未定义的引用。
     */
    static <T> RegistryGetter<T> registration() {
        return of(_ -> true, _ -> Optional.of(List.of()));
    }
}
