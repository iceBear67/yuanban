package io.ib67.yuanban.loot.function;

/**
 * {@code apply_bonus} 的加成公式：原版 {@code ApplyBonusCount$Formula} 的三种实现。
 */
public sealed interface ApplyBonusFormula {
    /**
     * 原版 {@code binomial_with_bonus_count}：在二项分布每次成功时额外加 {@code extraRounds} 轮。
     * JSON 字段 {@code extra}、{@code probability}。
     */
    record BinomialWithBonusCount(int extraRounds, float probability) implements ApplyBonusFormula {
    }

    /**
     * 原版 {@code uniform_bonus_count}：每级魔咒乘以 {@code bonusMultiplier} 后作为均匀分布上限。JSON 字段
     * {@code bonusMultiplier}。
     */
    record UniformBonusCount(int bonusMultiplier) implements ApplyBonusFormula {
    }

    /**
     * 原版 {@code ore_drops}：掉落 1 个，魔咒等级 {@code level} 时按概率多掉 {@code 1..level} 个。
     * 原版是单例。
     */
    record OreDrops() implements ApplyBonusFormula {
        public static final OreDrops INSTANCE = new OreDrops();
    }
}
