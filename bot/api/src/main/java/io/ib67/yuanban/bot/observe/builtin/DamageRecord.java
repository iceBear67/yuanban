package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation.EntityDamaged;
import io.ib67.yuanban.bot.observe.Observation.HealthChanged;

/**
 * 本地玩家受到伤害的一条记录。
 */
public sealed interface DamageRecord {
    /**
     * 目标为自己的 Damage Event
     */
    record Event(EntityDamaged event) implements DamageRecord {
    }

    /**
     * 生命值下降的 Set Health
     */
    record HealthDrop(HealthChanged change) implements DamageRecord {
    }
}
