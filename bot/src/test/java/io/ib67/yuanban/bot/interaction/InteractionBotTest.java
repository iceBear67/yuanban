package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.data.ItemData;
import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.view.EntityMetaIndex;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSpectatorActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
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

    private static ItemStack item(String name) {
        return new ItemStack(ItemData.id(Key.key(name)));
    }

    private static ClientboundBlockUpdatePacket blockUpdate(int x, int y, int z, Block block) {
        return new ClientboundBlockUpdatePacket(new BlockChangeEntry(Vector3i.from(x, y, z), block.stateId()));
    }

    private static ClientboundAddEntityPacket zombie(int entityId) {
        return new ClientboundAddEntityPacket(entityId, UUID.randomUUID(), EntityType.ZOMBIE, 3, 0, 0.5, 0, 0, 0);
    }

    private static ServerboundPlayerActionPacket awaitDigging(TestPeer peer, int mark, PlayerAction action) {
        return peer.awaitSent(mark, ServerboundPlayerActionPacket.class, packet -> packet.getAction() == action, TIMEOUT);
    }

    /**
     * 从 from 到 to（都在列表中）之间的 Client Tick End 数量，即两者相隔的 tick 数
     */
    private static int ticksBetween(List<MinecraftPacket> packets, MinecraftPacket from, MinecraftPacket to) {
        var counting = false;
        var ticks = 0;
        for (var packet : packets) {
            if (packet == from) {
                counting = true;
            } else if (packet == to) {
                return ticks;
            } else if (counting && packet instanceof ServerboundClientTickEndPacket) {
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
            var started = awaitDigging(peer, mark, PlayerAction.START_DIGGING);
            assertEquals(1, started.getSequence());
            assertEquals(Direction.UP, started.getFace());
            var finished = awaitDigging(peer, mark, PlayerAction.FINISH_DIGGING);
            assertEquals(2, finished.getSequence());
            var sent = peer.sentSince(mark);
            // 泥土空手：每 tick 1/15。第 1 tick 发送 Started digging 与 Swing Arm，同一 tick 累加第 1 次再发送 Swing Arm；
            // 第 15 tick 第 15 次累加达到 1，先发送 Finished digging 再发送 Swing Arm
            assertEquals(14, ticksBetween(sent, started, finished), sent::toString);
            var startIndex = sent.indexOf(started);
            assertInstanceOf(ServerboundSwingPacket.class, sent.get(startIndex + 1));
            assertInstanceOf(ServerboundSwingPacket.class, sent.get(startIndex + 2));
            assertInstanceOf(ServerboundSwingPacket.class, sent.get(sent.indexOf(finished) + 1));
            assertEquals(16, peer.sentSince(mark, ServerboundSwingPacket.class).size());
            assertFalse(first.future().isDone());
            var ackMark = peer.mark();
            peer.push(new ClientboundBlockChangedAckPacket(2));
            assertTrue(first.await(TIMEOUT));
            // 下一次挖掘不等待，立即发送 Started digging；挖掘延迟 5 在之后的「继续」中逐 tick 减少，期间不累加进度
            var secondStart = awaitDigging(peer, ackMark, PlayerAction.START_DIGGING);
            assertEquals(3, secondStart.getSequence());
            var secondFinished = awaitDigging(peer, ackMark, PlayerAction.FINISH_DIGGING);
            assertEquals(4, secondFinished.getSequence());
            assertEquals(19, ticksBetween(peer.sentSince(ackMark), secondStart, secondFinished));
            second.cancel();
            assertFalse(second.await(TIMEOUT));
        });
    }

    private static void waitTicks(TestPeer peer, int ticks) {
        var mark = peer.mark();
        var deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (peer.sentSince(mark, ServerboundClientTickEndPacket.class).size() < ticks) {
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
            peer.push(new ClientboundContainerSetSlotPacket(0, 1, 37, item("stick")));
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(new Vec(1, -1, 0));
            assertEquals(1, awaitDigging(peer, mark, PlayerAction.START_DIGGING).getSequence());
            assertTrue(bot.selectHotbar(1).await(TIMEOUT));
            // 原版 sameDestroyTarget 比较主手物品：换了物品就中止原来的挖掘，以新的序号重新开始，这个 tick 不累加进度
            var cancelled = awaitDigging(peer, mark, PlayerAction.CANCEL_DIGGING);
            assertEquals(Direction.UP, cancelled.getFace());
            assertEquals(0, cancelled.getSequence());
            var finished = awaitDigging(peer, mark, PlayerAction.FINISH_DIGGING);
            assertEquals(3, finished.getSequence());
            var sent = peer.sentSince(mark);
            var held = sent.stream().filter(ServerboundSetCarriedItemPacket.class::isInstance).findFirst().orElseThrow();
            assertTrue(sent.indexOf(held) < sent.indexOf(cancelled), sent::toString);
            var restarted = assertInstanceOf(ServerboundPlayerActionPacket.class, sent.get(sent.indexOf(cancelled) + 1));
            assertEquals(PlayerAction.START_DIGGING, restarted.getAction());
            assertEquals(2, restarted.getSequence());
            assertInstanceOf(ServerboundSwingPacket.class, sent.get(sent.indexOf(restarted) + 1));
            // 重新开始的 tick 不累加，之后 15 个 tick 各累加一次
            assertEquals(15, ticksBetween(sent, restarted, finished), sent::toString);
            peer.push(new ClientboundBlockChangedAckPacket(3));
            assertTrue(dig.await(TIMEOUT));
        });
    }

    @Test
    void piercingWeaponsStabInsteadOfDigging() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            peer.push(new ClientboundContainerSetSlotPacket(0, 1, 36, item("wooden_spear")));
            peer.barrier();
            // 原版 startAttack：主手拿着穿刺武器时发送 Stab 与 Swing Arm，不挖掘
            var mark = peer.mark();
            assertTrue(bot.attemptBreakBlock(new Vec(1, -1, 0)).await(TIMEOUT));
            peer.barrier();
            var actions = peer.sentSince(mark, ServerboundPlayerActionPacket.class);
            assertEquals(1, actions.size());
            assertEquals(PlayerAction.STAB, actions.getFirst().getAction());
            var sent = peer.sentSince(mark);
            assertInstanceOf(ServerboundSwingPacket.class, sent.get(sent.indexOf(actions.getFirst()) + 1));

            // 挖掘中换成穿刺武器：原版 continueAttack 什么都不做，不累加、不挥手
            peer.push(new ClientboundContainerSetSlotPacket(0, 2, 36, null),
                    new ClientboundContainerSetSlotPacket(0, 3, 37, item("wooden_spear")));
            mark = peer.mark();
            var dig = bot.attemptBreakBlock(new Vec(1, -1, 0));
            awaitDigging(peer, mark, PlayerAction.START_DIGGING);
            assertTrue(bot.selectHotbar(1).await(TIMEOUT));
            peer.barrier();
            var holding = peer.mark();
            waitTicks(peer, 20);
            assertTrue(peer.sentSince(holding, ServerboundSwingPacket.class).isEmpty());
            assertTrue(peer.sentSince(holding, ServerboundPlayerActionPacket.class).isEmpty());
            assertFalse(dig.future().isDone());
            dig.cancel();
            assertFalse(dig.await(TIMEOUT));
            assertEquals(PlayerAction.CANCEL_DIGGING, awaitDigging(peer, holding, PlayerAction.CANCEL_DIGGING).getAction());
        });
    }

    @Test
    void restrictedDiggingSendsNoStartedDigging() {
        // 冒险模式不能建造、主手为空：原版 startAttack 只挥手，不发送 Started digging
        withBot(GameMode.ADVENTURE, (peer, bot) -> {
            var mark = peer.mark();
            assertTrue(bot.attemptBreakBlock(new Vec(1, -1, 0)).await(TIMEOUT));
            peer.barrier();
            assertTrue(peer.sentSince(mark, ServerboundPlayerActionPacket.class).isEmpty());
            assertEquals(1, peer.sentSince(mark, ServerboundSwingPacket.class).size());
        });
    }

    @Test
    void spectatorsSendSpectatorActionInsteadOfDiggingOrAttacking() {
        // 原版 startAttack：旁观模式不挖掘、不攻击、不挥手，点击方块时发送不带实体编号的 Spectator Action，点击实体时带它的编号
        withBot(GameMode.SPECTATOR, (peer, bot) -> {
            var mark = peer.mark();
            assertTrue(bot.attemptBreakBlock(new Vec(1, -1, 0)).await(TIMEOUT));
            peer.barrier();
            assertEquals(List.of(new ServerboundSpectatorActionPacket(OptionalInt.empty())),
                    peer.sentSince(mark, ServerboundSpectatorActionPacket.class));
            assertTrue(peer.sentSince(mark, ServerboundPlayerActionPacket.class).isEmpty());
            assertTrue(peer.sentSince(mark, ServerboundSwingPacket.class).isEmpty());

            peer.push(zombie(100));
            mark = peer.mark();
            assertTrue(bot.attack(100).await(TIMEOUT));
            peer.barrier();
            assertEquals(List.of(new ServerboundSpectatorActionPacket(OptionalInt.of(100))),
                    peer.sentSince(mark, ServerboundSpectatorActionPacket.class));
            assertTrue(peer.sentSince(mark, ServerboundAttackPacket.class).isEmpty());
            assertTrue(peer.sentSince(mark, ServerboundSwingPacket.class).isEmpty());
        });
    }

    @Test
    void instantBreakWhileDiggingTakesTheCreativeBranch() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            bot.await(state -> state.player().onGround(), TIMEOUT);
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(new Vec(1, -1, 0));
            assertEquals(1, awaitDigging(peer, mark, PlayerAction.START_DIGGING).getSequence());
            // 挖掘中能力变成瞬间建造：原版 continueDestroyBlock 走创造模式的分支，不再累加进度，
            // 以新的序号发送 Started digging 与 Swing Arm，收到确认即执行完毕
            peer.push(new ClientboundPlayerAbilitiesPacket(false, false, false, true, 0.05f, 0.1f));
            var restarted = peer.awaitSent(mark, ServerboundPlayerActionPacket.class,
                    packet -> packet.getAction() == PlayerAction.START_DIGGING && packet.getSequence() == 2, TIMEOUT);
            peer.push(new ClientboundBlockChangedAckPacket(2));
            assertTrue(dig.await(TIMEOUT));
            peer.barrier();
            var sent = peer.sentSince(mark);
            assertEquals(new ServerboundSwingPacket(Hand.MAIN_HAND), sent.get(sent.indexOf(restarted) + 1));
            assertEquals(2, peer.sentSince(mark, ServerboundPlayerActionPacket.class).size(), sent::toString);
        });
    }

    @Test
    void breakBlockReportsWhetherTheBlockChanged() {
        withBot(GameMode.CREATIVE, (peer, bot) -> {
            // 确认之前收到 Block Update：broken 为 true
            var mark = peer.mark();
            var broken = bot.breakBlock(new Vec(1, -1, 0));
            var started = awaitDigging(peer, mark, PlayerAction.START_DIGGING);
            peer.push(blockUpdate(1, -1, 0, Block.AIR), new ClientboundBlockChangedAckPacket(started.getSequence()));
            var result = broken.await(TIMEOUT);
            assertTrue(result.broken());
            assertEquals(Block.DIRT, result.original());
            assertEquals(Block.AIR, result.changedTo());
            // 创造模式瞬间破坏，不发送 Finished digging
            assertTrue(peer.sentSince(mark, ServerboundPlayerActionPacket.class).stream()
                    .noneMatch(packet -> packet.getAction() == PlayerAction.FINISH_DIGGING));

            // 只收到确认：broken 为 false
            mark = peer.mark();
            var refused = bot.breakBlock(new Vec(2, -1, 0));
            started = awaitDigging(peer, mark, PlayerAction.START_DIGGING);
            peer.push(new ClientboundBlockChangedAckPacket(started.getSequence()));
            assertFalse(refused.await(TIMEOUT).broken());
            assertNull(refused.handle().observer().changedTo());

            // Block Update 只改变方块状态：broken 为 false
            mark = peer.mark();
            var rotated = bot.breakBlock(new Vec(3, -1, 0));
            started = awaitDigging(peer, mark, PlayerAction.START_DIGGING);
            peer.push(blockUpdate(3, -1, 0, Block.OAK_LOG.withProperty("axis", "x")),
                    new ClientboundBlockChangedAckPacket(started.getSequence()));
            assertFalse(rotated.await(TIMEOUT).broken());
        });
    }

    @Test
    void placeBlockSendsUseItemOnAndSwing() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            var mark = peer.mark();
            var place = bot.placeBlock(new Vec(1, -1, 0), Direction.UP, Hand.MAIN_HAND);
            var placement = peer.awaitSent(mark, ServerboundUseItemOnPacket.class, TIMEOUT);
            assertEquals(Vector3i.from(1, -1, 0), placement.getPosition());
            assertEquals(Direction.UP, placement.getFace());
            assertEquals(Hand.MAIN_HAND, placement.getHand());
            assertEquals(0.5f, placement.getCursorX());
            assertEquals(1f, placement.getCursorY());
            assertEquals(0.5f, placement.getCursorZ());
            assertFalse(placement.isInsideBlock());
            assertFalse(placement.isHitWorldBorder());
            var sent = peer.sentSince(mark);
            var swing = sent.get(sent.indexOf(placement) + 1);
            assertEquals(new ServerboundSwingPacket(Hand.MAIN_HAND), swing);
            // 先看向光标：朝向在之前的 tick 由移动包发出，Use Item On 在之后的 tick 发出
            assertAimedBefore(sent, placement, Rotation.lookAt(new Vec(0.5, 1.62, 0.5), new Vec(1.5, 0, 0.5)));
            peer.push(new ClientboundBlockChangedAckPacket(placement.getSequence()));
            assertTrue(place.await(TIMEOUT));
        });
    }

    @Test
    void useItemOnUsesTheOutlineForInsideBlockAndChecksTheBorder() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            // 眼睛 (0.5, 1.62, 0.5) 在蜘蛛网中：蜘蛛网没有碰撞箱，轮廓形状为整格。原版选取方块使用轮廓形状，Inside block 为 true
            peer.push(blockUpdate(0, 1, 0, Block.COBWEB));
            var mark = peer.mark();
            var use = bot.useItemOn(new Vec(0, 1, 0), Direction.UP, Hand.MAIN_HAND, new Vec(0.5, 0.5, 0.5));
            var placement = peer.awaitSent(mark, ServerboundUseItemOnPacket.class, TIMEOUT);
            assertTrue(placement.isInsideBlock());
            peer.push(new ClientboundBlockChangedAckPacket(placement.getSequence()));
            assertTrue(use.await(TIMEOUT));

            // 目标方块在世界边框之外（直径 2，范围 [-1, 1)）：原版 useItemOn 返回 FAIL，什么都不发送
            peer.push(new ClientboundSetBorderSizePacket(2));
            mark = peer.mark();
            assertTrue(bot.placeBlock(new Vec(1, -1, 0), Direction.UP, Hand.MAIN_HAND).await(TIMEOUT));
            peer.barrier();
            assertTrue(peer.sentSince(mark, ServerboundUseItemOnPacket.class).isEmpty());
            assertTrue(peer.sentSince(mark, ServerboundSwingPacket.class).isEmpty());
        });
    }

    /**
     * packet 之前最后一个带朝向的移动包的朝向为 rotation，并且两者之间至少隔着一个 Client Tick End
     */
    private static void assertAimedBefore(List<MinecraftPacket> sent, MinecraftPacket packet, Rotation rotation) {
        var index = sent.indexOf(packet);
        var tickEnds = 0;
        for (int i = index - 1; i >= 0; i--) {
            switch (sent.get(i)) {
                case ServerboundClientTickEndPacket _ -> tickEnds++;
                case ServerboundMovePlayerRotPacket look -> {
                    assertEquals(rotation, new Rotation(look.getYaw(), look.getPitch()));
                    assertTrue(tickEnds >= 1, sent::toString);
                    return;
                }
                case ServerboundMovePlayerPosRotPacket look -> {
                    assertEquals(rotation, new Rotation(look.getYaw(), look.getPitch()));
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
            var use = bot.useItem(Hand.MAIN_HAND, 3);
            var packet = peer.awaitSent(mark, ServerboundUseItemPacket.class, TIMEOUT);
            peer.push(new ClientboundBlockChangedAckPacket(packet.getSequence()));
            assertTrue(use.await(TIMEOUT));
            var release = peer.sentSince(mark, ServerboundPlayerActionPacket.class);
            assertEquals(1, release.size());
            assertEquals(PlayerAction.RELEASE_USE_ITEM, release.getFirst().getAction());
        });
    }

    @Test
    void attackNeedsAnEntityInTheModel() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            var missing = bot.attack(100);
            assertFalse(missing.await(TIMEOUT));
            assertEquals(ActionCancelledException.NO_ENTITY,
                    assertThrows(CompletionException.class, () -> missing.future().join()).getCause().getMessage());
            peer.push(zombie(100));
            var mark = peer.mark();
            assertTrue(bot.attack(100).await(TIMEOUT));
            var sent = peer.sentSince(mark);
            var attack = assertInstanceOf(ServerboundAttackPacket.class,
                    sent.stream().filter(ServerboundAttackPacket.class::isInstance).findFirst().orElseThrow());
            assertEquals(100, attack.getEntityId());
            assertEquals(new ServerboundSwingPacket(Hand.MAIN_HAND), sent.get(sent.indexOf(attack) + 1));
            // 僵尸包围盒中心为 (3, 0.975, 0.5)：先看向它，服务端收到朝向之后再攻击（击退方向取攻击者的朝向）
            assertAimedBefore(sent, attack, Rotation.lookAt(new Vec(0.5, 1.62, 0.5), new Vec(3, 0.975, 0.5)));

            // 幼年僵尸的包围盒为原版 Zombie.BABY_DIMENSIONS（0.49 × 0.98），中心为 (3, 0.49, 0.5)
            peer.push(zombie(101), new ClientboundSetEntityDataPacket(101, new EntityMetadata<?, ?>[]{
                    new BooleanEntityMetadata(EntityMetaIndex.ZOMBIE_IS_BABY.index(), MetadataTypes.BOOLEAN, true)}));
            mark = peer.mark();
            assertTrue(bot.attack(101).await(TIMEOUT));
            sent = peer.sentSince(mark);
            var babyAttack = sent.stream().filter(ServerboundAttackPacket.class::isInstance).findFirst().orElseThrow();
            assertAimedBefore(sent, babyAttack, Rotation.lookAt(new Vec(0.5, 1.62, 0.5), new Vec(3, 0.49, 0.5)));
        });
    }
}
