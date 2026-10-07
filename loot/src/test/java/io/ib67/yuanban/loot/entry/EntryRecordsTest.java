package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.EitherId;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.condition.KilledByPlayerCondition;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.DiscardFunction;
import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * loot.entry 包的 10 个条目 record 的最小行为。
 */
class EntryRecordsTest {

    private static final LootCondition COND = KilledByPlayerCondition.INSTANCE;
    private static final LootFunction FUN = new DiscardFunction();

    @Test
    void itemEntry() {
        var name = Key.key("minecraft:apple");
        var entry = new ItemEntry(List.of(COND), List.of(FUN), 2, 3, name);
        assertEquals(List.of(COND), entry.conditions());
        assertEquals(List.of(FUN), entry.functions());
        assertEquals(2, entry.weight());
        assertEquals(3, entry.quality());
        assertEquals(name, entry.name());
    }

    @Test
    void itemEntryDefaults() {
        var name = Key.key("minecraft:stone");
        var entry = new ItemEntry(name);
        assertEquals(1, entry.weight());
        assertEquals(0, entry.quality());
        assertTrue(entry.conditions().isEmpty());
        assertTrue(entry.functions().isEmpty());
    }

    @Test
    void nestedTableById() {
        var table = Key.key("minecraft:chest/simple_dungeon");
        var entry = new NestedTableEntry(List.of(), List.of(), 1, 0, io.ib67.yuanban.loot.EitherId.ofLeft(table));
        assertTrue(entry.value().isLeft());
        assertEquals(table, entry.value().left().get());
    }

    @Test
    void nestedTableInline() {
        LootTable inline = new LootTable(null, List.of(), List.of(), null);
        var entry = new NestedTableEntry(List.of(), List.of(), 1, 0,
                io.ib67.yuanban.loot.EitherId.<LootTable>ofRight(inline));
        assertTrue(entry.value().isRight());
        assertEquals(inline, entry.value().right().get());
    }

    @Test
    void dynamicEntry() {
        var name = Key.key("minecraft:contents");
        var entry = new DynamicEntry(List.of(COND), List.of(FUN), 5, 0, name);
        assertEquals(name, entry.name());
    }

    @Test
    void emptyEntry() {
        var entry = new EmptyEntry();
        assertEquals(1, entry.weight());
        assertEquals(0, entry.quality());
        assertTrue(entry.functions().isEmpty() && entry.conditions().isEmpty());
    }

    @Test
    void slotsEntry() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("source", "block_entity");
        var source = new SlotSource(json);
        var entry = new SlotsEntry(List.of(), List.of(), 1, 0, source);
        assertEquals(source, entry.slotSource());
    }

    @Test
    void tagEntryExpanded() {
        var name = Key.key("minecraft:wool");
        var entry = new TagEntry(List.of(COND), List.of(FUN), 4, 1, name, true);
        assertEquals(name, entry.name());
        assertTrue(entry.expand());
    }

    @Test
    void tagEntryNotExpanded() {
        var name = Key.key("minecraft:planks");
        var entry = new TagEntry(List.of(), List.of(), 1, 0, name, false);
        assertFalse(entry.expand());
    }

    @Test
    void compositeGroupEntry() {
        var child = new ItemEntry(Key.key("minecraft:diamond"));
        var entry = new GroupEntry(List.of(COND), List.of(FUN), List.of(child));
        assertEquals(List.of(child), entry.children());
        assertEquals(List.of(COND), entry.conditions());
    }

    @Test
    void compositeAlternativesEntry() {
        var a = new ItemEntry(Key.key("minecraft:diamond"));
        var b = new EmptyEntry();
        var entry = new AlternativesEntry(List.of(), List.of(), List.of(a, b));
        assertEquals(List.of(a, b), entry.children());
    }

    @Test
    void compositeSequenceEntry() {
        var a = new ItemEntry(Key.key("minecraft:diamond"));
        var entry = new SequenceEntry(List.of(), List.of(), List.of(a));
        assertEquals(List.of(a), entry.children());
    }
}
