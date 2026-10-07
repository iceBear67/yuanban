package io.ib67.yuanban.bot.observe.builtin;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置 observer 的记录列表：默认不限制数量；设置上限后，超出的记录只计数不保存。
 * 只由调度线程修改。
 */
public final class Records<T> {
    private final List<T> items = new ArrayList<>();
    private final int limit;
    private long dropped;

    Records(int limit) {
        this.limit = limit;
    }

    void add(T item) {
        if (items.size() < limit) {
            items.add(item);
        } else {
            dropped++;
        }
    }

    public List<T> items() {
        return List.copyOf(items);
    }

    /**
     * 是否有超出上限而没有保存的记录
     */
    public boolean truncated() {
        return dropped > 0;
    }

    /**
     * 包括没有保存的记录在内的总数
     */
    public long count() {
        return items.size() + dropped;
    }
}
