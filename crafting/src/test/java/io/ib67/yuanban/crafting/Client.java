package io.ib67.yuanban.crafting;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket.ClickType;
import net.minestom.server.network.packet.client.play.ClientCloseWindowPacket;
import net.minestom.server.network.packet.client.play.ClientPlaceRecipePacket;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;

import java.util.Map;

/**
 * 以客户端的身份给服务端发送数据包，数据包经过 Minestom 真实的处理流程。格子编号是协议中的窗口格子编号。
 */
final class Client {
    private final Player player;
    private int sequence;

    Client(Player player) {
        this.player = player;
    }

    void send(ClientPacket packet) {
        player.addPacketToQueue(packet);
        player.interpretPacketQueue();
    }

    void useBlock(Point position, PlayerHand hand) {
        send(new ClientPlayerBlockPlacementPacket(hand, position, BlockFace.TOP, 0.5F, 1.0F, 0.5F, false, false, ++sequence));
    }

    void click(int windowId, int slot, int button, ClickType type) {
        send(new ClientClickWindowPacket(windowId, 0, (short) slot, (byte) button, type, Map.of(), ItemStack.Hash.AIR));
    }

    void leftClick(int windowId, int slot) {
        click(windowId, slot, 0, ClickType.PICKUP);
    }

    void rightClick(int windowId, int slot) {
        click(windowId, slot, 1, ClickType.PICKUP);
    }

    void shiftClick(int windowId, int slot) {
        click(windowId, slot, 0, ClickType.QUICK_MOVE);
    }

    void doubleClick(int windowId, int slot) {
        click(windowId, slot, 0, ClickType.PICKUP_ALL);
    }

    /**
     * 数字键（0–8）或 F 键（40）交换。
     */
    void swap(int windowId, int slot, int key) {
        click(windowId, slot, key, ClickType.SWAP);
    }

    /**
     * 丢弃键，{@code all} 为真时同时按住 Ctrl。
     */
    void drop(int windowId, int slot, boolean all) {
        click(windowId, slot, all ? 1 : 0, ClickType.THROW);
    }

    /**
     * 拖动：{@code type} 为 0（左键）、1（右键）或 2（中键）。
     */
    void drag(int windowId, int type, int... slots) {
        click(windowId, -999, type * 4, ClickType.QUICK_CRAFT);
        for (int slot : slots) {
            click(windowId, slot, type * 4 + 1, ClickType.QUICK_CRAFT);
        }
        click(windowId, -999, type * 4 + 2, ClickType.QUICK_CRAFT);
    }

    void close(int windowId) {
        send(new ClientCloseWindowPacket(windowId));
    }

    void placeRecipe(int windowId, int displayId, boolean makeAll) {
        send(new ClientPlaceRecipePacket((byte) windowId, displayId, makeAll));
    }
}
