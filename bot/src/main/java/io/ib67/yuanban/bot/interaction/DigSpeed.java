package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.data.Items;
import io.ib67.yuanban.bot.registry.TagTable;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ToolData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * 每 tick 的挖掘进度，按 wiki Breaking 页的公式计算。
 * 工具规则中的方块集合为标签时在 {@link TagTable} 中判断，为方块列表时按方块编号判断。
 */
public final class DigSpeed {
    /**
     * wiki Breaking：挖掘疲劳 I 到 IV 及以上的系数
     */
    private static final double[] FATIGUE = {0.3, 0.09, 0.0027, 0.00081};
    private static final Key BLOCK_REGISTRY = Key.key("block");

    private DigSpeed() {
    }

    /**
     * @param held       手持的物品；空手时为 null
     * @param attributes 玩家的属性取值
     * @param effects    玩家的效果等级（放大器加一，没有时为 0）
     * @param onGround   玩家是否着地
     * @param eyesInWater 眼睛是否在水中
     * @return 这个 tick 增加的进度；达到 1 时破坏方块
     */
    public static double progress(Block block, @Nullable ItemStack held, TagTable tags, ToDoubleFunction<AttributeType> attributes,
                                  ToIntFunction<Effect> effects, boolean onGround, boolean eyesInWater) {
        var hardness = block.hardness();
        if (hardness < 0) {
            return 0;
        }
        var tool = Items.component(held, DataComponentTypes.TOOL);
        var speed = toolSpeed(tool, block, tags);
        if (speed > 1) {
            speed += attributes.applyAsDouble(AttributeType.Builtin.MINING_EFFICIENCY);
        }
        var haste = Math.max(effects.applyAsInt(Effect.HASTE), effects.applyAsInt(Effect.CONDUIT_POWER));
        if (haste > 0) {
            speed *= 1 + 0.2 * haste;
        }
        var fatigue = effects.applyAsInt(Effect.MINING_FATIGUE);
        if (fatigue > 0) {
            speed *= FATIGUE[Math.min(fatigue, FATIGUE.length) - 1];
        }
        speed *= attributes.applyAsDouble(AttributeType.Builtin.BLOCK_BREAK_SPEED);
        if (eyesInWater) {
            speed *= attributes.applyAsDouble(AttributeType.Builtin.SUBMERGED_MINING_SPEED);
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
    static double toolSpeed(@Nullable ToolData tool, Block block, TagTable tags) {
        if (tool == null) {
            return 1;
        }
        for (var rule : tool.getRules()) {
            if (rule.getSpeed() != null && matches(rule, block, tags)) {
                return rule.getSpeed();
            }
        }
        return tool.getDefaultMiningSpeed();
    }

    /**
     * 第一条匹配并给出 correct_for_drops 的规则的值；没有时为 false
     */
    static boolean correctForDrops(@Nullable ToolData tool, Block block, TagTable tags) {
        if (tool == null) {
            return false;
        }
        for (var rule : tool.getRules()) {
            if (rule.getCorrectForDrops() != null && matches(rule, block, tags)) {
                return rule.getCorrectForDrops();
            }
        }
        return false;
    }

    private static boolean matches(ToolData.Rule rule, Block block, TagTable tags) {
        var tag = rule.getBlocks().getLocation();
        if (tag != null) {
            return tags.contains(BLOCK_REGISTRY, tag, block.id());
        }
        return Objects.requireNonNull(rule.getBlocks().getHolders()).contains(block.id());
    }
}
