package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.EntityDamaged;
import io.ib67.yuanban.bot.observe.HealthChanged;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;

import java.util.List;

/**
 * 记录本地玩家受到的伤害：目标为自己的 Damage Event 与生命值下降的 Set Health。
 */
public final class DamageObserver extends BuiltinObserver {
    private Records<DamageRecord> damages = new Records<>(Integer.MAX_VALUE);

    DamageObserver() {
    }

    /**
     * 最多保存的记录数，在注册之前设置
     *
     * @throws IllegalStateException 已经注册过
     */
    public DamageObserver limit(int limit) {
        checkNotRegistered(this);
        this.damages = new Records<>(limit);
        return this;
    }

    public List<DamageRecord> damages() {
        checkReadable();
        return damages.items();
    }

    public boolean truncated() {
        checkReadable();
        return damages.truncated();
    }

    @Override
    public Subscription subscription() {
        return Subscription.of(EntityDamaged.class, HealthChanged.class);
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        switch (observation) {
            case EntityDamaged damaged -> {
                if (damaged.entityId() == context.state().player().entityId()) {
                    damages.add(new DamageRecord.Event(damaged));
                }
            }
            case HealthChanged changed -> {
                if (changed.health() < changed.oldHealth()) {
                    damages.add(new DamageRecord.HealthDrop(changed));
                }
            }
            default -> {
            }
        }
    }
}
