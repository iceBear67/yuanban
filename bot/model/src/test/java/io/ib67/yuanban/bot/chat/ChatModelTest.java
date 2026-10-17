package io.ib67.yuanban.bot.chat;

import io.ib67.yuanban.bot.view.ChatKind;
import io.ib67.yuanban.bot.view.ChatLine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatModelTest {
    private static Signature signature(int seed) {
        var bytes = new byte[256];
        bytes[0] = (byte) seed;
        return new Signature(bytes);
    }

    private static ChatLine line(String text) {
        return new ChatLine(ChatKind.SYSTEM, null, null, null, null, null, Component.text(text), text);
    }

    @Test
    void theLogKeepsAtMostTheConfiguredCapacity() {
        var chat = new ChatModel(2);
        chat.add(line("one"), null);
        chat.add(line("two"), null);
        chat.add(line("three"), null);
        assertEquals(List.of("two", "three"), chat.lines().stream().map(ChatLine::plainText).toList());
    }

    @Test
    void clearingRemovesEveryLine() {
        var chat = new ChatModel(10);
        chat.add(line("one"), null);
        chat.clear();
        assertTrue(chat.lines().isEmpty());
    }

    @Test
    void deletingAMessageNeedsItsSignature() {
        var chat = new ChatModel(10);
        var first = signature(1);
        var second = signature(2);
        chat.add(line("one"), first);
        chat.add(line("two"), second);
        chat.add(line("three"), null);
        assertTrue(chat.delete(first));
        assertEquals(List.of("two", "three"), chat.lines().stream().map(ChatLine::plainText).toList());
        // 已经删除的签名不再删除任何消息
        assertFalse(chat.delete(first));
        // 没有签名的消息不会被删除
        assertFalse(chat.delete(signature(3)));
    }

    @Test
    void globalIndicesMustBeContiguous() {
        var chat = new ChatModel(10);
        chat.checkGlobalIndex(0);
        chat.checkGlobalIndex(1);
        assertThrows(IllegalStateException.class, () -> chat.checkGlobalIndex(3));
        // 编号必须严格递增
        assertThrows(IllegalStateException.class, () -> chat.checkGlobalIndex(2));
    }

    @Test
    void aNewSessionRestartsTheIndicesAndSignatures() {
        var chat = new ChatModel(10);
        chat.checkGlobalIndex(0);
        chat.add(line("kept"), null);
        chat.newSession();
        // 聊天日志保留，全局编号与签名缓存重新开始
        assertEquals(1, chat.lines().size());
        chat.checkGlobalIndex(0);
        assertThrows(IllegalStateException.class, () -> chat.signatures().get(0));
    }
}
