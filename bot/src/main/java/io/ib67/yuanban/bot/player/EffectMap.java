package io.ib67.yuanban.bot.player;

import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地玩家的药水效果。剩余时间按 tick 递减，{@link Potion#INFINITE_DURATION} 不递减。
 */
public final class EffectMap {
    private final Map<PotionEffect, Potion> effects = new HashMap<>();

    public void put(Potion potion) {
        effects.put(potion.effect(), potion);
    }

    public @Nullable Potion remove(PotionEffect effect) {
        return effects.remove(effect);
    }

    public @Nullable Potion get(PotionEffect effect) {
        return effects.get(effect);
    }

    /**
     * 效果等级（放大器加一）；没有这个效果时为 0
     */
    public int level(PotionEffect effect) {
        var potion = effects.get(effect);
        return potion == null ? 0 : potion.amplifier() + 1;
    }

    public void tick() {
        effects.replaceAll((_, potion) -> potion.duration() == Potion.INFINITE_DURATION || potion.duration() <= 0
                ? potion
                : new Potion(potion.effect(), potion.amplifier(), potion.duration() - 1, potion.flags()));
    }

    public void clear() {
        effects.clear();
    }

    /**
     * 当前全部效果，{@link Potion#duration()} 为剩余的 tick 数
     */
    public Map<PotionEffect, Potion> all() {
        return Map.copyOf(effects);
    }
}
