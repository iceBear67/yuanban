package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.EntityTarget;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code set_name}：写入 {@code custom_name} 或 {@code item_name} 组件。JSON 字段
 * {@code name}（原版 {@code Component}）、{@code entity}（用于 {@code @s} 之类的实体文本占位解析，
 * 原版字段名 {@code entity}，缺省时不做占位替换）、{@code target}（默认 {@code custom_name}）。
 */
public record SetNameFunction(List<LootCondition> conditions,
                              Optional<Component> name,
                              Optional<EntityTarget> resolutionContext,
                              SetNameTarget target) implements LootFunction {
    public SetNameFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        name = name == null ? Optional.empty() : name;
        resolutionContext = resolutionContext == null ? Optional.empty() : resolutionContext;
        target = target == null ? SetNameTarget.CUSTOM_NAME : target;
    }

    public SetNameFunction(List<LootCondition> conditions,
                           @Nullable Component name,
                           @Nullable EntityTarget resolutionContext,
                           @Nullable SetNameTarget target) {
        this(conditions, Optional.ofNullable(name), Optional.ofNullable(resolutionContext), target);
    }

    /**
     * 对应原版 {@code SetNameFunction#getReferencedContextParams}：仅 {@code entity} 存在时引用对应参数。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return resolutionContext.<Set<ContextKey<?>>>map(entity -> Set.of(entity.contextParam())).orElse(Set.of());
    }
}
