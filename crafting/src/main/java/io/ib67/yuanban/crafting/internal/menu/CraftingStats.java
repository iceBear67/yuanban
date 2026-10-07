package io.ib67.yuanban.crafting.internal.menu;

import io.ib67.yuanban.crafting.MapDataAccess;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.statistic.PlayerStatistic;
import net.minestom.server.statistic.StatisticCategory;
import net.minestom.server.statistic.StatisticType;

/**
 * 合成产生的统计和物品后处理：原版 {@code ItemStack.onCraftedBy}、{@code Item.onCraftedBy} 和 {@code MapItem.onCraftedPostProcess}。
 */
final class CraftingStats {
    private CraftingStats() {
    }

    /**
     * 原版 {@code ItemStack.onCraftedBy(Player, int)}：增加 {@code minecraft:crafted} 统计，再做物品的后处理。
     */
    static ItemStack onCraftedBy(Player player, ItemStack stack, int count, MapDataAccess maps) {
        award(player, StatisticCategory.CRAFTED, stack.material().id(), count);
        return postProcess(stack, maps);
    }

    /**
     * 原版 {@code CraftingTableBlock.useWithoutItem} 中的 {@code minecraft:interact_with_crafting_table}。
     */
    static void interactWithCraftingTable(Player player) {
        award(player, StatisticCategory.CUSTOM, StatisticType.INTERACT_WITH_CRAFTING_TABLE.id(), 1);
    }

    /**
     * 原版 {@code Item.onCraftedBy(ItemStack, Player)}：只有已绘制的地图（原版 {@code MapItem}）有后处理。
     * 移除 {@code map_post_processing}，放大时复制地图数据并换成新的地图 ID。
     */
    static ItemStack postProcess(ItemStack stack, MapDataAccess maps) {
        if (stack.material() != Material.FILLED_MAP) {
            return stack;
        }
        var processing = stack.get(DataComponents.MAP_POST_PROCESSING);
        if (processing == null) {
            return stack;
        }
        var result = stack.without(DataComponents.MAP_POST_PROCESSING);
        return switch (processing) {
            case SCALE -> {
                var mapId = result.get(DataComponents.MAP_ID);
                if (mapId == null) {
                    yield result;
                }
                var copy = maps.createScaledCopy(mapId);
                yield copy.isPresent() ? result.with(DataComponents.MAP_ID, copy.getAsInt()) : result;
            }
            // 锁定地图的后处理只来自制图台，合成不会产生
            case LOCK -> throw new IllegalStateException("map lock post-processing is not produced by crafting: " + stack);
        };
    }

    /**
     * 原版 {@code ServerStatsCounter.increment}：累加，上限为 {@link Integer#MAX_VALUE}。
     */
    // FIXME(Minestom M14): PlayerStatistic 没有 equals 和 hashCode，统计映射无法按键查找，这里按类别和 ID 逐项比较，
    //  见 MINESTOM-BUG-REPORT.md
    private static void award(Player player, StatisticCategory category, int id, int amount) {
        var statistics = player.getStatisticValueMap();
        for (var entry : statistics.entrySet()) {
            var key = entry.getKey();
            if (key.getCategory() == category && key.getStatisticId() == id) {
                entry.setValue((int) Math.min((long) entry.getValue() + amount, Integer.MAX_VALUE));
                return;
            }
        }
        statistics.put(new PlayerStatistic(category, id), amount);
    }
}
