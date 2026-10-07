package io.ib67.yuanban.bot.inventory;

import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.observe.CursorChanged;
import io.ib67.yuanban.bot.observe.EntityEquipmentChanged;
import io.ib67.yuanban.bot.observe.HeldSlotChanged;
import io.ib67.yuanban.bot.observe.WindowClosed;
import io.ib67.yuanban.bot.observe.WindowOpened;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.network.packet.client.play.ClientCloseWindowPacket;
import net.minestom.server.network.packet.client.play.ClientHeldItemChangePacket;
import net.minestom.server.network.packet.server.play.CloseWindowPacket;
import net.minestom.server.network.packet.server.play.OpenHorseWindowPacket;
import net.minestom.server.network.packet.server.play.OpenWindowPacket;
import net.minestom.server.network.packet.server.play.SetCursorItemPacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryBotTest {
    @Test
    void clickAndCloseUseTheOpenWindow() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "clicker", new Pos(0.5, 0, 0.5));
            var mark = peer.mark();
            assertTrue(bot.inventory().leftClick(5).await(TestServer.TIMEOUT));
            var playerClick = peer.awaitSent(mark, ClientClickWindowPacket.class, TestServer.TIMEOUT);
            assertEquals(new ClientClickWindowPacket(0, 0, (short) 5, (byte) 0, ClientClickWindowPacket.ClickType.PICKUP,
                    Map.of(), ItemStack.Hash.AIR), playerClick);

            peer.push(new OpenWindowPacket(7, InventoryType.CHEST_1_ROW.getWindowType(), Component.text("box")));
            peer.push(new WindowItemsPacket(7, 3, Collections.nCopies(45, ItemStack.AIR), ItemStack.AIR));
            peer.barrier();
            assertEquals(7, bot.view().openWindow().windowId());
            mark = peer.mark();
            assertTrue(bot.inventory().swapWithHotbar(2, 4).await(TestServer.TIMEOUT));
            var chestClick = peer.awaitSent(mark, ClientClickWindowPacket.class, TestServer.TIMEOUT);
            assertEquals(new ClientClickWindowPacket(7, 3, (short) 2, (byte) 4, ClientClickWindowPacket.ClickType.SWAP,
                    Map.of(), ItemStack.Hash.AIR), chestClick);

            var closed = bot.observe(Observers.observations(WindowClosed.class));
            assertTrue(bot.inventory().close().await(TestServer.TIMEOUT));
            assertEquals(7, peer.awaitSent(mark, ClientCloseWindowPacket.class, TestServer.TIMEOUT).windowId());
            assertNull(bot.view().openWindow());
            closed.close();
            assertEquals(List.of(new WindowClosed(7)), closed.observer().observations());
        }
    }

    @Test
    void eachWindowKeepsItsOwnStateId() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "stateful", new Pos(0.5, 0, 0.5));
            var stone = ItemStack.of(Material.STONE);
            peer.push(new SetSlotPacket(0, 9, (short) 36, stone));
            // 原版每个菜单各有一个状态编号：新打开的窗口从 0 开始，同步到窗口 0 的修改不改变窗口 0 的状态编号
            peer.push(new OpenWindowPacket(7, InventoryType.CHEST_1_ROW.getWindowType(), Component.text("box")));
            peer.barrier();
            assertEquals(0, bot.view().openWindow().stateId());
            peer.push(new SetSlotPacket(7, 3, (short) 9, stone));
            peer.barrier();
            assertEquals(3, bot.view().openWindow().stateId());
            assertEquals(9, bot.view().inventory().stateId());
            assertEquals(stone, bot.view().inventory().slot(9));

            var mark = peer.mark();
            assertTrue(bot.inventory().leftClick(0).await(TestServer.TIMEOUT));
            assertEquals(3, peer.awaitSent(mark, ClientClickWindowPacket.class, TestServer.TIMEOUT).stateId());
            // 关闭之后点击窗口 0，使用窗口 0 自己的状态编号
            assertTrue(bot.inventory().close().await(TestServer.TIMEOUT));
            mark = peer.mark();
            assertTrue(bot.inventory().leftClick(36).await(TestServer.TIMEOUT));
            var click = peer.awaitSent(mark, ClientClickWindowPacket.class, TestServer.TIMEOUT);
            assertEquals(0, click.windowId());
            assertEquals(9, click.stateId());
        }
    }

    @Test
    void closeContainerIgnoresTheWindowId() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "closer", new Pos(0.5, 0, 0.5));
            var closed = bot.observe(Observers.observations(WindowClosed.class));
            peer.push(new OpenWindowPacket(7, InventoryType.CHEST_1_ROW.getWindowType(), Component.text("box")));
            // 原版 handleContainerClose 不看窗口编号，关闭当前打开的窗口
            peer.push(new CloseWindowPacket(3));
            peer.barrier();
            assertNull(bot.view().openWindow());
            closed.close();
            assertEquals(List.of(new WindowClosed(7)), closed.observer().observations());
        }
    }

    @Test
    void eachWindowKeepsItsOwnCursor() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "carrier", new Pos(0.5, 0, 0.5));
            var stone = ItemStack.of(Material.STONE);
            var dirt = ItemStack.of(Material.DIRT);
            var cursors = bot.observe(Observers.observations(CursorChanged.class));
            peer.push(new SetCursorItemPacket(stone));
            peer.push(new OpenWindowPacket(7, InventoryType.CHEST_1_ROW.getWindowType(), Component.text("box")));
            peer.barrier();
            // 原版每个菜单各有一个光标：新打开的窗口的光标为空
            assertEquals(ItemStack.AIR, bot.view().cursor());
            // 窗口 0 的 Set Container Content 只改变窗口 0 的光标
            peer.push(new WindowItemsPacket(0, 1, Collections.nCopies(46, ItemStack.AIR), dirt));
            peer.push(new SetCursorItemPacket(stone));
            peer.barrier();
            assertEquals(stone, bot.view().cursor());
            // 关闭窗口之后，光标为窗口 0 的光标
            peer.push(new CloseWindowPacket(7));
            peer.barrier();
            assertEquals(dirt, bot.view().cursor());
            cursors.close();
            assertEquals(List.of(new CursorChanged(ItemStack.AIR, stone), new CursorChanged(stone, ItemStack.AIR),
                    new CursorChanged(ItemStack.AIR, stone), new CursorChanged(stone, dirt)), cursors.observer().observations());
        }
    }

    @Test
    void slotsOutsideTheWindowEndTheBot() {
        var stone = ItemStack.of(Material.STONE);
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "outside", new Pos(0.5, 0, 0.5));
            // 窗口编号不是窗口 0 也不是打开的窗口：原版客户端忽略
            peer.push(new SetSlotPacket(9, 1, (short) 100, stone));
            peer.push(new SetSlotPacket(0, 1, (short) 45, stone));
            peer.barrier();
            assertEquals(stone, bot.view().inventory().slot(45));
            // 原版 AbstractContainerMenu.getSlot 越界抛出异常，客户端断开连接
            peer.push(new SetSlotPacket(0, 2, (short) 46, stone));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IndexOutOfBoundsException.class, bot.failure());
        }
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "negative", new Pos(0.5, 0, 0.5));
            peer.push(new SetSlotPacket(0, 1, (short) -1, stone));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IndexOutOfBoundsException.class, bot.failure());
        }
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "overflow", new Pos(0.5, 0, 0.5));
            peer.push(new WindowItemsPacket(0, 1, Collections.nCopies(47, stone), ItemStack.AIR));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IndexOutOfBoundsException.class, bot.failure());
        }
    }

    @Test
    void playerInventoryIndicesBeyondWindowZeroAreTheBodyArmorAndSaddle() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "equipped", new Pos(0.5, 0, 0.5));
            var armor = ItemStack.of(Material.DIAMOND_HORSE_ARMOR);
            var saddle = ItemStack.of(Material.SADDLE);
            var changes = bot.observe(Observers.observations(EntityEquipmentChanged.class));
            // 原版 Inventory.setItem：41 为身体护甲，42 为鞍，大于 42 的下标被忽略
            peer.push(new SetPlayerInventorySlotPacket(41, armor), new SetPlayerInventorySlotPacket(42, saddle),
                    new SetPlayerInventorySlotPacket(43, armor));
            peer.barrier();
            assertEquals(armor, bot.view().equipment(EquipmentSlot.BODY));
            assertEquals(saddle, bot.view().equipment(EquipmentSlot.SADDLE));
            assertThrows(IllegalArgumentException.class, () -> bot.view().equipment(EquipmentSlot.HELMET));
            changes.close();
            assertEquals(List.of(new EntityEquipmentChanged(TestServer.ENTITY_ID, EquipmentSlot.BODY, ItemStack.AIR, armor),
                    new EntityEquipmentChanged(TestServer.ENTITY_ID, EquipmentSlot.SADDLE, ItemStack.AIR, saddle)), changes.observer().observations());
            // 下标为负数：原版写入物品列表时越界，客户端断开连接
            peer.push(new SetPlayerInventorySlotPacket(-1, armor));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IndexOutOfBoundsException.class, bot.failure());
        }
    }

    @Test
    void horseScreensNeedAHorseOrANautilusInTheModel() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "rider", new Pos(0.5, 0, 0.5));
            var opened = bot.observe(Observers.observations(WindowOpened.class));
            peer.push(new SpawnEntityPacket(100, UUID.randomUUID(), EntityType.PIG, new Pos(2.5, 0, 2.5), 0, 0, Vec.ZERO),
                    new SpawnEntityPacket(101, UUID.randomUUID(), EntityType.DONKEY, new Pos(2.5, 0, 4.5), 0, 0, Vec.ZERO),
                    new SpawnEntityPacket(102, UUID.randomUUID(), EntityType.NAUTILUS, new Pos(4.5, 0, 2.5), 0, 0, Vec.ZERO));
            // 原版 handleMountScreenOpen：实体不在模型中、或者不是马类与鹦鹉螺时不打开窗口
            peer.push(new OpenHorseWindowPacket(1, 3, 999), new OpenHorseWindowPacket(2, 3, 100));
            peer.barrier();
            assertNull(bot.view().openWindow());
            peer.push(new OpenHorseWindowPacket(3, 5, 101));
            peer.barrier();
            assertEquals(2 + 3 * 5 + 36, bot.view().openWindow().slots().size());
            // 打开着的窗口不受其它类型的实体影响
            peer.push(new OpenHorseWindowPacket(4, 3, 100));
            peer.barrier();
            assertEquals(3, bot.view().openWindow().windowId());
            // 鹦鹉螺的窗口只有鞍与身体护甲 2 格，不看列数（原版 NautilusInventoryMenu）
            peer.push(new OpenHorseWindowPacket(5, 3, 102));
            peer.barrier();
            assertEquals(5, bot.view().openWindow().windowId());
            assertEquals(2 + 36, bot.view().openWindow().slots().size());
            opened.close();
            assertEquals(List.of(3, 5), opened.observer().observations().stream().map(WindowOpened::windowId).toList());
        }
    }

    @Test
    void selectHotbarUpdatesTheHeldSlot() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "holder", new Pos(0.5, 0, 0.5));
            var changes = bot.observe(Observers.observations(HeldSlotChanged.class));
            var mark = peer.mark();
            assertTrue(bot.selectHotbar(3).await(TestServer.TIMEOUT));
            assertEquals(3, peer.awaitSent(mark, ClientHeldItemChangePacket.class, TestServer.TIMEOUT).slot());
            assertEquals(3, bot.view().player().heldSlot());
            changes.close();
            assertEquals(List.of(new HeldSlotChanged(0, 3)), changes.observer().observations());
        }
    }
}
