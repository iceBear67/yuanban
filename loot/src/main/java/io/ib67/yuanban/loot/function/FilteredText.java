package io.ib67.yuanban.loot.function;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 带过滤的字符串：原版 {@code net.minecraft.server.network.Filterable<String>} 的最小承载。
 * 书页作者名字与书页内容均为该形态；原版 JSON 支持裸字符串或 {@code {raw, filtered}} 对象，
 * 本模块在 record 中保存拆开后的两个字段。
 */
public record FilteredText(String raw, Optional<String> filtered) {
    public FilteredText {
        filtered = filtered == null ? Optional.empty() : filtered;
    }

    public FilteredText(String raw) {
        this(raw, Optional.empty());
    }
}
