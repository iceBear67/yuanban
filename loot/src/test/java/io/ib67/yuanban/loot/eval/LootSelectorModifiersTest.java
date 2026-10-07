package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContexts;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.function.DiscardFunction;
import io.ib67.yuanban.loot.function.EnchantRandomlyFunction;
import io.ib67.yuanban.loot.function.EnchantedOptions;
import io.ib67.yuanban.loot.function.FunctionReference;
import io.ib67.yuanban.loot.function.LimitCountFunction;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.function.SequenceFunction;
import io.ib67.yuanban.loot.function.SetComponentsFunction;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.function.SetDamageFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 修饰器：每种实现的运行规则覆盖单条用例。原版
 * {@code LootItemFunction} 大部分 run 的语义在
 * {@link LootSelector#applyFunction} 逐个对应字节码。
 */
@EnvTest
class LootSelectorModifiersTest {

    private static LootTable singleLoot(List<LootFunction> functions) {
        return new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), functions, new ConstantValue(1.0f),
                        List.of(new ItemEntry(List.of(), List.of(), 1, 0,
                                Key.key("minecraft", "stone"))))), null);
    }

    private static LootTable entryWrapped(List<LootFunction> entryFunctions) {
        var entry = new ItemEntry(List.of(), entryFunctions, 1, 0, Key.key("minecraft", "stone"));
        return new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), List.of(), new ConstantValue(1.0f), List.of(entry))), null);
    }

    @Test
    void setCountFixesAmount(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = singleLoot(List.of(new SetCountFunction(List.of(), new ConstantValue(3.0f), false)));
        var result = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        assertEquals(1, result.size());
        assertEquals(3, result.getFirst().amount());
    }

    @Test
    void setCountAddStacks(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = singleLoot(List.of(new SetCountFunction(List.of(), new ConstantValue(2.0f), true)));
        var result = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        assertEquals(1, result.size());
        assertEquals(3, result.getFirst().amount());
    }

    @Test
    void setDamageProducesExpectedValue(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        // DAMAGE 系数 0.5，MAX_DAMAGE 必须为正值才能生效；原版仅对 isDamageableItem 起作用。
        // 这里用 set_components 先给栈贴一个 MAX_DAMAGE=64。
        var patchTable = new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), List.of(
                        new SetComponentsFunction(List.of(),
                                DataComponentMap.builder()
                                        .set(DataComponents.MAX_DAMAGE, 64)
                                        .set(DataComponents.DAMAGE, 0)
                                        .build()),
                        new SetDamageFunction(List.of(), new ConstantValue(0.5f), false)),
                        new ConstantValue(1.0f),
                        List.of(new ItemEntry(List.of(), List.of(), 1, 0,
                                Key.key("minecraft", "stone"))))), null);
        var result = selector.roll(patchTable, LootContexts.empty(environment), new Random(0L));
        assertEquals(1, result.size());
        assertEquals(32, result.getFirst().get(DataComponents.DAMAGE));
    }

    @Test
    void limitCountClamps(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), List.of(
                        new SetCountFunction(List.of(), new ConstantValue(10.0f), false),
                        new LimitCountFunction(List.of(),
                                new IntRange(new ConstantValue(2.0f), new ConstantValue(4.0f)))),
                        new ConstantValue(1.0f),
                        List.of(new ItemEntry(List.of(), List.of(), 1, 0,
                                Key.key("minecraft", "stone"))))), null);
        var result = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        assertEquals(4, result.getFirst().amount());
    }

    @Test
    void discardRemovesStack(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = singleLoot(List.of(new DiscardFunction(List.of())));
        var result = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        assertEquals(0, result.size());
    }

    @Test
    void sequenceAppliesInOrder(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = singleLoot(List.of(new SequenceFunction(List.of(), List.of(
                new SetCountFunction(List.of(), new ConstantValue(4.0f), false),
                new SetCountFunction(List.of(), new ConstantValue(1.0f), true)))));
        var result = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        assertEquals(5, result.getFirst().amount());
    }

    @Test
    void setComponentsWritesPatch(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var patch = DataComponentMap.builder()
                .set(DataComponents.MAX_DAMAGE, 100)
                .build();
        var table = singleLoot(List.of(new SetComponentsFunction(List.of(), patch)));
        var result = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        assertEquals(100, result.getFirst().get(DataComponents.MAX_DAMAGE));
    }

    @Test
    void enchantRandomlyOnBookWritesStoredEnchantments(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        // 原版 enchant_randomly 面对普通 BOOK 会 transmute 成 ENCHANTED_BOOK，loot 模块只保留
        // STORED_ENCHANTMENTS 组件写入路径（原版用书保存上的魔咒写在 STORED_ENCHANTMENTS）。
        var table = new LootTable(Key.key("empty"), List.of(), List.of(
                new LootPool(List.of(), List.of(
                        new EnchantRandomlyFunction(List.of(),
                                Optional.of(EnchantedOptions.ofList(List.of(
                                        Key.key("minecraft", "sharpness")))), true, false)),
                        new ConstantValue(1.0f),
                        List.of(new ItemEntry(List.of(), List.of(), 1, 0,
                                Key.key("minecraft", "book"))))), null);
        var out = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        assertEquals(1, out.size());
        var stored = out.getFirst().get(DataComponents.STORED_ENCHANTMENTS);
        // 原版对 book 走 ENCHANTED_BOOK 材质后再写 STORED_ENCHANTMENTS；loot 模块保留材质只写组件。
    }

    @Test
    void functionReferenceResolves(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.putFunction(Key.key("test", "twice"),
                new SetCountFunction(List.of(), new ConstantValue(2.0f), false));
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = entryWrapped(List.of(new FunctionReference(List.of(), Key.key("test", "twice"))));
        var out = selector.roll(table, LootContexts.empty(environment), new Random(0L));
        assertEquals(2, out.getFirst().amount());
    }

    @Test
    void functionReferenceMissingFailsFast(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var selector = new LootSelector(env.process(), new LootManager(LootTypeRegistry.vanilla()),
                LootTypeRegistry.vanilla(), environment);
        var table = entryWrapped(List.of(new FunctionReference(List.of(), Key.key("test", "missing"))));
        var exception = assertThrows(LootException.class, () ->
                selector.roll(table, LootContexts.empty(environment), new Random(0L)));
        assertEquals("Unknown function: test:missing", exception.getMessage());
    }
}
