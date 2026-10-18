package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observation.ActionCompleted;
import io.ib67.yuanban.bot.observe.Observation.PlayerMoved;
import io.ib67.yuanban.bot.observe.Observation.PlayerTeleported;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import net.minestom.server.coordinate.Point;

/**
 * {@code distanceWithIn} 与 {@code distanceOutOf}：玩家位置（脚部）与目标的直线距离满足条件时取消交给它的动作并关闭自己；
 * 动作先结束时关闭自己。注册时先检查一次距离条件，之后在每条 {@link PlayerMoved}、{@link PlayerTeleported} 之后检查。
 * 不在任何世界中时注册不做注册时的检查（没有玩家位置），进入世界之后照常在每条 {@link PlayerMoved}、{@link PlayerTeleported} 之后检查。
 * 取消的原因固定为 {@link ActionCancelledException#BY_OBSERVER}，与使用者调用 {@link Action#cancel()} 得到的
 * {@link ActionCancelledException#BY_USER} 区分。
 */
public final class DistanceObserver extends BuiltinObserver {
    private final double range;
    private final Point target;
    private final Action action;
    private final boolean within;
    private boolean met;

    DistanceObserver(double range, Point target, Action action, boolean within) {
        this.range = range;
        this.target = target;
        this.action = action;
        this.within = within;
    }

    /**
     * 是否因满足距离条件而关闭
     */
    public boolean met() {
        checkReadable();
        return met;
    }

    @Override
    public Subscription subscription() {
        return Subscription.of(PlayerMoved.class, PlayerTeleported.class, ActionCompleted.class);
    }

    @Override
    void started(ObserverContext context) {
        var metNow = context.world() != null && check(context);
        if (!metNow && action.future().isDone()) {
            context.close();
        }
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        switch (observation) {
            case PlayerMoved _, PlayerTeleported _ -> check(context);
            case ActionCompleted(Action completed, var _) -> {
                if (completed == action) {
                    context.close();
                }
            }
            default -> {
            }
        }
    }

    /**
     * @return 距离条件是否满足；满足时取消动作并关闭自己
     */
    private boolean check(ObserverContext context) {
        if (met) {
            return true;
        }
        var distance = context.state().player().position().distance(target);
        if (within ? distance <= range : distance > range) {
            met = true;
            action.cancel(ActionCancelledException.BY_OBSERVER);
            context.close();
            return true;
        }
        return false;
    }
}
