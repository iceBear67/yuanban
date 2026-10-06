package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.internal.Batch;
import io.ib67.yuanban.datapack.internal.LoadRun;
import io.ib67.yuanban.datapack.internal.PackSelector;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.selection.DataPackConfig;
import io.ib67.yuanban.datapack.selection.Selection;
import io.ib67.yuanban.datapack.selection.SelectionEditor;
import io.ib67.yuanban.datapack.selection.SelectionMode;
import io.ib67.yuanban.datapack.selection.SelectionRequest;
import io.ib67.yuanban.datapack.subscribe.LoadKind;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;

import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个世界的数据包会话。持有当前批次，会话期间世界的特性保持不变。
 */
public final class DataPackSession implements AutoCloseable {
    private final DataPackLoader loader;
    private final Map<SubscriberKey<?>, Object> retained;
    private final AtomicBoolean reloading = new AtomicBoolean();
    private volatile SortedMap<String, Pack> available;
    private volatile Selection selection;
    private volatile Batch batch;

    DataPackSession(DataPackLoader loader,
                    SortedMap<String, Pack> available,
                    Selection selection,
                    Batch batch,
                    Map<SubscriberKey<?>, Object> retained) {
        this.loader = loader;
        this.available = available;
        this.selection = selection;
        this.batch = batch;
        this.retained = Map.copyOf(retained);
    }

    /**
     * 当前启用的数据包、启用的特性、待持久化的启用列表和禁用列表、选择报告。
     */
    public Selection selection() {
        return selection;
    }

    /**
     * 最近一次发现的全部数据包，按 ID 排序。
     */
    public SortedMap<String, Pack> available() {
        return available;
    }

    public SelectionEditor editor() {
        var current = selection;
        return new SelectionEditor(available, current.packs(), current.enabledFeatures());
    }

    /**
     * 重新加载 {@code RELOADABLE} 订阅者。阻塞到全部 {@code apply} 执行完。
     * 失败的时候会话保持原状；成功之后宿主把新的 {@code selection().config()} 写回存档。
     *
     * @throws LoadFailedException   索引、视图或者准备任务失败，没有调用过任何 {@code apply}
     * @throws IllegalStateException 同一个会话有另一个重载正在执行
     */
    public Selection reload(ReloadRequest request) {
        Objects.requireNonNull(request, "request");
        if (!reloading.compareAndSet(false, true)) {
            throw new IllegalStateException("another reload is running");
        }
        try {
            var current = selection;
            SortedMap<String, Pack> newAvailable;
            Selection newSelection;
            switch (request) {
                case ReloadRequest.Rescan() -> {
                    newAvailable = loader.discover();
                    newSelection = PackSelector.select(newAvailable, new SelectionRequest(
                            new DataPackConfig(current.ids(), current.config().disabled()),
                            current.enabledFeatures(), SelectionMode.LOAD_WORLD, false), loader.settings());
                }
                case ReloadRequest.Select(var ids) -> {
                    newAvailable = available;
                    newSelection = PackSelector.explicit(newAvailable, ids, current.enabledFeatures());
                }
            }
            var participants = loader.graph().participants(LoadKind.RELOAD);
            var outcome = LoadRun.prepare(newSelection.packs(), participants, retained, newSelection.enabledFeatures(),
                    LoadKind.RELOAD, loader.executor());
            // apply 抛出的异常原样传播：一部分订阅者已经提交，框架不尝试恢复
            LoadRun.apply(participants, outcome.results());
            var previous = batch;
            batch = outcome.batch();
            available = newAvailable;
            selection = newSelection;
            previous.close();
            return newSelection;
        } finally {
            reloading.set(false);
        }
    }

    /**
     * 关闭当前批次。
     */
    @Override
    public void close() {
        batch.close();
    }
}
