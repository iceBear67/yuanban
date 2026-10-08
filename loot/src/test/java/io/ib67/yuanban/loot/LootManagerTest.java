package io.ib67.yuanban.loot;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.loot.condition.MatchToolCondition;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.entry.GroupEntry;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.entry.NestedTableEntry;
import io.ib67.yuanban.loot.entry.TagEntry;
import io.ib67.yuanban.loot.function.SetContainerLootTableFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.BlockEntityType;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LootManager} 的注册、查询、快照和校验。
 */
class LootManagerTest {
    private static final Key TABLE_A = Key.key("test", "a");
    private static final Key TABLE_B = Key.key("test", "b");
    private static final Key TABLE_C = Key.key("test", "c");
    private static final Key TABLE_MISSING = Key.key("test", "missing");
    private static final BlockEntityType BLOCK_ENTITY_CHEST = BlockEntityType.CHEST;

    private static ItemEntry item(int weight) {
        return new ItemEntry(List.of(), List.of(), weight, 0, Material.STONE);
    }

    /**
     * 物品集合为标签 {@code tag} 的物品谓词。
     */
    private static ItemPredicate taggedItems(Key tag) {
        return new ItemPredicate(Optional.of(HolderSet.named(tag)), MinMaxBounds.ANY_INT, ComponentMatchers.ANY);
    }

    private static LootPool pool(ConstantValue rolls, LootEntry... entries) {
        return new LootPool(List.of(), List.of(), rolls, ConstantValue.ZERO, List.of(entries));
    }

    private static LootTable tableWith(LootPool... pools) {
        return new LootTable(LootTable.DEFAULT_TYPE, List.of(), List.of(pools), Optional.empty());
    }

    private static NestedTableEntry ref(Key id) {
        return new NestedTableEntry(List.of(), List.of(), 1, 0, id);
    }

    private static LootManager newManager() {
        return new LootManager(LootTypeRegistry.vanilla());
    }

    @Test
    void replaceAll之后能查到新内容() {
        var manager = newManager();
        var table = tableWith(pool(new ConstantValue(1), item(1)));
        var tables = new TreeMap<Key, LootTable>();
        tables.put(TABLE_A, table);
        manager.replaceAll(tables);

        assertTrue(manager.get(TABLE_A).isPresent());
        assertSame(table, manager.get(TABLE_A).orElseThrow());
        assertEquals(Set.of(TABLE_A), manager.tables().keySet());
    }

    @Test
    void 再次替换后旧ID消失拿到第一次快照的调用方不受影响() {
        var manager = newManager();
        var first = new TreeMap<Key, LootTable>();
        first.put(TABLE_A, tableWith(pool(new ConstantValue(1), item(1))));
        manager.replaceAll(first);
        var firstSnapshot = manager.tables();

        var second = new TreeMap<Key, LootTable>();
        second.put(TABLE_B, tableWith(pool(new ConstantValue(1), item(1))));
        manager.replaceAll(second);

        assertFalse(manager.get(TABLE_A).isPresent(), "旧 ID 应当不再能查到");
        assertTrue(manager.get(TABLE_B).isPresent());
        assertEquals(Set.of(TABLE_B), manager.tables().keySet());

        assertEquals(Set.of(TABLE_A), firstSnapshot.keySet(), "第一次拿到的快照保持替换前的内容");
        assertThrows(UnsupportedOperationException.class, () -> firstSnapshot.remove(TABLE_A),
                "快照不允许修改");
    }

    @Test
    void 引用不存在的表会失败并指明表ID() {
        var manager = newManager();
        var table = tableWith(pool(new ConstantValue(1), ref(TABLE_MISSING)));
        var tables = new TreeMap<Key, LootTable>();
        tables.put(TABLE_A, table);

        var thrown = assertThrows(LootException.class, () -> manager.replaceAll(tables));
        assertTrue(thrown.getMessage().contains(TABLE_A.asString()), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(TABLE_MISSING.asString()), thrown.getMessage());
        assertTrue(manager.get(TABLE_A).isEmpty(), "校验失败时不做任何修改");
    }

    @Test
    void 表直接引用自己会失败并指明表ID() {
        var manager = newManager();
        var table = tableWith(pool(new ConstantValue(1), ref(TABLE_A)));
        var tables = new TreeMap<Key, LootTable>();
        tables.put(TABLE_A, table);

        var thrown = assertThrows(LootException.class, () -> manager.replaceAll(tables));
        assertTrue(thrown.getMessage().contains(TABLE_A.asString()), thrown.getMessage());
        assertTrue(manager.get(TABLE_A).isEmpty());
    }

    @Test
    void 间接引用环AB到A会失败并指明表ID() {
        var manager = newManager();
        var tableA = tableWith(pool(new ConstantValue(1), ref(TABLE_B)));
        var tableB = tableWith(pool(new ConstantValue(1), ref(TABLE_A)));
        var tables = new TreeMap<Key, LootTable>();
        tables.put(TABLE_A, tableA);
        tables.put(TABLE_B, tableB);

        var thrown = assertThrows(LootException.class, () -> manager.replaceAll(tables));
        var message = thrown.getMessage();
        assertTrue(message.contains(TABLE_A.asString()) && message.contains(TABLE_B.asString()),
                message);
        assertTrue(manager.get(TABLE_A).isEmpty());
        assertTrue(manager.get(TABLE_B).isEmpty());
    }

    @Test
    void 内联表引用宿主表会失败并指明表ID() {
        var manager = newManager();
        var inline = tableWith(pool(new ConstantValue(1),
                new NestedTableEntry(List.of(), List.of(), 1, 0, TABLE_A)));
        var outer = tableWith(pool(new ConstantValue(1),
                new NestedTableEntry(List.of(), List.of(), 1, 0, inline)));
        var tables = new TreeMap<Key, LootTable>();
        tables.put(TABLE_A, outer);

        var thrown = assertThrows(LootException.class, () -> manager.replaceAll(tables));
        assertTrue(thrown.getMessage().contains(TABLE_A.asString()), thrown.getMessage());
        assertTrue(manager.get(TABLE_A).isEmpty());
    }

    @Test
    void 复合条目展开后的总权重越过Int上限会失败并指明表ID() {
        var manager = newManager();
        var group = new GroupEntry(List.of(),
                List.of(item(Integer.MAX_VALUE), item(Integer.MAX_VALUE)));
        var table = tableWith(pool(new ConstantValue(1), group));
        var tables = new TreeMap<Key, LootTable>();
        tables.put(TABLE_A, table);

        var thrown = assertThrows(LootException.class, () -> manager.replaceAll(tables));
        assertTrue(thrown.getMessage().contains(TABLE_A.asString()), thrown.getMessage());
        assertTrue(manager.get(TABLE_A).isEmpty());
    }

    @Test
    void 替换成功后types保持不变() {
        var types = LootTypeRegistry.vanilla();
        var manager = new LootManager(types);
        var tables = new TreeMap<Key, LootTable>();
        tables.put(TABLE_A, tableWith(pool(new ConstantValue(1), item(1))));
        manager.replaceAll(tables);
        assertSame(types, manager.types());
    }

    @Test
    void inspect收集tag条目引用() {
        var tag = new TagEntry(List.of(), List.of(), 1, 0, Key.key("minecraft", "ores"), false);
        var table = tableWith(pool(new ConstantValue(1), tag));

        var contents = LootContents.inspect(table);
        assertEquals(Set.of(Key.key("minecraft", "ores")), contents.itemTags());
        assertTrue(contents.tables().isEmpty());
        assertTrue(contents.nested().isEmpty());
    }

    @Test
    void inspect收集match_tool里的物品标签() {
        var condition = new MatchToolCondition(Optional.of(taggedItems(Key.key("minecraft", "foo"))));
        var pool = new LootPool(List.of(condition), List.of(), new ConstantValue(1),
                ConstantValue.ZERO, List.of(item(1)));
        var table = tableWith(pool);

        var contents = LootContents.inspect(table);
        assertEquals(Set.of(Key.key("minecraft", "foo")), contents.itemTags());
    }

    @Test
    void inspect收集set_container_loot_table的表引用() {
        var function = new SetContainerLootTableFunction(List.of(), TABLE_B, BLOCK_ENTITY_CHEST);
        var table = new LootTable(LootTable.DEFAULT_TYPE, List.of(function), List.of(), Optional.empty());

        var contents = LootContents.inspect(table);
        assertEquals(Set.of(TABLE_B), contents.tables());
        assertTrue(contents.itemTags().isEmpty());
    }

    @Test
    void inspect按出现顺序收集内联表() {
        var inlineA = tableWith(pool(new ConstantValue(1), item(1)));
        var inlineB = tableWith(pool(new ConstantValue(1), item(1)));
        var nestedA = new NestedTableEntry(List.of(), List.of(), 1, 0, inlineA);
        var nestedB = new NestedTableEntry(List.of(), List.of(), 1, 0, inlineB);
        var outer = tableWith(pool(new ConstantValue(1), nestedA, nestedB));

        var contents = LootContents.inspect(outer);
        assertEquals(2, contents.nested().size());
        assertSame(inlineA, contents.nested().getFirst());
        assertSame(inlineB, contents.nested().get(1));
    }

    @Test
    void inspect把match_tool和tag与set_container_loot_table一起收集() {
        var tag = new TagEntry(List.of(), List.of(), 1, 0, Key.key("minecraft", "ores"), true);
        var function = new SetContainerLootTableFunction(List.of(), TABLE_C, BLOCK_ENTITY_CHEST);
        var pool = new LootPool(
                List.of(new MatchToolCondition(Optional.of(taggedItems(Key.key("minecraft", "foo"))))),
                List.of(function), new ConstantValue(1), ConstantValue.ZERO, List.of(tag));
        var table = tableWith(pool);

        var contents = LootContents.inspect(table);
        assertEquals(Set.of(Key.key("minecraft", "ores"), Key.key("minecraft", "foo")), contents.itemTags());
        assertEquals(Set.of(TABLE_C), contents.tables());
    }
}
