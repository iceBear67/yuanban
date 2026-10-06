package io.ib67.yuanban.datapack.pack;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * {@code filter.block} 中的一条规则。缺少的一项匹配任何字符串。
 * 和原版 {@code IdentifierPattern} 相同，正则只要在字符串中找到匹配片段就算匹配。
 */
public record FilterRule(@Nullable Pattern namespace, @Nullable Pattern path) {
    public boolean matchesNamespace(String value) {
        return namespace == null || namespace.matcher(value).find();
    }

    public boolean matchesPath(String value) {
        return path == null || path.matcher(value).find();
    }
}
