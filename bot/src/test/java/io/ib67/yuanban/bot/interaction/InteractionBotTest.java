package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
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
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.AttackRange;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
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
import java.util.HashMap;
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
            assertAimedBefore(sent, placement, Rotation.lookAt(new Vec(0.5, 1.62F, 0.5), new Vec(1.5, 0, 0.5)));
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

    @Test
    void aTargetDueNorthIsAimedAtBeforeTheBlockIsUsed() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            // 光标为 (0, -1, -1) 顶面的中心 (0.5, 0, -0.5)，在眼睛 (0.5, 1.62, 0.5) 的正北：
            // Rotation.lookAt 的偏航角为 -180，本地玩家的位置把它规范为 180，两者按规范之后的值比较
            var eye = new Vec(0.5, 1.62F, 0.5);
            var lookAt = Rotation.lookAt(eye, new Vec(0.5, 0, -0.5));
            assertEquals(-180f, lookAt.yaw());
            var mark = peer.mark();
            var place = bot.placeBlock(new Vec(0, -1, -1), Direction.UP, Hand.MAIN_HAND);
            var placement = peer.awaitSent(mark, ServerboundUseItemOnPacket.class, TIMEOUT);
            assertEquals(Vector3i.from(0, -1, -1), placement.getPosition());
            assertAimedBefore(peer.sentSince(mark), placement, new Rotation(180, lookAt.pitch()));
            peer.push(new ClientboundBlockChangedAckPacket(placement.getSequence()));
            assertTrue(place.await(TIMEOUT));
        });
    }

    private static String cancelReason(Action action) {
        return assertThrows(CompletionException.class, () -> action.future().join()).getCause().getMessage();
    }

    @Test
    void handActionsOnUnloadedChunksAreCancelled() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            // (100, -1, 0) 所在的区块 (6, 0) 没有加载
            var far = new Vec(100, -1, 0);
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(far);
            assertFalse(dig.await(TIMEOUT));
            assertEquals(ActionCancelledException.CHUNK_NOT_LOADED, cancelReason(dig));
            var place = bot.placeBlock(far, Direction.UP, Hand.MAIN_HAND);
            assertFalse(place.await(TIMEOUT));
            assertEquals(ActionCancelledException.CHUNK_NOT_LOADED, cancelReason(place));
            // breakBlock：observer 注册时所在区块未加载，original 为 null；动作以 CHUNK_NOT_LOADED 取消
            var broken = bot.breakBlock(far);
            var observer = broken.await(TIMEOUT);
            assertFalse(observer.broken());
            assertNull(observer.original());
            assertEquals(ActionCancelledException.CHUNK_NOT_LOADED, cancelReason(broken.action()));
            peer.barrier();
            assertTrue(peer.sentSince(mark, ServerboundPlayerActionPacket.class).isEmpty());
            assertTrue(peer.sentSince(mark, ServerboundUseItemOnPacket.class).isEmpty());
            assertTrue(peer.sentSince(mark, ServerboundSwingPacket.class).isEmpty());
            assertEquals(BotStatus.PLAY, bot.status());
        });
    }

    @Test
    void unloadingTheTargetChunkWhileDiggingCancelsTheDigging() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            // 目标 (16, -1, 0) 在区块 (1, 0) 中，空手挖石头需要 150 tick
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(new Vec(16, -1, 0));
            awaitDigging(peer, mark, PlayerAction.START_DIGGING);
            peer.push(new ClientboundForgetLevelChunkPacket(1, 0));
            assertFalse(dig.await(TIMEOUT));
            assertEquals(ActionCancelledException.CHUNK_NOT_LOADED, cancelReason(dig));
            // 与原版准星离开方块时的 stopDestroyBlock 相同：发送 Cancelled digging，面为 DOWN
            var cancelled = awaitDigging(peer, mark, PlayerAction.CANCEL_DIGGING);
            assertEquals(Vector3i.from(16, -1, 0), cancelled.getPosition());
            assertEquals(Direction.DOWN, cancelled.getFace());
            assertEquals(BotStatus.PLAY, bot.status());
        });
    }

    @Test
    void diggingIsCancelledBeforeAnythingIsSentWhenTheChunkAtTheEyesIsNotLoaded() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            // 目标 (16, -1, 0) 所在的区块 (1, 0) 已加载，眼睛所在的区块 (0, 0) 被卸载：计算进度时读取眼睛所在的方块
            peer.push(new ClientboundForgetLevelChunkPacket(0, 0));
            peer.barrier();
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(new Vec(16, -1, 0));
            assertFalse(dig.await(TIMEOUT));
            assertEquals(ActionCancelledException.CHUNK_NOT_LOADED, cancelReason(dig));
            // 开始的 tick 先计算进度、再发送 Started digging（原版 startDestroyBlock 的顺序），所以什么都没有发出
            peer.barrier();
            assertTrue(peer.sentSince(mark, ServerboundPlayerActionPacket.class).isEmpty());
            assertTrue(peer.sentSince(mark, ServerboundSwingPacket.class).isEmpty());
            assertEquals(BotStatus.PLAY, bot.status());
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
            assertAimedBefore(sent, attack, Rotation.lookAt(new Vec(0.5, 1.62F, 0.5), new Vec(3, 0.975F, 0.5)));

            // 幼年僵尸的包围盒为原版 Zombie.BABY_DIMENSIONS（0.49 × 0.98），中心为 (3, 0.49, 0.5)
            peer.push(zombie(101), new ClientboundSetEntityDataPacket(101, new EntityMetadata<?, ?>[]{
                    new BooleanEntityMetadata(EntityMetaIndex.ZOMBIE_IS_BABY.index(), MetadataTypes.BOOLEAN, true)}));
            mark = peer.mark();
            assertTrue(bot.attack(101).await(TIMEOUT));
            sent = peer.sentSince(mark);
            var babyAttack = sent.stream().filter(ServerboundAttackPacket.class::isInstance).findFirst().orElseThrow();
            assertAimedBefore(sent, babyAttack, Rotation.lookAt(new Vec(0.5, 1.62F, 0.5), new Vec(3, 0.49F, 0.5)));
        });
    }

    /**
     * 提交 attack，等它结束并且之后发出的数据包都已到达
     *
     * @return 这期间发出的数据包；动作被取消时取消原因放在 reason 中
     */
    private static List<MinecraftPacket> attack(TestPeer peer, Bot bot, int entityId, String[] reason) {
        var mark = peer.mark();
        var action = bot.attack(entityId);
        if (!action.await(TIMEOUT)) {
            reason[0] = assertThrows(CompletionException.class, () -> action.future().join()).getCause().getMessage();
        } else {
            reason[0] = null;
        }
        waitTicks(peer, 2);
        return peer.sentSince(mark);
    }

    private static void assertAttacked(List<MinecraftPacket> sent, String reason, int entityId) {
        assertNull(reason);
        var attacks = sent.stream().filter(ServerboundAttackPacket.class::isInstance).map(ServerboundAttackPacket.class::cast).toList();
        assertEquals(1, attacks.size(), sent::toString);
        assertEquals(entityId, attacks.getFirst().getEntityId());
    }

    /**
     * 原版 startAttack 没有选中实体：不攻击，只挥手；动作以 OUT_OF_RANGE 取消
     */
    private static void assertMissed(List<MinecraftPacket> sent, String reason) {
        assertEquals(ActionCancelledException.OUT_OF_RANGE, reason);
        assertTrue(sent.stream().noneMatch(ServerboundAttackPacket.class::isInstance), sent::toString);
        assertEquals(List.of(new ServerboundSwingPacket(Hand.MAIN_HAND)),
                sent.stream().filter(ServerboundSwingPacket.class::isInstance).toList());
    }

    @Test
    void attackChecksTheVanillaReach() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            var reason = new String[1];
            // 眼睛在 (0.5, 1.62, 0.5)。僵尸在 x = 6：视线进入包围盒的位置离眼睛约 5.3 格，超出实体交互距离 3
            peer.push(new ClientboundAddEntityPacket(100, UUID.randomUUID(), EntityType.ZOMBIE, 6, 0, 0.5, 0, 0, 0));
            assertMissed(attack(peer, bot, 100, reason), reason[0]);

            // 僵尸在 x = 3.5：包围盒中心离眼睛约 3.07 格，视线进入包围盒的位置约 2.76 格，按进入的位置判断，照常攻击
            peer.push(new ClientboundAddEntityPacket(101, UUID.randomUUID(), EntityType.ZOMBIE, 3.5, 0, 0.5, 0, 0, 0));
            assertAttacked(attack(peer, bot, 101, reason), reason[0], 101);

            // 服务端把实体交互距离改为 6 之后，x = 6 的僵尸照常攻击
            peer.push(new ClientboundUpdateAttributesPacket(TestServer.ENTITY_ID,
                    List.of(new Attribute(AttributeType.Builtin.ENTITY_INTERACTION_RANGE, 6))));
            assertAttacked(attack(peer, bot, 100, reason), reason[0], 100);
        });
    }

    @Test
    void attackOutOfReachInSpectatorModeSendsSpectatorActionWithoutTarget() {
        // 原版 startAttack：旁观模式下没有选中实体时调用 spectatorNoAction，发送不带实体编号的 Spectator Action
        withBot(GameMode.SPECTATOR, (peer, bot) -> {
            var reason = new String[1];
            peer.push(new ClientboundAddEntityPacket(100, UUID.randomUUID(), EntityType.ZOMBIE, 6, 0, 0.5, 0, 0, 0));
            var sent = attack(peer, bot, 100, reason);
            assertEquals(ActionCancelledException.OUT_OF_RANGE, reason[0]);
            assertEquals(List.of(new ServerboundSpectatorActionPacket(OptionalInt.empty())),
                    sent.stream().filter(ServerboundSpectatorActionPacket.class::isInstance).toList());
            assertTrue(sent.stream().noneMatch(packet -> packet instanceof ServerboundSwingPacket || packet instanceof ServerboundAttackPacket));
        });
    }

    @Test
    void attackRangeComponentOfTheMainHandLimitsTheReach() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            var reason = new String[1];
            // 主手物品的 attack_range：最小 2、最大 3，hitboxMargin 为 0
            var components = new DataComponents(new HashMap<>());
            components.put(DataComponentTypes.ATTACK_RANGE, new AttackRange(2, 3, 2, 3, 0, 1));
            peer.push(new ClientboundContainerSetSlotPacket(0, 1, 36, new ItemStack(ItemData.id(Key.key("stick")), 1, components)));
            peer.barrier();
            // 僵尸在 x = 1.5：先沿 attack_range 的距离（从 2 格起）选取不到，再按实体交互距离选中它，
            // 但视线进入包围盒的位置离眼睛约 0.72 格，小于最小距离 2
            peer.push(new ClientboundAddEntityPacket(100, UUID.randomUUID(), EntityType.ZOMBIE, 1.5, 0, 0.5, 0, 0, 0));
            assertMissed(attack(peer, bot, 100, reason), reason[0]);
            // 僵尸在 x = 3：进入的位置约 2.27 格，在 [2, 3] 之内
            peer.push(new ClientboundAddEntityPacket(101, UUID.randomUUID(), EntityType.ZOMBIE, 3, 0, 0.5, 0, 0, 0));
            assertAttacked(attack(peer, bot, 101, reason), reason[0], 101);
        });
    }

    @Test
    void pickableProjectilesHaveAPickRadiusOfOne() {
        withBot(GameMode.SURVIVAL, (peer, bot) -> {
            var reason = new String[1];
            // 潜影贝子弹（0.3125 × 0.3125，中心与眼睛同高）在 x = 4：包围盒按选取半径 1 扩大之后，视线进入的位置约 2.34 格
            peer.push(new ClientboundAddEntityPacket(100, UUID.randomUUID(), EntityType.SHULKER_BULLET, 4, 1.62 - 0.15625, 0.5, 0, 0, 0));
            assertAttacked(attack(peer, bot, 100, reason), reason[0], 100);
            // 火球（1 × 1）属于 minecraft:redirectable_projectile，同样可以选取：在 x = 4.5 时进入的位置约 2.5 格
            peer.push(new ClientboundAddEntityPacket(101, UUID.randomUUID(), EntityType.FIREBALL, 4.5, 1.12, 0.5, 0, 0, 0));
            assertAttacked(attack(peer, bot, 101, reason), reason[0], 101);
            // 雪球（0.25 × 0.25）不可选取，选取半径为 0：在 x = 4 时进入的位置约 3.4 格
            peer.push(new ClientboundAddEntityPacket(102, UUID.randomUUID(), EntityType.SNOWBALL, 4, 1.62 - 0.125, 0.5, 0, 0, 0));
            assertMissed(attack(peer, bot, 102, reason), reason[0]);
        });
    }
}
