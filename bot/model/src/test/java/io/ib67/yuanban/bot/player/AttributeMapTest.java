package io.ib67.yuanban.bot.player;

import io.ib67.yuanban.bot.data.AttributeData;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.ModifierOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributeMapTest {
    private static final AttributeType SPEED = AttributeType.Builtin.MOVEMENT_SPEED;

    @Test
    void defaultsComeFromTheAttributeData() {
        // attribute.json 中 jump_strength 的默认值为原版的 (double) 0.42F
        assertEquals((double) 0.42F, AttributeData.defaultValue(AttributeType.Builtin.JUMP_STRENGTH));
        assertEquals(20.0, AttributeData.defaultValue(AttributeType.Builtin.MAX_HEALTH));
        assertEquals(1.0, AttributeData.min(AttributeType.Builtin.MAX_HEALTH));
        assertEquals(1024.0, AttributeData.max(AttributeType.Builtin.MAX_HEALTH));
        var entity = new AttributeMap(AttributeMap.DEFAULT_BASE);
        assertEquals((double) 0.42F, entity.value(AttributeType.Builtin.JUMP_STRENGTH));
        // 玩家的移动速度基础值为 (double) 0.1F
        var player = new AttributeMap(AttributeMap.PLAYER_BASE);
        assertEquals((double) 0.1F, player.value(SPEED));
        assertEquals(1.0, player.value(AttributeType.Builtin.ATTACK_DAMAGE));
    }

    @Test
    void modifiersApplyInTheVanillaOrder() {
        var map = new AttributeMap(AttributeMap.DEFAULT_BASE);
        map.apply(List.of(new Attribute(SPEED, 0.1, List.of(
                new AttributeModifier(Key.key("test:total"), 0.5, ModifierOperation.ADD_MULTIPLIED_TOTAL),
                new AttributeModifier(Key.key("test:base"), 1.0, ModifierOperation.ADD_MULTIPLIED_BASE),
                new AttributeModifier(Key.key("test:add"), 0.1, ModifierOperation.ADD)))));
        // 先加 ADD：0.2；ADD_MULTIPLIED_BASE 按加法之后的值：0.2 + 0.2 × 1；ADD_MULTIPLIED_TOTAL：0.4 × 1.5
        assertEquals((0.1 + 0.1) * 2 * 1.5, map.value(SPEED), 1e-12);
        // 同名的修饰符替换之前的
        map.addModifier(SPEED, new AttributeModifier(Key.key("test:total"), 1.0, ModifierOperation.ADD_MULTIPLIED_TOTAL));
        assertEquals((0.1 + 0.1) * 2 * 2, map.value(SPEED), 1e-12);
        map.removeModifier(SPEED, Key.key("test:total"));
        assertEquals((0.1 + 0.1) * 2, map.value(SPEED), 1e-12);
    }

    @Test
    void valuesAreLimitedToTheAttributeRange() {
        var map = new AttributeMap(AttributeMap.DEFAULT_BASE);
        var maxHealth = AttributeType.Builtin.MAX_HEALTH;
        map.apply(List.of(new Attribute(maxHealth, 4000, List.of())));
        assertEquals(1024.0, map.value(maxHealth));
        map.apply(List.of(new Attribute(maxHealth, 20, List.of(
                new AttributeModifier(Key.key("test:drain"), -50, ModifierOperation.ADD)))));
        assertEquals(1.0, map.value(maxHealth));
    }

    @Test
    void respawnCopiesBaseValuesWithOrWithoutModifiers() {
        var old = new AttributeMap(AttributeMap.PLAYER_BASE);
        old.apply(List.of(new Attribute(SPEED, 0.2, List.of(
                new AttributeModifier(Key.key("test:boost"), 0.5, ModifierOperation.ADD_MULTIPLIED_TOTAL)))));
        var all = new AttributeMap(AttributeMap.PLAYER_BASE);
        all.copyAll(old);
        assertEquals(0.2 * 1.5, all.value(SPEED), 1e-12);
        var bases = new AttributeMap(AttributeMap.PLAYER_BASE);
        bases.copyBaseValues(old);
        assertEquals(0.2, bases.value(SPEED), 1e-12);
    }
}
