package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContextArg;
import io.ib67.yuanban.loot.entry.SlotsEntry;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.slot.ContainerComponent;
import io.ib67.yuanban.loot.slot.SlotRange;
import io.ib67.yuanban.loot.slot.SlotSource;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static io.ib67.yuanban.loot.eval.EvalSupport.context;
import static io.ib67.yuanban.loot.eval.EvalSupport.selector;
import static io.ib67.yuanban.loot.eval.EvalSupport.single;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code slots} 条目与槽位来源：原版 {@code SlotLoot}、{@code SlotSources} 与 {@code Entity#getSlot} 的槽位映射。
 */
@EnvTest
class SlotSourceTest {

    private static SlotRange range(String name) {
        return SlotRange.VALUES.stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    }

    private static SlotSource of(EntityTarget target, String range) {
        return new SlotSource.SlotRangeSource(new LootContextArg.Entity(target), range(range));
    }

    private static List<ItemStack> roll(Env env, InMemoryLootEnvironment environment, Object entity, SlotSource source,
                                        io.ib67.yuanban.loot.function.LootFunction... functions) {
        var entry = new SlotsEntry(List.of(), List.of(functions), 1, 0, source);
        return selector(env, environment).roll(single(entry), context(environment, null, ContextKey.THIS_ENTITY, entity), new Random(0L));
    }

    @Test
    void playerInventorySlotsAreCopiedThroughEntryFunctions(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var player = env.createPlayer(env.createFlatInstance(), new Pos(0, 42, 0));
        player.getInventory().setItemStack(0, ItemStack.of(Material.APPLE, 3));
        player.getInventory().setItemStack(8, ItemStack.of(Material.DIAMOND));
        // 每个物品单独经过条目的修饰器；空槽位不产出
        var result = roll(env, environment, player, of(EntityTarget.THIS, "hotbar.*"),
                new SetCountFunction(List.of(), new ConstantValue(1.0f), true));
        assertEquals(List.of(ItemStack.of(Material.APPLE, 4), ItemStack.of(Material.DIAMOND, 2)), result);
        // 原物品不变
        assertEquals(ItemStack.of(Material.APPLE, 3), player.getInventory().getItemStack(0));
    }

    @Test
    void playerCursorAndCraftingSlots(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var player = env.createPlayer(env.createFlatInstance(), new Pos(0, 42, 0));
        player.getInventory().setCursorItem(ItemStack.of(Material.STICK));
        player.getInventory().setItemStack(PlayerInventoryUtils.CRAFT_SLOT_1 + 2, ItemStack.of(Material.OAK_PLANKS));
        assertEquals(List.of(ItemStack.of(Material.STICK)), roll(env, environment, player, of(EntityTarget.THIS, "player.cursor")));
        assertEquals(List.of(ItemStack.of(Material.OAK_PLANKS)), roll(env, environment, player, of(EntityTarget.THIS, "player.crafting.*")));
    }

    @Test
    void mobEquipment(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var zombie = new LivingEntity(EntityType.ZOMBIE);
        zombie.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.IRON_SWORD));
        zombie.setEquipment(EquipmentSlot.BOOTS, ItemStack.of(Material.IRON_BOOTS));
        assertEquals(List.of(ItemStack.of(Material.IRON_SWORD)), roll(env, environment, zombie, of(EntityTarget.THIS, "weapon.mainhand")));
        assertEquals(List.of(ItemStack.of(Material.IRON_BOOTS)), roll(env, environment, zombie, of(EntityTarget.THIS, "armor.*")));
        // 生物没有物品栏槽位
        assertEquals(List.of(), roll(env, environment, zombie, of(EntityTarget.THIS, "hotbar.*")));
    }

    @Test
    void itemEntitySlotZero(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var item = new ItemEntity(ItemStack.of(Material.EMERALD, 2));
        assertEquals(List.of(ItemStack.of(Material.EMERALD, 2)), roll(env, environment, item, of(EntityTarget.THIS, "contents")));
    }

    @Test
    void filteredLimitedAndContents(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var player = env.createPlayer(env.createFlatInstance(), new Pos(0, 42, 0));
        player.getInventory().setItemStack(0, ItemStack.of(Material.SHULKER_BOX).with(DataComponents.CONTAINER,
                List.of(ItemStack.of(Material.GOLD_INGOT), ItemStack.AIR, ItemStack.of(Material.IRON_INGOT))));
        player.getInventory().setItemStack(1, ItemStack.of(Material.APPLE));
        player.getInventory().setItemStack(2, ItemStack.of(Material.APPLE, 2));
        var apples = new ItemPredicate(Optional.of(HolderSet.direct(List.of(RegistryKey.<Material>of(Key.key("apple"))))),
                MinMaxBounds.ANY_INT, ComponentMatchers.ANY);
        var hotbar = of(EntityTarget.THIS, "hotbar.*");
        assertEquals(List.of(ItemStack.of(Material.APPLE)),
                roll(env, environment, player, new SlotSource.LimitSlots(new SlotSource.Filtered(hotbar, apples), 1)));
        assertEquals(List.of(ItemStack.of(Material.GOLD_INGOT), ItemStack.of(Material.IRON_INGOT)),
                roll(env, environment, player, new SlotSource.Contents(new SlotSource.LimitSlots(hotbar, 1), ContainerComponent.CONTAINER)));
        assertEquals(List.of(), roll(env, environment, player, SlotSource.Empty.INSTANCE));
        assertEquals(List.of(ItemStack.of(Material.APPLE, 3)),
                roll(env, environment, player, new SlotSource.Group(List.of(new SlotSource.Filtered(hotbar, apples)))));
    }
}
