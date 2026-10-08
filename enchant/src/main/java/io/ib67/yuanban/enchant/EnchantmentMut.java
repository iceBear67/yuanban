package io.ib67.yuanban.enchant;

import io.ib67.yuanban.codec.HolderSet;
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
 * <p>
 * 三个物品或附魔集合字段与原版相同，可以写成 {@code #标签}、单个 ID 或者 ID 列表，
 * 分别解码为 {@link HolderSet.Named} 和 {@link HolderSet.Direct}。
 *
 * @param supportedItems {@code supported_items}
 * @param primaryItems   {@code primary_items}，没有这个字段的时候为 {@code null}
 * @param exclusiveSet   {@code exclusive_set}，没有这个字段的时候为空的 {@link HolderSet.Direct}，与原版 {@code HolderSet.empty()} 相同
 * @param effects        效果组件和按该组件的编解码器解码出的值，保持 JSON 中键的顺序
 */
public record EnchantmentMut(
        Component description,
        HolderSet<Material> supportedItems,
        @Nullable HolderSet<Material> primaryItems,
        HolderSet<Enchantment> exclusiveSet,
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
        Objects.requireNonNull(exclusiveSet, "exclusiveSet");
        Objects.requireNonNull(minCost, "minCost");
        Objects.requireNonNull(maxCost, "maxCost");
        slots = List.copyOf(slots);
        effects = List.copyOf(effects);
    }

    /**
     * 交给 {@link Enchantment.Builder} 组装注册表条目。效果值的类型在解码的时候已经按组件检查过。
     * {@code primaryItems} 为 {@code null} 时也传给构造器：构造器的缺省值是空标签，
     * Minestom 会把它同步给客户端，使任何物品都不算主要物品；原版在这个字段缺失时把全部支持的物品都当作主要物品。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    Enchantment build(RegistryTag<Material> supportedItems,
                      @Nullable RegistryTag<Material> primaryItems,
                      RegistryTag<Enchantment> exclusiveSet) {
        var builder = Enchantment.builder()
                .description(description)
                .supportedItems(supportedItems)
                .primaryItems(primaryItems)
                .exclusiveSet(exclusiveSet)
                .weight(weight)
                .maxLevel(maxLevel)
                .minCost(minCost)
                .maxCost(maxCost)
                .anvilCost(anvilCost)
                .slots(slots);
        for (var effect : effects) {
            builder.effect((DataComponent) effect.getKey(), effect.getValue());
        }
        return builder.build();
    }
}
