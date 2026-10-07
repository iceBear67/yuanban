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
 * 原版 {@code set_lore}：写入 {@code lore} 组件。JSON 字段 {@code lore}（原版 {@code Component} 列表）、
 * {@code entity}（可选）、以及原版解码里默认 {@code replace_all} 的 {@code mode}（对应
 * {@code ListOperation}，本模块用 {@code mode} 承载，缺省为 {@code AppendOperation} 对应原版行为）。
 */
public record SetLoreFunction(List<LootCondition> conditions,
                              List<Component> lore,
                              ListOperation mode,
                              Optional<EntityTarget> resolutionContext) implements LootFunction {
    public SetLoreFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        lore = lore == null ? List.of() : List.copyOf(lore);
        mode = mode == null ? AppendOperation.INSTANCE : mode;
        resolutionContext = resolutionContext == null ? Optional.empty() : resolutionContext;
    }

    public SetLoreFunction(List<LootCondition> conditions, List<Component> lore) {
        this(conditions, lore, AppendOperation.INSTANCE, Optional.empty());
    }

    /**
     * 对应原版 {@code SetLoreFunction#getReferencedContextParams}：仅 {@code entity} 存在时引用对应参数。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return resolutionContext.<Set<ContextKey<?>>>map(entity -> Set.of(entity.contextParam())).orElse(Set.of());
    }
}
