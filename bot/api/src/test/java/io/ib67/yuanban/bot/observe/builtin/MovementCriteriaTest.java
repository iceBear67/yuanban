package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Region;
import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 移动条件：位移长度、方向与当前位置，各项同时满足才算满足。
 */
class MovementCriteriaTest {
    private static MovementTrack track(Vec start, Vec current) {
        return new MovementTrack(1, EntityType.ZOMBIE, start, current, false);
    }

    @Test
    void atLeastChecksTheDisplacementLength() {
        var criteria = MovementCriteria.create().atLeast(2.0);
        assertTrue(criteria.test(track(Vec.ZERO, new Vec(2, 0, 0))));
        assertFalse(criteria.test(track(Vec.ZERO, new Vec(1.9, 0, 0))));
        // 长度按整个位移计算，不只是水平方向
        assertTrue(criteria.test(track(Vec.ZERO, new Vec(0, 0, 2))));
    }

    @Test
    void towardsChecksTheAngleOfTheDisplacement() {
        var criteria = MovementCriteria.create().towards(new Vec(10, 0, 0), 10);
        assertTrue(criteria.test(track(Vec.ZERO, new Vec(1, 0, 0))));
        // 与指向目标的夹角超过范围
        assertFalse(criteria.test(track(Vec.ZERO, new Vec(1, 1, 0))));
        // 没有位移时无法判断方向
        assertFalse(criteria.test(track(Vec.ZERO, Vec.ZERO)));
        // 目标就在起点上时同样无法判断
        assertFalse(criteria.test(track(Vec.ZERO, new Vec(0, 1, 0))));
    }

    @Test
    void intoChecksTheCurrentPosition() {
        var criteria = MovementCriteria.create().into(Region.around(new Vec(10, 0, 10), 2));
        assertTrue(criteria.test(track(Vec.ZERO, new Vec(10, 0, 10))));
        assertTrue(criteria.test(track(Vec.ZERO, new Vec(12, 0, 10))));
        assertFalse(criteria.test(track(Vec.ZERO, new Vec(12.5, 0, 10))));
    }

    @Test
    void everyConditionMustHold() {
        var criteria = MovementCriteria.create()
                .atLeast(1.0)
                .towards(new Vec(10, 0, 0), 5)
                .into(Region.around(Vec.ZERO, 2));
        assertTrue(criteria.test(track(Vec.ZERO, new Vec(1.5, 0, 0))));
        // 位移太小
        assertFalse(criteria.test(track(Vec.ZERO, new Vec(0.5, 0, 0))));
        // 方向不对
        assertFalse(criteria.test(track(Vec.ZERO, new Vec(1.5, 0, 1))));
        // 当前位置在区域之外
        assertFalse(criteria.test(track(Vec.ZERO, new Vec(3, 0, 0))));
    }

    @Test
    void criteriaWithoutAConditionAreRejected() {
        // 至少设置 atLeast、towards、into 中的一项
        assertThrows(IllegalArgumentException.class, () -> MovementCriteria.create().validate());
        assertThrows(IllegalArgumentException.class,
                () -> Observers.movementOf(_ -> true, MovementCriteria.create().entities(1)));
    }

    @Test
    void theEntityCountMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> MovementCriteria.create().entities(0));
        assertThrows(IllegalArgumentException.class, () -> MovementCriteria.create().entities(-1));
    }

    @Test
    void eachMethodReturnsANewCriteria() {
        var base = MovementCriteria.create();
        base.atLeast(1);
        base.towards(new Vec(1, 0, 0), 30);
        base.into(Region.around(Vec.ZERO, 1));
        base.entities(2);
        // 原对象没有设置任何条件
        assertThrows(IllegalArgumentException.class, base::validate);
        // 设置过条件的副本同时保留全部设置
        var full = base.atLeast(1).towards(new Vec(1, 0, 0), 30).into(Region.around(Vec.ZERO, 1)).entities(2);
        full.validate();
    }
}
