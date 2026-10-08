package io.ib67.yuanban.bot.inventory;

import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.data.ItemData;
import io.ib67.yuanban.bot.observe.Observation.CursorChanged;
import io.ib67.yuanban.bot.observe.Observation.EntityEquipmentChanged;
import io.ib67.yuanban.bot.observe.Observation.HeldSlotChanged;
import io.ib67.yuanban.bot.observe.Observation.WindowClosed;
import io.ib67.yuanban.bot.observe.Observation.WindowOpened;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundMountScreenOpenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetCursorItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryBotTest {
    private static ItemStack item(String name) {
        return new ItemStack(ItemData.id(Key.key(name)));
    }

    /**
     * Set Container Content 的物品数组：count 个槽位都放 item
     */
    private static ItemStack[] items(int count, ItemStack item) {
        var items = new ItemStack[count];
        Arrays.fill(items, item);
        return items;
    }

    @Test
    void clickAndCloseUseTheOpenWindow() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "clicker", new Pos(0.5, 0, 0.5));
            var mark = peer.mark();
            assertTrue(bot.inventory().leftClick(5).await(TestServer.TIMEOUT));
            var playerClick = peer.awaitSent(mark, ServerboundContainerClickPacket.class, TestServer.TIMEOUT);
            assertEquals(new ServerboundContainerClickPacket(0, 0, 5, ContainerActionType.CLICK_ITEM, ClickItemAction.LEFT_CLICK,
                    null, Map.of()), playerClick);

            peer.push(new ClientboundOpenScreenPacket(7, ContainerType.GENERIC_9X1, Component.text("box")));
            peer.push(new ClientboundContainerSetContentPacket(7, 3, new ItemStack[45], null));
            peer.barrier();
            assertEquals(7, bot.view().openWindow().windowId());
            mark = peer.mark();
            assertTrue(bot.inventory().swapWithHotbar(2, 4).await(TestServer.TIMEOUT));
            var chestClick = peer.awaitSent(mark, ServerboundContainerClickPacket.class, TestServer.TIMEOUT);
            assertEquals(new ServerboundContainerClickPacket(7, 3, 2, ContainerActionType.MOVE_TO_HOTBAR_SLOT,
                    MoveToHotbarAction.SLOT_5, null, Map.of()), chestClick);

            var closed = bot.observe(Observers.observations(WindowClosed.class));
            assertTrue(bot.inventory().close().await(TestServer.TIMEOUT));
            assertEquals(7, peer.awaitSent(mark, ServerboundContainerClosePacket.class, TestServer.TIMEOUT).getContainerId());
            assertNull(bot.view().openWindow());
            closed.close();
            assertEquals(List.of(new WindowClosed(7)), closed.observer().observations());
        }
    }

    @Test
    void clickRejectsAButtonOfAnotherMode() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "mismatch", new Pos(0.5, 0, 0.5));
            // 按键的类型必须与模式对应，在提交请求的线程上检查
            assertThrows(IllegalArgumentException.class,
                    () -> bot.inventory().click(5, ContainerActionType.SHIFT_CLICK_ITEM, ClickItemAction.LEFT_CLICK));
        }
    }

    @Test
    void eachWindowKeepsItsOwnStateId() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "stateful", new Pos(0.5, 0, 0.5));
            var stone = item("stone");
            peer.push(new ClientboundContainerSetSlotPacket(0, 9, 36, stone));
            // 原版每个菜单各有一个状态编号：新打开的窗口从 0 开始，同步到窗口 0 的修改不改变窗口 0 的状态编号
            peer.push(new ClientboundOpenScreenPacket(7, ContainerType.GENERIC_9X1, Component.text("box")));
            peer.barrier();
            assertEquals(0, bot.view().openWindow().stateId());
            peer.push(new ClientboundContainerSetSlotPacket(7, 3, 9, stone));
            peer.barrier();
            assertEquals(3, bot.view().openWindow().stateId());
            assertEquals(9, bot.view().inventory().stateId());
            assertEquals(stone, bot.view().inventory().slot(9));

            var mark = peer.mark();
            assertTrue(bot.inventory().leftClick(0).await(TestServer.TIMEOUT));
            assertEquals(3, peer.awaitSent(mark, ServerboundContainerClickPacket.class, TestServer.TIMEOUT).getStateId());
            // 关闭之后点击窗口 0，使用窗口 0 自己的状态编号
            assertTrue(bot.inventory().close().await(TestServer.TIMEOUT));
            mark = peer.mark();
            assertTrue(bot.inventory().leftClick(36).await(TestServer.TIMEOUT));
            var click = peer.awaitSent(mark, ServerboundContainerClickPacket.class, TestServer.TIMEOUT);
            assertEquals(0, click.getContainerId());
            assertEquals(9, click.getStateId());
        }
    }

    @Test
    void closeContainerIgnoresTheWindowId() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "closer", new Pos(0.5, 0, 0.5));
            var closed = bot.observe(Observers.observations(WindowClosed.class));
            peer.push(new ClientboundOpenScreenPacket(7, ContainerType.GENERIC_9X1, Component.text("box")));
            // 原版 handleContainerClose 不看窗口编号，关闭当前打开的窗口
            peer.push(new ClientboundContainerClosePacket(3));
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
            var stone = item("stone");
            var dirt = item("dirt");
            var cursors = bot.observe(Observers.observations(CursorChanged.class));
            peer.push(new ClientboundSetCursorItemPacket(stone));
            peer.push(new ClientboundOpenScreenPacket(7, ContainerType.GENERIC_9X1, Component.text("box")));
            peer.barrier();
            // 原版每个菜单各有一个光标：新打开的窗口的光标为空
            assertNull(bot.view().cursor());
            // 窗口 0 的 Set Container Content 只改变窗口 0 的光标
            peer.push(new ClientboundContainerSetContentPacket(0, 1, new ItemStack[46], dirt));
            peer.push(new ClientboundSetCursorItemPacket(stone));
            peer.barrier();
            assertEquals(stone, bot.view().cursor());
            // 关闭窗口之后，光标为窗口 0 的光标
            peer.push(new ClientboundContainerClosePacket(7));
            peer.barrier();
            assertEquals(dirt, bot.view().cursor());
            cursors.close();
            assertEquals(List.of(new CursorChanged(null, stone), new CursorChanged(stone, null),
                    new CursorChanged(null, stone), new CursorChanged(stone, dirt)), cursors.observer().observations());
        }
    }

    @Test
    void slotsOutsideTheWindowEndTheBot() {
        var stone = item("stone");
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "outside", new Pos(0.5, 0, 0.5));
            // 窗口编号不是窗口 0 也不是打开的窗口：原版客户端忽略
            peer.push(new ClientboundContainerSetSlotPacket(9, 1, 100, stone));
            peer.push(new ClientboundContainerSetSlotPacket(0, 1, 45, stone));
            peer.barrier();
            assertEquals(stone, bot.view().inventory().slot(45));
            // 原版 AbstractContainerMenu.getSlot 越界抛出异常，客户端断开连接
            peer.push(new ClientboundContainerSetSlotPacket(0, 2, 46, stone));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IndexOutOfBoundsException.class, bot.failure());
        }
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "negative", new Pos(0.5, 0, 0.5));
            peer.push(new ClientboundContainerSetSlotPacket(0, 1, -1, stone));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IndexOutOfBoundsException.class, bot.failure());
        }
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "overflow", new Pos(0.5, 0, 0.5));
            peer.push(new ClientboundContainerSetContentPacket(0, 1, items(47, stone), null));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IndexOutOfBoundsException.class, bot.failure());
        }
    }

    @Test
    void playerInventoryIndicesBeyondWindowZeroAreTheBodyArmorAndSaddle() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "equipped", new Pos(0.5, 0, 0.5));
            var armor = item("diamond_horse_armor");
            var saddle = item("saddle");
            var changes = bot.observe(Observers.observations(EntityEquipmentChanged.class));
            // 原版 Inventory.setItem：41 为身体护甲，42 为鞍，大于 42 的下标被忽略
            peer.push(new ClientboundSetPlayerInventoryPacket(41, armor), new ClientboundSetPlayerInventoryPacket(42, saddle),
                    new ClientboundSetPlayerInventoryPacket(43, armor));
            peer.barrier();
            assertEquals(armor, bot.view().equipment(EquipmentSlot.BODY));
            assertEquals(saddle, bot.view().equipment(EquipmentSlot.SADDLE));
            assertThrows(IllegalArgumentException.class, () -> bot.view().equipment(EquipmentSlot.HELMET));
            changes.close();
            assertEquals(List.of(new EntityEquipmentChanged(TestServer.ENTITY_ID, EquipmentSlot.BODY, null, armor),
                    new EntityEquipmentChanged(TestServer.ENTITY_ID, EquipmentSlot.SADDLE, null, saddle)), changes.observer().observations());
            // 下标为负数：原版写入物品列表时越界，客户端断开连接
            peer.push(new ClientboundSetPlayerInventoryPacket(-1, armor));
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
            peer.push(new ClientboundAddEntityPacket(100, UUID.randomUUID(), EntityType.PIG, 2.5, 0, 2.5, 0, 0, 0),
                    new ClientboundAddEntityPacket(101, UUID.randomUUID(), EntityType.DONKEY, 2.5, 0, 4.5, 0, 0, 0),
                    new ClientboundAddEntityPacket(102, UUID.randomUUID(), EntityType.NAUTILUS, 4.5, 0, 2.5, 0, 0, 0));
            // 原版 handleMountScreenOpen：实体不在模型中、或者不是马类与鹦鹉螺时不打开窗口
            peer.push(new ClientboundMountScreenOpenPacket(1, 3, 999), new ClientboundMountScreenOpenPacket(2, 3, 100));
            peer.barrier();
            assertNull(bot.view().openWindow());
            peer.push(new ClientboundMountScreenOpenPacket(3, 5, 101));
            peer.barrier();
            assertEquals(2 + 3 * 5 + 36, bot.view().openWindow().slots().size());
            // 打开着的窗口不受其它类型的实体影响
            peer.push(new ClientboundMountScreenOpenPacket(4, 3, 100));
            peer.barrier();
            assertEquals(3, bot.view().openWindow().windowId());
            // 鹦鹉螺的窗口只有鞍与身体护甲 2 格，不看列数（原版 NautilusInventoryMenu）
            peer.push(new ClientboundMountScreenOpenPacket(5, 3, 102));
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
            assertEquals(3, peer.awaitSent(mark, ServerboundSetCarriedItemPacket.class, TestServer.TIMEOUT).getSlot());
            assertEquals(3, bot.view().player().heldSlot());
            changes.close();
            assertEquals(List.of(new HeldSlotChanged(0, 3)), changes.observer().observations());
        }
    }
}
