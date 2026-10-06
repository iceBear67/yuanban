package io.ib67.yuanban.datapack.provider;

import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.pack.Pack;

import java.util.function.Consumer;

/**
 * 数据包提供者。启动和 {@code /reload} 的时候被调用，把发现的数据包交给 {@code output}。
 * 发现阶段只读取 {@code pack.mcmeta}，不保持数据包处于打开状态。
 */
@FunctionalInterface
public interface PackProvider {
    void discover(LoaderSettings settings, Consumer<Pack> output);
}
