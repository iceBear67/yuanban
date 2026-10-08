package io.ib67.yuanban.bot.chat;

import io.ib67.yuanban.bot.view.ChatLine;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;

/**
 * 聊天日志：Player Chat Message、Disguised Chat Message、不带 overlay 的 System Chat Message，有长度上限。
 */
public final class ChatModel {
    private final int capacity;
    private final ArrayDeque<Entry> log = new ArrayDeque<>();
    private SignatureCache signatures = new SignatureCache();
    private LastSeenTracker lastSeen = new LastSeenTracker();
    /**
     * 下一条 Player Chat Message 的全局编号（原版 {@code ClientPacketListener.nextChatIndex}）
     */
    private int nextChatIndex;

    public ChatModel(int capacity) {
        this.capacity = capacity;
    }

    public SignatureCache signatures() {
        return signatures;
    }

    public LastSeenTracker lastSeen() {
        return lastSeen;
    }

    /**
     * Login (play)：已读跟踪、签名缓存与全局编号重新开始（原版客户端 {@code handleLogin}），聊天日志保留
     */
    public void newSession() {
        signatures = new SignatureCache();
        lastSeen = new LastSeenTracker();
        nextChatIndex = 0;
    }

    /**
     * 原版 {@code handlePlayerChat}：Player Chat Message 的全局编号从 0 开始逐条加一
     *
     * @throws IllegalStateException 编号不连续（原版客户端以 {@code multiplayer.disconnect.bad_chat_index} 断开连接）
     */
    public void checkGlobalIndex(int globalIndex) {
        var expected = nextChatIndex++;
        if (globalIndex != expected) {
            throw new IllegalStateException("missing or out-of-order player chat message: expected global index "
                    + expected + " but got " + globalIndex);
        }
    }

    public void add(ChatLine line, @Nullable Signature signature) {
        log.addLast(new Entry(line, signature));
        while (log.size() > capacity) {
            log.removeFirst();
        }
    }

    /**
     * Delete Message：删除带这个签名的消息
     *
     * @return 是否删除了消息
     */
    public boolean delete(Signature signature) {
        return log.removeIf(entry -> signature.equals(entry.signature()));
    }

    /**
     * Clear Chat（配置阶段）
     */
    public void clear() {
        log.clear();
    }

    public List<ChatLine> lines() {
        return log.stream().map(Entry::line).toList();
    }

    private record Entry(ChatLine line, @Nullable Signature signature) {
    }
}
