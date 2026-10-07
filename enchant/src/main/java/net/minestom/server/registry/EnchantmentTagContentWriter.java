package net.minestom.server.registry;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 替换附魔注册表标签的条目，保留标签对象本身。和 {@code item-tag} 对物品标签做的一样。
 */
// FIXME(Minestom M5): Minestom 没有修改静态/动态注册表标签内容的公开接口，这里借用同名包调用包内可见的
//  RegistryTagImpl.Backed.add/remove，要求 Minestom 位于 classpath 上，见 MINESTOM-BUG-REPORT.md
@ApiStatus.Internal
public final class EnchantmentTagContentWriter {
    private EnchantmentTagContentWriter() {
    }

    /**
     * 现有条目按迭代顺序和 {@code entries} 不同的时候，移除全部旧条目，再按顺序加入新条目。
     *
     * @param tag     {@link Registry#getOrCreateTag} 返回的标签
     * @param entries 新条目，不含重复
     */
    public static <T> void replace(RegistryTag<T> tag, List<RegistryKey<T>> entries) {
        var backed = (RegistryTagImpl.Backed<T>) tag;
        if (sameOrder(backed, entries)) {
            return;
        }
        var current = new ArrayList<RegistryKey<T>>(backed.size());
        backed.forEach(current::add);
        for (var key : current) {
            backed.remove(key);
        }
        for (var key : entries) {
            backed.add(key);
        }
    }

    private static <T> boolean sameOrder(RegistryTagImpl.Backed<T> tag, List<RegistryKey<T>> entries) {
        if (tag.size() != entries.size()) {
            return false;
        }
        var iterator = tag.iterator();
        for (var key : entries) {
            if (!iterator.next().key().equals(key.key())) {
                return false;
            }
        }
        return true;
    }
}
