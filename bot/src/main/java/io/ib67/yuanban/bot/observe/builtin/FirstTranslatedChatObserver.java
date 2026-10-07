package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.ChatReceived;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.view.ChatLine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 收到第一条含有指定翻译键的聊天消息时关闭自己：消息的组件（包括子组件与翻译参数）中有这个翻译键的翻译组件即为匹配，
 * 不考虑消息的其它内容。{@link ChatLine#plainText()} 不翻译翻译组件，按翻译键匹配加入消息、死亡消息等由服务端翻译键组成的消息。
 */
public final class FirstTranslatedChatObserver extends BuiltinObserver {
    private final String translationKey;
    private @Nullable ChatLine matched;

    FirstTranslatedChatObserver(String translationKey) {
        this.translationKey = translationKey;
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
        if (matched == null && observation instanceof ChatReceived(ChatLine line) && contains(line.message())) {
            matched = line;
            context.close();
        }
    }

    private boolean contains(Component component) {
        if (component instanceof TranslatableComponent translatable) {
            if (translatable.key().equals(translationKey)) {
                return true;
            }
            for (var argument : translatable.arguments()) {
                if (argument.value() instanceof Component value && contains(value)) {
                    return true;
                }
            }
        }
        for (var child : component.children()) {
            if (contains(child)) {
                return true;
            }
        }
        return false;
    }
}
