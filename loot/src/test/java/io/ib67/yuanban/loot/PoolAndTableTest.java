package io.ib67.yuanban.loot;

import io.ib67.yuanban.loot.entry.EmptyEntry;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LootPool 与 LootTable 的紧凑构造器缺省值测试。
 */
class PoolAndTableTest {

    @Test
    void lootPoolNoBonusRolls() {
        var rolls = new ConstantValue(1.0f);
        var entries = List.of((io.ib67.yuanban.loot.entry.LootEntry) new ItemEntry(Key.key("minecraft:stone")));
        var pool = new LootPool(List.of(), List.of(), rolls, entries);
        assertEquals(rolls, pool.rolls());
        assertEquals(ConstantValue.ZERO, pool.bonusRolls());
        assertEquals(entries, pool.entries());
    }

    @Test
    void lootPoolWithBonusRolls() {
        var rolls = new ConstantValue(1.0f);
        var bonus = new ConstantValue(2.0f);
        var entries = List.of((io.ib67.yuanban.loot.entry.LootEntry) new ItemEntry(Key.key("minecraft:stone")));
        var pool = new LootPool(List.of(), List.of(), rolls, bonus, entries);
        assertEquals(bonus, pool.bonusRolls());
    }

    @Test
    void lootTableDefaultType() {
        var table = new LootTable(null, List.of(), List.of(), null);
        assertEquals(LootTable.DEFAULT_TYPE, table.type());
        assertEquals(Key.key("generic"), LootTable.DEFAULT_TYPE);
        assertTrue(table.randomSequence().isEmpty());
        assertTrue(table.pools().isEmpty());
    }

    @Test
    void lootTableKnownTypesIncludeAllVanillaContextTypes() {
        for (var known : new String[]{
                "minecraft:generic", "minecraft:chest", "minecraft:command", "minecraft:selector",
                "minecraft:villager_trade", "minecraft:fishing", "minecraft:entity", "minecraft:equipment",
                "minecraft:archaeology", "minecraft:gift", "minecraft:barter", "minecraft:vault",
                "minecraft:advancement_reward", "minecraft:advancement_entity", "minecraft:advancement_location",
                "minecraft:block_use", "minecraft:block", "minecraft:shearing",
                "minecraft:entity_interact", "minecraft:block_interact"
        }) {
            assertTrue(LootTable.isKnownType(Key.key(known)), known);
        }
        assertFalse(LootTable.isKnownType(Key.key("minecraft:nope")));
    }

    @Test
    void lootTableOfRandomSequence() {
        var table = new LootTable(Key.key("minecraft:chest"), List.of(), List.of(),
                java.util.Optional.of(Key.key("minecraft:entities/zombie")));
        assertEquals(Key.key("minecraft:chest"), table.type());
        assertEquals(Key.key("minecraft:entities/zombie"), table.randomSequence().orElseThrow());
    }
}
