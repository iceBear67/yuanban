package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.registry.TagTable;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.Registries;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 挖掘进度与 wiki Breaking 页的公式与示例一致。
 */
class DigSpeedTest {
    private static final TagTable TAGS = TagTable.EMPTY.apply(Registries.tagsPacket(TestServer.vanilla()));

    private static double progress(Block block, ItemStack held, Map<Attribute, Double> attributes,
                                   Map<PotionEffect, Integer> effects, boolean onGround, boolean eyesInWater) {
        return DigSpeed.progress(block, held, TAGS,
                attribute -> attributes.getOrDefault(attribute, attribute.defaultValue()),
                effect -> effects.getOrDefault(effect, 0), onGround, eyesInWater);
    }

    @Test
    void hasteTwoAndEfficiencyFiveDiamondPickaxeBreaksStoneInstantly() {
        var pickaxe = ItemStack.of(Material.DIAMOND_PICKAXE);
        // 效率 V：mining_efficiency 为 5² + 1 = 26
        var progress = progress(Block.STONE, pickaxe, Map.of(Attribute.MINING_EFFICIENCY, 26.0),
                Map.of(PotionEffect.HASTE, 2), true, false);
        // (8 + 26) × 1.4 / 1.5 / 30
        assertEquals(34 * 1.4 / 1.5 / 30, progress, 1e-6);
        assertTrue(progress >= 1);
    }

    @Test
    void bareHandOnStoneCannotHarvest() {
        // 1 / 1.5 / 100
        assertEquals(1 / 1.5 / 100, progress(Block.STONE, ItemStack.AIR, Map.of(), Map.of(), true, false), 1e-9);
    }

    @Test
    void diamondPickaxeOnStone() {
        assertEquals(8 / 1.5 / 30, progress(Block.STONE, ItemStack.of(Material.DIAMOND_PICKAXE), Map.of(), Map.of(), true, false), 1e-6);
    }

    @Test
    void airborneUnderwaterAndFatigue() {
        var base = progress(Block.DIRT, ItemStack.AIR, Map.of(), Map.of(), true, false);
        assertEquals(base / 5, progress(Block.DIRT, ItemStack.AIR, Map.of(), Map.of(), false, false), 1e-9);
        // submerged_mining_speed 默认为 0.2
        assertEquals(base * 0.2, progress(Block.DIRT, ItemStack.AIR, Map.of(), Map.of(), true, true), 1e-9);
        assertEquals(base * 0.09, progress(Block.DIRT, ItemStack.AIR, Map.of(), Map.of(PotionEffect.MINING_FATIGUE, 2), true, false), 1e-9);
        assertEquals(base * 0.00081, progress(Block.DIRT, ItemStack.AIR, Map.of(), Map.of(PotionEffect.MINING_FATIGUE, 7), true, false), 1e-12);
    }

    @Test
    void unbreakableAndInstantBlocks() {
        assertEquals(0, progress(Block.BEDROCK, ItemStack.AIR, Map.of(), Map.of(), true, false));
        assertTrue(progress(Block.SHORT_GRASS, ItemStack.AIR, Map.of(), Map.of(), true, false) >= 1);
    }
}
