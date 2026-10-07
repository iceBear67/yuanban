package io.ib67.yuanban.loot.number;

import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;

import java.util.Set;

/**
 * 环境属性取值：原版 {@code providers.number.EnvironmentAttributeValue}，JSON 字段 {@code attribute}。
 * 原版字段类型是类型化的 {@code EnvironmentAttribute} 对象，本模块只承载它的命名空间 ID。
 */
public record EnvironmentAttributeValue(Key attribute) implements NumberProvider {

    /**
     * 对应原版 {@code EnvironmentAttributeValue#getReferencedContextParams}。
     * 原版按属性类型决定（位置类引用 {@code ORIGIN}，否则为空）；本模块只承载 ID，统一返回 {@code ORIGIN}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ORIGIN);
    }
}
