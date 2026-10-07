package io.ib67.yuanban.crafting.recipe.kind.crafting;

import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 原版 {@code Item.getCraftingRemainder()}：合成时留在合成格中的物品。
 */
public final class CraftingRemainders {
    // FIXME(Minestom M6): Minestom 的 Material 没有合成剩余物数据，这里内置原版 26.2 Items 中的取值，见 MINESTOM-BUG-REPORT.md
    private static final Map<Material, Material> REMAINDERS = Map.of(
            Material.WATER_BUCKET, Material.BUCKET,
            Material.LAVA_BUCKET, Material.BUCKET,
            Material.MILK_BUCKET, Material.BUCKET,
            Material.DRAGON_BREATH, Material.GLASS_BOTTLE,
            Material.HONEY_BOTTLE, Material.GLASS_BOTTLE
    );

    private CraftingRemainders() {
    }

    /**
     * 物品的合成剩余物，没有剩余物的时候返回 {@code null}。
     */
    public static @Nullable Material of(Material material) {
        return REMAINDERS.get(material);
    }
}
