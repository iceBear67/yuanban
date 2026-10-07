package io.ib67.yuanban.crafting;

import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.CraftingRecipe;
import io.ib67.yuanban.crafting.recipe.RecipeHolder;
import io.ib67.yuanban.crafting.recipe.RecipeType;
import io.ib67.yuanban.crafting.recipe.SingleRecipeInput;
import io.ib67.yuanban.crafting.recipe.SmithingRecipeInput;
import io.ib67.yuanban.crafting.testing.InMemoryMaps;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.color.Color;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.book.FilteredText;
import net.minestom.server.item.component.ArmorTrim;
import net.minestom.server.item.component.BannerPatterns;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.FireworkList;
import net.minestom.server.item.component.MapPostProcessing;
import net.minestom.server.item.component.PotDecorations;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.item.component.WrittenBookContent;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.potion.PotionType;
import net.minestom.server.registry.RegistryKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配方的匹配、产出和剩余物。配方来自原版 26.2 服务端 jar 中的数据，预期值按原版各配方类的代码推算。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class RecipeMatchingTest {
    private static final ItemStack AIR = ItemStack.AIR;

    @TempDir
    Path tempDir;

    /**
     * 原版 {@code ShapedRecipePattern.matches}：图案可以在合成格中平移，也可以左右翻转。
     */
    @Test
    void shapedMatchesShiftedAndMirrored(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var planks = ItemStack.of(Material.OAK_PLANKS);
        var stick = ItemStack.of(Material.STICK);
        // 原版图案 ["XX", "X#", " #"]
        assertCrafts(crafting, ItemStack.of(Material.WOODEN_AXE), grid("""
                XX.
                XS.
                .S.""", Map.of('X', planks, 'S', stick)));
        assertCrafts(crafting, ItemStack.of(Material.WOODEN_AXE), grid("""
                .XX
                .SX
                .S.""", Map.of('X', planks, 'S', stick)));
        // 上下翻转不匹配
        assertEquals(Optional.empty(), find(crafting, grid("""
                .S.
                XS.
                XX.""", Map.of('X', planks, 'S', stick))));
    }

    /**
     * 原版 {@code ShapelessRecipe.matches}：原料数相同，每个原料分配到一个格子。
     */
    @Test
    void shapelessAssignsIngredients(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var key = Map.of('G', ItemStack.of(Material.GUNPOWDER), 'B', ItemStack.of(Material.BLAZE_POWDER), 'C', ItemStack.of(Material.CHARCOAL));
        assertCrafts(crafting, ItemStack.of(Material.FIRE_CHARGE, 3), grid("""
                ..C
                .B.
                G..""", key));
        assertEquals(Optional.empty(), find(crafting, grid("""
                G.C
                .B.
                G..""", key)));
    }

    /**
     * 原版 {@code TransmuteRecipe}：结果保留输入物品的组件；结果与输入完全相同时不匹配；材料数量加到结果数量上。
     */
    @Test
    void transmuteKeepsComponents(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var named = ItemStack.of(Material.PURPLE_SHULKER_BOX).with(DataComponents.CUSTOM_NAME, Component.text("Box"));
        assertCrafts(crafting, ItemStack.of(Material.BLACK_SHULKER_BOX).with(DataComponents.CUSTOM_NAME, Component.text("Box")), grid("""
                SD.
                ...
                ...""", Map.of('S', named, 'D', ItemStack.of(Material.BLACK_DYE))));
        // 原版 TransmuteRecipe.matches：结果数量为 1 并且与输入物品完全相同时不匹配
        assertEquals(Optional.empty(), find(crafting, grid("""
                SD.
                ...
                ...""", Map.of('S', ItemStack.of(Material.BLACK_SHULKER_BOX), 'D', ItemStack.of(Material.BLACK_DYE)))));

        var map = ItemStack.of(Material.FILLED_MAP).with(DataComponents.MAP_ID, 7);
        assertCrafts(crafting, ItemStack.of(Material.FILLED_MAP, 3).with(DataComponents.MAP_ID, 7), grid("""
                FM.
                M..
                ...""", Map.of('F', map, 'M', ItemStack.of(Material.MAP))));
    }

    /**
     * 原版 {@code ItemStackTemplate.apply}：结果模板中与物品原型相同的组件也会覆盖输入物品上的值。
     */
    @Test
    void resultTemplateOverridesInputComponents(Env env) {
        var loaded = new TestWorld(tempDir)
                .vanilla("recipe/reset_stack_size.json", """
                        {"type": "crafting_transmute", "input": "minecraft:stone", "material": "minecraft:dirt",
                         "result": {"id": "minecraft:stone", "components": {"minecraft:max_stack_size": 64}}}
                        """)
                .vanilla("recipe/too_many_swords.json", """
                        {"type": "crafting_shapeless", "ingredients": ["minecraft:diamond"], "result": {"id": "minecraft:diamond_sword", "count": 2}}
                        """)
                .load(env.process());
        var crafting = loaded.crafting();
        var smallStacks = ItemStack.of(Material.STONE).with(DataComponents.MAX_STACK_SIZE, 16);
        assertCrafts(crafting, ItemStack.of(Material.STONE), grid("""
                SD.
                ...
                ...""", Map.of('S', smallStacks, 'D', ItemStack.of(Material.DIRT))));
        // 原版 ItemStack.validateStrict：数量超过最大堆叠数的结果不合法，产出为空
        var swords = find(crafting, CraftingInput.of(1, 1, List.of(ItemStack.of(Material.DIAMOND)))).orElseThrow();
        assertEquals(AIR, swords.value().assemble(CraftingInput.of(1, 1, List.of(ItemStack.of(Material.DIAMOND))), crafting.recipes().context()));
        loaded.session().close();
    }

    /**
     * 原版 {@code DyedItemColor.applyDyes}。红色染料的颜色为 0xB02E26，蓝色染料为 0x3C44AA。
     */
    @Test
    void dyeMixesColors(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        assertCrafts(crafting, ItemStack.of(Material.LEATHER_HELMET).with(DataComponents.DYED_COLOR, new Color(176, 46, 38)), grid("""
                HR.
                ...
                ...""", Map.of('H', ItemStack.of(Material.LEATHER_HELMET), 'R', ItemStack.of(Material.RED_DYE))));
        // 已有颜色 (0, 255, 0) 与蓝色：分量平均为 (30, 161, 85)，最大分量平均为 212.5，按 212.5 / 161 放大
        var green = ItemStack.of(Material.LEATHER_HELMET).with(DataComponents.DYED_COLOR, new Color(0, 255, 0));
        assertCrafts(crafting, ItemStack.of(Material.LEATHER_HELMET).with(DataComponents.DYED_COLOR, new Color(39, 212, 112)), grid("""
                H..
                .B.
                ...""", Map.of('H', green, 'B', ItemStack.of(Material.BLUE_DYE))));
    }

    @Test
    void imbueCopiesPotion(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var potion = new PotionContents(PotionType.SWIFTNESS);
        assertCrafts(crafting, ItemStack.of(Material.TIPPED_ARROW, 8).with(DataComponents.POTION_CONTENTS, potion), grid("""
                AAA
                APA
                AAA""", Map.of('A', ItemStack.of(Material.ARROW), 'P', ItemStack.of(Material.LINGERING_POTION).with(DataComponents.POTION_CONTENTS, potion))));
    }

    @Test
    void decoratedPotTakesFourSides(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var key = Map.of('A', ItemStack.of(Material.ANGLER_POTTERY_SHERD), 'B', ItemStack.of(Material.BRICK), 'R', ItemStack.of(Material.ARCHER_POTTERY_SHERD));
        assertCrafts(crafting, ItemStack.of(Material.DECORATED_POT).with(DataComponents.POT_DECORATIONS,
                new PotDecorations(Material.ANGLER_POTTERY_SHERD, Material.BRICK, Material.BRICK, Material.ARCHER_POTTERY_SHERD)), grid("""
                .A.
                B.B
                .R.""", key));
        assertEquals(Optional.empty(), find(crafting, grid("""
                .A.
                B.B
                ...""", key)));
    }

    /**
     * 原版 {@code BookCloningRecipe}：代数加一，结果数量为书与笔的数量，原书留在合成格中；代数超出范围时不匹配。
     */
    @Test
    void bookCloning(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var original = book(0);
        var input = grid("""
                O.W
                W..
                ...""", Map.of('O', original, 'W', ItemStack.of(Material.WRITABLE_BOOK)));
        assertCrafts(crafting, ItemStack.of(Material.WRITTEN_BOOK, 2).with(DataComponents.WRITTEN_BOOK_CONTENT, book(1).get(DataComponents.WRITTEN_BOOK_CONTENT)), input);
        var recipe = find(crafting, input).orElseThrow().value();
        // 去掉空行空列之后的输入为 3×2：成书、空格、书与笔、书与笔、空格、空格
        assertEquals(List.of(original, AIR, AIR, AIR, AIR, AIR), recipe.remainingItems(input));
        assertEquals(Optional.empty(), find(crafting, grid("""
                OW.
                ...
                ...""", Map.of('O', book(2), 'W', ItemStack.of(Material.WRITABLE_BOOK)))));
    }

    /**
     * 原版 {@code MapExtendingRecipe}：缩放等级小于 4、不是探险家地图的地图可以放大，结果带上 {@code map_post_processing}。
     */
    @Test
    void mapExtendingReadsMapData(Env env) {
        var maps = new InMemoryMaps();
        var crafting = TestWorld.loadVanilla(env.process(), maps);
        int small = maps.create(0, 0, 0, false);
        int largest = maps.create(0, 0, 4, false);
        int explorer = maps.create(0, 0, 1, true);
        var paper = ItemStack.of(Material.PAPER);
        assertCrafts(crafting, map(small).with(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE), mapGrid(map(small), paper));
        assertEquals(Optional.empty(), find(crafting, mapGrid(map(largest), paper)));
        assertEquals(Optional.empty(), find(crafting, mapGrid(map(explorer), paper)));
        assertEquals(Optional.empty(), find(crafting, mapGrid(map(99), paper)));
    }

    @Test
    void fireworks(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var explosion = new FireworkExplosion(FireworkExplosion.Shape.STAR, List.of(DyeColor.RED.fireworkColor()), List.of(), false, true);
        var star = ItemStack.of(Material.FIREWORK_STAR).with(DataComponents.FIREWORK_EXPLOSION, explosion);
        assertCrafts(crafting, ItemStack.of(Material.FIREWORK_ROCKET, 3).with(DataComponents.FIREWORKS, new FireworkList(2, List.of(explosion))), grid("""
                PG.
                GS.
                ...""", Map.of('P', ItemStack.of(Material.PAPER), 'G', ItemStack.of(Material.GUNPOWDER), 'S', star)));
        // 火药最多三个
        assertEquals(Optional.empty(), find(crafting, grid("""
                PGG
                GG.
                ...""", Map.of('P', ItemStack.of(Material.PAPER), 'G', ItemStack.of(Material.GUNPOWDER)))));

        assertCrafts(crafting, ItemStack.of(Material.FIREWORK_STAR).with(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL, List.of(DyeColor.RED.fireworkColor(), DyeColor.BLUE.fireworkColor()), List.of(), true, false)), grid("""
                GRB
                FD.
                ...""", Map.of('G', ItemStack.of(Material.GUNPOWDER), 'R', ItemStack.of(Material.RED_DYE), 'B', ItemStack.of(Material.BLUE_DYE),
                'F', ItemStack.of(Material.FIRE_CHARGE), 'D', ItemStack.of(Material.DIAMOND))));

        assertCrafts(crafting, ItemStack.of(Material.FIREWORK_STAR).with(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(
                FireworkExplosion.Shape.STAR, explosion.colors(), List.of(DyeColor.WHITE.fireworkColor()), false, true)), grid("""
                SW.
                ...
                ...""", Map.of('S', star, 'W', ItemStack.of(Material.WHITE_DYE))));
    }

    /**
     * 原版 {@code BannerDuplicateRecipe} 与 {@code ShieldDecorationRecipe}。
     */
    @Test
    void banners(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var patterns = new BannerPatterns(List.of(new BannerPatterns.Layer(BannerPattern.STRIPE_BOTTOM, DyeColor.RED)));
        var patterned = ItemStack.of(Material.WHITE_BANNER).with(DataComponents.BANNER_PATTERNS, patterns);
        var input = grid("""
                PB.
                ...
                ...""", Map.of('P', patterned, 'B', ItemStack.of(Material.WHITE_BANNER)));
        assertCrafts(crafting, patterned, input);
        assertEquals(List.of(patterned, AIR), find(crafting, input).orElseThrow().value().remainingItems(input).subList(0, 2));
        // 底色不同的旗帜不能复制
        assertEquals(Optional.empty(), find(crafting, grid("""
                PB.
                ...
                ...""", Map.of('P', patterned, 'B', ItemStack.of(Material.RED_BANNER)))));

        var redPatterned = ItemStack.of(Material.RED_BANNER).with(DataComponents.BANNER_PATTERNS, patterns);
        assertCrafts(crafting, ItemStack.of(Material.SHIELD).with(DataComponents.BANNER_PATTERNS, patterns).with(DataComponents.BASE_COLOR, DyeColor.RED), grid("""
                BS.
                ...
                ...""", Map.of('B', redPatterned, 'S', ItemStack.of(Material.SHIELD))));
    }

    /**
     * 原版 {@code RepairItemRecipe}：耐久为两者剩余耐久之和加最大耐久的 5%，只保留诅咒魔咒。
     * 钻石镐最大耐久 1561：剩余 561 + 361 + 78 = 1000，损坏值为 561。
     */
    @Test
    void repairKeepsOnlyCurses(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var cursed = ItemStack.of(Material.DIAMOND_PICKAXE).with(DataComponents.DAMAGE, 1000)
                .with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.VANISHING_CURSE, 1, Enchantment.EFFICIENCY, 3)));
        var worn = ItemStack.of(Material.DIAMOND_PICKAXE).with(DataComponents.DAMAGE, 1200);
        assertCrafts(crafting, ItemStack.of(Material.DIAMOND_PICKAXE).with(DataComponents.DAMAGE, 561)
                .with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.VANISHING_CURSE, 1))), grid("""
                C..
                W..
                ...""", Map.of('C', cursed, 'W', worn)));
    }

    /**
     * 原版 {@code CraftingRecipe.defaultCraftingReminder}：牛奶桶留下桶。
     */
    @Test
    void remainders(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var input = grid("""
                MMM
                SES
                WWW""", Map.of('M', ItemStack.of(Material.MILK_BUCKET), 'S', ItemStack.of(Material.SUGAR), 'E', ItemStack.of(Material.EGG),
                'W', ItemStack.of(Material.WHEAT)));
        var recipe = find(crafting, input).orElseThrow();
        assertEquals(Key.key("cake"), recipe.id());
        var bucket = ItemStack.of(Material.BUCKET);
        assertEquals(List.of(bucket, bucket, bucket, AIR, AIR, AIR, AIR, AIR, AIR), recipe.value().remainingItems(input));
    }

    @Test
    void cookingStonecuttingAndSmithing(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var recipes = crafting.recipes();
        var context = recipes.context();
        var ore = new SingleRecipeInput(ItemStack.of(Material.IRON_ORE));
        assertEquals(ItemStack.of(Material.IRON_INGOT), recipes.getRecipeFor(RecipeType.SMELTING, ore, null).orElseThrow().value().assemble(ore, context));
        var stone = new SingleRecipeInput(ItemStack.of(Material.STONE));
        assertTrue(recipes.getRecipeFor(RecipeType.STONECUTTING, stone, null).isPresent());

        var sword = ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.CUSTOM_NAME, Component.text("Blade"));
        var upgrade = new SmithingRecipeInput(ItemStack.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE), sword, ItemStack.of(Material.NETHERITE_INGOT));
        assertEquals(ItemStack.of(Material.NETHERITE_SWORD).with(DataComponents.CUSTOM_NAME, Component.text("Blade")),
                recipes.getRecipeFor(RecipeType.SMITHING, upgrade, null).orElseThrow().value().assemble(upgrade, context));

        var ingot = ItemStack.of(Material.IRON_INGOT);
        var trim = new SmithingRecipeInput(ItemStack.of(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE), ItemStack.of(Material.IRON_CHESTPLATE), ingot);
        var trimmed = recipes.getRecipeFor(RecipeType.SMITHING, trim, null).orElseThrow().value().assemble(trim, context);
        assertEquals(new ArmorTrim(ingot.get(DataComponents.PROVIDES_TRIM_MATERIAL), RegistryKey.of("sentry")), trimmed.get(DataComponents.TRIM));
        // 已经有同样纹饰的物品没有结果
        var again = new SmithingRecipeInput(trim.template(), trimmed, ingot);
        assertEquals(AIR, recipes.getRecipeFor(RecipeType.SMITHING, again, null).orElseThrow().value().assemble(again, context));
    }

    private static ItemStack book(int generation) {
        return ItemStack.of(Material.WRITTEN_BOOK).with(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(new FilteredText<>("Title", null), "Author", generation, List.of(), true));
    }

    private static ItemStack map(int id) {
        return ItemStack.of(Material.FILLED_MAP).with(DataComponents.MAP_ID, id);
    }

    private static CraftingInput mapGrid(ItemStack map, ItemStack paper) {
        return grid("""
                PPP
                PMP
                PPP""", Map.of('P', paper, 'M', map));
    }

    /**
     * 3×3 合成格，{@code .} 为空格。
     */
    static CraftingInput grid(String layout, Map<Character, ItemStack> key) {
        var items = new ArrayList<ItemStack>(9);
        for (var row : layout.split("\n")) {
            for (char c : row.strip().toCharArray()) {
                items.add(c == '.' ? AIR : key.get(c));
            }
        }
        return CraftingInput.of(3, 3, items);
    }

    static Optional<RecipeHolder<CraftingRecipe>> find(Crafting crafting, CraftingInput input) {
        return crafting.recipes().getRecipeFor(RecipeType.CRAFTING, input, null);
    }

    static void assertCrafts(Crafting crafting, ItemStack expected, CraftingInput input) {
        var recipe = find(crafting, input);
        assertTrue(recipe.isPresent(), "no recipe matches");
        assertEquals(expected, recipe.get().value().assemble(input, crafting.recipes().context()));
    }
}
