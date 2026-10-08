package io.ib67.yuanban.codec;

import net.kyori.adventure.key.Key;
import net.minestom.server.registry.Registry;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.registry.TagKey;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code net.minecraft.core.HolderSet}：注册表中一组元素，写作标签（{@code #id}）或者元素列表。
 * <p>
 * 解码时由 {@link RegistryCodecs#homogeneousList} 检查元素和标签存在；这里只保存 ID，
 * 求值时再按名字到 Minestom 的注册表中解析，使同一次加载中新增或删除的标签在提交之后才生效。
 *
 * @param <T> 注册表元素的类型
 */
public sealed interface HolderSet<T> permits HolderSet.Direct, HolderSet.Named {
    static <T> Direct<T> direct(List<? extends RegistryKey<T>> keys) {
        return new Direct<>(List.copyOf(keys));
    }

    static <T> Named<T> named(Key tag) {
        return new Named<>(TagKey.of(tag));
    }

    /**
     * 原版 {@code HolderSet.contains}。{@link Named} 在 {@code registry} 中找不到标签时作为空集合，
     * 与原版未绑定的 {@code HolderSet.Named} 相同。
     */
    boolean contains(Key id, Registry<T> registry);

    /**
     * 元素列表，保持顺序和重复项，与原版 {@code HolderSet.direct} 相同。
     */
    record Direct<T>(List<RegistryKey<T>> keys) implements HolderSet<T> {
        public Direct {
            keys = List.copyOf(keys);
        }

        @Override
        public boolean contains(Key id, Registry<T> registry) {
            for (var key : keys) {
                if (key.key().equals(id)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 转换成 Minestom 的直接标签，保持顺序。
         */
        public RegistryTag<T> toRegistryTag() {
            return RegistryTag.direct(keys.stream().map(key -> RegistryKey.<T>of(key.key())).toList());
        }
    }

    /**
     * 标签引用。
     */
    record Named<T>(TagKey<T> tag) implements HolderSet<T> {
        public Named {
            Objects.requireNonNull(tag, "tag");
        }

        @Override
        public boolean contains(Key id, Registry<T> registry) {
            var resolved = registry.getTag(tag);
            return resolved != null && resolved.contains(RegistryKey.of(id));
        }
    }
}
