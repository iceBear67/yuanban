package io.ib67.yuanban.crafting.recipe;

import io.ib67.yuanban.crafting.MapDataAccess;
import net.minestom.server.registry.Registries;

/**
 * 配方匹配和产出时可以读取的游戏状态，对应原版传给配方的 {@code Level}。
 */
public interface RecipeContext {
    Registries registries();

    MapDataAccess maps();
}
