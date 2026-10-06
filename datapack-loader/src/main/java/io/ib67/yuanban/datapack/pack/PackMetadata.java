package io.ib67.yuanban.datapack.pack;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * {@code pack.mcmeta} 中框架读取的内容。
 *
 * @param description 文本组件，原样保存
 * @param formats     {@code min_format} 和 {@code max_format} 组成的范围
 * @param features    数据包所需的特性开关
 * @param filter      {@code filter} 段落，没有的时候是 {@link ResourceFilter#NONE}
 * @param overlays    {@code overlays.entries}，保持文件中的顺序
 */
public record PackMetadata(JsonElement description,
                           FormatRange formats,
                           Set<Key> features,
                           ResourceFilter filter,
                           List<OverlayEntry> overlays) {
    public PackMetadata {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(formats, "formats");
        Objects.requireNonNull(filter, "filter");
        features = Set.copyOf(features);
        overlays = List.copyOf(overlays);
    }
}
