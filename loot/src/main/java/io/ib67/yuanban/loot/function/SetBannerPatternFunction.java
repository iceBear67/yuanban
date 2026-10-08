package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.item.component.BannerPatterns;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code set_banner_pattern}：写入旗帜图案。JSON 字段 {@code patterns}（图案层列表，每层的
 * {@code pattern} 是旗帜图案注册表中的 ID 或内联的图案定义，{@code color} 是染料颜色名）、
 * {@code append}（必填；为 {@code true} 时接在物品现有的图案之后，否则替换）。
 */
public record SetBannerPatternFunction(List<LootCondition> conditions,
                                       BannerPatterns patterns,
                                       boolean append) implements LootFunction {
    public SetBannerPatternFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(patterns, "patterns");
    }
}
