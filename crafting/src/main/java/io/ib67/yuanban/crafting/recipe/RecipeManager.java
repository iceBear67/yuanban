package io.ib67.yuanban.crafting.recipe;

import io.ib67.yuanban.crafting.MapDataAccess;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.DeclareRecipesPacket;
import net.minestom.server.network.packet.server.play.RecipeBookAddPacket;
import net.minestom.server.recipe.Ingredient;
import net.minestom.server.recipe.RecipeProperty;
import net.minestom.server.registry.Registries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 原版 {@code RecipeManager}：持有当前的全部配方，按类型查找匹配的配方，并把配方书和配方数据发给客户端。
 * <p>
 * 由 {@link io.ib67.yuanban.crafting.Crafting} 创建。{@link #replaceRecipes} 一次算好全部派生数据并整体替换，
 * 查询方法可以在任何线程调用。
 */
public final class RecipeManager {
    private final ServerProcess process;
    private final RecipeContext context;
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public RecipeManager(ServerProcess process, MapDataAccess maps) {
        this.process = Objects.requireNonNull(process, "process");
        Objects.requireNonNull(maps, "maps");
        this.context = new RecipeContext() {
            @Override
            public Registries registries() {
                return process;
            }

            @Override
            public MapDataAccess maps() {
                return maps;
            }
        };
    }

    /**
     * 配方匹配和产出时使用的游戏状态。
     */
    public RecipeContext context() {
        return context;
    }

    /**
     * 替换全部配方，然后给全部在线玩家发送新的配方数据和配方书，顺序与原版 {@code PlayerList.reloadResources} 相同。
     * 标签原料引用 Minestom 注册表中的标签，调用之前物品标签必须已经提交。
     */
    public void replaceRecipes(NavigableMap<Key, Recipe<?>> recipes) {
        var next = Snapshot.build(recipes);
        snapshot = next;
        for (var player : process.connection().getOnlinePlayers()) {
            next.sendTo(player);
        }
    }

    /**
     * 给一个玩家发送当前的配方数据和替换整个配方书的条目。
     */
    public void sendRecipes(Player player) {
        snapshot.sendTo(player);
    }

    /**
     * 当前的全部配方，按配方 ID 的自然顺序排列。
     */
    public NavigableMap<Key, Recipe<?>> recipes() {
        return snapshot.recipes;
    }

    public Optional<RecipeHolder<?>> byId(Key id) {
        return Optional.ofNullable(snapshot.byId.get(id));
    }

    /**
     * 原版 {@code RecipeManager.getRecipeFor}：配方提示属于这个类型并且匹配时直接返回；否则输入为空时没有结果，
     * 再否则返回配方 ID 最小的匹配配方。
     */
    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, R extends Recipe<I>> Optional<RecipeHolder<R>> getRecipeFor(RecipeType<R> type, I input, @Nullable Key hint) {
        var current = snapshot;
        if (hint != null) {
            var hinted = current.byId.get(hint);
            if (hinted != null && hinted.value().type() == type && ((R) hinted.value()).matches(input, context)) {
                return Optional.of((RecipeHolder<R>) hinted);
            }
        }
        if (input.isEmpty()) {
            return Optional.empty();
        }
        for (var holder : current.byType.getOrDefault(type, List.of())) {
            if (((R) holder.value()).matches(input, context)) {
                return Optional.of((RecipeHolder<R>) holder);
            }
        }
        return Optional.empty();
    }

    /**
     * 配方书条目 ID 对应的条目与配方。
     */
    public Optional<RecipeDisplayInfo> displayInfo(int displayId) {
        var displays = snapshot.displays;
        return displayId >= 0 && displayId < displays.size() ? Optional.of(displays.get(displayId)) : Optional.empty();
    }

    private record Snapshot(NavigableMap<Key, Recipe<?>> recipes, Map<Key, RecipeHolder<?>> byId,
                            Map<RecipeType<?>, List<RecipeHolder<?>>> byType, List<RecipeDisplayInfo> displays,
                            DeclareRecipesPacket declareRecipes, RecipeBookAddPacket recipeBook) {
        static final Snapshot EMPTY = build(new TreeMap<>());

        /**
         * 原版 {@code RecipeMap.create} 与 {@code RecipeManager.finalizeRecipeLoading}。
         */
        // FIXME(Minestom M7): Material 没有所需特性开关数据，显示数据、物品属性和切石机列表不按特性开关过滤；
        //  26.2 中过滤不改变结果，见 MINESTOM-BUG-REPORT.md
        static Snapshot build(NavigableMap<Key, Recipe<?>> source) {
            var recipes = Collections.unmodifiableNavigableMap(new TreeMap<>(source));
            var byId = new HashMap<Key, RecipeHolder<?>>();
            var byType = new LinkedHashMap<RecipeType<?>, List<RecipeHolder<?>>>();
            var properties = new EnumMap<RecipeProperty, Set<Material>>(RecipeProperty.class);
            for (var property : RecipeProperty.values()) {
                properties.put(property, new LinkedHashSet<>());
            }
            var stonecutter = new ArrayList<DeclareRecipesPacket.StonecutterRecipe>();
            recipes.forEach((id, recipe) -> {
                var holder = new RecipeHolder<>(id, recipe);
                byId.put(id, holder);
                byType.computeIfAbsent(recipe.type(), _ -> new ArrayList<>()).add(holder);
                // 原版：不能放置的非特殊配方不进入物品属性集合和切石机列表
                if (!recipe.isSpecial() && recipe.placementInfo().isImpossibleToPlace()) {
                    return;
                }
                collectProperties(recipe, properties);
                if (recipe instanceof StonecutterRecipe stonecutterRecipe) {
                    stonecutter.add(new DeclareRecipesPacket.StonecutterRecipe(stonecutterRecipe.ingredient().toNetwork(), stonecutterRecipe.resultDisplay()));
                }
            });
            var displays = displays(recipes, byId);
            var propertyLists = new EnumMap<RecipeProperty, List<Material>>(RecipeProperty.class);
            properties.forEach((property, items) -> propertyLists.put(property, List.copyOf(items)));
            var declare = new DeclareRecipesPacket(propertyLists, stonecutter);
            var book = new RecipeBookAddPacket(displays.stream().map(RecipeDisplayInfo::toEntry).toList(), true);
            byType.replaceAll((_, holders) -> List.copyOf(holders));
            return new Snapshot(recipes, Map.copyOf(byId), Collections.unmodifiableMap(byType), List.copyOf(displays), declare, book);
        }

        /**
         * 原版 {@code RecipeManager.RECIPE_PROPERTY_SETS}。
         */
        private static void collectProperties(Recipe<?> recipe, Map<RecipeProperty, Set<Material>> properties) {
            switch (recipe) {
                case SmithingRecipe smithing -> {
                    smithing.additionIngredient().ifPresent(addition -> properties.get(RecipeProperty.SMITHING_ADDITION).addAll(addition.items()));
                    properties.get(RecipeProperty.SMITHING_BASE).addAll(smithing.baseIngredient().items());
                    smithing.templateIngredient().ifPresent(template -> properties.get(RecipeProperty.SMITHING_TEMPLATE).addAll(template.items()));
                }
                case CookingRecipe cooking -> properties.get(switch (cooking.kind()) {
                    case SMELTING -> RecipeProperty.FURNACE_INPUT;
                    case BLASTING -> RecipeProperty.BLAST_FURNACE_INPUT;
                    case SMOKING -> RecipeProperty.SMOKER_INPUT;
                    case CAMPFIRE_COOKING -> RecipeProperty.CAMPFIRE_INPUT;
                }).addAll(cooking.ingredient().items());
                case CraftingRecipe _, StonecutterRecipe _ -> {
                }
            }
        }

        /**
         * 原版 {@code RecipeManager.unpackRecipeInfo}：分组 ID 按非空分组字符串第一次出现的顺序分配。
         */
        private static List<RecipeDisplayInfo> displays(NavigableMap<Key, Recipe<?>> recipes, Map<Key, RecipeHolder<?>> byId) {
            var result = new ArrayList<RecipeDisplayInfo>();
            var groups = new HashMap<String, Integer>();
            recipes.forEach((id, recipe) -> {
                Integer group = recipe.group().isEmpty() ? null : groups.computeIfAbsent(recipe.group(), _ -> groups.size());
                List<Ingredient> requirements = recipe.isSpecial()
                        ? null
                        : recipe.placementInfo().ingredients().stream().map(ItemIngredient::toNetwork).toList();
                for (var display : recipe.display()) {
                    result.add(new RecipeDisplayInfo(result.size(), display, group, recipe.recipeBookCategory(), requirements, byId.get(id)));
                }
            });
            return result;
        }

        void sendTo(Player player) {
            player.sendPackets(declareRecipes, recipeBook);
        }
    }
}
