package io.ib67.yuanban.bot.player;

import io.ib67.yuanban.bot.data.AttributeData;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.ModifierOperation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * 一个实体的属性。取值按 wiki Attribute 页的修饰符规则计算：基础值加上全部 ADD_VALUE 修饰符；
 * 再对每个 ADD_MULTIPLIED_BASE 修饰符加上「加法之后的值 × 数值」；再对每个 ADD_MULTIPLIED_TOTAL 修饰符乘以「1 + 数值」；
 * 最后限制到属性的取值范围（{@link AttributeData}）。
 */
public final class AttributeMap {
    /**
     * 原版玩家的基础值（原版 {@code Player.createAttributes}）：移动速度为 {@code (double) 0.1F}，攻击伤害为 1，
     * 路径点的发送与接收距离为 6.0E7，其余为属性的默认值
     */
    public static final ToDoubleFunction<AttributeType> PLAYER_BASE = attribute -> {
        if (attribute == AttributeType.Builtin.MOVEMENT_SPEED) {
            return 0.1F;
        }
        if (attribute == AttributeType.Builtin.ATTACK_DAMAGE) {
            return 1.0;
        }
        if (attribute == AttributeType.Builtin.WAYPOINT_TRANSMIT_RANGE || attribute == AttributeType.Builtin.WAYPOINT_RECEIVE_RANGE) {
            return 6.0E7;
        }
        return AttributeData.defaultValue(attribute);
    };
    public static final ToDoubleFunction<AttributeType> DEFAULT_BASE = AttributeData::defaultValue;

    private final ToDoubleFunction<AttributeType> baseValues;
    private final Map<AttributeType, Instance> instances = new HashMap<>();

    public AttributeMap(ToDoubleFunction<AttributeType> baseValues) {
        this.baseValues = baseValues;
    }

    /**
     * Update Attributes：替换数据包中列出的属性的基础值与全部修饰符（原版客户端行为）
     */
    public void apply(List<Attribute> attributes) {
        for (var attribute : attributes) {
            instances.put(attribute.getType(), new Instance(attribute.getValue(), attribute.getModifiers()));
        }
    }

    public double value(AttributeType attribute) {
        var instance = instances.get(attribute);
        if (instance != null) {
            return instance.value(attribute);
        }
        return Math.clamp(baseValues.applyAsDouble(attribute), AttributeData.min(attribute), AttributeData.max(attribute));
    }

    public void addModifier(AttributeType attribute, AttributeModifier modifier) {
        instances.computeIfAbsent(attribute, a -> new Instance(baseValues.applyAsDouble(a), List.of()))
                .modifiers.put(modifier.getId(), modifier);
    }

    public void removeModifier(AttributeType attribute, Key id) {
        var instance = instances.get(attribute);
        if (instance != null) {
            instance.modifiers.remove(id);
        }
    }

    /**
     * Respawn 带 Data kept 0x01：复制另一份属性的基础值与全部修饰符（原版 {@code AttributeMap.assignAllValues}）
     */
    public void copyAll(AttributeMap other) {
        other.instances.forEach((attribute, instance) ->
                instances.put(attribute, new Instance(instance.base, instance.modifiers.values())));
    }

    /**
     * Respawn 不带 Data kept 0x01：只复制另一份属性的基础值，不带修饰符（原版 {@code AttributeMap.assignBaseValues}）
     */
    public void copyBaseValues(AttributeMap other) {
        other.instances.forEach((attribute, instance) -> instances.put(attribute, new Instance(instance.base, List.of())));
    }

    /**
     * 已经收到或者本地修改过的属性的取值
     */
    public Map<AttributeType, Double> values() {
        var result = new HashMap<AttributeType, Double>();
        instances.forEach((attribute, instance) -> result.put(attribute, instance.value(attribute)));
        return result;
    }

    /**
     * 疾跑修饰符的名称（原版 {@code LivingEntity.SPEED_MODIFIER_SPRINTING_ID}）
     */
    public static final Key SPRINTING_ID = Key.key("minecraft:sprinting");

    /**
     * 一个属性的基础值与修饰符；修饰符按名称保存，同名的修饰符替换之前的
     */
    private static final class Instance {
        private final double base;
        private final Map<Key, AttributeModifier> modifiers = new LinkedHashMap<>();

        private Instance(double base, Iterable<AttributeModifier> modifiers) {
            this.base = base;
            modifiers.forEach(modifier -> this.modifiers.put(modifier.getId(), modifier));
        }

        private double value(AttributeType attribute) {
            var added = base;
            for (var modifier : modifiers.values()) {
                if (modifier.getOperation() == ModifierOperation.ADD) {
                    added += modifier.getAmount();
                }
            }
            var result = added;
            for (var modifier : modifiers.values()) {
                if (modifier.getOperation() == ModifierOperation.ADD_MULTIPLIED_BASE) {
                    result += added * modifier.getAmount();
                }
            }
            for (var modifier : modifiers.values()) {
                if (modifier.getOperation() == ModifierOperation.ADD_MULTIPLIED_TOTAL) {
                    result *= 1.0 + modifier.getAmount();
                }
            }
            return Math.clamp(result, AttributeData.min(attribute), AttributeData.max(attribute));
        }
    }
}
