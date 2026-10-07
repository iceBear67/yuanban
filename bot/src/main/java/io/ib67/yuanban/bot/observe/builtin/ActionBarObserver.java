package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.ActionBarShown;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * 记录显示过的动作栏文本。
 */
public final class ActionBarObserver extends BuiltinObserver {
    private Records<Component> texts = new Records<>(Integer.MAX_VALUE);

    ActionBarObserver() {
    }

    /**
     * 最多保存的记录数，在注册之前设置
     *
     * @throws IllegalStateException 已经注册过
     */
    public ActionBarObserver limit(int limit) {
        checkNotRegistered(this);
        this.texts = new Records<>(limit);
        return this;
    }

    public List<Component> texts() {
        checkReadable();
        return texts.items();
    }

    public boolean truncated() {
        checkReadable();
        return texts.truncated();
    }

    @Override
    public Subscription subscription() {
        return Subscription.of(ActionBarShown.class);
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        if (observation instanceof ActionBarShown(Component text)) {
            texts.add(text);
        }
    }
}
