package io.ib67.yuanban.loot.slot;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContextArg;
import io.ib67.yuanban.loot.predicate.ItemPredicate;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 槽位来源：{@code slots} 条目从中取出物品，对应原版 {@code net.minecraft.world.item.slot.SlotSource}。
 * 六种子类型与原版 {@code SlotSources} 登记的类型一一对应：{@code group}、{@code filtered}、
 * {@code limit_slots}、{@code slot_range}、{@code contents}、{@code empty}。
 */
public sealed interface SlotSource {
    /**
     * 求值时要求上下文提供的参数，对应原版 {@code LootContextUser#getReferencedContextParams}；
     * 只有 {@code slot_range} 引用参数，嵌套的来源由外层逐个检查。
     */
    default Set<ContextKey<?>> referencedParams() {
        return Set.of();
    }

    /**
     * 原版 {@code GroupSlotSource}：按顺序连接各来源的物品。JSON 字段 {@code terms}，也可以直接写成来源列表。
     */
    record Group(List<SlotSource> terms) implements SlotSource {
        public Group {
            terms = List.copyOf(terms);
        }
    }

    /**
     * 原版 {@code FilteredSlotSource}：只保留满足 {@code item_filter} 的物品。
     */
    record Filtered(SlotSource slotSource, ItemPredicate itemFilter) implements SlotSource {
        public Filtered {
            Objects.requireNonNull(slotSource, "slotSource");
            Objects.requireNonNull(itemFilter, "itemFilter");
        }
    }

    /**
     * 原版 {@code LimitSlotSource}：最多取前 {@code limit} 个物品，{@code limit} 为正数。
     */
    record LimitSlots(SlotSource slotSource, int limit) implements SlotSource {
        public LimitSlots {
            Objects.requireNonNull(slotSource, "slotSource");
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive: " + limit);
            }
        }
    }

    /**
     * 原版 {@code RangeSlotSource}：读取上下文实体或方块实体在 {@code slots} 中的槽位。
     */
    record SlotRangeSource(LootContextArg source, SlotRange slots) implements SlotSource {
        public SlotRangeSource {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(slots, "slots");
        }

        /**
         * 对应原版 {@code RangeSlotSource#getReferencedContextParams}。
         */
        @Override
        public Set<ContextKey<?>> referencedParams() {
            return Set.of(source.contextParam());
        }
    }

    /**
     * 原版 {@code ContentsSlotSource}：把每个物品换成它的容器组件中装着的物品。
     */
    record Contents(SlotSource slotSource, ContainerComponent component) implements SlotSource {
        public Contents {
            Objects.requireNonNull(slotSource, "slotSource");
            Objects.requireNonNull(component, "component");
        }
    }

    /**
     * 原版 {@code EmptySlotSource}：不提供任何物品。
     */
    record Empty() implements SlotSource {
        public static final Empty INSTANCE = new Empty();
    }
}
