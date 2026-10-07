package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.MoveOptions;
import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.observe.BotTicked;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.PlayerMoved;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import io.ib67.yuanban.bot.scheduler.BotInstance;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.pathfinding.PNode;
import net.minestom.server.entity.pathfinding.PathGenerator;
import net.minestom.server.entity.pathfinding.generators.GroundNodeGenerator;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientEntityActionPacket;
import net.minestom.server.network.packet.client.play.ClientInputPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerAbilitiesPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import net.minestom.server.network.packet.client.play.ClientTickEndPacket;
import net.minestom.server.network.packet.client.play.ClientVehicleMovePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.AcknowledgeBlockChangePacket;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;
import net.minestom.server.network.packet.server.play.EntityEffectPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.PlayerAbilitiesPacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import net.minestom.server.network.packet.server.play.PlayerRotationPacket;
import net.minestom.server.network.packet.server.play.RemoveEntityEffectPacket;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import net.minestom.server.network.packet.server.play.SetTickStatePacket;
import net.minestom.server.network.packet.server.play.SetTimePacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementBotTest {
    private static final Duration TIMEOUT = TestServer.TIMEOUT;

    private interface BotTest {
        void run(TestPeer peer, Bot bot);
    }

    private static void withBot(Pos spawn, TestServer.ChunkConsumer chunks, BotTest test) {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = runtime.connect(BotOptions.builder().username("mover").transport(peer).build());
            TestServer.configure(peer, "mover");
            TestServer.join(peer, spawn, GameMode.SURVIVAL, chunks);
            test.run(peer, bot);
        }
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

    /**
     * 移动包发出的朝向：朝向改变的 tick 同时到了每 20 tick 发送一次位置的时候，由 Set Player Position and Rotation 发出
     */
    private static Rotation sentRotation(ClientPacket packet) {
        return switch (packet) {
            case ClientPlayerRotationPacket rotation -> new Rotation(rotation.yaw(), rotation.pitch());
            case ClientPlayerPositionAndRotationPacket both -> new Rotation(both.position().yaw(), both.position().pitch());
            default -> throw new AssertionError("not a rotation: " + packet);
        };
    }

    private static boolean sendsRotation(ClientPacket packet) {
        return packet instanceof ClientPlayerRotationPacket || packet instanceof ClientPlayerPositionAndRotationPacket;
    }

    private static Rotation awaitRotation(TestPeer peer, int mark) {
        return sentRotation(peer.awaitSent(mark, ClientPacket.class, MovementBotTest::sendsRotation, TIMEOUT));
    }

    @Test
    void rotateAndLookAt() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            var mark = peer.mark();
            assertTrue(bot.rotate(90, 10).await(TIMEOUT));
            var rotation = awaitRotation(peer, mark);
            assertEquals(90, rotation.yaw());
            assertEquals(10, rotation.pitch());

            mark = peer.mark();
            // 眼睛在 (0.5, 1.62, 0.5)，目标在正东方同一高度
            assertTrue(bot.lookAt(new Vec(10.5, 1.62, 0.5)).await(TIMEOUT));
            var look = awaitRotation(peer, mark);
            assertEquals(-90, look.yaw(), 1e-4);
            assertEquals(0, look.pitch(), 1e-4);

            assertThrows(IllegalArgumentException.class, () -> bot.rotate(0, 91));
            assertThrows(IllegalArgumentException.class, () -> bot.rotate(0, Float.NaN));
            assertFalse(bot.lookAt(12345).await(TIMEOUT));
        });
    }

    @Test
    void rotationsSetByTheServerStay() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            assertTrue(bot.rotate(90, 10).await(TIMEOUT));
            // 原版 handleMovePlayer 设置的朝向一直保持，rotate 留下的朝向不再生效
            peer.push(new PlayerPositionAndLookPacket(2, new Pos(0.5, 0, 0.5), Vec.ZERO, 30, 5, 0));
            peer.barrier();
            waitTicks(peer, 3);
            var position = bot.view().player().position();
            assertEquals(30, position.yaw());
            assertEquals(5, position.pitch());

            // handleRotatePlayer 同样如此
            assertTrue(bot.rotate(90, 10).await(TIMEOUT));
            peer.push(new PlayerRotationPacket(-45, false, 20, false));
            peer.barrier();
            waitTicks(peer, 3);
            position = bot.view().player().position();
            assertEquals(-45, position.yaw());
            assertEquals(20, position.pitch());
        });
    }

    @Test
    void moveToOnFlatGround() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (_, bot) -> {
            var target = new Vec(6.5, 0, 3.5);
            assertTrue(bot.moveTo(target).await(TIMEOUT));
            var position = bot.view().player().position();
            assertTrue(Math.hypot(position.x() - target.x(), position.z() - target.z()) <= 0.5, position.toString());
            assertTrue(bot.view().player().onGround());
        });
    }

    @Test
    void moveToJumpsUpAStep() {
        withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
            if (x == 0) {
                for (int bx = 3; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        chunk.set(bx, 0, bz, Block.STONE);
                    }
                }
            }
        }, (peer, bot) -> {
            var mark = peer.mark();
            assertTrue(bot.moveTo(new Vec(5.5, 1, 0.5)).await(TIMEOUT));
            var position = bot.view().player().position();
            assertEquals(1, position.y(), 1e-6);
            assertTrue(peer.sentSince(mark, ClientInputPacket.class).stream().anyMatch(ClientInputPacket::jump));
        });
    }

    @Test
    void moveToClimbsSlabsAndStairs() {
        // GroundNodeGenerator 的节点高度为整数：走上半砖的节点比半砖顶面高 0.5，楼梯的下层台阶也比节点低 0.5；
        // 到达节点与起跳都按碰撞箱计算的站立高度判断
        for (var step : List.of(Block.STONE_SLAB, Block.STONE_STAIRS.withProperty("facing", "east"))) {
            withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
                if (x == 0) {
                    for (int bz = 0; bz < 16; bz++) {
                        chunk.set(2, 0, bz, step);
                        for (int bx = 3; bx < 16; bx++) {
                            chunk.set(bx, 0, bz, Block.STONE);
                        }
                    }
                }
            }, (_, bot) -> {
                var move = bot.moveTo(new Vec(5.5, 1, 0.5));
                assertTrue(move.await(TIMEOUT), () -> step + ": " + move.future());
                assertEquals(1, bot.view().player().position().y(), 1e-4, step::toString);
            });
        }
    }

    @Test
    void moveToWalksAlongSlabsAndStepsDown() {
        withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
            if (x == 0) {
                for (int bz = 0; bz < 16; bz++) {
                    for (int bx = 2; bx <= 4; bx++) {
                        chunk.set(bx, 0, bz, Block.STONE_SLAB);
                    }
                }
            }
        }, (_, bot) -> {
            // 半砖上的节点与半砖所在层加一的节点同高，站立高度都是 0.5：在半砖上行走不起跳
            var onSlab = bot.moveTo(new Vec(3.5, 1, 0.5));
            assertTrue(onSlab.await(TIMEOUT), () -> onSlab.future().toString());
            assertEquals(0.5, bot.view().player().position().y(), 1e-4);
            var down = bot.moveTo(new Vec(7.5, 0, 0.5));
            assertTrue(down.await(TIMEOUT), () -> down.future().toString());
            assertEquals(0, bot.view().player().position().y(), 1e-4);
        });
    }

    @Test
    void moveToFallsFromALedge() {
        withBot(new Pos(0.5, 2, 0.5), (x, z, chunk) -> {
            if (x == 0) {
                for (int bz = 0; bz < 16; bz++) {
                    for (int y = 0; y <= 1; y++) {
                        chunk.set(0, y, bz, Block.STONE);
                        chunk.set(1, y, bz, Block.STONE);
                    }
                }
            }
        }, (_, bot) -> {
            assertEquals(2, bot.view().player().position().y(), 1e-6);
            assertTrue(bot.moveTo(new Vec(4.5, 0, 0.5)).await(TIMEOUT));
            assertEquals(0, bot.view().player().position().y(), 1e-6);
        });
    }

    @Test
    void moveToWithoutAPathStopsAtTheNearestPoint() {
        // 远离原点：寻路结果末尾的 REPATH 节点坐标为 (0, 0, 0)，走向它会离开已加载的区块
        var spawn = new Pos(160.5, 0, 160.5);
        var target = new Vec(168.5, 0, 168.5);
        withBot(spawn, (x, z, chunk) -> {
            if (x == 10 && z == 10) {
                for (int bx = 7; bx <= 9; bx++) {
                    for (int bz = 7; bz <= 9; bz++) {
                        if (bx == 8 && bz == 8) {
                            continue;
                        }
                        for (int y = 0; y <= 2; y++) {
                            chunk.set(bx, y, bz, Block.STONE);
                        }
                    }
                }
            }
        }, (_, bot) -> {
            // 前提：这个场景下搜索达到节点上限，Minestom 在最近节点之后接上 REPATH 节点
            var snapshot = bot.inspect(_ -> ((BotInstance) bot).state().world().snapshot(spawn, MoveOptions.DEFAULT.maxDistance()));
            var path = PathGenerator.generate(snapshot, spawn, target, MoveOptions.DEFAULT.closeDistance(),
                    MoveOptions.DEFAULT.maxDistance(), PathPlanner.PATH_VARIANCE, new BoundingBox(0.6, 1.8, 0.6), true,
                    new GroundNodeGenerator(), null);
            assertEquals(PNode.Type.REPATH, path.getNodes().getLast().getType(), path::toString);

            var action = bot.moveTo(target);
            assertFalse(action.await(Duration.ofSeconds(20)));
            var cause = assertThrows(CompletionException.class, () -> action.future().join()).getCause();
            assertEquals(ActionCancelledException.NO_PATH, cause.getMessage());
            // 停在石头围成的格子旁边，没有走向原点
            var position = bot.view().player().position();
            assertTrue(Math.hypot(position.x() - target.x(), position.z() - target.z()) < 3, position.toString());
        });
    }

    @Test
    void movingIntoAnUnloadedChunkIsCancelled() {
        // 已加载的区块为 x 方向的 1 到 3（方块 16 到 63）
        withBot(new Pos(47.5, 0, 0.5), (_, _, _) -> {
        }, (_, bot) -> {
            var forward = bot.moveForward(100, MoveOptions.builder().direction(new Vec(1, 0, 0)).build());
            assertFalse(forward.await(TIMEOUT));
            assertEquals(ActionCancelledException.CHUNK_NOT_LOADED,
                    assertThrows(CompletionException.class, () -> forward.future().join()).getCause().getMessage());
            assertTrue(bot.view().player().position().x() < 64);
        });
    }

    @Test
    void moveToIsBlockedAfterThreeRepaths() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            var action = bot.moveTo(new Vec(40.5, 0, 0.5));
            // 每次服务端传送都触发重新寻路；第 4 次传送时重新寻路的次数已经用完
            for (int teleport = 1; teleport <= MoveToAction.MAX_REPATHS + 1; teleport++) {
                bot.await(state -> state.player().position().x() > 2, TIMEOUT);
                assertFalse(action.future().isDone());
                peer.push(new PlayerPositionAndLookPacket(teleport, new Pos(0.5, 0, 0.5), Vec.ZERO, 0, 0, 0));
                peer.barrier();
            }
            assertFalse(action.await(TIMEOUT));
            assertEquals(ActionCancelledException.BLOCKED,
                    assertThrows(CompletionException.class, () -> action.future().join()).getCause().getMessage());
        });
    }

    /**
     * 用 observeDuring 提交 moveTo，在它开始寻路的那个 tick 结束时停住调度线程，等测试线程把 packets 交给 bot 之后再继续：
     * 这些数据包在下一个 tick 之前应用，那时寻路的结果还没有被取回。返回之后每个 tick 结束时玩家的位置，在动作结束之后读取
     */
    private static List<Pos> moveToWithPacketsWhilePlanning(TestPeer peer, Bot bot, Vec target, ServerPacket... packets)
            throws InterruptedException {
        var positions = new ArrayList<Pos>();
        var planning = new CountDownLatch(1);
        var pushed = new CountDownLatch(1);
        var observed = bot.observeDuring(new Observer() {
            private boolean first = true;

            @Override
            public Subscription subscription() {
                return Subscription.of(BotTicked.class);
            }

            @Override
            public void onObservation(Observation observation, ObserverContext context) {
                if (first) {
                    first = false;
                    planning.countDown();
                    try {
                        assertTrue(pushed.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                } else {
                    positions.add(context.state().player().position());
                }
            }
        }, b -> b.moveTo(target));
        assertTrue(planning.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        peer.push(packets);
        pushed.countDown();
        assertTrue(observed.action().await(TIMEOUT), () -> observed.action().future().toString());
        observed.await(TIMEOUT);
        return positions;
    }

    @Test
    void aTeleportWhilePlanningDiscardsThePath() throws Exception {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            try {
                // 寻路从 (0.5, 0, 0.5) 出发；寻路期间被传送到 (6.5, 0, 10.5)，旧路径的第一个节点在 x = 1.5 附近
                var positions = moveToWithPacketsWhilePlanning(peer, bot, new Vec(6.5, 0, 0.5),
                        new PlayerPositionAndLookPacket(2, new Pos(6.5, 0, 10.5), Vec.ZERO, 0, 0, 0));
                assertTrue(positions.stream().allMatch(position -> position.x() > 5), positions::toString);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void blocksChangedWhilePlanningTriggerARepath() throws Exception {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            // 寻路期间在 x = 3 立起一堵两格高、z 从 -3 到 3 的墙，挡住寻路快照中的直线路径
            var wall = new ArrayList<ServerPacket>();
            for (int z = -3; z <= 3; z++) {
                for (int y = 0; y <= 1; y++) {
                    wall.add(new BlockChangePacket(new Vec(3, y, z), Block.STONE));
                }
            }
            try {
                var positions = moveToWithPacketsWhilePlanning(peer, bot, new Vec(6.5, 0, 0.5), wall.toArray(ServerPacket[]::new));
                // 寻路完成时发现路径上的方块被改变，立即重新寻路绕过墙：从来没有连续 20 tick 被挡住（那样才会因为被阻挡而重新寻路）
                var stuck = 0;
                var longest = 0;
                for (int i = 1; i < positions.size(); i++) {
                    var from = positions.get(i - 1);
                    var to = positions.get(i);
                    stuck = Math.hypot(to.x() - from.x(), to.z() - from.z()) < MoveToAction.STUCK_DISTANCE ? stuck + 1 : 0;
                    longest = Math.max(longest, stuck);
                }
                assertTrue(longest < MoveToAction.STUCK_TICKS, () -> "stuck for " + positions);
                assertEquals(6.5, bot.view().player().position().x(), 0.5);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void moveForwardIntoAWallIsCancelledAsBlocked() {
        withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
            if (x == 0) {
                for (int bz = 0; bz < 16; bz++) {
                    for (int y = 0; y <= 2; y++) {
                        chunk.set(3, y, bz, Block.STONE);
                    }
                }
            }
        }, (_, bot) -> {
            var forward = bot.moveForward(10, MoveOptions.builder().direction(new Vec(1, 0, 0)).build());
            assertFalse(forward.await(TIMEOUT));
            assertEquals(ActionCancelledException.BLOCKED,
                    assertThrows(CompletionException.class, () -> forward.future().join()).getCause().getMessage());
            // 玩家停在墙前：x 不超过 3 - 0.3
            assertTrue(bot.view().player().position().x() <= 2.7 + 1e-6);
            assertThrows(IllegalArgumentException.class,
                    () -> bot.moveForward(1, MoveOptions.builder().direction(new Vec(0, 1, 0)).build()));
        });
    }

    @Test
    void jumpToChoosesWalkingOrSprintingAndEndsWhenLanding() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            bot.await(state -> state.player().onGround(), TIMEOUT);
            // 水平距离 2 格：不超过 2 格时行走；起跳后再次着地即执行完毕
            var mark = peer.mark();
            var walk = bot.observeDuring(Observers.observations(PlayerMoved.class), b -> b.jumpTo(new Vec(2, -1, 0)));
            var moves = walk.await(TIMEOUT).observations();
            assertTrue(walk.action().await(TIMEOUT));
            assertTrue(moves.stream().anyMatch(moved -> !moved.onGround()), moves::toString);
            assertTrue(moves.getLast().onGround(), moves::toString);
            var landed = bot.view().player().position();
            assertTrue(Math.hypot(landed.x() - 2.5, landed.z() - 0.5) < 2, landed.toString());
            peer.barrier();
            assertTrue(peer.sentSince(mark, ClientInputPacket.class).stream().anyMatch(input -> input.jump() && !input.sprint()));
            assertTrue(peer.sentSince(mark, ClientEntityActionPacket.class).isEmpty());

            // 超过 2 格时疾跑起跳
            mark = peer.mark();
            var start = bot.view().player().position();
            assertTrue(bot.jumpTo(new Vec(6, -1, 0)).await(TIMEOUT));
            var sprinted = bot.view().player().position();
            assertTrue(Math.hypot(sprinted.x() - 6.5, sprinted.z() - 0.5) < Math.hypot(start.x() - 6.5, start.z() - 0.5), sprinted.toString());
            peer.barrier();
            assertTrue(peer.sentSince(mark, ClientInputPacket.class).stream().anyMatch(input -> input.jump() && input.sprint()));
            assertTrue(peer.sentSince(mark, ClientEntityActionPacket.class).stream()
                    .anyMatch(action -> action.action() == ClientEntityActionPacket.Action.START_SPRINTING));

            // 指定了移动方式时不按距离选择
            mark = peer.mark();
            assertTrue(bot.jumpTo(new Vec(12, -1, 0), MoveOptions.builder().mode(MovementMode.WALK).build()).await(TIMEOUT));
            peer.barrier();
            assertTrue(peer.sentSince(mark, ClientInputPacket.class).stream().anyMatch(ClientInputPacket::jump));
            assertTrue(peer.sentSince(mark, ClientInputPacket.class).stream().noneMatch(ClientInputPacket::sprint));
        });
    }

    @Test
    void jumpToEndsWhenTheJumpDoesNotLeaveTheGround() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            bot.await(state -> state.player().onGround(), TIMEOUT);
            // 跳跃力为 0：原版 jumpFromGround 不起跳，玩家一直着地
            peer.push(new EntityAttributesPacket(TestServer.ENTITY_ID,
                    List.of(new EntityAttributesPacket.Property(Attribute.JUMP_STRENGTH, 0, List.of()))));
            peer.barrier();
            assertTrue(bot.jumpTo(new Vec(2, -1, 0)).await(TIMEOUT));
            assertEquals(0, bot.view().player().position().y(), 1e-4);
        });
    }

    @Test
    void landingStopsFlyingOutsideSpectatorMode() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            bot.await(state -> state.player().onGround(), TIMEOUT);
            var mark = peer.mark();
            // 允许飞行并且正在飞行，但站在地上：原版 LocalPlayer.aiStep 在着地时停止飞行并发送 Player Abilities
            peer.push(new PlayerAbilitiesPacket((byte) 0x06, 0.05f, 0.1f));
            var abilities = peer.awaitSent(mark, ClientPlayerAbilitiesPacket.class, TIMEOUT);
            assertEquals(0, abilities.flags());
            peer.barrier();
            assertFalse(bot.view().player().abilities().flying());
            assertTrue(bot.view().player().abilities().allowFlying());
        });
    }

    @Test
    void sprintingFollowsTheVanillaConditions() {
        withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
            if (x == 0) {
                for (int bz = 0; bz < 16; bz++) {
                    for (int y = 0; y <= 2; y++) {
                        chunk.set(12, y, bz, Block.STONE);
                    }
                }
            }
        }, (peer, bot) -> {
            var east = MoveOptions.builder().direction(new Vec(1, 0, 0)).mode(MovementMode.SPRINT).build();
            // 饥饿值不大于 6：不开始疾跑；Player Input 的疾跑键照常按下
            peer.push(new UpdateHealthPacket(20, 6, 0));
            peer.barrier();
            var mark = peer.mark();
            assertTrue(bot.moveForward(2, east).await(TIMEOUT));
            peer.barrier();
            assertTrue(peer.sentSince(mark, ClientEntityActionPacket.class).isEmpty());
            assertTrue(peer.sentSince(mark, ClientInputPacket.class).stream().anyMatch(ClientInputPacket::sprint));

            // 失明：不开始疾跑
            peer.push(new UpdateHealthPacket(20, 20, 5),
                    new EntityEffectPacket(TestServer.ENTITY_ID, new Potion(PotionEffect.BLINDNESS, 0, 100)));
            peer.barrier();
            mark = peer.mark();
            assertTrue(bot.moveForward(2, east).await(TIMEOUT));
            peer.barrier();
            assertTrue(peer.sentSince(mark, ClientEntityActionPacket.class).isEmpty());

            // 开始疾跑，撞上 x = 12 的墙时停止疾跑（迎面的碰撞）
            peer.push(new RemoveEntityEffectPacket(TestServer.ENTITY_ID, PotionEffect.BLINDNESS));
            peer.barrier();
            mark = peer.mark();
            assertFalse(bot.moveForward(20, east).await(TIMEOUT));
            peer.barrier();
            var commands = peer.sentSince(mark, ClientEntityActionPacket.class).stream().map(ClientEntityActionPacket::action).toList();
            assertEquals(List.of(ClientEntityActionPacket.Action.START_SPRINTING, ClientEntityActionPacket.Action.STOP_SPRINTING), commands);
        });
    }

    @Test
    void jumpToTheCurrentBlockJumpsStraightUp() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            bot.await(state -> state.player().onGround(), TIMEOUT);
            var mark = peer.mark();
            // 与目标顶面中心的水平距离小于 0.1：空中不加速，原地起跳
            assertTrue(bot.jumpTo(new Vec(0, -1, 0)).await(TIMEOUT));
            var position = bot.view().player().position();
            assertEquals(0.5, position.x(), 1e-9);
            assertEquals(0.5, position.z(), 1e-9);
            assertTrue(bot.view().player().onGround());
            peer.barrier();
            var inputs = peer.sentSince(mark, ClientInputPacket.class);
            assertTrue(inputs.stream().anyMatch(ClientInputPacket::jump));
            assertTrue(inputs.stream().noneMatch(input -> input.forward() || input.backward() || input.left() || input.right()));
        });
    }

    @Test
    void suspensionStopsPositionButNotRotation() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            bot.suspendMovement();
            assertTrue(bot.rotate(30, 0).await(TIMEOUT));
            assertTrue(bot.view().movementSuspended());
            var start = bot.view().player().position();
            var forward = bot.moveForward(3);
            var mark = peer.mark();
            waitTicks(peer, 25);
            assertFalse(forward.future().isDone());
            var during = bot.view().player().position();
            assertEquals(start.x(), during.x(), 1e-9);
            assertEquals(start.z(), during.z(), 1e-9);
            // 每 20 tick 至少发送一次位置；Player Input 按暂停开始时的期望方向照常发送
            assertFalse(peer.sentSince(mark, ClientPlayerPositionPacket.class).isEmpty()
                    && peer.sentSince(mark, ClientPlayerPositionAndRotationPacket.class).isEmpty());
            bot.resumeMovement();
            assertTrue(forward.await(TIMEOUT));
            assertFalse(bot.view().movementSuspended());
        });
    }

    @Test
    void movesReachedDuringSuspensionWaitAndKeepTheManualRotation() {
        withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
            if (x == 0 && z == 0) {
                chunk.set(2, -1, 0, Block.DIRT);
            }
        }, (peer, bot) -> {
            bot.await(state -> state.player().onGround(), TIMEOUT);
            bot.suspendMovement();
            bot.await(state -> state.movementSuspended(), TIMEOUT);
            assertTrue(bot.rotate(30, 10).await(TIMEOUT));
            var move = bot.moveTo(new Vec(0.5, 0, 6.5));
            var after = bot.rotate(-45, 0);
            // 手部动作结束之后朝向回到 rotate 设置的朝向：暂停期间轮到的 moveTo 没有开始，没有清除它
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(new Vec(2, -1, 0));
            var finished = peer.awaitSent(mark, ClientPlayerActionPacket.class,
                    packet -> packet.status() == ClientPlayerActionPacket.Status.FINISHED_DIGGING, TIMEOUT);
            peer.push(new AcknowledgeBlockChangePacket(finished.sequence()));
            assertTrue(dig.await(TIMEOUT));
            bot.await(state -> state.player().position().yaw() == 30 && state.player().position().pitch() == 10, TIMEOUT);
            assertFalse(move.future().isDone());
            assertFalse(after.future().isDone());
            // 恢复之后 moveTo 才开始，排在它后面的 rotate 随之执行
            bot.resumeMovement();
            assertTrue(move.await(TIMEOUT));
            assertTrue(after.await(TIMEOUT));
            assertEquals(-45, bot.view().player().position().yaw());
        });
    }

    private static SpawnEntityPacket vehicle(int id, EntityType type, Pos position) {
        return new SpawnEntityPacket(id, UUID.randomUUID(), type, position, position.yaw(), 0, Vec.ZERO);
    }

    @Test
    void ridingABoatFollowsTheSeatAndControlsTheBoat() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            var boat = new Pos(2.5, 0, 0.5, 90, 0);
            // 载具不在模型中：整个 Set Passengers 被忽略
            peer.push(new SetPassengersPacket(300, List.of(TestServer.ENTITY_ID)));
            peer.push(vehicle(300, EntityType.OAK_BOAT, boat), new SetPassengersPacket(300, List.of(TestServer.ENTITY_ID)));
            peer.barrier();
            waitTicks(peer, 2);
            var mark = peer.mark();
            waitTicks(peer, 3);
            var player = bot.view().player();
            // 座位：船的位置加上船高度的三分之一，减去玩家的载具挂点 0.6；新登上船时偏航角设为船的偏航角
            assertEquals(2.5, player.position().x(), 1e-9);
            assertEquals(0.5625 / 3 - 0.6, player.position().y(), 1e-9);
            assertEquals(0.5, player.position().z(), 1e-9);
            assertEquals(90, player.position().yaw());
            // 每 tick 发送 Set Player Rotation（两个标记为 false）与 Move Vehicle，不发送移动包
            var sent = peer.sentSince(mark);
            assertTrue(sent.stream().noneMatch(packet -> packet instanceof ClientPlayerPositionPacket
                    || packet instanceof ClientPlayerPositionAndRotationPacket), sent::toString);
            var rotations = peer.sentSince(mark, ClientPlayerRotationPacket.class);
            var vehicleMoves = peer.sentSince(mark, ClientVehicleMovePacket.class);
            var ticks = peer.sentSince(mark, ClientTickEndPacket.class).size();
            assertEquals(ticks, rotations.size());
            assertEquals(ticks, vehicleMoves.size());
            assertFalse(rotations.getFirst().onGround());
            assertEquals(boat, vehicleMoves.getFirst().position());

            // 下船之后恢复移动模拟与移动包
            peer.push(new SetPassengersPacket(300, List.of()));
            peer.barrier();
            mark = peer.mark();
            waitTicks(peer, 25);
            assertTrue(peer.sentSince(mark, ClientVehicleMovePacket.class).isEmpty());
            assertFalse(peer.sentSince(mark, ClientPlayerPositionPacket.class).isEmpty()
                    && peer.sentSince(mark, ClientPlayerPositionAndRotationPacket.class).isEmpty());
        });
    }

    @Test
    void ridingAMinecartDoesNotControlItAndAnUnloadedVehicleSkipsThePlayerTick() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            peer.push(vehicle(301, EntityType.MINECART, new Pos(1.5, 0, 0.5)), new SetPassengersPacket(301, List.of(TestServer.ENTITY_ID)));
            peer.barrier();
            waitTicks(peer, 1);
            var mark = peer.mark();
            waitTicks(peer, 3);
            assertTrue(peer.sentSince(mark, ClientVehicleMovePacket.class).isEmpty());
            assertFalse(peer.sentSince(mark, ClientPlayerRotationPacket.class).isEmpty());
            // 矿车的乘客挂点取 Minestom 数据中的 PASSENGER
            var attachment = EntityType.MINECART.entityAttachments("PASSENGER").getFirst();
            assertEquals(attachment.y() - 0.6, bot.view().player().position().y(), 1e-9);

            // 矿车自己乘坐的船所在区块未加载：原版从最下层的载具开始执行乘客，本地玩家整个 tick 不执行，只发送 Client Tick End
            peer.push(vehicle(303, EntityType.OAK_BOAT, new Pos(100.5, 0, 0.5)), new SetPassengersPacket(303, List.of(301)));
            peer.barrier();
            waitTicks(peer, 1);
            mark = peer.mark();
            waitTicks(peer, 3);
            for (ClientPacket packet : peer.sentSince(mark)) {
                assertInstanceOf(ClientTickEndPacket.class, packet);
            }

            // 载具所在区块未加载：同样只发送 Client Tick End
            peer.push(vehicle(302, EntityType.OAK_BOAT, new Pos(100.5, 0, 0.5)), new SetPassengersPacket(302, List.of(TestServer.ENTITY_ID)));
            peer.barrier();
            waitTicks(peer, 1);
            mark = peer.mark();
            waitTicks(peer, 3);
            for (ClientPacket packet : peer.sentSince(mark)) {
                assertInstanceOf(ClientTickEndPacket.class, packet);
            }
        });
    }

    @Test
    void seatsOfHorsesAndCamelsFollowTheVanillaAttachments() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            // 马：Minestom 数据中的 PASSENGER 挂点，没有人立时不加偏移
            peer.push(vehicle(310, EntityType.HORSE, new Pos(2.5, 0, 0.5)), new SetPassengersPacket(310, List.of(TestServer.ENTITY_ID)));
            peer.barrier();
            waitTicks(peer, 2);
            var horse = EntityType.HORSE.entityAttachments("PASSENGER").getFirst();
            assertEquals(horse.y() - 0.6, bot.view().player().position().y(), 1e-9);

            // 骆驼：站立时座位高度为 2.375 − 0.375，第一个乘客向前 0.5；坐下（上次姿态改变的 tick 为负数）时为
            // 坐下尺寸的高度 0.945 − 0.375 再加 0.2
            peer.push(new SetTimePacket(1000, Map.of()));
            peer.push(vehicle(311, EntityType.CAMEL, new Pos(5.5, 0, 0.5)), new SetPassengersPacket(311, List.of(TestServer.ENTITY_ID)));
            peer.barrier();
            waitTicks(peer, 2);
            var standing = bot.view().player().position();
            assertEquals(2.0 - 0.6, standing.y(), 1e-6);
            assertEquals(1.0, standing.z(), 1e-6);
            peer.push(new EntityMetaDataPacket(311, Map.of(
                    MetadataDef.POSE.index(), Metadata.Pose(EntityPose.SITTING),
                    MetadataDef.Camel.LAST_POSE_CHANGE_TICK.index(), Metadata.VarLong(-500))));
            peer.barrier();
            waitTicks(peer, 2);
            assertEquals(0.77 - 0.6, bot.view().player().position().y(), 1e-6);
        });
    }

    @Test
    void horsesAnimateRearingEveryTickUnlessFrozenWithoutPlayerPassengers() {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            var attachment = EntityType.HORSE.entityAttachments("PASSENGER").getFirst();
            Map<Integer, Metadata.Entry<?>> rearing = Map.of(MetadataDef.AbstractHorse.ABSTRACT_HORSE_FLAGS.index(), Metadata.Byte((byte) 0x20));
            // 原版对每匹马每 tick 更新人立动画，与是否被乘坐无关：人立 6 tick 之后进度为 1，登上它的第一个 tick 乘客就随之后仰
            peer.push(vehicle(320, EntityType.HORSE, new Pos(2.5, 0, 0.5)), new EntityMetaDataPacket(320, rearing));
            peer.barrier();
            waitTicks(peer, 7);
            var seats = bot.observe(Observers.observations(PlayerMoved.class, moved -> Math.abs(moved.to().x() - 2.5) < 1e-9));
            peer.push(new SetPassengersPacket(320, List.of(TestServer.ENTITY_ID)));
            peer.barrier();
            waitTicks(peer, 2);
            seats.close();
            var seat = seats.observer().observations().getFirst().to();
            assertEquals(attachment.y() + 0.15 - 0.6, seat.y(), 1e-6);
            assertEquals(0.5 + attachment.z() - 0.7, seat.z(), 1e-6);

            // 冻结并且不在步进中时，没有玩家乘客的马不 tick：进度保持 0；登上之后它有了玩家乘客，从 0 开始更新
            peer.push(new SetPassengersPacket(320, List.of()), new SetTickStatePacket(20, true));
            peer.push(vehicle(321, EntityType.HORSE, new Pos(5.5, 0, 0.5)), new EntityMetaDataPacket(321, rearing));
            peer.barrier();
            waitTicks(peer, 7);
            var frozenSeats = bot.observe(Observers.observations(PlayerMoved.class, moved -> Math.abs(moved.to().x() - 5.5) < 1e-9));
            peer.push(new SetPassengersPacket(321, List.of(TestServer.ENTITY_ID)));
            peer.barrier();
            waitTicks(peer, 3);
            frozenSeats.close();
            var frozen = frozenSeats.observer().observations();
            assertEquals(attachment.y() - 0.6, frozen.get(0).to().y(), 1e-6);
            assertEquals(attachment.y() + 0.15 * 0.45 - 0.6, frozen.get(1).to().y(), 1e-6);
        });
    }

    @Test
    void handActionTargetOverridesManualRotation() {
        withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
            if (x == 0 && z == 0) {
                chunk.set(2, -1, 0, Block.DIRT);
            }
        }, (peer, bot) -> {
            assertTrue(bot.rotate(0, 0).await(TIMEOUT));
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(new Vec(2, -1, 0));
            var finished = peer.awaitSent(mark, ClientPlayerActionPacket.class,
                    packet -> packet.status() == ClientPlayerActionPacket.Status.FINISHED_DIGGING, TIMEOUT);
            var rotation = sentRotation(peer.sentSince(mark).stream().filter(MovementBotTest::sendsRotation).findFirst().orElseThrow());
            var expected = Rotation.lookAt(new Vec(0.5, 1.62, 0.5), new Vec(2.5, -0.5, 0.5));
            assertEquals(expected.yaw(), rotation.yaw(), 1e-4);
            assertEquals(expected.pitch(), rotation.pitch(), 1e-4);
            peer.push(new AcknowledgeBlockChangePacket(finished.sequence()));
            assertTrue(dig.await(TIMEOUT));
        });
    }
}
