package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.registry.TagTable;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.Tool;
import net.minestom.server.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * 每 tick 的挖掘进度，按 wiki Breaking 页的公式计算。
 * 工具规则中的方块集合按标签名称在 {@link TagTable} 中判断，直接列出的方块按名称判断。
 */
public final class DigSpeed {
    /**
     * wiki Breaking：挖掘疲劳 I 到 IV 及以上的系数
     */
    private static final double[] FATIGUE = {0.3, 0.09, 0.0027, 0.00081};
    private static final String BLOCK_REGISTRY = "minecraft:block";

    private DigSpeed() {
    }

    /**
     * @param held       手持的物品
     * @param attributes 玩家的属性取值
     * @param effects    玩家的效果等级（放大器加一，没有时为 0）
     * @param onGround   玩家是否着地
     * @param eyesInWater 眼睛是否在水中
     * @return 这个 tick 增加的进度；达到 1 时破坏方块
     */
    public static double progress(Block block, ItemStack held, TagTable tags, ToDoubleFunction<Attribute> attributes,
                                  ToIntFunction<PotionEffect> effects, boolean onGround, boolean eyesInWater) {
        var hardness = block.hardness();
        if (hardness < 0) {
            return 0;
        }
        var tool = held.get(DataComponents.TOOL);
        var speed = toolSpeed(tool, block, tags);
        if (speed > 1) {
            speed += attributes.applyAsDouble(Attribute.MINING_EFFICIENCY);
        }
        var haste = Math.max(effects.applyAsInt(PotionEffect.HASTE), effects.applyAsInt(PotionEffect.CONDUIT_POWER));
        if (haste > 0) {
            speed *= 1 + 0.2 * haste;
        }
        var fatigue = effects.applyAsInt(PotionEffect.MINING_FATIGUE);
        if (fatigue > 0) {
            speed *= FATIGUE[Math.min(fatigue, FATIGUE.length) - 1];
        }
        speed *= attributes.applyAsDouble(Attribute.BLOCK_BREAK_SPEED);
        if (eyesInWater) {
            speed *= attributes.applyAsDouble(Attribute.SUBMERGED_MINING_SPEED);
        }
        if (!onGround) {
            speed /= 5;
        }
        var canHarvest = !block.requiresTool() || correctForDrops(tool, block, tags);
        return speed / hardness / (canHarvest ? 30 : 100);
    }

    /**
     * 第一条匹配并给出速度的规则的速度；没有匹配时为默认速度，没有工具组件时为 1
     */
    static double toolSpeed(@Nullable Tool tool, Block block, TagTable tags) {
        if (tool == null) {
            return 1;
        }
        for (var rule : tool.rules()) {
            if (rule.speed() != null && matches(rule, block, tags)) {
                return rule.speed();
            }
        }
        return tool.defaultMiningSpeed();
    }

    /**
     * 第一条匹配并给出 correct_for_drops 的规则的值；没有时为 false
     */
    static boolean correctForDrops(@Nullable Tool tool, Block block, TagTable tags) {
        if (tool == null) {
            return false;
        }
        for (var rule : tool.rules()) {
            if (rule.correctForDrops() != null && matches(rule, block, tags)) {
                return rule.correctForDrops();
            }
        }
        return false;
    }

    private static boolean matches(Tool.Rule rule, Block block, TagTable tags) {
        var tagKey = rule.blocks().key();
        if (tagKey != null) {
            return tags.contains(BLOCK_REGISTRY, tagKey.key().asString(), block.id());
        }
        for (var key : rule.blocks()) {
            if (key.key().equals(block.key())) {
                return true;
            }
        }
        return false;
    }
}
