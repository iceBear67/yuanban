package io.ib67.yuanban.loot.number;

import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;
import net.minestom.server.world.attribute.EnvironmentAttribute;

import java.util.Objects;
import java.util.Set;

/**
 * 环境属性取值：原版 {@code providers.number.EnvironmentAttributeValue}，JSON 字段 {@code attribute}
 * （环境属性 ID，按环境属性注册表解码，只接受能转换成数值的属性）。
 */
public record EnvironmentAttributeValue(EnvironmentAttribute<?> attribute) implements NumberProvider {
    /**
     * 原版 {@code EnvironmentAttributes} 中以 {@code notPositional()} 注册的属性，取值与位置无关。
     */
    private static final Set<Key> NOT_POSITIONAL = Set.of(
            Key.key("gameplay/sky_light_level"),
            Key.key("gameplay/fast_lava"));

    public EnvironmentAttributeValue {
        Objects.requireNonNull(attribute, "attribute");
    }

    /**
     * 原版 {@code EnvironmentAttribute#isPositional}。Minestom 的环境属性没有这一项，按原版注册时的设置给出。
     */
    public static boolean isPositional(EnvironmentAttribute<?> attribute) {
        return !NOT_POSITIONAL.contains(attribute.key());
    }

    /**
     * 对应原版 {@code EnvironmentAttributeValue#getReferencedContextParams}：取值与位置有关的属性引用 {@code ORIGIN}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return isPositional(attribute) ? Set.of(ContextKey.ORIGIN) : Set.of();
    }
}
