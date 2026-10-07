package io.ib67.yuanban.recipe;

import com.google.gson.JsonParser;
import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.Recipe;
import io.ib67.yuanban.crafting.recipe.kind.cooking.CookingCategory;
import io.ib67.yuanban.crafting.recipe.kind.cooking.CookingKind;
import io.ib67.yuanban.crafting.recipe.kind.cooking.CookingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.CraftingBookInfo;
import io.ib67.yuanban.crafting.recipe.kind.crafting.CraftingCategory;
import io.ib67.yuanban.crafting.recipe.kind.crafting.DyeRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ImbueRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ShapedRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ShapelessRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.TransmuteRecipe;
import io.ib67.yuanban.crafting.recipe.kind.misc.IntBounds;
import io.ib67.yuanban.crafting.recipe.kind.misc.ItemStackTemplate;
import io.ib67.yuanban.crafting.recipe.kind.smithing.SmithingTransformRecipe;
import io.ib67.yuanban.crafting.recipe.kind.smithing.SmithingTrimRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.BannerDuplicateRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.BookCloningRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.DecoratedPotRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.FireworkRocketRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.FireworkStarFadeRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.FireworkStarRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.MapExtendingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.RepairItemRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.ShieldDecorationRecipe;
import io.ib67.yuanban.crafting.recipe.kind.stonecutter.StonecutterRecipe;
import io.ib67.yuanban.itemtag.ItemTags;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import io.ib67.yuanban.recipe.internal.RecipeCodecs;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.registry.RegistryKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解码不经过加载流程，不修改 Minestom 的标签，同一个类中的测试可以各自新建 {@code ServerProcess}。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class RecipeDecodingTest {
    private static final ItemIngredient PLANKS = new ItemIngredient.OfTag(Key.key("planks"), List.of(Material.OAK_PLANKS, Material.BIRCH_PLANKS));
    private static final ItemTags TAGS = new ItemTags(new TreeMap<>(Map.of(
            Key.key("planks"), PLANKS.items(),
            Key.key("empty"), List.of())));

    @Test
    void shaped(Env env) {
        var recipe = assertInstanceOf(ShapedRecipe.class, decode(env, """
                {"type": "minecraft:crafting_shaped", "group": "sticks", "key": {"#": "#minecraft:planks", "s": ["stone", "minecraft:dirt"]},
                 "pattern": ["   ", " # ", " s "], "result": {"count": 4, "id": "minecraft:stick"}}
                """));
        assertEquals(new CraftingBookInfo(CraftingCategory.MISC, "sticks"), recipe.book());
        assertEquals(1, recipe.width());
        assertEquals(2, recipe.height());
        assertEquals(List.of(Optional.of(PLANKS), Optional.of(new ItemIngredient.OfItems(List.of(Material.STONE, Material.DIRT)))), recipe.ingredients());
        assertEquals(new ItemStackTemplate(Material.STICK, 4, DataComponentMap.EMPTY), recipe.result());
    }

    @Test
    void shapedKeepsInnerSpaces(Env env) {
        var recipe = assertInstanceOf(ShapedRecipe.class, decode(env, """
                {"type": "crafting_shaped", "category": "building", "show_notification": false,
                 "key": {"#": "minecraft:stone"}, "pattern": ["# #", "###"], "result": "minecraft:stone_bricks"}
                """));
        var stone = Optional.<ItemIngredient>of(new ItemIngredient.OfItems(List.of(Material.STONE)));
        assertEquals(CraftingCategory.BUILDING, recipe.book().category());
        assertEquals(List.of(stone, Optional.empty(), stone, stone, stone, stone), recipe.ingredients());
        assertEquals(ItemStackTemplate.of(Material.STONE_BRICKS), recipe.result());
    }

    @Test
    void shapelessWithComponents(Env env) {
        var recipe = assertInstanceOf(ShapelessRecipe.class, decode(env, """
                {"type": "crafting_shapeless", "ingredients": ["minecraft:stone", "#planks"],
                 "result": {"id": "minecraft:stone", "components": {"minecraft:max_stack_size": 16}}}
                """));
        assertEquals(List.of(new ItemIngredient.OfItems(List.of(Material.STONE)), PLANKS), recipe.ingredients());
        assertEquals(16, recipe.result().components().get(DataComponents.MAX_STACK_SIZE));
    }

    /**
     * 原版 {@code ItemStackTemplate} 原样保存组件补丁：与物品原型相同的条目也保留，产出时用来覆盖输入物品的组件。
     */
    @Test
    void resultKeepsComponentsEqualToPrototype(Env env) {
        var recipe = assertInstanceOf(TransmuteRecipe.class, decode(env, """
                {"type": "crafting_transmute", "input": "minecraft:stone", "material": "minecraft:dirt",
                 "result": {"id": "minecraft:stone", "components": {"minecraft:max_stack_size": 64, "!minecraft:food": {}}}}
                """));
        assertEquals(64, recipe.result().components().get(DataComponents.MAX_STACK_SIZE));
        assertEquals(2, recipe.result().components().entrySet().size());
    }

    @Test
    void transmute(Env env) {
        var defaults = assertInstanceOf(TransmuteRecipe.class, decode(env, """
                {"type": "crafting_transmute", "input": "#planks", "material": "minecraft:stone", "result": "minecraft:dirt"}
                """));
        assertEquals(IntBounds.exactly(1), defaults.materialCount());
        assertEquals(false, defaults.addMaterialCountToResult());

        var range = assertInstanceOf(TransmuteRecipe.class, decode(env, """
                {"type": "crafting_transmute", "input": "#planks", "material": "minecraft:stone", "result": "minecraft:dirt",
                 "material_count": {"min": 2, "max": 8}, "add_material_count_to_result": true}
                """));
        assertEquals(IntBounds.between(2, 8), range.materialCount());
        assertEquals(true, range.addMaterialCountToResult());

        var exact = assertInstanceOf(TransmuteRecipe.class, decode(env, """
                {"type": "crafting_transmute", "input": "#planks", "material": "minecraft:stone", "result": "minecraft:dirt", "material_count": 3}
                """));
        assertEquals(IntBounds.exactly(3), exact.materialCount());
    }

    @Test
    void dyeAndImbue(Env env) {
        var dye = assertInstanceOf(DyeRecipe.class, decode(env, """
                {"type": "crafting_dye", "group": "g", "target": "minecraft:leather_helmet", "dye": "minecraft:red_dye", "result": "minecraft:leather_helmet"}
                """));
        assertEquals("g", dye.book().group());
        var imbue = assertInstanceOf(ImbueRecipe.class, decode(env, """
                {"type": "crafting_imbue", "source": "minecraft:lingering_potion", "material": "minecraft:arrow", "result": {"id": "minecraft:tipped_arrow", "count": 8}}
                """));
        assertEquals(8, imbue.result().count());
    }

    @Test
    void specialRecipes(Env env) {
        var pot = assertInstanceOf(DecoratedPotRecipe.class, decode(env, """
                {"type": "crafting_decorated_pot", "back": "minecraft:brick", "left": "minecraft:brick", "right": "minecraft:brick",
                 "front": "minecraft:brick", "result": "minecraft:decorated_pot"}
                """));
        assertEquals(List.of(Material.BRICK), pot.front().items());

        var books = assertInstanceOf(BookCloningRecipe.class, decode(env, """
                {"type": "crafting_special_bookcloning", "source": "minecraft:written_book", "material": "minecraft:writable_book", "result": "minecraft:written_book"}
                """));
        assertEquals(IntBounds.between(0, 1), books.allowedGenerations());

        var star = assertInstanceOf(FireworkStarRecipe.class, decode(env, """
                {"type": "crafting_special_firework_star",
                 "shapes": {"large_ball": "minecraft:fire_charge", "creeper": "minecraft:creeper_head"},
                 "trail": "minecraft:diamond", "twinkle": "minecraft:glowstone_dust", "fuel": "minecraft:gunpowder", "dye": "minecraft:red_dye",
                 "result": "minecraft:firework_star"}
                """));
        assertEquals(Map.of(
                FireworkExplosion.Shape.LARGE_BALL, new ItemIngredient.OfItems(List.of(Material.FIRE_CHARGE)),
                FireworkExplosion.Shape.CREEPER, new ItemIngredient.OfItems(List.of(Material.CREEPER_HEAD))), star.shapes());
        // 查找爆炸类型时按配方文件中的顺序
        assertEquals(List.of(FireworkExplosion.Shape.LARGE_BALL, FireworkExplosion.Shape.CREEPER), List.copyOf(star.shapes().keySet()));

        assertEquals(new RepairItemRecipe(), decode(env, """
                {"type": "crafting_special_repairitem"}
                """));
        assertInstanceOf(MapExtendingRecipe.class, decode(env, """
                {"type": "crafting_special_mapextending", "map": "minecraft:filled_map", "material": "minecraft:paper", "result": "minecraft:filled_map"}
                """));
        assertInstanceOf(FireworkRocketRecipe.class, decode(env, """
                {"type": "crafting_special_firework_rocket", "shell": "minecraft:paper", "fuel": "minecraft:gunpowder",
                 "star": "minecraft:firework_star", "result": "minecraft:firework_rocket"}
                """));
        assertInstanceOf(FireworkStarFadeRecipe.class, decode(env, """
                {"type": "crafting_special_firework_star_fade", "target": "minecraft:firework_star", "dye": "minecraft:red_dye", "result": "minecraft:firework_star"}
                """));
        assertInstanceOf(BannerDuplicateRecipe.class, decode(env, """
                {"type": "crafting_special_bannerduplicate", "banner": "minecraft:white_banner", "result": "minecraft:white_banner"}
                """));
        assertInstanceOf(ShieldDecorationRecipe.class, decode(env, """
                {"type": "crafting_special_shielddecoration", "banner": "minecraft:white_banner", "target": "minecraft:shield", "result": "minecraft:shield"}
                """));
    }

    @Test
    void cooking(Env env) {
        var smelting = assertInstanceOf(CookingRecipe.class, decode(env, """
                {"type": "minecraft:smelting", "experience": 0.7, "group": "iron_ingot", "ingredient": "minecraft:iron_ore", "result": {"id": "minecraft:iron_ingot"}}
                """));
        assertEquals(CookingKind.SMELTING, smelting.kind());
        assertEquals(CookingCategory.MISC, smelting.book().category());
        assertEquals(0.7F, smelting.experience());
        assertEquals(200, smelting.cookingTime());

        for (var kind : List.of(CookingKind.BLASTING, CookingKind.SMOKING, CookingKind.CAMPFIRE_COOKING)) {
            var recipe = assertInstanceOf(CookingRecipe.class, decode(env, """
                    {"type": "%s", "category": "food", "ingredient": "minecraft:beef", "result": "minecraft:cooked_beef"}
                    """.formatted(kind.type())));
            assertEquals(kind, recipe.kind());
            assertEquals(CookingCategory.FOOD, recipe.book().category());
            assertEquals(100, recipe.cookingTime());
            assertEquals(0.0F, recipe.experience());
        }
        var explicit = assertInstanceOf(CookingRecipe.class, decode(env, """
                {"type": "campfire_cooking", "cookingtime": 600, "ingredient": "minecraft:beef", "result": "minecraft:cooked_beef"}
                """));
        assertEquals(600, explicit.cookingTime());
    }

    @Test
    void stonecutterAndSmithing(Env env) {
        var stonecutter = assertInstanceOf(StonecutterRecipe.class, decode(env, """
                {"type": "stonecutting", "ingredient": "minecraft:stone", "result": {"id": "minecraft:stone_slab", "count": 2}}
                """));
        assertEquals(2, stonecutter.result().count());

        var transform = assertInstanceOf(SmithingTransformRecipe.class, decode(env, """
                {"type": "smithing_transform", "base": "minecraft:diamond_sword", "result": "minecraft:netherite_sword"}
                """));
        assertEquals(Optional.empty(), transform.template());
        assertEquals(Optional.empty(), transform.addition());

        var trim = assertInstanceOf(SmithingTrimRecipe.class, decode(env, """
                {"type": "minecraft:smithing_trim", "addition": "minecraft:iron_ingot", "base": "#planks",
                 "pattern": "minecraft:sentry", "template": "minecraft:sentry_armor_trim_smithing_template"}
                """));
        assertEquals(Key.key("sentry"), assertInstanceOf(RegistryKey.class, trim.pattern()).key());
    }

    @Test
    void errors(Env env) {
        assertError(env, "{\"type\": \"minecraft:brewing\"}",
                "Unknown registry key in ResourceKey[minecraft:root / minecraft:recipe_serializer]: minecraft:brewing");
        assertError(env, "{\"type\": \"stonecutting\", \"ingredient\": \"#minecraft:absent\", \"result\": \"minecraft:stone\"}",
                "Missing tag: 'minecraft:absent' in 'minecraft:item'");
        assertError(env, "{\"type\": \"stonecutting\", \"ingredient\": \"minecraft:not_an_item\", \"result\": \"minecraft:stone\"}",
                "Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: minecraft:not_an_item");
        assertError(env, "{\"type\": \"stonecutting\", \"ingredient\": \"minecraft:air\", \"result\": \"minecraft:stone\"}",
                "Item must not be minecraft:air");
        assertError(env, "{\"type\": \"stonecutting\", \"ingredient\": [], \"result\": \"minecraft:stone\"}",
                "List must have contents");
        assertError(env, "{\"type\": \"stonecutting\", \"ingredient\": \"minecraft:stone\", \"result\": {\"id\": \"minecraft:stone\", \"count\": 100}}",
                "Value must be within range [1;99]: 100");
        // show_notification 不保存，但仍然按原版检查类型
        assertError(env, "{\"type\": \"stonecutting\", \"show_notification\": \"yes\", \"ingredient\": \"minecraft:stone\", \"result\": \"minecraft:stone\"}",
                "Not a boolean");
        assertError(env, shaped("[\"#\",\"#\",\"#\",\"#\"]", "{\"#\": \"minecraft:stone\"}"), "Invalid pattern: too many rows, 3 is maximum");
        assertError(env, shaped("[]", "{\"#\": \"minecraft:stone\"}"), "Invalid pattern: empty pattern not allowed");
        assertError(env, shaped("[\"####\"]", "{\"#\": \"minecraft:stone\"}"), "Invalid pattern: too many columns, 3 is maximum");
        assertError(env, shaped("[\"##\",\"#\"]", "{\"#\": \"minecraft:stone\"}"), "Invalid pattern: each row must be the same width");
        assertError(env, shaped("[\"#\"]", "{\"##\": \"minecraft:stone\"}"), "is an invalid symbol (must be 1 character only).");
        assertError(env, shaped("[\"#\"]", "{\" \": \"minecraft:stone\"}"), "Invalid key entry: ' ' is a reserved symbol.");
        assertError(env, shaped("[\"#x\"]", "{\"#\": \"minecraft:stone\"}"), "Pattern references symbol 'x' but it's not defined in the key");
        assertError(env, shaped("[\"#\"]", "{\"#\": \"minecraft:stone\", \"y\": \"minecraft:dirt\"}"), "Key defines symbols that aren't used in pattern: [y]");
        assertError(env, shaped("[\"  \",\"  \"]", "{}"), "Invalid pattern: pattern contains only spaces");
        assertError(env, """
                {"type": "crafting_shapeless", "result": "minecraft:stone", "ingredients": ["stone","stone","stone","stone","stone","stone","stone","stone","stone","stone"]}
                """, "List is too long: 10, expected range [1-9]");
        assertError(env, """
                {"type": "crafting_transmute", "input": "stone", "material": "stone", "result": "stone", "material_count": 9}
                """, "Range must be within [1..8], but was 9..9");
        assertError(env, """
                {"type": "crafting_transmute", "input": "stone", "material": "stone", "result": "stone", "material_count": {"min": 2}}
                """, "Range must be within [1..8], but was 2..+∞");
        assertError(env, """
                {"type": "crafting_transmute", "input": "stone", "material": "stone", "result": "stone", "material_count": {"min": 3, "max": 2}}
                """, "Swapped bounds in range");
        assertError(env, """
                {"type": "crafting_special_bookcloning", "source": "stone", "material": "stone", "result": "stone", "allowed_generations": {"min": 0, "max": 3}}
                """, "Range must be within [0..2], but was 0..3");
        assertError(env, """
                {"type": "crafting_special_firework_star", "shapes": {"square": "stone"}, "trail": "stone", "twinkle": "stone",
                 "fuel": "stone", "dye": "stone", "result": "stone"}
                """, "Unknown element name:square");
        assertError(env, """
                {"type": "smithing_trim", "addition": "stone", "base": "stone", "pattern": "minecraft:unknown_pattern", "template": "stone"}
                """, "Unknown key minecraft:unknown_pattern");
    }

    private static String shaped(String pattern, String key) {
        return "{\"type\": \"crafting_shaped\", \"pattern\": " + pattern + ", \"key\": " + key + ", \"result\": \"minecraft:stone\"}";
    }

    private static Recipe<?> decode(Env env, String json) {
        return new RecipeCodecs(TAGS, env.process()).decode(JsonParser.parseString(json)).getOrThrow();
    }

    private static void assertError(Env env, String json, String expected) {
        var result = new RecipeCodecs(TAGS, env.process()).decode(JsonParser.parseString(json));
        var error = result.error().orElseThrow(() -> new AssertionError("decoded successfully: " + json)).message();
        assertTrue(error.contains(expected), () -> "expected '" + expected + "' in: " + error);
    }
}
