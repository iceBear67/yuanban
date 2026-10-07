package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.ChatReceived;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.view.ChatLine;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 收到第一条匹配的聊天消息时关闭自己。
 */
public final class FirstChatObserver extends BuiltinObserver {
    private final Predicate<ChatLine> filter;
    private @Nullable ChatLine matched;

    FirstChatObserver(Predicate<ChatLine> filter) {
        this.filter = filter;
    }

    /**
     * 匹配的聊天消息；在匹配之前被关闭时为空
     */
    public Optional<ChatLine> matched() {
        checkReadable();
        return Optional.ofNullable(matched);
    }

    @Override
    public Subscription subscription() {
        return Subscription.of(ChatReceived.class);
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        if (matched == null && observation instanceof ChatReceived(ChatLine line) && filter.test(line)) {
            matched = line;
            context.close();
        }
    }
}
