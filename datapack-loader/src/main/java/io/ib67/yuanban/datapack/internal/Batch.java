package io.ib67.yuanban.datapack.internal;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次加载打开的全部数据包。重载成功之后，新批次替换旧批次，旧批次被关闭。
 */
@ApiStatus.Internal
public final class Batch implements AutoCloseable {
    private final List<OpenedPack> opened = new ArrayList<>();
    private boolean closed;

    public synchronized void add(OpenedPack pack) {
        if (closed) {
            throw new IllegalStateException("batch is already closed");
        }
        opened.add(pack);
    }

    /**
     * 关闭全部数据包。任何一个关闭失败都会在全部尝试之后抛出，其余失败作为 suppressed。
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        UncheckedIOException failure = null;
        for (var pack : opened) {
            try {
                pack.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = new UncheckedIOException("无法关闭数据包 " + pack.packId(), e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        opened.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
