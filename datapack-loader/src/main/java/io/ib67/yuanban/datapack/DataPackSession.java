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

import org.jetbrains.annotations.Nullable;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个世界的数据包会话。持有当前批次，会话期间世界的特性保持不变。
 * 存在 {@code STARTUP} 订阅者的时候，初始批次在会话关闭之前一直保持打开：
 * 这些订阅者不会再执行 {@code apply}，它们提交的内容和保留的准备结果可能引用初始批次中的文件。
 */
public final class DataPackSession implements AutoCloseable {
    private final DataPackLoader loader;
    private final Map<SubscriberKey<?>, Object> retained;
    private final @Nullable Batch initialBatch;
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
        this.initialBatch = loader.graph().hasStartupSubscribers() ? batch : null;
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
     * 失败的时候会话保持原状；返回结果之后新的启用列表已经生效，宿主把 {@code selection().config()} 写回存档。
     * 关闭旧批次失败不影响重载结果，失败原因放在 {@link ReloadResult#closeFailure()} 中。
     *
     * @throws LoadFailedException   索引、视图或者准备任务失败，没有调用过任何 {@code apply}
     * @throws IllegalStateException 同一个会话有另一个重载正在执行
     */
    public ReloadResult reload(ReloadRequest request) {
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
            if (previous == initialBatch) {
                return new ReloadResult(newSelection, Optional.empty());
            }
            try {
                previous.close();
                return new ReloadResult(newSelection, Optional.empty());
            } catch (UncheckedIOException e) {
                return new ReloadResult(newSelection, Optional.of(e));
            }
        } finally {
            reloading.set(false);
        }
    }

    /**
     * 关闭当前批次，以及保持打开的初始批次。
     */
    @Override
    public void close() {
        var current = batch;
        if (initialBatch == null || initialBatch == current) {
            current.close();
            return;
        }
        try {
            current.close();
        } catch (UncheckedIOException e) {
            try {
                initialBatch.close();
            } catch (UncheckedIOException initialFailure) {
                e.addSuppressed(initialFailure);
            }
            throw e;
        }
        initialBatch.close();
    }
}
