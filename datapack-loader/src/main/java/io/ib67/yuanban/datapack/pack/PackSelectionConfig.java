package io.ib67.yuanban.datapack.pack;

import java.util.Objects;

/**
 * 数据包在启用列表中的位置规则，对应原版同名类型。
 *
 * @param required        是否总是出现在启用列表中
 * @param defaultPosition 插入的时候默认所在的一端
 * @param fixedPosition   插入其他数据包的时候是否保持在默认位置那一端
 */
public record PackSelectionConfig(boolean required, PackPosition defaultPosition, boolean fixedPosition) {
    public PackSelectionConfig {
        Objects.requireNonNull(defaultPosition, "defaultPosition");
    }
}
