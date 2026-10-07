package io.ib67.yuanban.crafting;

import io.ib67.yuanban.crafting.internal.menu.CraftingEvents;
import io.ib67.yuanban.crafting.recipe.RecipeManager;
import net.minestom.server.ServerProcess;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;

import java.util.Objects;

/**
 * 合成模块的入口：持有配方注册表，提供工作台、背包合成格和配方书放置的事件监听。
 * <p>
 * 宿主把 {@link #eventNode()} 加入 {@code process.eventHandler()}，再把 {@link #recipes()} 交给 {@code item-recipe} 的订阅者。
 */
public final class Crafting {
    private final RecipeManager recipes;
    private final EventNode<Event> eventNode;

    public Crafting(ServerProcess process, MapDataAccess maps) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(maps, "maps");
        this.recipes = new RecipeManager(process, maps);
        this.eventNode = CraftingEvents.create(recipes);
    }

    public RecipeManager recipes() {
        return recipes;
    }

    public EventNode<Event> eventNode() {
        return eventNode;
    }
}
