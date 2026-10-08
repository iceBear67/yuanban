package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.data.AttributeData;
import io.ib67.yuanban.bot.data.ItemData;
import io.ib67.yuanban.bot.registry.TagTable;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 挖掘进度与 wiki Breaking 页的公式与示例一致。
 */
class DigSpeedTest {
    private static final TagTable TAGS = TagTable.EMPTY.apply(TestServer.tags());
    private static final ItemStack DIAMOND_PICKAXE = new ItemStack(ItemData.id(Key.key("diamond_pickaxe")));

    /**
     * @param held 手持的物品；空手时为 null
     */
    private static double progress(Block block, @Nullable ItemStack held, Map<AttributeType, Double> attributes,
                                   Map<Effect, Integer> effects, boolean onGround, boolean eyesInWater) {
        return DigSpeed.progress(block, held, TAGS,
                attribute -> attributes.getOrDefault(attribute, AttributeData.defaultValue(attribute)),
                effect -> effects.getOrDefault(effect, 0), onGround, eyesInWater);
    }

    @Test
    void hasteTwoAndEfficiencyFiveDiamondPickaxeBreaksStoneInstantly() {
        // 效率 V：mining_efficiency 为 5² + 1 = 26
        var progress = progress(Block.STONE, DIAMOND_PICKAXE, Map.of(AttributeType.Builtin.MINING_EFFICIENCY, 26.0),
                Map.of(Effect.HASTE, 2), true, false);
        // (8 + 26) × 1.4 / 1.5 / 30
        assertEquals(34 * 1.4 / 1.5 / 30, progress, 1e-6);
        assertTrue(progress >= 1);
    }

    @Test
    void bareHandOnStoneCannotHarvest() {
        // 1 / 1.5 / 100
        assertEquals(1 / 1.5 / 100, progress(Block.STONE, null, Map.of(), Map.of(), true, false), 1e-9);
    }

    @Test
    void diamondPickaxeOnStone() {
        assertEquals(8 / 1.5 / 30, progress(Block.STONE, DIAMOND_PICKAXE, Map.of(), Map.of(), true, false), 1e-6);
    }

    @Test
    void airborneUnderwaterAndFatigue() {
        var base = progress(Block.DIRT, null, Map.of(), Map.of(), true, false);
        assertEquals(base / 5, progress(Block.DIRT, null, Map.of(), Map.of(), false, false), 1e-9);
        // submerged_mining_speed 默认为 0.2
        assertEquals(base * 0.2, progress(Block.DIRT, null, Map.of(), Map.of(), true, true), 1e-9);
        assertEquals(base * 0.09, progress(Block.DIRT, null, Map.of(), Map.of(Effect.MINING_FATIGUE, 2), true, false), 1e-9);
        assertEquals(base * 0.00081, progress(Block.DIRT, null, Map.of(), Map.of(Effect.MINING_FATIGUE, 7), true, false), 1e-12);
    }

    @Test
    void unbreakableAndInstantBlocks() {
        assertEquals(0, progress(Block.BEDROCK, null, Map.of(), Map.of(), true, false));
        assertTrue(progress(Block.SHORT_GRASS, null, Map.of(), Map.of(), true, false) >= 1);
    }
}
