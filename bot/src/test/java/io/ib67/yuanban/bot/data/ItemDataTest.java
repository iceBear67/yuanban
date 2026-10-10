package io.ib67.yuanban.bot.data;

import it.unimi.dsi.fastutil.ints.IntList;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.AttackRange;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ToolData;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.UseEffects;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDataTest {
    private static final int DIAMOND_PICKAXE = ItemData.id(Key.key("diamond_pickaxe"));

    private static DataComponents patch() {
        return new DataComponents(new HashMap<>());
    }

    @Test
    void idsFollowTheItemRegistry() {
        assertEquals(0, ItemData.id(Key.key("air")));
        assertEquals(1, ItemData.id(Key.key("stone")));
        assertThrows(IllegalArgumentException.class, () -> ItemData.id(Key.key("test:unknown")));
    }

    @Test
    void toolRulesKeepTagsAndBlockListsAndFillOmittedFields() {
        var tool = Objects.requireNonNull(ItemData.prototype(DIAMOND_PICKAXE, DataComponentTypes.TOOL));
        // 原版 Tool.CODEC 的默认值：default_mining_speed 1，damage_per_block 1，can_destroy_blocks_in_creative true
        assertEquals(1.0f, tool.getDefaultMiningSpeed());
        assertEquals(1, tool.getDamagePerBlock());
        assertTrue(tool.isCanDestroyBlocksInCreative());
        var rules = tool.getRules();
        assertEquals(2, rules.size());
        assertEquals(Key.key("incorrect_for_diamond_tool"), rules.get(0).getBlocks().getLocation());
        assertNull(rules.get(0).getSpeed());
        assertEquals(Boolean.FALSE, rules.get(0).getCorrectForDrops());
        assertEquals(Key.key("mineable/pickaxe"), rules.get(1).getBlocks().getLocation());
        assertEquals(8.0f, rules.get(1).getSpeed());
        assertEquals(Boolean.TRUE, rules.get(1).getCorrectForDrops());
        // 剪刀的第一条规则直接列出方块，按方块编号保存
        var shears = Objects.requireNonNull(ItemData.prototype(ItemData.id(Key.key("shears")), DataComponentTypes.TOOL));
        var cobweb = shears.getRules().getFirst();
        assertNull(cobweb.getBlocks().getLocation());
        assertEquals(IntList.of(Block.COBWEB.id()), cobweb.getBlocks().getHolders());
        assertNull(shears.getRules().get(1).getCorrectForDrops());
        assertNull(ItemData.prototype(ItemData.id(Key.key("stone")), DataComponentTypes.TOOL));
    }

    @Test
    void equippableFillsOmittedFields() {
        var helmet = Objects.requireNonNull(ItemData.prototype(ItemData.id(Key.key("diamond_helmet")), DataComponentTypes.EQUIPPABLE));
        assertEquals(EquipmentSlot.HELMET, helmet.slot());
        assertEquals(BuiltinSound.ITEM_ARMOR_EQUIP_DIAMOND, helmet.equipSound());
        assertEquals(Key.key("diamond"), helmet.model());
        assertNull(helmet.allowedEntities());
        // 原版 Equippable.CODEC 的默认值
        assertTrue(helmet.dispensable());
        assertTrue(helmet.swappable());
        assertTrue(helmet.damageOnHurt());
        assertFalse(helmet.equipOnInteract());
        assertFalse(helmet.canBeSheared());
        assertEquals(BuiltinSound.ITEM_SHEARS_SNIP, helmet.shearingSound());
        var saddle = Objects.requireNonNull(ItemData.prototype(ItemData.id(Key.key("saddle")), DataComponentTypes.EQUIPPABLE));
        assertEquals(EquipmentSlot.SADDLE, saddle.slot());
        assertEquals(Key.key("can_equip_saddle"), Objects.requireNonNull(saddle.allowedEntities()).getLocation());
        assertTrue(saddle.equipOnInteract());
        assertTrue(saddle.canBeSheared());
        assertEquals(BuiltinSound.ITEM_SADDLE_UNEQUIP, saddle.shearingSound());
    }

    @Test
    void useEffectsAndPiercingWeaponsFillOmittedFields() {
        // 原版 UseEffects.CODEC 的默认值为 (false, true, 0.2F)
        assertEquals(new UseEffects(false, true, 0.2f), ItemData.prototype(ItemData.id(Key.key("bow")), DataComponentTypes.USE_EFFECTS));
        var spear = ItemData.id(Key.key("wooden_spear"));
        assertEquals(new UseEffects(true, false, 1.0f), ItemData.prototype(spear, DataComponentTypes.USE_EFFECTS));
        var piercing = Objects.requireNonNull(ItemData.prototype(spear, DataComponentTypes.PIERCING_WEAPON));
        assertTrue(piercing.dealsKnockback());
        assertFalse(piercing.dismounts());
        assertEquals(BuiltinSound.ITEM_SPEAR_WOOD_ATTACK, piercing.sound());
        assertEquals(BuiltinSound.ITEM_SPEAR_WOOD_HIT, piercing.hitSound());
    }

    @Test
    void attackRanges() {
        assertEquals(new AttackRange(2.0f, 4.5f, 2.0f, 6.5f, 0.125f, 0.5f),
                ItemData.prototype(ItemData.id(Key.key("wooden_spear")), DataComponentTypes.ATTACK_RANGE));
        assertNull(ItemData.prototype(ItemData.id(Key.key("stick")), DataComponentTypes.ATTACK_RANGE));
    }

    @Test
    void otherComponentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ItemData.prototype(DIAMOND_PICKAXE, DataComponentTypes.MAX_DAMAGE));
        assertThrows(IllegalArgumentException.class,
                () -> Items.component(new ItemStack(DIAMOND_PICKAXE), DataComponentTypes.MAX_DAMAGE));
    }

    @Test
    void patchesOverrideAndRemoveDefaultComponents() {
        assertNull(Items.component(null, DataComponentTypes.TOOL));
        var plain = new ItemStack(DIAMOND_PICKAXE);
        assertEquals(ItemData.prototype(DIAMOND_PICKAXE, DataComponentTypes.TOOL), Items.component(plain, DataComponentTypes.TOOL));
        var replaced = new ToolData(List.of(), 3.0f, 2, false);
        var override = patch();
        override.put(DataComponentTypes.TOOL, replaced);
        assertEquals(replaced, Items.component(new ItemStack(DIAMOND_PICKAXE, 1, override), DataComponentTypes.TOOL));
        // 补丁中值为 null 表示移除默认组件
        var removal = patch();
        removal.put(DataComponentTypes.TOOL, null);
        var removed = new ItemStack(DIAMOND_PICKAXE, 1, removal);
        assertNull(Items.component(removed, DataComponentTypes.TOOL));
        assertFalse(Items.has(removed, DataComponentTypes.TOOL));
        assertTrue(Items.has(plain, DataComponentTypes.TOOL));
        // 原版物品没有默认的 can_break
        assertNull(Items.component(plain, DataComponentTypes.CAN_BREAK));
    }

    @Test
    void stacksCompareItemsPatchesAndAmounts() {
        var plain = new ItemStack(DIAMOND_PICKAXE, 1);
        assertTrue(Items.sameItemSameComponents(null, null));
        assertFalse(Items.sameItemSameComponents(plain, null));
        // null 补丁与空补丁相同
        assertTrue(Items.sameStack(plain, new ItemStack(DIAMOND_PICKAXE, 1, patch())));
        assertTrue(Items.sameItemSameComponents(plain, new ItemStack(DIAMOND_PICKAXE, 5)));
        assertFalse(Items.sameStack(plain, new ItemStack(DIAMOND_PICKAXE, 5)));
        assertFalse(Items.sameItemSameComponents(plain, new ItemStack(ItemData.id(Key.key("stone")), 1)));
        var damaged = patch();
        damaged.put(DataComponentTypes.DAMAGE, 3);
        assertFalse(Items.sameItemSameComponents(plain, new ItemStack(DIAMOND_PICKAXE, 1, damaged)));
        var sameDamage = patch();
        sameDamage.put(DataComponentTypes.DAMAGE, 3);
        assertTrue(Items.sameStack(new ItemStack(DIAMOND_PICKAXE, 1, sameDamage), new ItemStack(DIAMOND_PICKAXE, 1, damaged)));
    }
}
