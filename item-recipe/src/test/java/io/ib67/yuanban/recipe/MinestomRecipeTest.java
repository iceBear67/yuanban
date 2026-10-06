package io.ib67.yuanban.recipe;

import com.google.gson.JsonParser;
import io.ib67.yuanban.itemtag.ItemTags;
import io.ib67.yuanban.recipe.internal.MinestomRecipe;
import io.ib67.yuanban.recipe.internal.RecipeCodecs;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.play.RecipeBookAddPacket;
import net.minestom.server.recipe.Ingredient;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.RecipeProperty;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;
import net.minestom.server.registry.TagKey;
import net.minestom.testing.Env;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 不经过加载流程，不修改 Minestom 的标签。标签原料使用 Minestom 自带的 {@code minecraft:planks}。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class MinestomRecipeTest {
    private static final SlotDisplay CRAFTING_TABLE = new SlotDisplay.Item(Material.CRAFTING_TABLE);
    private static final SlotDisplay PLANKS = new SlotDisplay.Tag(TagKey.of("minecraft:planks"));
    private static final ItemTags TAGS = new ItemTags(new TreeMap<>(Map.of(
            Key.key("planks"), List.of(Material.OAK_PLANKS),
            Key.key("empty"), List.of())));

    @Test
    void shaped(Env env) {
        var recipe = recipe(env, """
                {"type": "crafting_shaped", "group": "sticks", "category": "equipment", "key": {"#": "#planks", "b": "minecraft:milk_bucket"},
                 "pattern": ["# ", " b"], "result": {"id": "minecraft:stick", "count": 4}}
                """);
        var milk = new SlotDisplay.Composite(List.of(new SlotDisplay.WithRemainder(
                new SlotDisplay.Item(Material.MILK_BUCKET), new SlotDisplay.ItemStack(ItemStack.of(Material.BUCKET)))));
        assertEquals(List.of(new RecipeDisplay.CraftingShaped(2, 2, List.of(PLANKS, SlotDisplay.Empty.INSTANCE, SlotDisplay.Empty.INSTANCE, milk),
                new SlotDisplay.ItemStack(ItemStack.of(Material.STICK, 4)), CRAFTING_TABLE)), recipe.createRecipeDisplays());
        assertEquals(RecipeBookCategory.CRAFTING_EQUIPMENT, recipe.recipeBookCategory());
        assertEquals("sticks", recipe.recipeBookGroup());
        assertEquals(Map.of(), recipe.itemProperties());
        assertEquals(List.of(new Ingredient(Material.staticRegistry().getTag(TagKey.of("minecraft:planks"))), new Ingredient(Material.MILK_BUCKET)),
                recipe.craftingRequirements());
        encode(env, recipe);
    }

    @Test
    void transmute(Env env) {
        var recipe = recipe(env, """
                {"type": "crafting_transmute", "input": "minecraft:stone", "material": "minecraft:dirt", "material_count": {"min": 1, "max": 2},
                 "add_material_count_to_result": true, "result": {"id": "minecraft:gravel", "count": 2}}
                """);
        var stone = new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.STONE)));
        var dirt = new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.DIRT)));
        assertEquals(List.of(
                new RecipeDisplay.CraftingShapeless(List.of(stone, dirt), new SlotDisplay.ItemStack(ItemStack.of(Material.GRAVEL, 3)), CRAFTING_TABLE),
                new RecipeDisplay.CraftingShapeless(List.of(stone, dirt, dirt), new SlotDisplay.ItemStack(ItemStack.of(Material.GRAVEL, 4)), CRAFTING_TABLE)
        ), recipe.createRecipeDisplays());
        assertEquals(List.of(new Ingredient(Material.STONE), new Ingredient(Material.DIRT), new Ingredient(Material.DIRT)), recipe.craftingRequirements());
        assertNull(recipe.recipeBookGroup());
        encode(env, recipe);
    }

    @Test
    void dyeAndImbue(Env env) {
        var dye = recipe(env, """
                {"type": "crafting_dye", "target": "minecraft:leather_helmet", "dye": "minecraft:red_dye", "result": "minecraft:leather_helmet"}
                """);
        var dyes = new SlotDisplay.OnlyWithComponent(new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.RED_DYE))), DataComponents.DYE);
        var helmet = new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.LEATHER_HELMET)));
        assertEquals(List.of(new RecipeDisplay.CraftingShapeless(List.of(helmet, dyes), new SlotDisplay.Dyed(dyes, helmet), CRAFTING_TABLE)),
                dye.createRecipeDisplays());
        encode(env, dye);

        var imbue = recipe(env, """
                {"type": "crafting_imbue", "source": "minecraft:lingering_potion", "material": "minecraft:arrow", "result": {"id": "minecraft:tipped_arrow", "count": 8}}
                """);
        var arrow = new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.ARROW)));
        var potion = new SlotDisplay.WithAnyPotion(new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.LINGERING_POTION))));
        assertEquals(List.of(new RecipeDisplay.CraftingShaped(3, 3, List.of(arrow, arrow, arrow, arrow, potion, arrow, arrow, arrow, arrow),
                        new SlotDisplay.WithAnyPotion(new SlotDisplay.ItemStack(ItemStack.of(Material.TIPPED_ARROW, 8))), CRAFTING_TABLE)),
                imbue.createRecipeDisplays());
        assertEquals(9, imbue.craftingRequirements().size());
        encode(env, imbue);
    }

    @Test
    void cooking(Env env) {
        var blasting = recipe(env, """
                {"type": "blasting", "category": "food", "group": "iron", "ingredient": "minecraft:iron_ore", "result": "minecraft:iron_ingot", "experience": 0.7}
                """);
        assertEquals(List.of(new RecipeDisplay.Furnace(new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.IRON_ORE))),
                SlotDisplay.AnyFuel.INSTANCE, new SlotDisplay.ItemStack(ItemStack.of(Material.IRON_INGOT)),
                new SlotDisplay.Item(Material.BLAST_FURNACE), 100, 0.7F)), blasting.createRecipeDisplays());
        assertEquals(RecipeBookCategory.BLAST_FURNACE_MISC, blasting.recipeBookCategory());
        assertEquals(Map.of(RecipeProperty.BLAST_FURNACE_INPUT, List.of(Material.IRON_ORE)), blasting.itemProperties());
        encode(env, blasting);

        assertEquals(RecipeBookCategory.FURNACE_BLOCKS, recipe(env, """
                {"type": "smelting", "category": "blocks", "ingredient": "minecraft:sand", "result": "minecraft:glass"}
                """).recipeBookCategory());
        assertEquals(RecipeBookCategory.SMOKER_FOOD, recipe(env, """
                {"type": "smoking", "ingredient": "minecraft:beef", "result": "minecraft:cooked_beef"}
                """).recipeBookCategory());
        var campfire = recipe(env, """
                {"type": "campfire_cooking", "ingredient": "#planks", "result": "minecraft:charcoal"}
                """);
        assertEquals(RecipeBookCategory.CAMPFIRE, campfire.recipeBookCategory());
        assertEquals(Map.of(RecipeProperty.CAMPFIRE_INPUT, List.of(Material.OAK_PLANKS)), campfire.itemProperties());
    }

    @Test
    void stonecutter(Env env) {
        var single = recipe(env, """
                {"type": "stonecutting", "ingredient": "minecraft:stone", "result": {"id": "minecraft:stone_slab", "count": 2}}
                """);
        // Minestom 问题 M3 的绕过方法：单个物品的原料显示为 Item
        assertEquals(List.of(new RecipeDisplay.Stonecutter(new SlotDisplay.Item(Material.STONE),
                        new SlotDisplay.ItemStack(ItemStack.of(Material.STONE_SLAB, 2)), new SlotDisplay.Item(Material.STONECUTTER))),
                single.createRecipeDisplays());
        assertEquals(RecipeBookCategory.STONECUTTER, single.recipeBookCategory());

        var several = recipe(env, """
                {"type": "stonecutting", "ingredient": ["minecraft:stone", "minecraft:granite"], "result": "minecraft:stone_slab"}
                """);
        assertEquals(new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.STONE), new SlotDisplay.Item(Material.GRANITE))),
                ((RecipeDisplay.Stonecutter) several.createRecipeDisplays().getFirst()).ingredient());
    }

    @Test
    void smithing(Env env) {
        var transform = recipe(env, """
                {"type": "smithing_transform", "template": "minecraft:netherite_upgrade_smithing_template", "base": "minecraft:diamond_sword",
                 "result": "minecraft:netherite_sword"}
                """);
        assertEquals(List.of(new RecipeDisplay.Smithing(
                new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE))),
                new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.DIAMOND_SWORD))),
                SlotDisplay.Empty.INSTANCE,
                new SlotDisplay.ItemStack(ItemStack.of(Material.NETHERITE_SWORD)),
                new SlotDisplay.Item(Material.SMITHING_TABLE))), transform.createRecipeDisplays());
        assertEquals(Map.of(RecipeProperty.SMITHING_TEMPLATE, List.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                RecipeProperty.SMITHING_BASE, List.of(Material.DIAMOND_SWORD)), transform.itemProperties());
        assertEquals(2, transform.craftingRequirements().size());
        encode(env, transform);

        var trim = recipe(env, """
                {"type": "smithing_trim", "addition": "minecraft:iron_ingot", "base": "minecraft:iron_chestplate",
                 "pattern": "minecraft:sentry", "template": "minecraft:sentry_armor_trim_smithing_template"}
                """);
        // Minestom 问题 M2 的绕过方法：没有显示数据，物品属性照常给出
        assertEquals(List.of(), trim.createRecipeDisplays());
        assertEquals(Map.of(RecipeProperty.SMITHING_TEMPLATE, List.of(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                RecipeProperty.SMITHING_BASE, List.of(Material.IRON_CHESTPLATE),
                RecipeProperty.SMITHING_ADDITION, List.of(Material.IRON_INGOT)), trim.itemProperties());
        assertEquals(RecipeBookCategory.SMITHING, trim.recipeBookCategory());
    }

    @Test
    void specialAndImpossibleRecipes(Env env) {
        var repair = recipe(env, """
                {"type": "crafting_special_repairitem"}
                """);
        assertEquals(List.of(), repair.createRecipeDisplays());
        assertEquals(RecipeBookCategory.CRAFTING_MISC, repair.recipeBookCategory());
        assertNull(repair.craftingRequirements());

        var impossible = recipe(env, """
                {"type": "smelting", "ingredient": "#empty", "result": "minecraft:stone"}
                """);
        assertEquals(1, impossible.createRecipeDisplays().size());
        assertEquals(List.of(), impossible.craftingRequirements());
        assertEquals(Map.of(), impossible.itemProperties());
    }

    private static MinestomRecipe recipe(Env env, String json) {
        var definition = new RecipeCodecs(TAGS, env.process()).decode(JsonParser.parseString(json)).getOrThrow();
        return MinestomRecipe.of(Key.key("test:recipe"), definition);
    }

    /**
     * 配方书条目能够按协议编码。
     */
    private static void encode(Env env, MinestomRecipe recipe) {
        var entries = recipe.createRecipeDisplays().stream()
                .map(display -> new RecipeBookAddPacket.Entry(0, display, null, recipe.recipeBookCategory(),
                        recipe.craftingRequirements(), false, false))
                .toList();
        NetworkBuffer.resizableBuffer(env.process()).write(RecipeBookAddPacket.SERIALIZER, new RecipeBookAddPacket(entries, true));
    }
}
