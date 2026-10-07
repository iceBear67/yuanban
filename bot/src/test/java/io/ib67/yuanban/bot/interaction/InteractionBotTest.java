package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.movement.Rotation;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientAnimationPacket;
import net.minestom.server.network.packet.client.play.ClientAttackPacket;
import net.minestom.server.network.packet.client.play.ClientHeldItemChangePacket;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import net.minestom.server.network.packet.client.play.ClientTickEndPacket;
import net.minestom.server.network.packet.client.play.ClientUseItemPacket;
import net.minestom.server.network.packet.server.play.AcknowledgeBlockChangePacket;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionBotTest {
    private static final Duration TIMEOUT = TestServer.TIMEOUT;

    private interface BotTest {
        void run(TestPeer peer, Bot bot);
    }

    private static void withBot(GameMode gameMode, BotTest test) {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = runtime.connect(BotOptions.builder().username("hands").transport(peer).build());
            TestServer.configure(peer, "hands");
            TestServer.join(peer, new Pos(0.5, 0, 0.5), gameMode, (x, z, chunk) -> {
                if (x == 0 && z == 0) {
                    chunk.set(1, -1, 0, Block.DIRT);
                    chunk.set(2, -1, 0, Block.DIRT);
                    chunk.set(3, -1, 0, Block.OAK_LOG.withProperty("axis", "y"));
                }
            });
            test.run(peer, bot);
        }
    }

    private static ClientPlayerActionPacket awaitDigging(TestPeer peer, int mark, ClientPlayerActionPacket.Status status) {
        return peer.awaitSent(mark, ClientPlayerActionPacket.class, packet -> packet.status() == status, TIMEOUT);
    }

    /**
     * 从 from 到 to（都在列表中）之间的 Client Tick End 数量，即两者相隔的 tick 数
     */
    private static int ticksBetween(List<ClientPacket> packets, ClientPacket from, ClientPacket to) {
        var counting = false;
        var ticks = 0;
        for (var packet : packets) {
            if (packet == from) {
                counting = true;
            } else if (packet == to) {
                return ticks;
            } else if (counting && packet instanceof ClientTickEndPacket) {
                ticks++;
            }
        }
        throw new AssertionError(to + " not found after " + from);
    }

    @Test
    void survivalDiggingSequenceSwingsAndDelay() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            // 加载之后第一次模拟时速度为零，第二次模拟才着地；不着地时挖掘速度为五分之一
            bot.await(state -> state.player().onGround(), TIMEOUT);
            var mark = peer.mark();
            var first = bot.attemptBreakBlock(new Vec(1, -1, 0));
            var second = bot.attemptBreakBlock(new Vec(2, -1, 0));
            var started = awaitDigging(peer, mark, ClientPlayerActionPacket.Status.STARTED_DIGGING);
            assertEquals(1, started.sequence());
            assertEquals(BlockFace.TOP, started.blockFace());
            var finished = awaitDigging(peer, mark, ClientPlayerActionPacket.Status.FINISHED_DIGGING);
            assertEquals(2, finished.sequence());
            var sent = peer.sentSince(mark);
            // 泥土空手：每 tick 1/15。第 1 tick 发送 Started digging 与 Swing Arm，同一 tick 累加第 1 次再发送 Swing Arm；
            // 第 15 tick 第 15 次累加达到 1，先发送 Finished digging 再发送 Swing Arm
            assertEquals(14, ticksBetween(sent, started, finished), sent::toString);
            var startIndex = sent.indexOf(started);
            assertInstanceOf(ClientAnimationPacket.class, sent.get(startIndex + 1));
            assertInstanceOf(ClientAnimationPacket.class, sent.get(startIndex + 2));
            assertInstanceOf(ClientAnimationPacket.class, sent.get(sent.indexOf(finished) + 1));
            assertEquals(16, peer.sentSince(mark, ClientAnimationPacket.class).size());
            assertFalse(first.future().isDone());
            var ackMark = peer.mark();
            peer.push(new AcknowledgeBlockChangePacket(2));
            assertTrue(first.await(TIMEOUT));
            // 下一次挖掘不等待，立即发送 Started digging；挖掘延迟 5 在之后的「继续」中逐 tick 减少，期间不累加进度
            var secondStart = awaitDigging(peer, ackMark, ClientPlayerActionPacket.Status.STARTED_DIGGING);
            assertEquals(3, secondStart.sequence());
            var secondFinished = awaitDigging(peer, ackMark, ClientPlayerActionPacket.Status.FINISHED_DIGGING);
            assertEquals(4, secondFinished.sequence());
            assertEquals(19, ticksBetween(peer.sentSince(ackMark), secondStart, secondFinished));
            second.cancel();
            assertFalse(second.await(TIMEOUT));
        });
    }

    private static void waitTicks(TestPeer peer, int ticks) {
        var mark = peer.mark();
        var deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (peer.sentSince(mark, ClientTickEndPacket.class).size() < ticks) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting for " + ticks + " ticks");
            }
            Thread.onSpinWait();
        }
    }

    @Test
    void changingTheHeldItemRestartsDigging() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            bot.await(state -> state.player().onGround(), TIMEOUT);
            peer.push(new SetSlotPacket(0, 1, (short) 37, ItemStack.of(Material.STICK)));
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(new Vec(1, -1, 0));
            assertEquals(1, awaitDigging(peer, mark, ClientPlayerActionPacket.Status.STARTED_DIGGING).sequence());
            assertTrue(bot.selectHotbar(1).await(TIMEOUT));
            // 原版 sameDestroyTarget 比较主手物品：换了物品就中止原来的挖掘，以新的序号重新开始，这个 tick 不累加进度
            var cancelled = awaitDigging(peer, mark, ClientPlayerActionPacket.Status.CANCELLED_DIGGING);
            assertEquals(BlockFace.TOP, cancelled.blockFace());
            assertEquals(0, cancelled.sequence());
            var finished = awaitDigging(peer, mark, ClientPlayerActionPacket.Status.FINISHED_DIGGING);
            assertEquals(3, finished.sequence());
            var sent = peer.sentSince(mark);
            var held = sent.stream().filter(ClientHeldItemChangePacket.class::isInstance).findFirst().orElseThrow();
            assertTrue(sent.indexOf(held) < sent.indexOf(cancelled), sent::toString);
            var restarted = assertInstanceOf(ClientPlayerActionPacket.class, sent.get(sent.indexOf(cancelled) + 1));
            assertEquals(ClientPlayerActionPacket.Status.STARTED_DIGGING, restarted.status());
            assertEquals(2, restarted.sequence());
            assertInstanceOf(ClientAnimationPacket.class, sent.get(sent.indexOf(restarted) + 1));
            // 重新开始的 tick 不累加，之后 15 个 tick 各累加一次
            assertEquals(15, ticksBetween(sent, restarted, finished), sent::toString);
            peer.push(new AcknowledgeBlockChangePacket(3));
            assertTrue(dig.await(TIMEOUT));
        });
    }

    @Test
    void piercingWeaponsStabInsteadOfDigging() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            peer.push(new SetSlotPacket(0, 1, (short) 36, ItemStack.of(Material.WOODEN_SPEAR)));
            peer.barrier();
            // 原版 startAttack：主手拿着穿刺武器时发送 Stab 与 Swing Arm，不挖掘
            var mark = peer.mark();
            assertTrue(bot.attemptBreakBlock(new Vec(1, -1, 0)).await(TIMEOUT));
            peer.barrier();
            var actions = peer.sentSince(mark, ClientPlayerActionPacket.class);
            assertEquals(1, actions.size());
            assertEquals(ClientPlayerActionPacket.Status.STAB, actions.getFirst().status());
            var sent = peer.sentSince(mark);
            assertInstanceOf(ClientAnimationPacket.class, sent.get(sent.indexOf(actions.getFirst()) + 1));

            // 挖掘中换成穿刺武器：原版 continueAttack 什么都不做，不累加、不挥手
            peer.push(new SetSlotPacket(0, 2, (short) 36, ItemStack.AIR), new SetSlotPacket(0, 3, (short) 37, ItemStack.of(Material.WOODEN_SPEAR)));
            mark = peer.mark();
            var dig = bot.attemptBreakBlock(new Vec(1, -1, 0));
            awaitDigging(peer, mark, ClientPlayerActionPacket.Status.STARTED_DIGGING);
            assertTrue(bot.selectHotbar(1).await(TIMEOUT));
            peer.barrier();
            var holding = peer.mark();
            waitTicks(peer, 20);
            assertTrue(peer.sentSince(holding, ClientAnimationPacket.class).isEmpty());
            assertTrue(peer.sentSince(holding, ClientPlayerActionPacket.class).isEmpty());
            assertFalse(dig.future().isDone());
            dig.cancel();
            assertFalse(dig.await(TIMEOUT));
            assertEquals(ClientPlayerActionPacket.Status.CANCELLED_DIGGING,
                    awaitDigging(peer, holding, ClientPlayerActionPacket.Status.CANCELLED_DIGGING).status());
        });
    }

    @Test
    void restrictedDiggingSendsNoStartedDigging() {
        // 冒险模式不能建造、主手为空：原版 startAttack 只挥手，不发送 Started digging
        withBot(GameMode.ADVENTURE, (peer, bot) -> {
            var mark = peer.mark();
            assertTrue(bot.attemptBreakBlock(new Vec(1, -1, 0)).await(TIMEOUT));
            peer.barrier();
            assertTrue(peer.sentSince(mark, ClientPlayerActionPacket.class).isEmpty());
            assertEquals(1, peer.sentSince(mark, ClientAnimationPacket.class).size());
        });
        // 旁观模式什么都不发送
        withBot(GameMode.SPECTATOR, (peer, bot) -> {
            var mark = peer.mark();
            assertTrue(bot.attemptBreakBlock(new Vec(1, -1, 0)).await(TIMEOUT));
            peer.barrier();
            assertTrue(peer.sentSince(mark, ClientPlayerActionPacket.class).isEmpty());
            assertTrue(peer.sentSince(mark, ClientAnimationPacket.class).isEmpty());
        });
    }

    @Test
    void breakBlockReportsWhetherTheBlockChanged() {
        withBot(GameMode.CREATIVE, (peer, bot) -> {
            // 确认之前收到 Block Update：broken 为 true
            var mark = peer.mark();
            var broken = bot.breakBlock(new Vec(1, -1, 0));
            var started = awaitDigging(peer, mark, ClientPlayerActionPacket.Status.STARTED_DIGGING);
            peer.push(new BlockChangePacket(new Vec(1, -1, 0), Block.AIR.stateId()), new AcknowledgeBlockChangePacket(started.sequence()));
            var result = broken.await(TIMEOUT);
            assertTrue(result.broken());
            assertEquals(Block.DIRT, result.original());
            assertEquals(Block.AIR, result.changedTo());
            // 创造模式瞬间破坏，不发送 Finished digging
            assertTrue(peer.sentSince(mark, ClientPlayerActionPacket.class).stream()
                    .noneMatch(packet -> packet.status() == ClientPlayerActionPacket.Status.FINISHED_DIGGING));

            // 只收到确认：broken 为 false
            mark = peer.mark();
            var refused = bot.breakBlock(new Vec(2, -1, 0));
            started = awaitDigging(peer, mark, ClientPlayerActionPacket.Status.STARTED_DIGGING);
            peer.push(new AcknowledgeBlockChangePacket(started.sequence()));
            assertFalse(refused.await(TIMEOUT).broken());
            assertNull(refused.handle().observer().changedTo());

            // Block Update 只改变方块状态：broken 为 false
            mark = peer.mark();
            var rotated = bot.breakBlock(new Vec(3, -1, 0));
            started = awaitDigging(peer, mark, ClientPlayerActionPacket.Status.STARTED_DIGGING);
            peer.push(new BlockChangePacket(new Vec(3, -1, 0), Block.OAK_LOG.withProperty("axis", "x").stateId()),
                    new AcknowledgeBlockChangePacket(started.sequence()));
            assertFalse(rotated.await(TIMEOUT).broken());
        });
    }

    @Test
    void placeBlockSendsUseItemOnAndSwing() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            var mark = peer.mark();
            var place = bot.placeBlock(new Vec(1, -1, 0), BlockFace.TOP, PlayerHand.MAIN);
            var placement = peer.awaitSent(mark, ClientPlayerBlockPlacementPacket.class, TIMEOUT);
            assertEquals(new Vec(1, -1, 0), placement.blockPosition());
            assertEquals(BlockFace.TOP, placement.blockFace());
            assertEquals(0.5f, placement.cursorPositionX());
            assertEquals(1f, placement.cursorPositionY());
            assertEquals(0.5f, placement.cursorPositionZ());
            assertFalse(placement.insideBlock());
            assertFalse(placement.hitWorldBorder());
            var sent = peer.sentSince(mark);
            var swing = sent.get(sent.indexOf(placement) + 1);
            assertEquals(new ClientAnimationPacket(PlayerHand.MAIN), swing);
            // 先看向光标：朝向在之前的 tick 由移动包发出，Use Item On 在之后的 tick 发出
            assertAimedBefore(sent, placement, Rotation.lookAt(new Vec(0.5, 1.62, 0.5), new Vec(1.5, 0, 0.5)));
            peer.push(new AcknowledgeBlockChangePacket(placement.sequence()));
            assertTrue(place.await(TIMEOUT));
        });
    }

    /**
     * packet 之前最后一个带朝向的移动包的朝向为 rotation，并且两者之间至少隔着一个 Client Tick End
     */
    private static void assertAimedBefore(List<ClientPacket> sent, ClientPacket packet, Rotation rotation) {
        var index = sent.indexOf(packet);
        var tickEnds = 0;
        for (int i = index - 1; i >= 0; i--) {
            switch (sent.get(i)) {
                case ClientTickEndPacket _ -> tickEnds++;
                case ClientPlayerRotationPacket look -> {
                    assertEquals(rotation, new Rotation(look.yaw(), look.pitch()));
                    assertTrue(tickEnds >= 1, sent::toString);
                    return;
                }
                case ClientPlayerPositionAndRotationPacket look -> {
                    assertEquals(rotation, new Rotation(look.position().yaw(), look.position().pitch()));
                    assertTrue(tickEnds >= 1, sent::toString);
                    return;
                }
                default -> {
                }
            }
        }
        throw new AssertionError("no rotation sent before " + packet + ": " + sent);
    }

    @Test
    void useItemHoldReleasesAfterTheGivenTicks() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            var mark = peer.mark();
            var use = bot.useItem(PlayerHand.MAIN, 3);
            var packet = peer.awaitSent(mark, ClientUseItemPacket.class, TIMEOUT);
            peer.push(new AcknowledgeBlockChangePacket(packet.sequence()));
            assertTrue(use.await(TIMEOUT));
            var release = peer.sentSince(mark, ClientPlayerActionPacket.class);
            assertEquals(1, release.size());
            assertEquals(ClientPlayerActionPacket.Status.UPDATE_ITEM_STATE, release.getFirst().status());
        });
    }

    @Test
    void attackNeedsAnEntityInTheModel() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            var missing = bot.attack(100);
            assertFalse(missing.await(TIMEOUT));
            assertEquals(ActionCancelledException.NO_ENTITY,
                    assertThrows(CompletionException.class, () -> missing.future().join()).getCause().getMessage());
            peer.push(new SpawnEntityPacket(100, UUID.randomUUID(), EntityType.ZOMBIE, new Pos(3, 0, 0.5), 0, 0, Vec.ZERO));
            var mark = peer.mark();
            assertTrue(bot.attack(100).await(TIMEOUT));
            var sent = peer.sentSince(mark);
            var attack = assertInstanceOf(ClientAttackPacket.class, sent.stream().filter(ClientAttackPacket.class::isInstance).findFirst().orElseThrow());
            assertEquals(100, attack.targetId());
            assertEquals(new ClientAnimationPacket(PlayerHand.MAIN), sent.get(sent.indexOf(attack) + 1));
            // 僵尸包围盒中心为 (3, 0.975, 0.5)：先看向它，服务端收到朝向之后再攻击（击退方向取攻击者的朝向）
            assertAimedBefore(sent, attack, Rotation.lookAt(new Vec(0.5, 1.62, 0.5), new Vec(3, 0.975, 0.5)));
        });
    }
}
