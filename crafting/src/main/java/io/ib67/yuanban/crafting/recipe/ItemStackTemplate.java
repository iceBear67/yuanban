package io.ib67.yuanban.crafting.recipe;

import io.ib67.yuanban.crafting.internal.item.ItemValidation;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.Objects;

/**
 * 原版 {@code ItemStackTemplate}：配方的结果。{@code components} 原样保存配方文件中的组件补丁（包括移除条目），
 * 与物品原型相同的条目也保留，产出时用它覆盖输入物品上的同名组件。
 */
public record ItemStackTemplate(Material material, int count, DataComponentMap components) {
    public ItemStackTemplate {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(components, "components");
        if (count == 0 || material == Material.AIR) {
            throw new IllegalStateException("Item must be non-empty");
        }
    }

    public static ItemStackTemplate of(Material material) {
        return new ItemStackTemplate(material, 1, DataComponentMap.EMPTY);
    }

    public ItemStackTemplate withCount(int count) {
        return this.count == count ? this : new ItemStackTemplate(material, count, components);
    }

    /**
     * 原版 {@code ItemStackTemplate.create}：不合法时为 {@link ItemStack#AIR}。
     */
    public ItemStack create() {
        return ItemValidation.validateStrict(ItemStack.of(material, count, components));
    }

    /**
     * 原版 {@code ItemStackTemplate.apply(DataComponentPatch)}。
     */
    public ItemStack apply(DataComponentMap additionalPatch) {
        return apply(count, additionalPatch);
    }

    /**
     * 原版 {@code ItemStackTemplate.apply(int, DataComponentPatch)}：以 {@code additionalPatch} 建立物品，再逐项应用这个模板的补丁。
     * 不合法时为 {@link ItemStack#AIR}。
     */
    public ItemStack apply(int count, DataComponentMap additionalPatch) {
        var patch = additionalPatch;
        for (var entry : components.entrySet()) {
            patch = entry.value() == null ? patch.remove(entry.component()) : set(patch, entry.component(), entry.value());
        }
        return ItemValidation.validateStrict(ItemStack.of(material, count, patch));
    }

    /**
     * 原版 {@code TransmuteRecipe.createWithOriginalComponents}：输入物品的组件补丁加上模板的补丁，数量为模板数量加 {@code extraCount}。
     */
    public ItemStack applyOriginalComponents(ItemStack input, int extraCount) {
        return apply(count + extraCount, input.componentPatch());
    }

    /**
     * 显示数据中的物品。原版直接编码模板，不检查合法性。
     */
    public ItemStack toDisplayStack() {
        return ItemStack.of(material, count, components);
    }

    @SuppressWarnings("unchecked")
    private static DataComponentMap set(DataComponentMap patch, DataComponent<?> component, Object value) {
        return patch.set((DataComponent<Object>) component, value);
    }
}
