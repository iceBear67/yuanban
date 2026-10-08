package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.registry.TagTable;
import it.unimi.dsi.fastutil.ints.IntList;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.AdventureModePredicate;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.AdventureModePredicate.BlockPredicate;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.AdventureModePredicate.PropertyMatcher;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentMatchers;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.HolderSet;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPredicatesTest {
    private static final TagTable TAGS = TagTable.EMPTY.apply(TestServer.tags());
    private static final DataComponentMatchers NO_COMPONENTS = new DataComponentMatchers(Map.of(), Map.of());
    private static final Vec POSITION = new Vec(3, 64, -7);

    private static boolean test(BlockPredicate predicate, Block block, @Nullable NbtMap blockEntity) {
        return BlockPredicates.test(new AdventureModePredicate(List.of(predicate)), block, blockEntity, POSITION, TAGS);
    }

    private static BlockPredicate properties(PropertyMatcher... matchers) {
        return new BlockPredicate(null, List.of(matchers), null, NO_COMPONENTS);
    }

    private static BlockPredicate nbt(NbtMap nbt) {
        return new BlockPredicate(null, null, nbt, NO_COMPONENTS);
    }

    @Test
    void blocksMatchByServerTagOrById() {
        var pickaxe = new BlockPredicate(new HolderSet(Key.key("mineable/pickaxe")), null, null, NO_COMPONENTS);
        assertTrue(test(pickaxe, Block.STONE, null));
        assertFalse(test(pickaxe, Block.DIRT, null));
        // 标签按服务端下发的标签表匹配
        assertFalse(BlockPredicates.test(new AdventureModePredicate(List.of(pickaxe)), Block.STONE, null, POSITION, TagTable.EMPTY));
        var listed = new BlockPredicate(new HolderSet(IntList.of(Block.DIRT.id(), Block.GRASS_BLOCK.id())), null, null, NO_COMPONENTS);
        assertTrue(test(listed, Block.GRASS_BLOCK.withProperty("snowy", "true"), null));
        assertFalse(test(listed, Block.STONE, null));
    }

    @Test
    void anyPredicateMatches() {
        var stone = new BlockPredicate(new HolderSet(IntList.of(Block.STONE.id())), null, null, NO_COMPONENTS);
        var dirt = new BlockPredicate(new HolderSet(IntList.of(Block.DIRT.id())), null, null, NO_COMPONENTS);
        var both = new AdventureModePredicate(List.of(stone, dirt));
        assertTrue(BlockPredicates.test(both, Block.DIRT, null, POSITION, TAGS));
        assertFalse(BlockPredicates.test(both, Block.SAND, null, POSITION, TAGS));
        assertFalse(BlockPredicates.test(new AdventureModePredicate(List.of()), Block.DIRT, null, POSITION, TAGS));
    }

    @Test
    void exactPropertiesCompareTheValue() {
        var delayTwo = properties(new PropertyMatcher("delay", "2", null, null));
        assertTrue(test(delayTwo, Block.REPEATER.withProperty("delay", "2"), null));
        assertFalse(test(delayTwo, Block.REPEATER.withProperty("delay", "3"), null));
        // 方块没有这个属性，或者给出的值不是它的取值
        assertFalse(test(delayTwo, Block.STONE, null));
        assertFalse(test(properties(new PropertyMatcher("delay", "9", null, null)), Block.REPEATER, null));
    }

    @Test
    void integerRangesCompareNumbers() {
        var twoToThree = properties(new PropertyMatcher("delay", null, "2", "3"));
        assertFalse(test(twoToThree, Block.REPEATER.withProperty("delay", "1"), null));
        assertTrue(test(twoToThree, Block.REPEATER.withProperty("delay", "2"), null));
        assertTrue(test(twoToThree, Block.REPEATER.withProperty("delay", "3"), null));
        assertFalse(test(twoToThree, Block.REPEATER.withProperty("delay", "4"), null));
        // 只有下限
        var atLeastThree = properties(new PropertyMatcher("delay", null, "3", null));
        assertTrue(test(atLeastThree, Block.REPEATER.withProperty("delay", "4"), null));
        assertFalse(test(atLeastThree, Block.REPEATER.withProperty("delay", "2"), null));
        // 范围的端点不是这个属性的取值时不匹配
        assertFalse(test(properties(new PropertyMatcher("delay", null, "0", null)), Block.REPEATER.withProperty("delay", "4"), null));
    }

    @Test
    void enumRangesFollowTheDeclarationOrder() {
        // 原版水平朝向的取值顺序为 north、south、west、east
        var southToWest = properties(new PropertyMatcher("facing", null, "south", "west"));
        assertFalse(test(southToWest, Block.FURNACE.withProperty("facing", "north"), null));
        assertTrue(test(southToWest, Block.FURNACE.withProperty("facing", "south"), null));
        assertTrue(test(southToWest, Block.FURNACE.withProperty("facing", "west"), null));
        assertFalse(test(southToWest, Block.FURNACE.withProperty("facing", "east"), null));
    }

    @Test
    void booleanRangesPutFalseBeforeTrue() {
        var unlit = properties(new PropertyMatcher("lit", null, null, "false"));
        assertTrue(test(unlit, Block.FURNACE.withProperty("lit", "false"), null));
        assertFalse(test(unlit, Block.FURNACE.withProperty("lit", "true"), null));
        var lit = properties(new PropertyMatcher("lit", null, "true", null));
        assertTrue(test(lit, Block.FURNACE.withProperty("lit", "true"), null));
        assertFalse(test(lit, Block.FURNACE.withProperty("lit", "false"), null));
    }

    @Test
    void nbtMatchesPartiallyAgainstTheFullBlockEntityData() {
        var item = NbtMap.builder().putByte("Slot", (byte) 1).putString("id", "minecraft:stone").build();
        var other = NbtMap.builder().putByte("Slot", (byte) 0).putString("id", "minecraft:dirt").build();
        var chest = NbtMap.builder().putString("CustomName", "\"box\"")
                .putList("Items", NbtType.COMPOUND, List.of(other, item)).build();
        assertTrue(test(nbt(NbtMap.builder().putString("CustomName", "\"box\"").build()), Block.CHEST, chest));
        assertFalse(test(nbt(NbtMap.builder().putString("CustomName", "\"bag\"").build()), Block.CHEST, chest));
        // 列表中的每个元素都要有部分匹配的元素
        var slotOne = NbtMap.builder().putList("Items", NbtType.COMPOUND, List.of(NbtMap.builder().putByte("Slot", (byte) 1).build())).build();
        assertTrue(test(nbt(slotOne), Block.CHEST, chest));
        var slotTwo = NbtMap.builder().putList("Items", NbtType.COMPOUND, List.of(NbtMap.builder().putByte("Slot", (byte) 2).build())).build();
        assertFalse(test(nbt(slotTwo), Block.CHEST, chest));
        // 空列表只匹配空列表
        var empty = NbtMap.builder().putList("Items", NbtType.COMPOUND, List.of()).build();
        assertFalse(test(nbt(empty), Block.CHEST, chest));
        assertTrue(test(nbt(empty), Block.CHEST, empty));
        // 比较的数据带有方块实体的类型与坐标（原版 saveWithFullMetadata）
        var located = NbtMap.builder().putString("id", "minecraft:chest").putInt("x", 3).putInt("y", 64).putInt("z", -7).build();
        assertTrue(test(nbt(located), Block.CHEST, chest));
        assertFalse(test(nbt(located.toBuilder().putInt("x", 4).build()), Block.CHEST, chest));
        // 类型不同的值不匹配
        assertFalse(test(nbt(NbtMap.builder().putInt("CustomName", 1).build()), Block.CHEST, chest));
        // 没有方块实体时不匹配
        assertFalse(test(nbt(NbtMap.builder().putString("CustomName", "\"box\"").build()), Block.CHEST, null));
    }

    @Test
    void componentConditionsAreNotSupported() {
        var components = new DataComponentMatchers(Map.of(),
                Map.of(new DataComponentMatchers.PredicateType(true, 0), NbtMap.EMPTY));
        var predicate = new BlockPredicate(null, null, null, components);
        // 没有方块实体时与原版相同，直接不匹配
        assertFalse(test(predicate, Block.CHEST, null));
        assertThrows(UnsupportedOperationException.class, () -> test(predicate, Block.CHEST, NbtMap.EMPTY));
    }
}
