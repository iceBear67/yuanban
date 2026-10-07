package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 原版 {@code environment_attribute_check}：判定环境属性的取值。JSON 字段 {@code attribute}
 * （原版 {@code EnvironmentAttribute} 的命名空间 ID）、{@code value}（按属性的 JSON 取值原样保留；
 * 本模块取值类型按原版按属性类型的序列化字符串承载）。
 */
public record EnvironmentAttributeCheckCondition(Key attribute, @Nullable String value) implements LootCondition {

    /**
     * 对应原版 {@code EnvironmentAttributeCheck#getReferencedContextParams}。
     * 原版按属性类型决定是否引用 {@code ORIGIN}；本模块只承载 ID，统一返回 {@code ORIGIN}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ORIGIN);
    }
}
