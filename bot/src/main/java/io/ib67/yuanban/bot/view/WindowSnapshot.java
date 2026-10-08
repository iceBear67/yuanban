package io.ib67.yuanban.bot.view;

import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundMerchantOffersPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 一个窗口的快照。一般的窗口排列为：独有槽位，然后主物品栏 27 格，然后快捷栏 9 格；
 * 合成器的结果槽位排在快捷栏之后，讲台不包含玩家物品栏；窗口 0 的排列见 wiki Inventory。
 *
 * @param type       窗口类型；窗口 0 与马、鹦鹉螺的物品栏为 null
 * @param stateId    这个窗口最近收到的状态编号（每个窗口各自记录，新打开的窗口从 0 开始）
 * @param slots      各槽位的物品，空槽位为 null
 * @param properties Set Container Property 给出的属性
 * @param trades     Merchant Offers 给出的交易；没有收到时为 null
 */
public record WindowSnapshot(int windowId, @Nullable ContainerType type, Component title, int stateId,
                             List<@Nullable ItemStack> slots, Map<Integer, Integer> properties,
                             @Nullable ClientboundMerchantOffersPacket trades) {
    public WindowSnapshot {
        slots = Collections.unmodifiableList(new ArrayList<>(slots));
        properties = Map.copyOf(properties);
    }

    /**
     * @return 槽位中的物品；空槽位为 null
     */
    public @Nullable ItemStack slot(int index) {
        return slots.get(index);
    }
}
