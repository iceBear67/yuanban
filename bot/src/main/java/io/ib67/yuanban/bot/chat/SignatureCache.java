package io.ib67.yuanban.bot.chat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;

/**
 * 消息签名缓存，用于解析按编号引用的签名（原版客户端 {@code MessageSignatureCache}，容量 128）。
 * 每条玩家聊天把它的已读签名与自己的签名移到最前面。
 */
public final class SignatureCache {
    public static final int SIZE = 128;

    private final Signature[] entries = new Signature[SIZE];

    /**
     * @param lastSeen  消息正文中的已读签名（已经解析为完整签名）
     * @param signature 消息自己的签名
     */
    public void push(List<Signature> lastSeen, @Nullable Signature signature) {
        var deque = new ArrayDeque<Signature>(lastSeen.size() + 1);
        deque.addAll(lastSeen);
        if (signature != null) {
            deque.add(signature);
        }
        var pushed = List.copyOf(deque);
        for (int i = 0; !deque.isEmpty() && i < entries.length; i++) {
            var previous = entries[i];
            entries[i] = deque.removeLast();
            if (previous != null && !pushed.contains(previous)) {
                deque.addFirst(previous);
            }
        }
    }

    /**
     * @return 编号对应的签名
     * @throws IllegalStateException 编号超出范围，或者缓存中这个编号没有签名
     *                               （原版客户端对这两种情况都以无效数据包断开连接）
     */
    public Signature get(int id) {
        if (id < 0 || id >= entries.length) {
            throw new IllegalStateException("message signature id " + id + " is out of range [0, " + entries.length + ")");
        }
        var signature = entries[id];
        if (signature == null) {
            throw new IllegalStateException("no message signature is cached with id " + id);
        }
        return signature;
    }
}
