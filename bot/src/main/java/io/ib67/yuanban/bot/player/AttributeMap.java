package io.ib67.yuanban.bot.player;

import net.kyori.adventure.key.Key;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeInstance;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * 一个实体的属性。取值按 wiki Attribute 页的修饰符规则计算（使用 Minestom 的 {@link AttributeInstance}）。
 */
public final class AttributeMap {
    /**
     * 原版玩家的基础值（原版 {@code Player.createAttributes}）：移动速度为 {@code (double) 0.1F}，攻击伤害为 1，
     * 路径点的发送与接收距离为 6.0E7，其余为属性的默认值
     */
    public static final ToDoubleFunction<Attribute> PLAYER_BASE = attribute -> {
        if (attribute.equals(Attribute.MOVEMENT_SPEED)) {
            return 0.1F;
        }
        if (attribute.equals(Attribute.ATTACK_DAMAGE)) {
            return 1.0;
        }
        if (attribute.equals(Attribute.WAYPOINT_TRANSMIT_RANGE) || attribute.equals(Attribute.WAYPOINT_RECEIVE_RANGE)) {
            return 6.0E7;
        }
        return attribute.defaultValue();
    };
    public static final ToDoubleFunction<Attribute> DEFAULT_BASE = Attribute::defaultValue;

    private final ToDoubleFunction<Attribute> baseValues;
    private final Map<Attribute, AttributeInstance> instances = new HashMap<>();

    public AttributeMap(ToDoubleFunction<Attribute> baseValues) {
        this.baseValues = baseValues;
    }

    /**
     * Update Attributes：替换数据包中列出的属性的基础值与全部修饰符（原版客户端行为）
     */
    public void apply(List<EntityAttributesPacket.Property> properties) {
        for (var property : properties) {
            instances.put(property.attribute(),
                    new AttributeInstance(property.attribute(), property.value(), property.modifiers(), null));
        }
    }

    public double value(Attribute attribute) {
        var instance = instances.get(attribute);
        if (instance != null) {
            return instance.getValue();
        }
        return Math.clamp(baseValues.applyAsDouble(attribute), attribute.minValue(), attribute.maxValue());
    }

    public void addModifier(Attribute attribute, AttributeModifier modifier) {
        instance(attribute).addModifier(modifier);
    }

    public void removeModifier(Attribute attribute, Key id) {
        var instance = instances.get(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    /**
     * Respawn 带 Data kept 0x01：复制另一份属性的基础值与全部修饰符（原版 {@code AttributeMap.assignAllValues}）
     */
    public void copyAll(AttributeMap other) {
        other.instances.forEach((attribute, instance) -> instances.put(attribute,
                new AttributeInstance(attribute, instance.getBaseValue(), instance.modifiers(), null)));
    }

    /**
     * Respawn 不带 Data kept 0x01：只复制另一份属性的基础值，不带修饰符（原版 {@code AttributeMap.assignBaseValues}）
     */
    public void copyBaseValues(AttributeMap other) {
        other.instances.forEach((attribute, instance) -> instances.put(attribute,
                new AttributeInstance(attribute, instance.getBaseValue(), List.of(), null)));
    }

    /**
     * 已经收到或者本地修改过的属性的取值
     */
    public Map<Attribute, Double> values() {
        var result = new HashMap<Attribute, Double>();
        instances.forEach((attribute, instance) -> result.put(attribute, instance.getValue()));
        return result;
    }

    private AttributeInstance instance(Attribute attribute) {
        return instances.computeIfAbsent(attribute,
                a -> new AttributeInstance(a, baseValues.applyAsDouble(a), List.of(), null));
    }

    /**
     * 疾跑修饰符的名称（原版 {@code LivingEntity.SPEED_MODIFIER_SPRINTING_ID}）
     */
    public static final Key SPRINTING_ID = Key.key("minecraft:sprinting");
}
