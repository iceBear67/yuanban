package io.ib67.yuanban.bot.chat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * 最近已读的带签名消息（原版客户端 {@code LastSeenMessagesTracker}，窗口为 20 条），
 * 用于计算 Chat Message 中的 Message Count、Acknowledged 与 Checksum。
 */
public final class LastSeenTracker {
    public static final int WINDOW = 20;
    /**
     * 未确认的消息超过这个数量时发送 Acknowledge Message（原版客户端行为）
     */
    public static final int ACKNOWLEDGE_THRESHOLD = 64;

    private final Signature[] tracked = new Signature[WINDOW];
    /**
     * 还没有随 Chat Message 告知服务端的条目（原版 {@code LastSeenTrackedEntry.pending}）
     */
    private final boolean[] pending = new boolean[WINDOW];
    private int tail;
    private int offset;
    private @Nullable Signature lastTracked;

    /**
     * 处理完一条带签名的玩家聊天（原版 {@code addPending}）
     *
     * @param shown 消息是否显示给玩家：没有显示的消息（完全过滤、发送者不在玩家列表中）只计入未确认的消息数，
     *              在窗口中占一个空位，不计入已读（原版这个位置的条目为空）
     * @return 是否计入（与上一条签名相同时不计入）
     */
    public boolean add(Signature signature, boolean shown) {
        if (signature.equals(lastTracked)) {
            return false;
        }
        lastTracked = signature;
        tracked[tail] = shown ? signature : null;
        pending[tail] = shown;
        tail = (tail + 1) % WINDOW;
        offset++;
        return true;
    }

    /**
     * Delete Message（原版 {@code ignorePending}）：这条消息还没有随 Chat Message 告知服务端时，从跟踪中去掉
     */
    public void ignorePending(Signature signature) {
        for (int i = 0; i < WINDOW; i++) {
            if (tracked[i] != null && pending[i] && tracked[i].equals(signature)) {
                tracked[i] = null;
                pending[i] = false;
                return;
            }
        }
    }

    public int offset() {
        return offset;
    }

    /**
     * 取出并清零未确认的消息数，用于 Acknowledge Message
     */
    public int takeOffset() {
        var result = offset;
        offset = 0;
        return result;
    }

    /**
     * 生成 Chat Message 中的已读信息，并清零未确认的消息数；跟踪中的条目随之告知服务端
     */
    public Update update() {
        var messageCount = takeOffset();
        var acknowledged = new BitSet(WINDOW);
        var signatures = new ArrayList<Signature>(WINDOW);
        for (int i = 0; i < WINDOW; i++) {
            var index = (tail + i) % WINDOW;
            var signature = tracked[index];
            if (signature != null) {
                acknowledged.set(i);
                signatures.add(signature);
                pending[index] = false;
            }
        }
        return new Update(messageCount, acknowledged, checksum(signatures));
    }

    /**
     * wiki Chat Message：对已读签名依次计算 {@code 31 * h + Arrays.hashCode(签名)}（初值 1），转为 byte，结果为 0 时改为 1
     */
    public static byte checksum(List<Signature> signatures) {
        int hash = 1;
        for (var signature : signatures) {
            hash = 31 * hash + signature.hashCode();
        }
        var result = (byte) hash;
        return result == 0 ? 1 : result;
    }

    /**
     * @param messageCount 自上次发送以来新增的消息数
     * @param acknowledged 20 位的已读标记
     */
    public record Update(int messageCount, BitSet acknowledged, byte checksum) {
    }
}
