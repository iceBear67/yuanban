package io.ib67.yuanban.loot.predicate;

import io.ib67.yuanban.loot.predicate.component.ComponentPredicate;
import net.minestom.server.component.DataComponentMap;

import java.util.List;
import java.util.Objects;

/**
 * 组件匹配，对应原版 {@code net.minecraft.advancements.predicates.DataComponentMatchers}，内联在物品谓词和方块谓词中。
 *
 * @param exact   JSON 字段 {@code components}：每个组件的值必须与这里完全相同，缺省为空
 * @param partial JSON 字段 {@code predicates}：按组件谓词逐个检查，保持 JSON 中的顺序，缺省为空
 */
public record ComponentMatchers(DataComponentMap exact, List<ComponentPredicate> partial) {
    /**
     * 原版 {@code DataComponentMatchers.ANY}。
     */
    public static final ComponentMatchers ANY = new ComponentMatchers(DataComponentMap.EMPTY, List.of());

    public ComponentMatchers {
        Objects.requireNonNull(exact, "exact");
        partial = List.copyOf(partial);
    }

    /**
     * 原版 {@code DataComponentMatchers#isEmpty}。
     */
    public boolean isEmpty() {
        return exact.isEmpty() && partial.isEmpty();
    }
}
