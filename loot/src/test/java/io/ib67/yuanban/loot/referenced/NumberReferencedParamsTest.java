package io.ib67.yuanban.loot.referenced;

import io.ib67.yuanban.loot.number.BinomialValue;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.EnvironmentAttributeValue;
import io.ib67.yuanban.loot.number.ScoreboardName;
import io.ib67.yuanban.loot.number.ScoreboardValue;
import io.ib67.yuanban.loot.number.StorageValue;
import io.ib67.yuanban.loot.number.SumValue;
import io.ib67.yuanban.loot.number.UniformValue;
import io.ib67.yuanban.codec.NbtPath;
import net.kyori.adventure.key.Key;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.ib67.yuanban.loot.referenced.ReferencedParamsFixture.ids;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 数字提供器 record 的 {@code referencedParams} 对照原版 {@code LootContextUser#getReferencedContextParams} 的抽样断言。
 */
class NumberReferencedParamsTest {

    @Test
    void constant() {
        assertEquals(Set.of(), ids(ConstantValue.ZERO.referencedParams()));
    }

    @Test
    void uniformOfConstants() {
        assertEquals(Set.of(), ids(new UniformValue(new ConstantValue(1), new ConstantValue(5)).referencedParams()));
    }

    @Test
    void uniformMergesBounds() {
        var value = new UniformValue(
                new ScoreboardValue(new ScoreboardName.Context(EntityTarget.THIS), "kills"),
                new ConstantValue(5));
        // 原版 UniformGenerator 继承默认实现，返回空集；子提供器由外层 validate 递归覆盖。
        assertEquals(Set.of(), ids(value.referencedParams()));
    }

    @Test
    void scoreboardContextTarget() {
        assertEquals(Set.of("minecraft:attacking_entity"),
                ids(new ScoreboardValue(new ScoreboardName.Context(EntityTarget.ATTACKER), "kills").referencedParams()));
    }

    @Test
    void scoreboardFixed() {
        assertEquals(Set.of(),
                ids(new ScoreboardValue(new ScoreboardName.Fixed("player"), "kills").referencedParams()));
    }

    @Test
    void environmentAttribute() {
        assertEquals(Set.of("minecraft:origin"),
                ids(new EnvironmentAttributeValue(EnvironmentAttribute.MUSIC_VOLUME).referencedParams()));
    }

    @Test
    void sumMergesTerms() {
        var value = new SumValue(List.of(
                new EnvironmentAttributeValue(EnvironmentAttribute.MUSIC_VOLUME),
                new ScoreboardValue(new ScoreboardName.Context(EntityTarget.THIS), "kills"),
                new ConstantValue(1)));
        assertEquals(Set.of(), ids(value.referencedParams()));
    }

    @Test
    void sumOfConstants() {
        var value = new SumValue(List.of(new ConstantValue(1), new ConstantValue(2)));
        assertEquals(Set.of(), ids(value.referencedParams()));
    }

    @Test
    void binomial() {
        assertEquals(Set.of(), ids(new BinomialValue(new ConstantValue(5), new ConstantValue(0.5f)).referencedParams()));
    }

    @Test
    void storage() throws Exception {
        assertEquals(Set.of(),
                ids(new StorageValue(Key.key("minecraft:some_storage"), NbtPath.of("path")).referencedParams()));
    }
}
