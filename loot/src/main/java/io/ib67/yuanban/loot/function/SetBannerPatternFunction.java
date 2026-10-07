package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code set_banner_pattern}：写入旗帜图案。JSON 字段 {@code patterns}
 * （{@link BannerLayer} 列表）、{@code append}（默认 {@code true}；为 {@code true} 时追加而非覆盖）。
 */
public record SetBannerPatternFunction(List<LootCondition> conditions,
                                       List<BannerLayer> patterns,
                                       boolean append) implements LootFunction {
    public SetBannerPatternFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        patterns = patterns == null ? List.of() : List.copyOf(patterns);
    }

    public SetBannerPatternFunction(List<LootCondition> conditions, List<BannerLayer> patterns) {
        this(conditions, patterns, true);
    }
}
