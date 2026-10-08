package io.ib67.yuanban.enchant.internal;

import io.ib67.yuanban.codec.RegistryId;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 原版 {@code MappedRegistry.freeze} 对同一批加载中未绑定引用的检查。
 * <p>
 * 原版在同一批注册表中加载附魔、附魔标签和附魔提供器，解码时附魔注册表通过注册查找回答：任何附魔 ID 和附魔标签
 * 都可以被引用。冻结注册表时，被引用而没有注册的附魔报 {@code Unbound values}，没有这样的附魔时，
 * 被引用而没有定义的标签报 {@code Unbound tags}。
 */
@ApiStatus.Internal
public final class UnboundReferences {
    /**
     * 原版 {@code Identifier.compareTo}：先比较路径，再比较命名空间。
     */
    private static final Comparator<Key> IDENTIFIER_ORDER = Comparator.comparing(Key::value).thenComparing(Key::namespace);

    private UnboundReferences() {
    }

    /**
     * @param unboundValues 被引用而没有注册的元素 ID，可以重复
     * @param unboundTags   被引用而没有定义的标签 ID，可以重复
     * @return 原版冻结注册表时的错误文本；两个集合都为空时没有错误
     */
    public static Optional<String> message(RegistryId<?> registry, Collection<Key> unboundValues, Collection<Key> unboundTags) {
        if (!unboundValues.isEmpty()) {
            return Optional.of("Unbound values in registry " + registry + ": " + format(unboundValues));
        }
        if (!unboundTags.isEmpty()) {
            return Optional.of("Unbound tags in registry " + registry + ": " + format(unboundTags));
        }
        return Optional.empty();
    }

    private static String format(Collection<Key> ids) {
        List<Key> sorted = ids.stream().distinct().sorted(IDENTIFIER_ORDER).toList();
        return sorted.stream().map(Key::asString).collect(Collectors.joining(", ", "[", "]"));
    }
}
