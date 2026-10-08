package io.ib67.yuanban.codec;

import net.minestom.server.component.DataComponentMap;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.Objects;

/**
 * 原版 {@code net.minecraft.world.item.ItemStackTemplate}：物品、数量和组件补丁。补丁按解码的内容原样保存，
 * 与原版相同；转换成 {@link ItemStack} 时 Minestom 才去掉与物品原型相同的部分。
 * 编解码器见 {@link ItemCodecs#ITEM_STACK_TEMPLATE}。
 *
 * @param item       物品，不能是空气
 * @param count      数量，不能为 0
 * @param components 组件补丁，可以含有移除标记
 */
public record ItemStackTemplate(Material item, int count, DataComponentMap components) {
    public ItemStackTemplate {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(components, "components");
        if (count == 0 || item == Material.AIR) {
            throw new IllegalStateException("Item must be non-empty");
        }
    }

    /**
     * 原版 {@code new ItemStack(item, count, components)}：只构造物品堆，不进行原版 {@code ItemStackTemplate.create()}
     * 中的 {@code ItemStack.validateStrict} 检查。
     */
    public ItemStack asItemStack() {
        return ItemStack.of(item, count, components);
    }
}
