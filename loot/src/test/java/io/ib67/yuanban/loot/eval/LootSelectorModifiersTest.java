package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.codec.NbtPath;
import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.condition.RandomChanceCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContext;
import io.ib67.yuanban.loot.context.LootContextArg;
import io.ib67.yuanban.loot.context.LootContexts;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.function.AppendOperation;
import io.ib67.yuanban.loot.function.ApplyBonusFormula;
import io.ib67.yuanban.loot.function.ApplyBonusFunction;
import io.ib67.yuanban.loot.function.AttributeModifierRecord;
import io.ib67.yuanban.loot.function.CopyBlockStateFunction;
import io.ib67.yuanban.loot.function.CopyComponentsFunction;
import io.ib67.yuanban.loot.function.CopyCustomDataFunction;
import io.ib67.yuanban.loot.function.CopyNameFunction;
import io.ib67.yuanban.loot.function.CopyOperation;
import io.ib67.yuanban.loot.function.DiscardFunction;
import io.ib67.yuanban.loot.function.EnchantRandomlyFunction;
import io.ib67.yuanban.loot.function.EnchantWithLevelsFunction;
import io.ib67.yuanban.loot.function.EnchantedCountIncreaseFunction;
import io.ib67.yuanban.loot.function.ExplosionDecayFunction;
import io.ib67.yuanban.loot.function.FillPlayerHeadFunction;
import io.ib67.yuanban.loot.function.FilteredFunction;
import io.ib67.yuanban.loot.function.FunctionReference;
import io.ib67.yuanban.loot.function.InsertOperation;
import io.ib67.yuanban.loot.function.LimitCountFunction;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.function.MergeStrategy;
import io.ib67.yuanban.loot.function.ModifyContentsFunction;
import io.ib67.yuanban.loot.function.NbtSource;
import io.ib67.yuanban.loot.function.ReplaceAllOperation;
import io.ib67.yuanban.loot.function.ReplaceSectionOperation;
import io.ib67.yuanban.loot.function.SequenceFunction;
import io.ib67.yuanban.loot.function.SetAttributesFunction;
import io.ib67.yuanban.loot.function.SetBannerPatternFunction;
import io.ib67.yuanban.loot.function.SetBookCoverFunction;
import io.ib67.yuanban.loot.function.SetComponentsFunction;
import io.ib67.yuanban.loot.function.SetContainerContentsFunction;
import io.ib67.yuanban.loot.function.SetContainerLootTableFunction;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.function.SetCustomDataFunction;
import io.ib67.yuanban.loot.function.SetCustomModelDataFunction;
import io.ib67.yuanban.loot.function.SetDamageFunction;
import io.ib67.yuanban.loot.function.SetEnchantmentsFunction;
import io.ib67.yuanban.loot.function.SetFireworkExplosionFunction;
import io.ib67.yuanban.loot.function.SetFireworksFunction;
import io.ib67.yuanban.loot.function.SetInstrumentFunction;
import io.ib67.yuanban.loot.function.SetItemFunction;
import io.ib67.yuanban.loot.function.SetLoreFunction;
import io.ib67.yuanban.loot.function.SetNameFunction;
import io.ib67.yuanban.loot.function.SetNameTarget;
import io.ib67.yuanban.loot.function.SetOminousBottleAmplifierFunction;
import io.ib67.yuanban.loot.function.SetPotionFunction;
import io.ib67.yuanban.loot.function.SetRandomDyesFunction;
import io.ib67.yuanban.loot.function.SetRandomPotionFunction;
import io.ib67.yuanban.loot.function.SetStewEffectFunction;
import io.ib67.yuanban.loot.function.SetWritableBookPagesFunction;
import io.ib67.yuanban.loot.function.SetWrittenBookPagesFunction;
import io.ib67.yuanban.loot.function.SmeltFunction;
import io.ib67.yuanban.loot.function.StandAloneOperation;
import io.ib67.yuanban.loot.function.StewEffectEntry;
import io.ib67.yuanban.loot.function.ToggleTooltipsFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.NumberProvider;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.slot.ContainerComponent;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.color.Color;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockEntityType;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.book.FilteredText;
import net.minestom.server.item.component.AttributeList;
import net.minestom.server.item.component.BannerPatterns;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.ItemBlockState;
import net.minestom.server.item.component.SeededContainerLoot;
import net.minestom.server.item.component.SuspiciousStewEffects;
import net.minestom.server.item.component.WrittenBookContent;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.PotionType;
import net.minestom.server.registry.RegistryKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static io.ib67.yuanban.loot.eval.EvalSupport.context;
import static io.ib67.yuanban.loot.eval.EvalSupport.selector;
import static io.ib67.yuanban.loot.eval.EvalSupport.single;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 修饰器：原版 {@code net.minecraft.world.level.storage.loot.functions} 同名类的 {@code run}。
 */
@EnvTest
class LootSelectorModifiersTest {
    /**
     * 唯一的条目产出 {@code material} 的物品，依次经过 {@code functions}。
     */
    private static List<ItemStack> roll(Env env, InMemoryLootEnvironment environment, LootContext context, Material material,
                                        LootFunction... functions) {
        var entry = new ItemEntry(List.of(), List.of(functions), 1, 0, material);
        return selector(env, environment).roll(single(entry), context, new Random(0L));
    }

    private static ItemStack rollOne(Env env, InMemoryLootEnvironment environment, LootContext context, Material material,
                                     LootFunction... functions) {
        var result = roll(env, environment, context, material, functions);
        assertEquals(1, result.size(), result.toString());
        return result.getFirst();
    }

    private static LootContext empty(InMemoryLootEnvironment environment) {
        return LootContexts.empty(null, environment);
    }

    @Test
    void setCountFixesAndAdds(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertEquals(3, rollOne(env, environment, empty(environment), Material.STONE,
                new SetCountFunction(List.of(), new ConstantValue(3.0f), false)).amount());
        assertEquals(3, rollOne(env, environment, empty(environment), Material.STONE,
                new SetCountFunction(List.of(), new ConstantValue(2.0f), true)).amount());
    }

    @Test
    void setCountSplitsAtMaxStackSize(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        // 原版 LootTable.createStackSplitter：100 个石头切成 64 与 36
        var result = roll(env, environment, empty(environment), Material.STONE,
                new SetCountFunction(List.of(), new ConstantValue(100.0f), false));
        assertEquals(List.of(64, 36), result.stream().map(ItemStack::amount).toList());
    }

    @Test
    void functionConditionsGateTheFunction(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var never = new SetCountFunction(List.of(new RandomChanceCondition(new ConstantValue(0.0f))), new ConstantValue(5.0f), false);
        assertEquals(1, rollOne(env, environment, empty(environment), Material.STONE, never).amount());
    }

    @Test
    void setDamageProducesExpectedValue(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.DIAMOND_SWORD,
                new SetDamageFunction(List.of(), new ConstantValue(0.5f), false));
        int maxDamage = ItemStack.of(Material.DIAMOND_SWORD).get(DataComponents.MAX_DAMAGE);
        assertEquals((int) Math.floor(0.5f * maxDamage), stack.get(DataComponents.DAMAGE));
        // 不可损坏的物品不修改
        assertEquals(ItemStack.of(Material.STONE), rollOne(env, environment, empty(environment), Material.STONE,
                new SetDamageFunction(List.of(), new ConstantValue(0.5f), false)));
    }

    @Test
    void limitCountClamps(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertEquals(4, rollOne(env, environment, empty(environment), Material.STONE,
                new SetCountFunction(List.of(), new ConstantValue(10.0f), false),
                new LimitCountFunction(List.of(), new IntRange(new ConstantValue(2.0f), new ConstantValue(4.0f)))).amount());
        // 原版 Mth.clamp：下界大于上界时结果是上界
        assertEquals(2, rollOne(env, environment, empty(environment), Material.STONE,
                new LimitCountFunction(List.of(), new IntRange(new ConstantValue(5.0f), new ConstantValue(2.0f)))).amount());
    }

    @Test
    void discardRemovesStack(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertTrue(roll(env, environment, empty(environment), Material.STONE, new DiscardFunction(List.of())).isEmpty());
    }

    @Test
    void sequenceAppliesInOrder(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertEquals(5, rollOne(env, environment, empty(environment), Material.STONE, new SequenceFunction(List.of(
                new SetCountFunction(List.of(), new ConstantValue(4.0f), false),
                new SetCountFunction(List.of(), new ConstantValue(1.0f), true)))).amount());
    }

    @Test
    void setComponentsWritesPatchAndRemovals(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var patch = DataComponentMap.patchBuilder()
                .set(DataComponents.CUSTOM_NAME, Component.text("named"))
                .remove(DataComponents.MAX_STACK_SIZE)
                .build();
        var stack = rollOne(env, environment, empty(environment), Material.DIAMOND_SWORD, new SetComponentsFunction(List.of(), patch));
        assertEquals(Component.text("named"), stack.get(DataComponents.CUSTOM_NAME));
        assertEquals(null, stack.get(DataComponents.MAX_STACK_SIZE));
    }

    @Test
    void setComponentsRevertsWhenTheResultIsInvalid(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        // 原版 ItemStack#applyComponentsAndValidate：可损坏又可堆叠的物品不合法，补丁被撤回
        var patch = DataComponentMap.builder().set(DataComponents.MAX_DAMAGE, 100).build();
        assertEquals(ItemStack.of(Material.STONE), rollOne(env, environment, empty(environment), Material.STONE,
                new SetComponentsFunction(List.of(), patch)));
    }

    @Test
    void setItemKeepsCountAndComponents(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.STONE,
                new SetCountFunction(List.of(), new ConstantValue(3.0f), false),
                new SetNameFunction(List.of(), Component.text("x"), null, SetNameTarget.CUSTOM_NAME),
                new SetItemFunction(List.of(), Material.DIRT));
        assertEquals(Material.DIRT, stack.material());
        assertEquals(3, stack.amount());
        assertEquals(Component.text("x"), stack.get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void setNameTargets(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.STONE,
                new SetNameFunction(List.of(), Component.text("item"), null, SetNameTarget.ITEM_NAME));
        assertEquals(Component.text("item"), stack.get(DataComponents.ITEM_NAME));
    }

    @Test
    void setLoreListOperations(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var a = Component.text("a");
        var b = Component.text("b");
        var c = Component.text("c");
        var base = new SetLoreFunction(List.of(), List.of(a, b), ReplaceAllOperation.INSTANCE, Optional.empty());
        assertEquals(List.of(a, c, b), rollOne(env, environment, empty(environment), Material.STONE, base,
                new SetLoreFunction(List.of(), List.of(c), new InsertOperation(1), Optional.empty())).get(DataComponents.LORE));
        assertEquals(List.of(c, b), rollOne(env, environment, empty(environment), Material.STONE, base,
                new SetLoreFunction(List.of(), List.of(c), new ReplaceSectionOperation(0, 1), Optional.empty())).get(DataComponents.LORE));
        // 偏移量越界时原样保留
        assertEquals(List.of(a, b), rollOne(env, environment, empty(environment), Material.STONE, base,
                new SetLoreFunction(List.of(), List.of(c), new InsertOperation(5), Optional.empty())).get(DataComponents.LORE));
        assertEquals(List.of(a, b, c), rollOne(env, environment, empty(environment), Material.STONE, base,
                new SetLoreFunction(List.of(), List.of(c), AppendOperation.INSTANCE, Optional.empty())).get(DataComponents.LORE));
    }

    @Test
    void functionReferenceResolves(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.putFunction(Key.key("test", "twice"), new SetCountFunction(List.of(), new ConstantValue(2.0f), false));
        assertEquals(2, rollOne(env, environment, empty(environment), Material.STONE,
                new FunctionReference(List.of(), Key.key("test", "twice"))).amount());
    }

    @Test
    void functionReferenceMissingFailsFast(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var exception = assertThrows(LootException.class, () -> roll(env, environment, empty(environment), Material.STONE,
                new FunctionReference(List.of(), Key.key("test", "missing"))));
        assertEquals("Unknown function: test:missing", exception.getMessage());
    }

    @Test
    void enchantRandomlyOnBookMakesEnchantedBook(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var options = Optional.<HolderSet<Enchantment>>of(HolderSet.direct(List.of(Enchantment.SHARPNESS)));
        var stack = rollOne(env, environment, empty(environment), Material.BOOK,
                new EnchantRandomlyFunction(List.of(), options, true, false));
        assertEquals(Material.ENCHANTED_BOOK, stack.material());
        int level = stack.get(DataComponents.STORED_ENCHANTMENTS).level(Enchantment.SHARPNESS);
        assertTrue(level >= 1 && level <= 5, "level " + level);
    }

    @Test
    void enchantRandomlyOnlyCompatible(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        // 锋利不能附在石头上，没有候选时物品不变
        var options = Optional.<HolderSet<Enchantment>>of(HolderSet.direct(List.of(Enchantment.SHARPNESS)));
        assertEquals(ItemStack.of(Material.STONE), rollOne(env, environment, empty(environment), Material.STONE,
                new EnchantRandomlyFunction(List.of(), options, true, false)));
    }

    @Test
    void enchantRandomlyAddsTradeCostWhenAllowed(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var options = Optional.<HolderSet<Enchantment>>of(HolderSet.direct(List.of(Enchantment.SHARPNESS)));
        var context = context(environment, null, ContextKey.ADDITIONAL_COST_COMPONENT_ALLOWED, true);
        var stack = rollOne(env, environment, context, Material.BOOK, new EnchantRandomlyFunction(List.of(), options, true, true));
        assertNotNull(stack.get(DataComponents.ADDITIONAL_TRADE_COST));
    }

    @Test
    void enchantWithLevelsUsesEnchantable(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.DIAMOND_SWORD,
                new EnchantWithLevelsFunction(List.of(), new ConstantValue(30.0f)));
        assertTrue(!stack.get(DataComponents.ENCHANTMENTS).enchantments().isEmpty());
        // 没有 enchantable 组件的物品得不到附魔
        assertEquals(ItemStack.of(Material.STONE), rollOne(env, environment, empty(environment), Material.STONE,
                new EnchantWithLevelsFunction(List.of(), new ConstantValue(30.0f))));
    }

    @Test
    void setEnchantmentsTransmutesBookAndClamps(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var levels = new LinkedHashMap<RegistryKey<Enchantment>, NumberProvider>();
        levels.put(Enchantment.SHARPNESS, new ConstantValue(300.0f));
        var stack = rollOne(env, environment, empty(environment), Material.BOOK, new SetEnchantmentsFunction(List.of(), levels));
        assertEquals(Material.ENCHANTED_BOOK, stack.material());
        assertEquals(255, stack.get(DataComponents.STORED_ENCHANTMENTS).level(Enchantment.SHARPNESS));
        var add = new LinkedHashMap<RegistryKey<Enchantment>, NumberProvider>();
        add.put(Enchantment.SHARPNESS, new ConstantValue(-300.0f));
        var removed = rollOne(env, environment, empty(environment), Material.BOOK, new SetEnchantmentsFunction(List.of(), levels),
                new SetEnchantmentsFunction(List.of(), add, true));
        assertEquals(0, removed.get(DataComponents.STORED_ENCHANTMENTS).level(Enchantment.SHARPNESS));
    }

    @Test
    void setCustomDataMerges(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.STONE,
                new SetCustomDataFunction(List.of(), CompoundBinaryTag.builder().putInt("a", 1).build()),
                new SetCustomDataFunction(List.of(), CompoundBinaryTag.builder().putInt("b", 2).build()));
        assertEquals(CompoundBinaryTag.builder().putInt("a", 1).putInt("b", 2).build(), stack.get(DataComponents.CUSTOM_DATA).nbt());
    }

    @Test
    void furnaceSmeltUsesSmeltingView(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.putSmelting(Material.BEEF, ItemStack.of(Material.COOKED_BEEF));
        assertEquals(ItemStack.of(Material.COOKED_BEEF, 3), rollOne(env, environment, empty(environment), Material.BEEF,
                new SetCountFunction(List.of(), new ConstantValue(3.0f), false), new SmeltFunction(List.of(), true)));
        assertEquals(ItemStack.of(Material.COOKED_BEEF), rollOne(env, environment, empty(environment), Material.BEEF,
                new SetCountFunction(List.of(), new ConstantValue(3.0f), false), new SmeltFunction(List.of(), false)));
        // 没有配方时原样返回
        assertEquals(ItemStack.of(Material.STONE), rollOne(env, environment, empty(environment), Material.STONE, new SmeltFunction()));
    }

    @Test
    void enchantedCountIncreaseReadsLivingAttacker(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var attacker = new LivingEntity(EntityType.ZOMBIE);
        attacker.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.DIAMOND_SWORD)
                .with(DataComponents.ENCHANTMENTS, new EnchantmentList(Enchantment.LOOTING, 3)));
        var function = new EnchantedCountIncreaseFunction(List.of(), Enchantment.LOOTING, new ConstantValue(1.0f), 2);
        var context = context(environment, null, ContextKey.ATTACKING_ENTITY, attacker);
        // 1 + round(3 * 1) = 4，再限制为 2
        assertEquals(2, rollOne(env, environment, context, Material.STONE, function).amount());
        var unlimited = new EnchantedCountIncreaseFunction(List.of(), Enchantment.LOOTING, new ConstantValue(1.0f), 0);
        assertEquals(4, rollOne(env, environment, context, Material.STONE, unlimited).amount());
    }

    @Test
    void setAttributesReplacesSameId(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var id = Key.key("example:bonus");
        var first = new AttributeModifierRecord(id, Attribute.ATTACK_DAMAGE, AttributeOperation.ADD_VALUE,
                new ConstantValue(1.0f), List.of(EquipmentSlotGroup.MAIN_HAND));
        var second = new AttributeModifierRecord(id, Attribute.ATTACK_DAMAGE, AttributeOperation.ADD_VALUE,
                new ConstantValue(5.0f), List.of(EquipmentSlotGroup.MAIN_HAND));
        var stack = rollOne(env, environment, empty(environment), Material.STONE,
                new SetAttributesFunction(List.of(), List.of(first), true),
                new SetAttributesFunction(List.of(), List.of(second), false));
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers();
        assertEquals(1, modifiers.size());
        assertEquals(5.0, modifiers.getFirst().modifier().amount());
        assertEquals(EquipmentSlotGroup.MAIN_HAND, modifiers.getFirst().slot());
    }

    @Test
    void setStewEffectOnlyOnSuspiciousStew(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var function = new SetStewEffectFunction(List.of(), List.of(new StewEffectEntry(PotionEffect.NIGHT_VISION, new ConstantValue(7.0f))));
        var stew = rollOne(env, environment, empty(environment), Material.SUSPICIOUS_STEW, function);
        // 非瞬时效果的时长乘以 20
        assertEquals(List.of(new SuspiciousStewEffects.Effect(PotionEffect.NIGHT_VISION, 140)),
                stew.get(DataComponents.SUSPICIOUS_STEW_EFFECTS).effects());
        assertEquals(ItemStack.of(Material.STONE), rollOne(env, environment, empty(environment), Material.STONE, function));
    }

    @Test
    void copyNameFromEntity(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var entity = new Entity(EntityType.PIG);
        entity.set(DataComponents.CUSTOM_NAME, Component.text("Babe"));
        var stack = rollOne(env, environment, context(environment, null, ContextKey.THIS_ENTITY, entity), Material.STONE,
                new CopyNameFunction(List.of(), new LootContextArg.Entity(EntityTarget.THIS)));
        assertEquals(Component.text("Babe"), stack.get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void setContentsBuildsContainer(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.SHULKER_BOX,
                new SetContainerContentsFunction(List.of(), ContainerComponent.CONTAINER, List.of(
                        new ItemEntry(List.of(), List.of(new SetCountFunction(List.of(), new ConstantValue(2.0f), false)), 1, 0, Material.APPLE),
                        new ItemEntry(Material.DIAMOND))));
        assertEquals(List.of(ItemStack.of(Material.APPLE, 2), ItemStack.of(Material.DIAMOND)), stack.get(DataComponents.CONTAINER));
    }

    @Test
    void setContentsBundleWeight(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        // 原版 BundleContents：一个收纳袋最多 64 个可堆叠到 64 的物品，放入的物品移到最前面
        var stack = rollOne(env, environment, empty(environment), Material.BUNDLE,
                new SetContainerContentsFunction(List.of(), ContainerComponent.BUNDLE_CONTENTS, List.of(
                        new ItemEntry(List.of(), List.of(new SetCountFunction(List.of(), new ConstantValue(40.0f), false)), 1, 0, Material.STONE),
                        new ItemEntry(List.of(), List.of(new SetCountFunction(List.of(), new ConstantValue(40.0f), false)), 1, 0, Material.DIRT),
                        new ItemEntry(Material.SHULKER_BOX))));
        assertEquals(List.of(ItemStack.of(Material.DIRT, 24), ItemStack.of(Material.STONE, 40)), stack.get(DataComponents.BUNDLE_CONTENTS));
    }

    @Test
    void modifyContentsAppliesToEachItem(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.SHULKER_BOX,
                new SetContainerContentsFunction(List.of(), ContainerComponent.CONTAINER, List.of(new ItemEntry(Material.APPLE))),
                new ModifyContentsFunction(List.of(), ContainerComponent.CONTAINER,
                        new SetCountFunction(List.of(), new ConstantValue(100.0f), false)));
        // 修饰之后的数量限制在最大堆叠数以内
        assertEquals(List.of(ItemStack.of(Material.APPLE, 64)), stack.get(DataComponents.CONTAINER));
    }

    @Test
    void filteredChoosesBranch(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var apples = new ItemPredicate(Optional.of(HolderSet.direct(List.of(RegistryKey.<Material>of(Key.key("apple"))))),
                MinMaxBounds.ANY_INT, ComponentMatchers.ANY);
        var function = new FilteredFunction(List.of(), apples,
                Optional.of(new SetCountFunction(List.of(), new ConstantValue(2.0f), false)),
                Optional.of(new SetCountFunction(List.of(), new ConstantValue(3.0f), false)));
        assertEquals(2, rollOne(env, environment, empty(environment), Material.APPLE, function).amount());
        assertEquals(3, rollOne(env, environment, empty(environment), Material.STONE, function).amount());
        assertEquals(1, rollOne(env, environment, empty(environment), Material.STONE,
                new FilteredFunction(List.of(), apples, Optional.empty(), Optional.empty())).amount());
    }

    @Test
    void applyBonusFormulas(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var tool = ItemStack.of(Material.DIAMOND_PICKAXE).with(DataComponents.ENCHANTMENTS, new EnchantmentList(Enchantment.FORTUNE, 2));
        var context = context(environment, null, ContextKey.TOOL, tool);
        var oreDrops = rollOne(env, environment, context, Material.DIAMOND,
                new ApplyBonusFunction(List.of(), Enchantment.FORTUNE, ApplyBonusFormula.OreDrops.INSTANCE));
        assertTrue(oreDrops.amount() >= 1 && oreDrops.amount() <= 3);
        var uniform = rollOne(env, environment, context, Material.DIAMOND,
                new ApplyBonusFunction(List.of(), Enchantment.FORTUNE, new ApplyBonusFormula.UniformBonusCount(1)));
        assertTrue(uniform.amount() >= 1 && uniform.amount() <= 3);
        // 等级 2 加上额外 3 轮、成功概率 1：加 5
        assertEquals(6, rollOne(env, environment, context, Material.DIAMOND,
                new ApplyBonusFunction(List.of(), Enchantment.FORTUNE, new ApplyBonusFormula.BinomialWithBonusCount(3, 1.0f))).amount());
        // 工具没有附魔时等级为 0
        assertEquals(1, rollOne(env, environment, context(environment, null, ContextKey.TOOL, ItemStack.of(Material.STICK)),
                Material.DIAMOND, new ApplyBonusFunction(List.of(), Enchantment.FORTUNE, ApplyBonusFormula.OreDrops.INSTANCE)).amount());
    }

    @Test
    void setLootTableWritesContainerLoot(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.CHEST,
                new SetContainerLootTableFunction(List.of(), Key.key("chests/simple_dungeon"), 7L, BlockEntityType.CHEST));
        assertEquals(new SeededContainerLoot("minecraft:chests/simple_dungeon", 7L), stack.get(DataComponents.CONTAINER_LOOT));
    }

    @Test
    void explosionDecay(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        // 半径为 1 时每个物品都保留；半径很大时几乎都不保留
        assertEquals(5, rollOne(env, environment, context(environment, null, ContextKey.EXPLOSION_RADIUS, 1.0f), Material.STONE,
                new SetCountFunction(List.of(), new ConstantValue(5.0f), false), new ExplosionDecayFunction(List.of())).amount());
        assertTrue(roll(env, environment, context(environment, null, ContextKey.EXPLOSION_RADIUS, 1.0e9f), Material.STONE,
                new SetCountFunction(List.of(), new ConstantValue(5.0f), false), new ExplosionDecayFunction(List.of())).isEmpty());
    }

    @Test
    void fillPlayerHead(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 42, 0));
        var stack = rollOne(env, environment, context(environment, instance, ContextKey.THIS_ENTITY, player), Material.PLAYER_HEAD,
                new FillPlayerHeadFunction(List.of(), EntityTarget.THIS));
        var profile = stack.get(DataComponents.PROFILE);
        assertEquals(player.getUuid(), profile.profile().unify(GameProfile::uuid, partial -> null));
        assertEquals(player.getUsername(), profile.profile().unify(GameProfile::name, partial -> null));
        // 不是玩家头颅时不修改
        assertEquals(ItemStack.of(Material.STONE), rollOne(env, environment, context(environment, instance, ContextKey.THIS_ENTITY, player),
                Material.STONE, new FillPlayerHeadFunction(List.of(), EntityTarget.THIS)));
    }

    @Test
    void copyCustomDataFromStorage(Env env) throws Exception {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.putStorage(Key.key("example:data"), CompoundBinaryTag.builder()
                .put("source", CompoundBinaryTag.builder().putInt("x", 1).build())
                .put("list", ListBinaryTag.builder().add(IntBinaryTag.intBinaryTag(4)).build())
                .build());
        var function = new CopyCustomDataFunction(List.of(), new NbtSource.Storage(Key.key("example:data")), List.of(
                new CopyOperation(NbtPath.of("source"), NbtPath.of("copied"), MergeStrategy.REPLACE),
                new CopyOperation(NbtPath.of("source"), NbtPath.of("merged"), MergeStrategy.MERGE),
                new CopyOperation(NbtPath.of("list[0]"), NbtPath.of("appended"), MergeStrategy.APPEND),
                new CopyOperation(NbtPath.of("missing"), NbtPath.of("never"), MergeStrategy.REPLACE)));
        var data = rollOne(env, environment, empty(environment), Material.STONE, function).get(DataComponents.CUSTOM_DATA).nbt();
        assertEquals(CompoundBinaryTag.builder().putInt("x", 1).build(), data.getCompound("copied"));
        assertEquals(CompoundBinaryTag.builder().putInt("x", 1).build(), data.getCompound("merged"));
        assertEquals(4, data.getList("appended").getInt(0));
        assertEquals(null, data.get("never"));
    }

    @Test
    void copyCustomDataWithoutMatchesKeepsItem(Env env) throws Exception {
        var environment = new InMemoryLootEnvironment(env.process());
        var function = new CopyCustomDataFunction(List.of(), new NbtSource.Storage(Key.key("example:empty")), List.of(
                new CopyOperation(NbtPath.of("missing"), NbtPath.of("never"), MergeStrategy.REPLACE)));
        assertEquals(ItemStack.of(Material.STONE), rollOne(env, environment, empty(environment), Material.STONE, function));
    }

    @Test
    void copyStateCopiesKnownProperties(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var context = context(environment, null, ContextKey.BLOCK_STATE, Block.GRASS_BLOCK.withProperty("snowy", "true"));
        var stack = rollOne(env, environment, context, Material.GRASS_BLOCK,
                new CopyBlockStateFunction(List.of(), Block.GRASS_BLOCK, List.of("snowy", "unknown")));
        assertEquals(new ItemBlockState(Map.of("snowy", "true")), stack.get(DataComponents.BLOCK_STATE));
    }

    @Test
    void setBannerPatternAppends(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var flower = new BannerPatterns.Layer(BannerPattern.FLOWER, DyeColor.RED);
        var creeper = new BannerPatterns.Layer(BannerPattern.CREEPER, DyeColor.BLUE);
        var stack = rollOne(env, environment, empty(environment), Material.WHITE_BANNER,
                new SetBannerPatternFunction(List.of(), new BannerPatterns(List.of(flower)), false),
                new SetBannerPatternFunction(List.of(), new BannerPatterns(List.of(creeper)), true));
        assertEquals(new BannerPatterns(List.of(flower, creeper)), stack.get(DataComponents.BANNER_PATTERNS));
    }

    @Test
    void setPotionAndRandomPotion(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertEquals(PotionType.FIRE_RESISTANCE, rollOne(env, environment, empty(environment), Material.POTION,
                new SetPotionFunction(List.of(), PotionType.FIRE_RESISTANCE)).get(DataComponents.POTION_CONTENTS).potion());
        var options = Optional.<HolderSet<PotionType>>of(HolderSet.direct(List.of(RegistryKey.<PotionType>of(Key.key("swiftness")))));
        assertEquals(PotionType.SWIFTNESS, rollOne(env, environment, empty(environment), Material.POTION,
                new SetRandomPotionFunction(List.of(), options)).get(DataComponents.POTION_CONTENTS).potion());
        assertNotNull(rollOne(env, environment, empty(environment), Material.POTION,
                new SetRandomPotionFunction(List.of())).get(DataComponents.POTION_CONTENTS).potion());
    }

    @Test
    void setRandomDyesMakesCountOne(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.LEATHER_HELMET,
                new SetRandomDyesFunction(List.of(), new ConstantValue(2.0f)));
        assertNotNull(stack.get(DataComponents.DYED_COLOR));
        assertEquals(1, stack.amount());
    }

    @Test
    void setInstrumentPicksFromOptions(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var horn = env.process().instrument().getKey(0);
        var stack = rollOne(env, environment, empty(environment), Material.GOAT_HORN,
                new SetInstrumentFunction(List.of(), HolderSet.direct(List.of(horn))));
        assertEquals(horn, stack.get(DataComponents.INSTRUMENT));
    }

    @Test
    void setFireworksAndExplosion(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var explosion = new FireworkExplosion(FireworkExplosion.Shape.STAR, List.of(new Color(1)), List.of(), true, false);
        var fireworks = rollOne(env, environment, empty(environment), Material.FIREWORK_ROCKET,
                new SetFireworksFunction(List.of(), Optional.of(new StandAloneOperation<>(List.of(explosion), AppendOperation.INSTANCE)),
                        Optional.of(3)));
        assertEquals(3, fireworks.get(DataComponents.FIREWORKS).flightDuration());
        assertEquals(List.of(explosion), fireworks.get(DataComponents.FIREWORKS).explosions());
        var star = rollOne(env, environment, empty(environment), Material.FIREWORK_STAR,
                new SetFireworkExplosionFunction(List.of(), Optional.empty(), Optional.of(List.of(0xFF0000)), Optional.empty(),
                        Optional.of(true), Optional.empty()));
        assertEquals(new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, List.of(new Color(0xFF0000)), List.of(), true, false),
                star.get(DataComponents.FIREWORK_EXPLOSION));
    }

    @Test
    void bookFunctions(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var written = rollOne(env, environment, empty(environment), Material.WRITTEN_BOOK,
                new SetBookCoverFunction(List.of(), Optional.of(new FilteredText<>("Title", null)), Optional.of("Author"), Optional.empty()),
                new SetWrittenBookPagesFunction(List.of(), List.of(new FilteredText<>(Component.text("page"), null)), AppendOperation.INSTANCE));
        assertEquals(new WrittenBookContent(new FilteredText<>("Title", null), "Author", 0,
                List.of(new FilteredText<>(Component.text("page"), null)), false), written.get(DataComponents.WRITTEN_BOOK_CONTENT));
        var writable = rollOne(env, environment, empty(environment), Material.WRITABLE_BOOK,
                new SetWritableBookPagesFunction(List.of(), List.of(new FilteredText<>("hello", null)), AppendOperation.INSTANCE));
        assertEquals(List.of(new FilteredText<>("hello", null)), writable.get(DataComponents.WRITABLE_BOOK_CONTENT).pages());
    }

    @Test
    void toggleTooltips(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.DIAMOND_SWORD,
                new ToggleTooltipsFunction(List.of(), Map.<DataComponent<?>, Boolean>of(DataComponents.ENCHANTMENTS, false)));
        assertTrue(stack.get(DataComponents.TOOLTIP_DISPLAY).hiddenComponents().contains(DataComponents.ENCHANTMENTS));
    }

    @Test
    void setOminousBottleAmplifierClamps(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertEquals(4, rollOne(env, environment, empty(environment), Material.OMINOUS_BOTTLE,
                new SetOminousBottleAmplifierFunction(List.of(), new ConstantValue(9.0f))).get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER));
    }

    @Test
    void setCustomModelData(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var stack = rollOne(env, environment, empty(environment), Material.STONE, new SetCustomModelDataFunction(List.of(),
                Optional.of(new StandAloneOperation<>(List.<NumberProvider>of(new ConstantValue(1.5f)), AppendOperation.INSTANCE)),
                Optional.of(new StandAloneOperation<>(List.of(true), AppendOperation.INSTANCE)),
                Optional.empty(),
                Optional.of(new StandAloneOperation<>(List.<NumberProvider>of(new ConstantValue(255.0f)), AppendOperation.INSTANCE))));
        var data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        assertEquals(List.of(1.5f), data.floats());
        assertEquals(List.of(true), data.flags());
        assertEquals(List.of(new Color(255)), data.colors().stream().map(Color::fromRGBLike).toList());
    }

    @Test
    void copyComponentsFromTool(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var tool = ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.CUSTOM_NAME, Component.text("Blade"));
        var stack = rollOne(env, environment, context(environment, null, ContextKey.TOOL, tool), Material.STONE,
                new CopyComponentsFunction(List.of(), LootContextArg.Tool.INSTANCE,
                        Optional.of(List.of(DataComponents.CUSTOM_NAME)), Optional.empty()));
        assertEquals(Component.text("Blade"), stack.get(DataComponents.CUSTOM_NAME));
        assertEquals(CustomData.EMPTY.nbt(), stack.get(DataComponents.CUSTOM_DATA, CustomData.EMPTY).nbt());
        var attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS, AttributeList.EMPTY);
        assertTrue(attributes.modifiers().isEmpty());
    }
}
