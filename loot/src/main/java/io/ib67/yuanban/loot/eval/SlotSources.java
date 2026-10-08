package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.context.LootContext;
import io.ib67.yuanban.loot.context.LootContextArg;
import io.ib67.yuanban.loot.slot.SlotSource;
import net.minestom.server.ServerProcess;
import net.minestom.server.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版 {@code SlotSource#provide} 与 {@code SlotCollection} 的求值：结果是槽位中物品的列表，按槽位的顺序。
 */
final class SlotSources {
    private SlotSources() {
    }

    static List<ItemStack> provide(SlotSource source, LootContext context, ServerProcess process) {
        return switch (source) {
            case SlotSource.Group group -> {
                var result = new ArrayList<ItemStack>();
                for (var term : group.terms()) {
                    result.addAll(provide(term, context, process));
                }
                yield result;
            }
            case SlotSource.Filtered filtered -> provide(filtered.slotSource(), context, process).stream()
                    .filter(item -> ItemPredicates.test(filtered.itemFilter(), item, process))
                    .toList();
            case SlotSource.LimitSlots limit -> provide(limit.slotSource(), context, process).stream()
                    .limit(limit.limit())
                    .toList();
            case SlotSource.SlotRangeSource range -> fromRange(range, context);
            case SlotSource.Contents contents -> {
                var result = new ArrayList<ItemStack>();
                for (var item : provide(contents.slotSource(), context, process)) {
                    // 原版 ContainerComponentManipulator#getSlots：组件中不为空的物品
                    var items = item.get(contents.component().component());
                    if (items != null) {
                        for (var inner : items) {
                            if (!inner.isAir()) {
                                result.add(inner);
                            }
                        }
                    }
                }
                yield result;
            }
            case SlotSource.Empty ignored -> List.of();
        };
    }

    /**
     * 原版 {@code RangeSlotSource#provide}：来源是 {@code SlotProvider} 时取范围中存在的槽位，否则为空。
     */
    private static List<ItemStack> fromRange(SlotSource.SlotRangeSource range, LootContext context) {
        return switch (range.source()) {
            case LootContextArg.Entity target -> {
                var entity = LootEvaluation.entity(context, target.contextParam());
                if (entity == null) {
                    yield List.of();
                }
                var result = new ArrayList<ItemStack>();
                for (int slot : range.slots().slots()) {
                    var item = EntitySlots.get(entity, slot);
                    if (item != null) {
                        result.add(item);
                    }
                }
                yield result;
            }
            case LootContextArg.BlockEntity blockEntity -> {
                var block = LootEvaluation.block(context, blockEntity.contextParam());
                if (block == null) {
                    yield List.of();
                }
                // TODO(minestom): Minestom 的方块实体没有容器内容，原版的容器方块实体按槽位提供物品
                throw LootEvaluation.unsupported("slots of the block entity " + block.key().asString());
            }
            case LootContextArg.Tool ignored -> throw new IllegalStateException("slot_range does not accept the tool as source");
        };
    }
}
