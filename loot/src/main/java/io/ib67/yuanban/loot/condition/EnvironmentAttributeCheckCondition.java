package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.EnvironmentAttributeValue;
import net.minestom.server.world.attribute.EnvironmentAttribute;

import java.util.Objects;
import java.util.Set;

/**
 * 原版 {@code environment_attribute_check}：环境属性在当前位置的取值等于 {@code value}。JSON 字段
 * {@code attribute}（环境属性 ID）、{@code value}（按该属性的取值编解码器解码）。
 *
 * @param <V> 环境属性的取值类型
 */
public record EnvironmentAttributeCheckCondition<V>(EnvironmentAttribute<V> attribute, V value) implements LootCondition {
    public EnvironmentAttributeCheckCondition {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(value, "value");
    }

    /**
     * 对应原版 {@code EnvironmentAttributeCheck#getReferencedContextParams}：取值与位置有关的属性引用 {@code ORIGIN}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return EnvironmentAttributeValue.isPositional(attribute) ? Set.of(ContextKey.ORIGIN) : Set.of();
    }
}
