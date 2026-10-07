package io.ib67.yuanban.enchant;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponent;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 附魔定义的解码中间结果，逐字段存放，提交阶段用 {@link Enchantment.Builder} 转换为注册表条目。
 * {@link Enchantment} 的构造器对使用者不可见（只能通过 {@code Enchantment.builder()} 创建），
 * 而效果组件的映射也没有从序列化数据构建的公开接口，所以准备阶段先把全部字段存在这里。
 *
 * @param supportedItems {@code supported_items} 引用的物品标签 ID
 * @param primaryItems   {@code primary_items} 引用的物品标签 ID，没有的时候为 {@code null}
 * @param exclusiveSet   {@code exclusive_set} 引用的附魔标签 ID，没有的时候为 {@code null}
 * @param effects        效果键和按该键已注册编解码器解码出的值；谓词中的 JSON 已由编解码器处理
 */
public record EnchantmentMut(
        Component description,
        Key supportedItems,
        @Nullable Key primaryItems,
        @Nullable Key exclusiveSet,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        List<EquipmentSlotGroup> slots,
        List<Map.Entry<DataComponent<?>, Object>> effects
) {
    public EnchantmentMut {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(supportedItems, "supportedItems");
        Objects.requireNonNull(minCost, "minCost");
        Objects.requireNonNull(maxCost, "maxCost");
        slots = List.copyOf(slots);
        effects = List.copyOf(effects);
    }

    /**
     * 交给 {@link Enchantment.Builder} 组装注册表条目。效果值的类型在解码的时候已经按组件检查过。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    Enchantment build(RegistryTag<Material> supportedItems,
                      @Nullable RegistryTag<Material> primaryItems,
                      @Nullable RegistryTag<Enchantment> exclusiveSet) {
        var builder = Enchantment.builder()
                .description(description)
                .supportedItems(supportedItems)
                .exclusiveSet(Objects.requireNonNullElseGet(exclusiveSet, RegistryTag::empty))
                .weight(weight)
                .maxLevel(maxLevel)
                .minCost(minCost)
                .maxCost(maxCost)
                .anvilCost(anvilCost)
                .slots(slots);
        if (primaryItems != null) {
            builder.primaryItems(primaryItems);
        }
        for (var effect : effects) {
            builder.effect((DataComponent) effect.getKey(), effect.getValue());
        }
        return builder.build();
    }
}
