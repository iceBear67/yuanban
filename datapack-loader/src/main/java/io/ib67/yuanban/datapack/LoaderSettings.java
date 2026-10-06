package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.pack.PackFormat;
import net.kyori.adventure.key.Key;

import java.util.Objects;
import java.util.Set;

/**
 * 当前游戏版本相关的设置。
 *
 * @param dataFormat    当前游戏的数据包格式，不能低于 26.2 的 107.1
 * @param featureFlags  当前游戏已知的全部特性开关
 * @param defaultPackId 启用列表为空或者安全模式下启用的数据包
 */
public record LoaderSettings(PackFormat dataFormat, Set<Key> featureFlags, String defaultPackId) {
    public static final PackFormat MINIMUM_FORMAT = PackFormat.of(107, 1);

    public static final LoaderSettings MINECRAFT_26_2 = new LoaderSettings(
            PackFormat.of(107, 1),
            Set.of(
                    Key.key(Key.MINECRAFT_NAMESPACE, "vanilla"),
                    Key.key(Key.MINECRAFT_NAMESPACE, "trade_rebalance"),
                    Key.key(Key.MINECRAFT_NAMESPACE, "redstone_experiments"),
                    Key.key(Key.MINECRAFT_NAMESPACE, "minecart_improvements")
            ),
            "vanilla"
    );

    public LoaderSettings {
        Objects.requireNonNull(dataFormat, "dataFormat");
        Objects.requireNonNull(defaultPackId, "defaultPackId");
        if (dataFormat.compareTo(MINIMUM_FORMAT) < 0) {
            throw new IllegalArgumentException("data pack format " + dataFormat + " is lower than the minimum supported format " + MINIMUM_FORMAT);
        }
        featureFlags = Set.copyOf(featureFlags);
    }
}
