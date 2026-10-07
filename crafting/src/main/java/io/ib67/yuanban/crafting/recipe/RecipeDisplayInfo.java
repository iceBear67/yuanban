package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.network.packet.server.play.RecipeBookAddPacket;
import net.minestom.server.recipe.Ingredient;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.display.RecipeDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code RecipeManager.ServerDisplayInfo}：配方书中的一个条目和它所属的配方。
 *
 * @param displayId            条目 ID，即全部配方的显示数据按配方顺序排列后的序号
 * @param group                分组 ID；配方的分组为空字符串时为 {@code null}
 * @param craftingRequirements 客户端判断材料是否足够时使用的原料；特殊配方为 {@code null}
 */
public record RecipeDisplayInfo(int displayId, RecipeDisplay display, @Nullable Integer group, RecipeBookCategory category,
                                @Nullable List<Ingredient> craftingRequirements, RecipeHolder<?> parent) {
    public RecipeDisplayInfo {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(parent, "parent");
        craftingRequirements = craftingRequirements == null ? null : List.copyOf(craftingRequirements);
    }

    /**
     * 配方书条目。全部玩家已知全部配方，条目不提示、不高亮。
     */
    public RecipeBookAddPacket.Entry toEntry() {
        return new RecipeBookAddPacket.Entry(displayId, display, group, category, craftingRequirements, false, false);
    }
}
