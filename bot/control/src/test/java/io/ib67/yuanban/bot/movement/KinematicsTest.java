package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.interaction.HandController;
import io.ib67.yuanban.bot.player.LocalPlayer;
import io.ib67.yuanban.bot.registry.TagTable;
import io.ib67.yuanban.bot.testing.TestWorlds;
import io.ib67.yuanban.bot.view.ActiveEffect;
import io.ib67.yuanban.bot.view.EntityMetaIndex;
import io.ib67.yuanban.bot.world.ClientWorld;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 移动计算与 wiki Transportation、Jumping 页记载的数值一致。
 */
class KinematicsTest {
    private static final Vec EAST = new Vec(1, 0, 0);

    /**
     * 视距 12、向东 12 个区块的平地：测试中的玩家会持续向东移动
     */
    private static ClientWorld flatWorld() {
        return flatWorld(Block.STONE, -1);
    }

    /**
     * 地面最后一层为 {@code floor}（{@code floorY} 之上的那一层），下面为石头
     */
    private static ClientWorld flatWorld(Block floor, int floorY) {
        var world = new ClientWorld(TestWorlds.OVERWORLD, 0, TestWorlds.MIN_Y, TestWorlds.HEIGHT, 12, 12);
        for (int x = -2; x <= 12; x++) {
            for (int z = -2; z <= 2; z++) {
                var sections = TestWorlds.flatSections(0, TestWorlds.BIOME);
                if (floorY >= 0) {
                    TestWorlds.fill(sections, floorY, floorY, floor);
                }
                TestWorlds.install(world, x, z, sections);
            }
        }
        return world;
    }

    private static LocalPlayer standingPlayer(ClientWorld world) {
        var player = new LocalPlayer(UUID.randomUUID(), "test");
        player.position(new Pos(0.5, 0, 0.5, -90, 0));
        // 先让玩家落地
        for (int i = 0; i < 5; i++) {
            Kinematics.step(player, world, TagTable.EMPTY, MoveIntent.NONE, false, null);
        }
        assertTrue(player.onGround());
        return player;
    }

    /**
     * 持续时间无限、没有任何标记的效果
     */
    private static ActiveEffect infinite(Effect effect, int amplifier) {
        return new ActiveEffect(effect, amplifier, ActiveEffect.INFINITE_DURATION, false, false, false, false);
    }

    /**
     * 持续移动直到速度稳定，返回每秒的水平速度
     */
    private static double steadySpeed(MovementMode mode) {
        var world = flatWorld();
        var player = standingPlayer(world);
        if (mode == MovementMode.SPRINT) {
            player.attributes().addModifier(AttributeType.Builtin.MOVEMENT_SPEED, MovementController.SPRINTING);
        }
        if (mode == MovementMode.SNEAK) {
            player.sneaking(true);
        }
        var intent = new MoveIntent(EAST, mode, false);
        var sprinting = mode == MovementMode.SPRINT;
        for (int i = 0; i < 60; i++) {
            Kinematics.step(player, world, TagTable.EMPTY, intent, sprinting, null);
        }
        var before = player.position().x();
        Kinematics.step(player, world, TagTable.EMPTY, intent, sprinting, null);
        return (player.position().x() - before) * 20;
    }

    @Test
    void walking() {
        assertEquals(4.317, steadySpeed(MovementMode.WALK), 0.001);
    }

    @Test
    void sprinting() {
        assertEquals(5.612, steadySpeed(MovementMode.SPRINT), 0.001);
    }

    @Test
    void sneaking() {
        assertEquals(1.3, steadySpeed(MovementMode.SNEAK), 0.01);
    }

    private static double jumpHeight(int jumpBoostLevel) {
        var world = flatWorld();
        var player = standingPlayer(world);
        if (jumpBoostLevel > 0) {
            player.effects().put(infinite(Effect.JUMP_BOOST, jumpBoostLevel - 1));
        }
        var start = player.position().y();
        Kinematics.step(player, world, TagTable.EMPTY, new MoveIntent(Vec.ZERO, MovementMode.WALK, true), false, null);
        var max = player.position().y();
        for (int i = 0; i < 40; i++) {
            Kinematics.step(player, world, TagTable.EMPTY, MoveIntent.NONE, false, null);
            max = Math.max(max, player.position().y());
        }
        assertTrue(player.onGround());
        return max - start;
    }

    @Test
    void jumpHeight() {
        assertEquals(1.2522, jumpHeight(0), 0.0001);
    }

    @Test
    void jumpBoostOne() {
        assertEquals(1.8361, jumpHeight(1), 0.0001);
    }

    @Test
    void jumpBoostTwo() {
        assertEquals(2.5168, jumpHeight(2), 0.0001);
    }

    private static void attribute(LocalPlayer player, AttributeType attribute, double value) {
        player.attributes().apply(List.of(new Attribute(attribute, value)));
    }

    private static LocalPlayer airbornePlayer(ClientWorld world, double y) {
        var player = new LocalPlayer(UUID.randomUUID(), "test");
        player.position(new Pos(0.5, y, 0.5, -90, 0));
        return player;
    }

    /**
     * 飞行时持续向东移动，返回稳定之后每秒的水平速度
     */
    private static double flyingSpeed(boolean sprinting) {
        var world = flatWorld();
        var player = airbornePlayer(world, 10);
        // 允许飞行并且正在飞行，飞行速度 0.05（原版默认值）
        player.abilities().apply(new ClientboundPlayerAbilitiesPacket(false, true, true, false, 0.05f, 0.1f));
        var intent = new MoveIntent(EAST, sprinting ? MovementMode.SPRINT : MovementMode.WALK, false);
        // 空中的阻力为 0.91，速度收敛得比地面慢；空中没有碰撞，飞出已加载的区块之前把玩家放回起点，速度不变
        for (int i = 0; i < 200; i++) {
            if (player.position().x() > 100) {
                player.position(player.position().withX(0.5));
            }
            Kinematics.step(player, world, TagTable.EMPTY, intent, sprinting, null);
        }
        player.position(player.position().withX(0.5));
        var before = player.position().x();
        Kinematics.step(player, world, TagTable.EMPTY, intent, sprinting, null);
        // 飞行时竖直速度每 tick 乘以 0.6，不受重力影响
        assertEquals(10, player.position().y(), 1e-9);
        return (player.position().x() - before) * 20;
    }

    @Test
    void flyingUsesTheFlyingSpeedOfTheAbilities() {
        // wiki Transportation：飞行 10.89 m/s，疾跑飞行 21.78 m/s
        assertEquals(10.89, flyingSpeed(false), 0.01);
        assertEquals(21.78, flyingSpeed(true), 0.01);
    }

    @Test
    void usingAnItemSlowsTheInputDown() {
        var world = flatWorld();
        var player = standingPlayer(world);
        var intent = new MoveIntent(EAST, MovementMode.WALK, false);
        for (int i = 0; i < 60; i++) {
            Kinematics.step(player, world, TagTable.EMPTY, intent, false, HandController.DEFAULT_USE_EFFECTS);
        }
        var before = player.position().x();
        Kinematics.step(player, world, TagTable.EMPTY, intent, false, HandController.DEFAULT_USE_EFFECTS);
        // 原版 modifyInput：输入乘以物品的速度系数（默认 0.2），稳定速度为步行的 0.2 倍
        assertEquals(4.317 * 0.2, (player.position().x() - before) * 20, 0.001);
    }

    /**
     * 从高处由静止开始下落 ticks 个 tick 之后的竖直速度
     */
    private static double verticalVelocityAfter(LocalPlayer player, ClientWorld world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            Kinematics.step(player, world, TagTable.EMPTY, MoveIntent.NONE, false, null);
        }
        return player.velocity().y();
    }

    @Test
    void slowFallingLevitationAndNoGravity() {
        var world = flatWorld();
        // 缓降：下落时重力不超过 0.01，竖直速度趋向 -0.01 × 0.98 / (1 - 0.98) = -0.49
        var slow = airbornePlayer(world, 300);
        slow.effects().put(infinite(Effect.SLOW_FALLING, 0));
        assertEquals(-0.49, verticalVelocityAfter(slow, world, 400), 1e-3);

        // 飘浮：竖直速度每 tick 向 0.05 × (等级) 靠近 20%，再乘以 0.98，趋向 0.0098 / 0.216
        var levitating = airbornePlayer(world, 10);
        levitating.effects().put(infinite(Effect.LEVITATION, 0));
        assertEquals(0.0098 / 0.216, verticalVelocityAfter(levitating, world, 200), 1e-4);
        assertTrue(levitating.position().y() > 10);

        // NoGravity：没有重力，静止的玩家停在空中
        var floating = airbornePlayer(world, 10);
        var noGravity = EntityMetaIndex.HAS_NO_GRAVITY.index();
        floating.metadata().put(noGravity, new BooleanEntityMetadata(noGravity, MetadataTypes.BOOLEAN, true));
        assertEquals(0, verticalVelocityAfter(floating, world, 20), 0.0);
        assertEquals(10, floating.position().y(), 0.0);
    }

    @Test
    void movementAttributesAndJumpRules() {
        var world = flatWorld();
        // air_drag_modifier 为 0：空气阻力为 1，竖直速度每 tick 只减去重力
        var noDrag = airbornePlayer(world, 50);
        attribute(noDrag, AttributeType.Builtin.AIR_DRAG_MODIFIER, 0);
        assertEquals(-0.08, verticalVelocityAfter(noDrag, world, 1), 1e-9);
        assertEquals(-0.16, verticalVelocityAfter(noDrag, world, 1), 1e-9);

        // 起跳时竖直速度取起跳力与当前竖直速度中较大的一个
        var launched = standingPlayer(world);
        launched.velocity(new Vec(0, 1.0, 0));
        Kinematics.step(launched, world, TagTable.EMPTY, new MoveIntent(Vec.ZERO, MovementMode.WALK, true), false, null);
        assertEquals((1.0 - 0.08) * 0.98, launched.velocity().y(), 1e-6);

        // 水平速度的平方小于 9.0E-6 时在这个 tick 开始时归零
        var creeping = standingPlayer(world);
        var x = creeping.position().x();
        creeping.velocity(new Vec(0.002, creeping.velocity().y(), 0));
        Kinematics.step(creeping, world, TagTable.EMPTY, MoveIntent.NONE, false, null);
        assertEquals(x, creeping.position().x());
    }

    @Test
    void movementEfficiencyCancelsTheSpeedFactorOfSoulSand() {
        var world = new ClientWorld(TestWorlds.OVERWORLD, 0, TestWorlds.MIN_Y, TestWorlds.HEIGHT, 12, 12);
        for (int x = -2; x <= 12; x++) {
            for (int z = -2; z <= 2; z++) {
                var sections = TestWorlds.flatSections(0, TestWorlds.BIOME);
                TestWorlds.fill(sections, -1, -1, Block.SOUL_SAND);
                TestWorlds.install(world, x, z, sections);
            }
        }
        var player = new LocalPlayer(UUID.randomUUID(), "test");
        player.position(new Pos(0.5, 0, 0.5, -90, 0));
        attribute(player, AttributeType.Builtin.MOVEMENT_EFFICIENCY, 1);
        var intent = new MoveIntent(EAST, MovementMode.WALK, false);
        for (int i = 0; i < 60; i++) {
            Kinematics.step(player, world, TagTable.EMPTY, intent, false, null);
        }
        var before = player.position().x();
        Kinematics.step(player, world, TagTable.EMPTY, intent, false, null);
        // 灵魂沙的速度系数 0.4 按 movement_efficiency 向 1 插值：为 1 时与平地行走相同
        assertEquals(4.317, (player.position().x() - before) * 20, 0.001);
    }

    @Test
    void theSupportingBlockDecidesTheBlockBelow() {
        var world = new ClientWorld(TestWorlds.OVERWORLD, 0, TestWorlds.MIN_Y, TestWorlds.HEIGHT, 12, 12);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                var sections = TestWorlds.airSections(TestWorlds.BIOME);
                TestWorlds.fill(sections, -64, -3, Block.STONE);
                if (x == 0 && z == 0) {
                    TestWorlds.set(sections, 0, -1, 0, Block.ICE);
                }
                TestWorlds.install(world, x, z, sections);
            }
        }
        // 玩家中心在 x = 1.2 的空气上方，包围盒（x 从 0.9 到 1.5）压在 x = 0 的冰块边缘
        var player = new LocalPlayer(UUID.randomUUID(), "test");
        player.position(new Pos(1.2, 0, 0.5, -90, 0));
        for (int i = 0; i < 3; i++) {
            Kinematics.step(player, world, TagTable.EMPTY, MoveIntent.NONE, false, null);
        }
        assertTrue(player.onGround());
        assertEquals(new Vec(0, -1, 0), player.supportingBlock());
        // 原版 getBlockPosBelowThatAffectsMyMovement 取支撑方块所在的列
        assertEquals(new Vec(0, -1, 0), Kinematics.below(world, TagTable.EMPTY, player, player.position()));
    }
}
