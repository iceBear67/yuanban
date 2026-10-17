package io.ib67.yuanban.bot.movement;

import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotationTest {
    private static final float EPSILON = 1e-4f;

    @Test
    void lookAtUsesTheProtocolFormula() {
        // 正北：z 减小，偏航角为 180 度
        var north = Rotation.lookAt(Vec.ZERO, new Vec(0, 0, -1));
        assertEquals(180.0f, Math.abs(north.yaw()), EPSILON);
        // 正东：x 增大，偏航角为 -90 度（wiki Set Player Rotation）
        var east = Rotation.lookAt(Vec.ZERO, new Vec(1, 0, 0));
        assertEquals(-90.0f, east.yaw(), EPSILON);
        // 正南：z 增大，偏航角为 0
        assertEquals(0.0f, Rotation.lookAt(Vec.ZERO, new Vec(0, 0, 1)).yaw(), EPSILON);
        // 正西：x 减小，偏航角为 90 度
        assertEquals(90.0f, Rotation.lookAt(Vec.ZERO, new Vec(-1, 0, 0)).yaw(), EPSILON);
        // 正上方：俯仰角 -90；正下方：90
        assertEquals(-90.0f, Rotation.lookAt(Vec.ZERO, new Vec(0, 1, 0)).pitch(), EPSILON);
        assertEquals(90.0f, Rotation.lookAt(Vec.ZERO, new Vec(0, -1, 0)).pitch(), EPSILON);
    }

    @Test
    void horizontalOnlySetsTheYaw() {
        var rotation = Rotation.horizontal(0, 1);
        assertEquals(0.0f, rotation.yaw(), EPSILON);
        assertEquals(0.0f, rotation.pitch(), EPSILON);
        assertEquals(-45.0f, Rotation.horizontal(1, 1).yaw(), EPSILON);
    }

    @Test
    void theViewVectorPointsTowardsTheLookDirection() {
        // 偏航角 0、俯仰角 0：看向 +z
        var forward = new Rotation(0, 0).viewVector();
        assertEquals(0.0f, forward.x(), EPSILON);
        assertEquals(0.0f, forward.y(), EPSILON);
        assertEquals(1.0f, forward.z(), EPSILON);
        // 偏航角 -90：看向 +x
        var east = new Rotation(-90, 0).viewVector();
        assertEquals(1.0f, east.x(), EPSILON);
        assertEquals(0.0f, east.z(), EPSILON);
        // 俯仰角 -90：看向 +y
        var up = new Rotation(0, -90).viewVector();
        assertEquals(1.0f, up.y(), EPSILON);
        // 偏航角 180：看向 -z
        var north = new Rotation(180, 0).viewVector();
        assertEquals(-1.0f, north.z(), EPSILON);
        assertEquals(0.0f, north.x(), EPSILON);
    }

    @Test
    void theRotationArbiterPrefersTheHandThenTheMovementThenTheManualRotation() {
        var arbiter = new RotationArbiter();
        var previous = new Rotation(10, 20);
        var hand = new Rotation(30, 40);
        var move = new Rotation(50, 60);
        // 没有手部与移动目标时保持上一 tick 的朝向
        assertEquals(previous, arbiter.decide(null, null, false, previous));
        // 手部动作优先于移动动作
        assertEquals(hand, arbiter.decide(hand, move, true, previous));
        // 移动动作优先于手动设置的朝向
        arbiter.manual(new Rotation(70, 80));
        assertEquals(move, arbiter.decide(null, move, true, previous));
        // 移动中方向为零时保持上一 tick 的朝向，不使用手动设置的朝向
        assertEquals(previous, arbiter.decide(null, null, true, previous));
        // 没有移动时使用手动设置的朝向
        assertEquals(new Rotation(70, 80), arbiter.decide(null, null, false, previous));
    }

    @Test
    void clearingTheManualRotationFallsBackToThePreviousOne() {
        var arbiter = new RotationArbiter();
        var manual = new Rotation(70, 80);
        var previous = new Rotation(0, 0);
        arbiter.manual(manual);
        assertEquals(manual, arbiter.decide(null, null, false, previous));
        // moveTo 开始或服务端设置朝向时清除手动设置的朝向
        arbiter.clearManual();
        assertEquals(previous, arbiter.decide(null, null, false, previous));
    }

    @Test
    void theSineTableMatchesTheVanillaValues() {
        // 原版 Mth.sin 的 65536 项 float 查找表
        assertEquals(0.0f, SineTable.sin(0), 1e-9f);
        assertEquals(1.0f, SineTable.sin(Math.PI / 2), 1e-7f);
        assertEquals(1.0f, SineTable.cos(0), 1e-7f);
        assertEquals(0.0f, SineTable.cos(Math.PI / 2), 1e-6f);
        // 与 Math.sin 的差异在一项查找表的精度之内
        for (int degrees = 0; degrees < 360; degrees += 7) {
            var radians = Math.toRadians(degrees);
            assertEquals((float) Math.sin(radians), SineTable.sin(radians), 1e-4f, "sin " + degrees);
            assertEquals((float) Math.cos(radians), SineTable.cos(radians), 1e-4f, "cos " + degrees);
        }
    }

    @Test
    void rotatingAVectorAroundTheAxesFollowsTheVanillaVec3Methods() {
        var vector = new Vec(1, 0, 0);
        // 绕 x 轴转过 90 度：(1, 0, 0) 不变
        var x = SineTable.xRot(vector, (float) (Math.PI / 2));
        assertEquals(1.0f, x.x(), EPSILON);
        assertEquals(0.0f, x.y(), EPSILON);
        // 绕 y 轴转过 90 度：(1, 0, 0) → (0, 0, -1)
        var y = SineTable.yRot(vector, (float) (Math.PI / 2));
        assertEquals(0.0f, y.x(), EPSILON);
        assertEquals(-1.0f, y.z(), EPSILON);
        // 旋转之后长度不变
        assertEquals(1.0, SineTable.yRot(new Vec(0.6, 0.8, 0), 1.234f).length(), 1e-6);
    }

    @Test
    void directionsHaveUnitNormals() {
        for (var direction : org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.values()) {
            var normal = io.ib67.yuanban.bot.interaction.Directions.normal(direction);
            assertEquals(1.0, normal.length(), 1e-9, direction.name());
        }
    }
}
