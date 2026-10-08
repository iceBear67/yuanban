package io.ib67.yuanban.bot.player;

import io.ib67.yuanban.bot.view.ActiveEffect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地玩家的药水效果。剩余时间按 tick 递减，{@link ActiveEffect#INFINITE_DURATION} 不递减。
 */
public final class EffectMap {
    private final Map<Effect, ActiveEffect> effects = new HashMap<>();

    public void put(ActiveEffect effect) {
        effects.put(effect.effect(), effect);
    }

    public @Nullable ActiveEffect remove(Effect effect) {
        return effects.remove(effect);
    }

    public @Nullable ActiveEffect get(Effect effect) {
        return effects.get(effect);
    }

    /**
     * 效果等级（放大器加一）；没有这个效果时为 0
     */
    public int level(Effect effect) {
        var active = effects.get(effect);
        return active == null ? 0 : active.amplifier() + 1;
    }

    public void tick() {
        effects.replaceAll((_, active) -> active.isInfinite() || active.duration() <= 0
                ? active
                : active.withDuration(active.duration() - 1));
    }

    public void clear() {
        effects.clear();
    }

    /**
     * 当前全部效果，{@link ActiveEffect#duration()} 为剩余的 tick 数
     */
    public Map<Effect, ActiveEffect> all() {
        return Map.copyOf(effects);
    }
}
