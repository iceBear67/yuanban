package io.ib67.yuanban.recipe.internal;

import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.CachedPacket;
import net.minestom.server.recipe.RecipeManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;

/**
 * 绕过 Minestom 缺少的功能。Minestom 修复之后删除对应的方法。
 */
@ApiStatus.Internal
public final class MinestomWorkarounds {
    // FIXME(Minestom M6): Minestom 的 Material 没有合成剩余物数据，这里内置原版 26.2 Items 中的取值，见 MINESTOM-BUG-REPORT.md
    private static final Map<Material, Material> CRAFTING_REMAINDERS = Map.of(
            Material.WATER_BUCKET, Material.BUCKET,
            Material.LAVA_BUCKET, Material.BUCKET,
            Material.MILK_BUCKET, Material.BUCKET,
            Material.DRAGON_BREATH, Material.GLASS_BOTTLE,
            Material.HONEY_BOTTLE, Material.GLASS_BOTTLE
    );

    // FIXME(Minestom M1): RecipeManager 增删配方之后不刷新 DeclareRecipesPacket 的缓存，这里用反射读取私有字段，
    //  要求 Minestom 位于 classpath 上，见 MINESTOM-BUG-REPORT.md
    private static final VarHandle DECLARE_RECIPES_PACKET;

    static {
        try {
            DECLARE_RECIPES_PACKET = MethodHandles.privateLookupIn(RecipeManager.class, MethodHandles.lookup())
                    .findVarHandle(RecipeManager.class, "declareRecipesPacket", CachedPacket.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private MinestomWorkarounds() {
    }

    /**
     * 原版 {@code Item.getCraftingRemainder()}，没有剩余物的时候返回 {@code null}。
     */
    public static @Nullable Material craftingRemainder(Material material) {
        return CRAFTING_REMAINDERS.get(material);
    }

    /**
     * 让 {@link RecipeManager#getDeclareRecipesPacket()} 下一次读取的时候重新生成数据包。
     */
    public static void invalidateDeclareRecipes(RecipeManager manager) {
        ((CachedPacket) DECLARE_RECIPES_PACKET.get(manager)).invalidate();
    }
}
