package io.ib67.yuanban.bot.player;

import io.ib67.yuanban.bot.view.ActiveEffect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectMapTest {
    private static ActiveEffect effect(Effect effect, int amplifier, int duration) {
        return new ActiveEffect(effect, amplifier, duration, false, true, true, false);
    }

    @Test
    void levelsAreTheAmplifierPlusOne() {
        var effects = new EffectMap();
        assertEquals(0, effects.level(Effect.SPEED));
        effects.put(effect(Effect.SPEED, 0, 100));
        assertEquals(1, effects.level(Effect.SPEED));
        effects.put(effect(Effect.SPEED, 2, 100));
        assertEquals(3, effects.level(Effect.SPEED));
        // 移除之后等级为 0
        assertEquals(2, effects.remove(Effect.SPEED).amplifier());
        assertEquals(0, effects.level(Effect.SPEED));
        assertNull(effects.get(Effect.SPEED));
    }

    @Test
    void durationsCountDownButInfiniteEffectsStay() {
        var effects = new EffectMap();
        effects.put(effect(Effect.SPEED, 0, 2));
        effects.put(effect(Effect.INVISIBILITY, 0, ActiveEffect.INFINITE_DURATION));
        effects.tick();
        assertEquals(1, effects.get(Effect.SPEED).duration());
        // 无限持续时间不递减
        assertTrue(effects.get(Effect.INVISIBILITY).isInfinite());
        effects.tick();
        // 已经到 0 的效果不再递减
        assertEquals(0, effects.get(Effect.SPEED).duration());
        effects.tick();
        assertEquals(0, effects.get(Effect.SPEED).duration());
    }

    @Test
    void allReturnsASnapshot() {
        var effects = new EffectMap();
        effects.put(effect(Effect.SPEED, 0, 100));
        var all = effects.all();
        effects.clear();
        assertEquals(Map.of(), effects.all());
        assertEquals(1, all.size());
    }

    @Test
    void puttingAnEffectAgainReplacesIt() {
        var effects = new EffectMap();
        effects.put(effect(Effect.JUMP_BOOST, 0, 100));
        effects.put(effect(Effect.JUMP_BOOST, 1, 200));
        assertEquals(2, effects.level(Effect.JUMP_BOOST));
        assertEquals(200, effects.get(Effect.JUMP_BOOST).duration());
        assertEquals(1, effects.all().size());
    }
}
