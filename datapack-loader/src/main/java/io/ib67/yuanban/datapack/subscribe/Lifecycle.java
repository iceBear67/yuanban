package io.ib67.yuanban.datapack.subscribe;

/**
 * 订阅者的生命周期。
 */
public enum Lifecycle {
    /**
     * 只在会话启动的时候执行一次，对应原版只在打开世界的时候加载的内容，例如世界生成和各个动态注册表。
     */
    STARTUP,
    /**
     * 启动和每次重载都执行，对应原版 {@code /reload} 会重新加载的内容，例如标签、配方、战利品表、函数。
     */
    RELOADABLE
}
