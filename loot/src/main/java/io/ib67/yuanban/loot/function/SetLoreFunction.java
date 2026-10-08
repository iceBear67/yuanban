package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.EntityTarget;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code set_lore}：修改 {@code lore} 组件。JSON 字段 {@code lore}（文本组件列表，最多 256 行）、
 * 内联的列表操作（必填的 {@code mode} 以及各操作的字段，结果最多 256 行）、{@code entity}（可选，解析文本中
 * 选择器等内容使用的实体）。
 */
public record SetLoreFunction(List<LootCondition> conditions,
                              List<Component> lore,
                              ListOperation mode,
                              Optional<EntityTarget> resolutionContext) implements LootFunction {
    public SetLoreFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        lore = List.copyOf(lore);
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(resolutionContext, "resolutionContext");
    }

    /**
     * 对应原版 {@code SetLoreFunction#getReferencedContextParams}：仅 {@code entity} 存在时引用对应参数。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return resolutionContext.<Set<ContextKey<?>>>map(entity -> Set.of(entity.contextParam())).orElse(Set.of());
    }
}
