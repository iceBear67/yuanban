package io.ib67.yuanban.bot.observe;

import net.kyori.adventure.text.Component;
import net.minestom.server.inventory.InventoryType;
import org.jetbrains.annotations.Nullable;

/**
 * Open Screen 或 Open Horse Screen。
 *
 * @param type 窗口类型；马与鹦鹉螺的物品栏没有对应的 {@link InventoryType}，为 null
 */
public record WindowOpened(int windowId, @Nullable InventoryType type, Component title) implements Observation {
}
