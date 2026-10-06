package io.ib67.yuanban.datapack.selection;

/**
 * 选择算法的模式，对应原版 {@code configurePackRepository} 的 {@code initMode}。
 */
public enum SelectionMode {
    /**
     * 新建世界：允许启用全部已知特性，世界的特性由启用的数据包决定。
     */
    CREATE_WORLD,
    /**
     * 打开已有世界：特性来自存档。
     */
    LOAD_WORLD
}
