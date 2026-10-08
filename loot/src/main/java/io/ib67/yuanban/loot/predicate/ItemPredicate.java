package io.ib67.yuanban.loot.predicate;

import io.ib67.yuanban.codec.HolderSet;
import net.minestom.server.item.Material;
import net.minestom.server.utils.Range;

import java.util.Objects;
import java.util.Optional;

/**
 * 物品谓词，对应原版 {@code net.minecraft.advancements.predicates.ItemPredicate}。
 * JSON 是对象：{@code items}（物品集合，可选）、{@code count}（数量范围，缺省不限）、
 * 以及内联的 {@code components}、{@code predicates}（见 {@link ComponentMatchers}）。
 * <p>
 * 原版按 {@code items}、{@code count}、组件的顺序检查。物品集合求值时才按名字解析，
 * 同一次加载中新增或删除的物品标签在提交之后生效。
 *
 * @param count 两端为 {@code null} 表示不限，与原版 {@code MinMaxBounds.Ints.ANY} 相同
 */
public record ItemPredicate(Optional<HolderSet<Material>> items, Range.Int count, ComponentMatchers components) {
    public ItemPredicate {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(count, "count");
        Objects.requireNonNull(components, "components");
    }
}
