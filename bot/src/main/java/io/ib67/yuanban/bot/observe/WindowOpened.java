package io.ib67.yuanban.bot.observe;

import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.jetbrains.annotations.Nullable;

/**
 * Open Screen 或 Open Horse Screen。
 *
 * @param type 窗口类型；马与鹦鹉螺的物品栏没有对应的 {@link ContainerType}，为 null
 */
public record WindowOpened(int windowId, @Nullable ContainerType type, Component title) implements Observation {
}
