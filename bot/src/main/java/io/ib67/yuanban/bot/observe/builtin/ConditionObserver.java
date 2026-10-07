package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.StateUpdated;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.view.StateReader;

import java.util.function.Predicate;

/**
 * 条件成立时关闭自己。注册时先检查一次，之后在每条 {@link StateUpdated} 时检查。
 */
public final class ConditionObserver extends BuiltinObserver {
    private final Predicate<StateReader> closeWhen;
    private boolean met;

    ConditionObserver(Predicate<StateReader> closeWhen) {
        this.closeWhen = closeWhen;
    }

    /**
     * 是否因条件成立而关闭
     */
    public boolean met() {
        checkReadable();
        return met;
    }

    @Override
    public Subscription subscription() {
        return Subscription.of(StateUpdated.class);
    }

    @Override
    void started(ObserverContext context) {
        check(context);
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        check(context);
    }

    private void check(ObserverContext context) {
        if (!met && closeWhen.test(context.state())) {
            met = true;
            context.close();
        }
    }
}
