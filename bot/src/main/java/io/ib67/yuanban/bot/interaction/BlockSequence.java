package io.ib67.yuanban.bot.interaction;

import java.util.ArrayList;
import java.util.TreeMap;

/**
 * wiki 定义的方块交互序号（Sequence）：用手左键方块、右键使用物品、开始或结束挖掘时加一。
 * 收到 Acknowledge Block Change 时完成序号小于等于确认值的全部步骤。
 */
public final class BlockSequence {
    private int sequence;
    private final TreeMap<Integer, Runnable> pending = new TreeMap<>();

    /**
     * 开始一个带序号的步骤，返回它的序号（第一个为 1）
     */
    public int next() {
        return ++sequence;
    }

    /**
     * 收到不小于 sequence 的确认时执行 onAcknowledged
     */
    public void await(int sequence, Runnable onAcknowledged) {
        pending.put(sequence, onAcknowledged);
    }

    public void forget(int sequence) {
        pending.remove(sequence);
    }

    /**
     * 新建世界或者离开世界：序号从 0 重新开始，丢弃等待中的确认（原版的序号属于 {@code ClientLevel}）
     */
    public void reset() {
        sequence = 0;
        pending.clear();
    }

    public void acknowledge(int acknowledged) {
        var completed = pending.headMap(acknowledged, true);
        var callbacks = new ArrayList<>(completed.values());
        completed.clear();
        callbacks.forEach(Runnable::run);
    }
}
