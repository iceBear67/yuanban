package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.ChatReceived;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.view.ChatLine;

import java.util.List;
import java.util.function.Predicate;

/**
 * 记录满足条件的聊天消息。
 */
public final class ChatObserver extends BuiltinObserver {
    private final Predicate<ChatLine> filter;
    private Records<ChatLine> lines = new Records<>(Integer.MAX_VALUE);

    ChatObserver(Predicate<ChatLine> filter) {
        this.filter = filter;
    }

    /**
     * 最多保存的记录数，在注册之前设置
     *
     * @throws IllegalStateException 已经注册过
     */
    public ChatObserver limit(int limit) {
        checkNotRegistered(this);
        this.lines = new Records<>(limit);
        return this;
    }

    public List<ChatLine> lines() {
        checkReadable();
        return lines.items();
    }

    public boolean truncated() {
        checkReadable();
        return lines.truncated();
    }

    @Override
    public Subscription subscription() {
        return Subscription.of(ChatReceived.class);
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        if (observation instanceof ChatReceived(ChatLine line) && filter.test(line)) {
            lines.add(line);
        }
    }
}
