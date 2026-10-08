package io.ib67.yuanban.lootparser;

import io.ib67.yuanban.datapack.DataPackLoader;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.enchant.EnchantmentSubscriber;
import io.ib67.yuanban.enchant.EnchantmentTagSubscriber;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.context.LootContexts;
import io.ib67.yuanban.loot.entry.AlternativesEntry;
import io.ib67.yuanban.loot.eval.LootSelector;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 读取原版 26.2 服务端 jar 中的全部 loot_table 文件，并对几张表实际取用。jar 由根项目的 {@code extractVanillaServer}
 * 任务准备，路径通过系统属性 {@code yuanban.vanilla-server} 传入。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class VanillaLootTest {

    @Test
    void loadsAndRollsVanillaLootTables(Env env) throws IOException {
        var server = Path.of(System.getProperty("yuanban.vanilla-server"));
        var expectedCount = countLootTables(server);
        var process = env.process();
        var manager = new LootManager(LootTypeRegistry.vanilla());
        var subscriber = new LootTableSubscriber(process, manager);
        try (var jar = FileSystems.newFileSystem(URI.create("jar:" + server.toUri()), Map.of())) {
            var loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
                    .provider(new BuiltInPackProvider(jar.getPath("/"), "26.2"))
                    .subscribe(ItemTagSubscriber.subscription(process))
                    .subscribe(new EnchantmentTagSubscriber(process).subscription())
                    .subscribe(new EnchantmentSubscriber(process).subscription())
                    .subscribe(subscriber.subscription())
                    .executor(ForkJoinPool.commonPool())
                    .build();
            loader.start(LootTestWorld.createWorld()).close();
        }

        assertEquals(expectedCount, subscriber.current().tables().size());
        assertEquals(expectedCount, manager.tables().size());
        spotCheck(manager);

        var environment = new InMemoryLootEnvironment(process, 1234L);
        var selector = new LootSelector(process, manager, manager.types(), environment);
        var instance = env.createFlatInstance();
        diamondOre(selector, environment, instance);
        cowOnFire(selector, environment, instance);
        simpleDungeon(selector, environment, instance, manager);
        tallGrass(selector, environment, instance);
        turtleStruckByLightning(selector, environment, instance);
    }

    /** 对照原版 JSON 抽查四个表。 */
    private static void spotCheck(LootManager manager) {
        var grassBlock = manager.get(Key.key("blocks/grass_block")).orElseThrow();
        assertEquals(Key.key("minecraft:block"), grassBlock.type());
        assertEquals(1, grassBlock.pools().size());
        assertInstanceOf(AlternativesEntry.class, grassBlock.pools().getFirst().entries().getFirst());

        var zombie = manager.get(Key.key("entities/zombie")).orElseThrow();
        assertEquals(Key.key("minecraft:entity"), zombie.type());
        assertEquals(4, zombie.pools().size());

        var piglinBartering = manager.get(Key.key("gameplay/piglin_bartering")).orElseThrow();
        assertEquals(Key.key("minecraft:barter"), piglinBartering.type());
        assertEquals(19, piglinBartering.pools().getFirst().entries().size());

        var simpleDungeon = manager.get(Key.key("chests/simple_dungeon")).orElseThrow();
        assertEquals(Key.key("minecraft:chest"), simpleDungeon.type());
        assertEquals(3, simpleDungeon.pools().size());
    }

    /**
     * {@code blocks/diamond_ore}：精准采集得到矿石本身，否则得到钻石，时运按 {@code ore_drops} 增加数量。
     */
    private static void diamondOre(LootSelector selector, InMemoryLootEnvironment environment, Instance instance) {
        var silkTouch = ItemStack.of(Material.DIAMOND_PICKAXE).with(DataComponents.ENCHANTMENTS, new EnchantmentList(Enchantment.SILK_TOUCH, 1));
        var context = LootContexts.block(Block.DIAMOND_ORE, new Vec(0.5, 40.5, 0.5), silkTouch, null, null, null, instance, environment);
        assertEquals(List.of(ItemStack.of(Material.DIAMOND_ORE)), selector.roll(Key.key("blocks/diamond_ore"), context, new Random(0L)));

        var plain = LootContexts.block(Block.DIAMOND_ORE, new Vec(0.5, 40.5, 0.5), ItemStack.of(Material.DIAMOND_PICKAXE), null, null,
                null, instance, environment);
        assertEquals(List.of(ItemStack.of(Material.DIAMOND)), selector.roll(Key.key("blocks/diamond_ore"), plain, new Random(0L)));

        var fortune = ItemStack.of(Material.DIAMOND_PICKAXE).with(DataComponents.ENCHANTMENTS, new EnchantmentList(Enchantment.FORTUNE, 3));
        var fortuneContext = LootContexts.block(Block.DIAMOND_ORE, new Vec(0.5, 40.5, 0.5), fortune, null, null, null, instance, environment);
        var drops = selector.roll(Key.key("blocks/diamond_ore"), fortuneContext, new Random(0L));
        assertEquals(1, drops.size());
        assertEquals(Material.DIAMOND, drops.getFirst().material());
        // 原版 ore_drops：时运 3 时数量是 1 到 4
        assertTrue(drops.getFirst().amount() >= 1 && drops.getFirst().amount() <= 4);
    }

    /**
     * {@code entities/cow}：着火的牛掉落熟牛肉（熔炼配方由求值环境提供）。
     */
    private static void cowOnFire(LootSelector selector, InMemoryLootEnvironment environment, Instance instance) {
        environment.putSmelting(Material.BEEF, ItemStack.of(Material.COOKED_BEEF));
        var cow = new LivingEntity(EntityType.COW);
        cow.setInstance(instance, new Pos(0.5, 40, 0.5)).join();
        var killer = new LivingEntity(EntityType.ZOMBIE);
        killer.setInstance(instance, new Pos(2.5, 40, 0.5)).join();
        var damage = new Damage(DamageType.MOB_ATTACK, killer, killer, null, 1.0f);
        var context = LootContexts.entity(cow, cow.getPosition(), damage, killer, killer, null, instance, environment);

        var cold = materials(selector.roll(Key.key("entities/cow"), context, new Random(0L)));
        assertTrue(cold.contains(Material.BEEF), cold.toString());
        assertFalse(cold.contains(Material.COOKED_BEEF), cold.toString());

        cow.getEntityMeta().setOnFire(true);
        var burning = materials(selector.roll(Key.key("entities/cow"), context, new Random(0L)));
        assertTrue(burning.contains(Material.COOKED_BEEF), burning.toString());
        assertFalse(burning.contains(Material.BEEF), burning.toString());
    }

    /**
     * {@code chests/simple_dungeon}：三个池的物品都来自表中的条目，同一个随机序列得到同一组物品。
     */
    private static void simpleDungeon(LootSelector selector, InMemoryLootEnvironment environment, Instance instance, LootManager manager) {
        var context = LootContexts.chest(new Vec(0.5, 40, 0.5), null, instance, environment);
        var first = selector.roll(Key.key("chests/simple_dungeon"), context, new Random(1L));
        var second = selector.roll(Key.key("chests/simple_dungeon"), context, new Random(2L));
        assertFalse(first.isEmpty());
        // 表声明了 random_sequence，结果只取决于世界种子与序列 ID
        assertEquals(first, second);
        Set<Material> allowed = new java.util.HashSet<>();
        manager.get(Key.key("chests/simple_dungeon")).orElseThrow().pools().forEach(pool -> pool.entries().forEach(entry -> {
            if (entry instanceof io.ib67.yuanban.loot.entry.ItemEntry item) {
                allowed.add(item.name());
            }
        }));
        allowed.add(Material.ENCHANTED_BOOK);
        for (var stack : first) {
            assertTrue(allowed.contains(stack.material()), stack.toString());
        }
    }

    /**
     * {@code blocks/tall_grass}：下半格只有上方一格是上半格的高草时才产出（{@code location_check} 读取世界中的方块）。
     */
    private static void tallGrass(LootSelector selector, InMemoryLootEnvironment environment, Instance instance) {
        instance.loadChunk(0, 0).join();
        var lower = Block.TALL_GRASS.withProperty("half", "lower");
        instance.setBlock(3, 41, 3, lower);
        instance.setBlock(3, 42, 3, Block.TALL_GRASS.withProperty("half", "upper"));
        var shears = ItemStack.of(Material.SHEARS);
        var context = LootContexts.block(lower, new Vec(3.5, 41.5, 3.5), shears, null, null, 1.0f, instance, environment);
        assertEquals(List.of(ItemStack.of(Material.SHORT_GRASS, 2)), selector.roll(Key.key("blocks/tall_grass"), context, new Random(0L)));

        instance.setBlock(3, 42, 3, Block.AIR);
        assertTrue(selector.roll(Key.key("blocks/tall_grass"), context, new Random(0L)).isEmpty());
    }

    /**
     * {@code entities/turtle}：被闪电击中时多掉落一个碗（{@code damage_source_properties} 检查伤害类型标签）。
     */
    private static void turtleStruckByLightning(LootSelector selector, InMemoryLootEnvironment environment, Instance instance) {
        var turtle = new LivingEntity(EntityType.TURTLE);
        turtle.setInstance(instance, new Pos(5.5, 40, 5.5)).join();
        // 表中的 enchanted_count_increase 引用攻击者，覆盖检查要求上下文提供它
        var bystander = new LivingEntity(EntityType.ZOMBIE);
        var lightning = new Damage(DamageType.LIGHTNING_BOLT, null, null, null, 5.0f);
        var context = LootContexts.entity(turtle, turtle.getPosition(), lightning, bystander, null, null, instance, environment);
        assertTrue(materials(selector.roll(Key.key("entities/turtle"), context, new Random(0L))).contains(Material.BOWL));
        var generic = new Damage(DamageType.GENERIC, null, null, null, 5.0f);
        var plain = LootContexts.entity(turtle, turtle.getPosition(), generic, bystander, null, null, instance, environment);
        assertFalse(materials(selector.roll(Key.key("entities/turtle"), plain, new Random(0L))).contains(Material.BOWL));
    }

    private static List<Material> materials(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::material).toList();
    }

    private static int countLootTables(Path server) throws IOException {
        try (var zip = new ZipFile(server.toFile())) {
            var entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                var name = entries.nextElement().getName();
                if (name.startsWith("data/minecraft/loot_table/") && name.endsWith(".json")) {
                    count++;
                }
            }
            return count;
        }
    }
}
