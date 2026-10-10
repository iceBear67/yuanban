package io.ib67.yuanban.bot.data;

import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * 读取物品的组件与比较物品。MCProtocolLib 的 {@link ItemStack} 只带有相对默认组件的补丁，空槽位为 null。
 */
public final class Items {
    private Items() {
    }

    /**
     * 物品实际的组件：补丁中有这个组件时取补丁中的值（值为 null 表示补丁移除了它），否则取默认组件。
     * 只支持 {@link ItemData#prototype} 转换的五种组件与 {@link DataComponentTypes#CAN_BREAK}（原版物品都没有这个默认组件）
     *
     * @param item 物品；为 null（空槽位）时没有任何组件
     * @throws IllegalArgumentException 不支持的组件
     */
    public static <T> @Nullable T component(@Nullable ItemStack item, DataComponentType<T> type) {
        if (item == null) {
            return null;
        }
        var patch = item.getDataComponentsPatch();
        if (patch != null && patch.contains(type)) {
            return patch.get(type);
        }
        if (type == DataComponentTypes.CAN_BREAK) {
            return null;
        }
        return ItemData.prototype(item.getId(), type);
    }

    public static boolean has(@Nullable ItemStack item, DataComponentType<?> type) {
        return component(item, type) != null;
    }

    /**
     * 原版 {@code ItemStack.isSameItemSameComponents}：同一物品并且组件相同，不比较数量。两个都为空时相同
     */
    // TODO(T3): 原版解码物品时去掉补丁中与默认组件相同的条目，这里按原始补丁比较，服务端发来多余条目时结果与原版不同
    public static boolean sameItemSameComponents(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.getId() == b.getId() && patch(a).equals(patch(b));
    }

    /**
     * 同一物品、组件相同并且数量相同。两个都为空时相同
     */
    public static boolean sameStack(@Nullable ItemStack a, @Nullable ItemStack b) {
        return sameItemSameComponents(a, b) && (a == null || a.getAmount() == Objects.requireNonNull(b).getAmount());
    }

    /**
     * 补丁的内容；没有补丁时为空表，所以 null 补丁与空补丁相同
     */
    private static Map<?, ?> patch(ItemStack item) {
        DataComponents patch = item.getDataComponentsPatch();
        return patch == null ? Map.of() : patch.getDataComponents();
    }
}
