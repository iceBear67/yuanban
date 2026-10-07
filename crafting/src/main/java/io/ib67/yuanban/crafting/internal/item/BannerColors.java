package io.ib67.yuanban.crafting.internal.item;

import net.minestom.server.color.DyeColor;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 原版 {@code BannerItem}：原版 26.2 {@code Items} 中的 16 种旗帜物品与它们的底色。Minestom 的 {@code Material} 不区分物品类。
 */
@ApiStatus.Internal
public final class BannerColors {
    private static final Map<Material, DyeColor> COLORS = Map.ofEntries(
            Map.entry(Material.WHITE_BANNER, DyeColor.WHITE),
            Map.entry(Material.ORANGE_BANNER, DyeColor.ORANGE),
            Map.entry(Material.MAGENTA_BANNER, DyeColor.MAGENTA),
            Map.entry(Material.LIGHT_BLUE_BANNER, DyeColor.LIGHT_BLUE),
            Map.entry(Material.YELLOW_BANNER, DyeColor.YELLOW),
            Map.entry(Material.LIME_BANNER, DyeColor.LIME),
            Map.entry(Material.PINK_BANNER, DyeColor.PINK),
            Map.entry(Material.GRAY_BANNER, DyeColor.GRAY),
            Map.entry(Material.LIGHT_GRAY_BANNER, DyeColor.LIGHT_GRAY),
            Map.entry(Material.CYAN_BANNER, DyeColor.CYAN),
            Map.entry(Material.PURPLE_BANNER, DyeColor.PURPLE),
            Map.entry(Material.BLUE_BANNER, DyeColor.BLUE),
            Map.entry(Material.BROWN_BANNER, DyeColor.BROWN),
            Map.entry(Material.GREEN_BANNER, DyeColor.GREEN),
            Map.entry(Material.RED_BANNER, DyeColor.RED),
            Map.entry(Material.BLACK_BANNER, DyeColor.BLACK)
    );

    private BannerColors() {
    }

    /**
     * 旗帜物品的底色；不是旗帜物品时返回 {@code null}。
     */
    public static @Nullable DyeColor of(Material material) {
        return COLORS.get(material);
    }
}
