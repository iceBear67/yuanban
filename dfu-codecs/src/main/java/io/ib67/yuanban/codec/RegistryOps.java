package io.ib67.yuanban.codec;

import com.mojang.serialization.DynamicOps;

import java.util.Optional;

/**
 * 原版 {@code net.minecraft.resources.RegistryOps}：带有注册表信息的 {@link DynamicOps}。
 * {@link RegistryCodecs} 中的编解码器只有在 ops 是本类时才能访问注册表，否则报原版的
 * {@code Can't access registry ..} 等错误。
 */
public final class RegistryOps<T> extends DelegatingOps<T> {
    private final RegistryLookup lookup;

    private RegistryOps(DynamicOps<T> parent, RegistryLookup lookup) {
        super(parent);
        this.lookup = lookup;
    }

    public static <T> RegistryOps<T> create(DynamicOps<T> parent, RegistryLookup lookup) {
        return new RegistryOps<>(parent, lookup);
    }

    public RegistryLookup lookup() {
        return lookup;
    }

    public <U> RegistryOps<U> withParent(DynamicOps<U> parent) {
        @SuppressWarnings("unchecked")
        RegistryOps<U> self = (RegistryOps<U>) this;
        return parent == delegate ? self : new RegistryOps<>(parent, lookup);
    }

    public <E> Optional<RegistryGetter<E>> getter(RegistryId<E> registry) {
        return lookup.lookup(registry);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof RegistryOps<?> ops && delegate.equals(ops.delegate) && lookup.equals(ops.lookup);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode() * 31 + lookup.hashCode();
    }
}
