package io.ib67.yuanban.crafting;

import com.google.gson.JsonParser;
import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.Recipe;
import io.ib67.yuanban.crafting.recipe.RecipeType;
import io.ib67.yuanban.crafting.recipe.SingleRecipeInput;
import io.ib67.yuanban.crafting.testing.InMemoryMaps;
import io.ib67.yuanban.itemtag.ItemTags;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import io.ib67.yuanban.recipe.internal.RecipeCodecs;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.play.DeclareRecipesPacket;
import net.minestom.server.network.packet.server.play.RecipeBookAddPacket;
import net.minestom.server.recipe.Ingredient;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.RecipeProperty;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;
import net.minestom.server.registry.TagKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配方的显示数据、配方书类别与分组、放置信息，以及 {@code RecipeManager} 的查找和客户端同步。
 * 配方直接用 {@code item-recipe} 的解码器得到，不修改 Minestom 的标签；标签原料使用 Minestom 自带的 {@code minecraft:planks}。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class RecipeManagerTest {
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
        // 原版 Ingredient.display()：有合成剩余物的物品显示剩余物
        var milk = new SlotDisplay.Composite(List.of(new SlotDisplay.WithRemainder(
                new SlotDisplay.Item(Material.MILK_BUCKET), new SlotDisplay.ItemStack(ItemStack.of(Material.BUCKET)))));
        assertEquals(List.of(new RecipeDisplay.CraftingShaped(2, 2, List.of(PLANKS, SlotDisplay.Empty.INSTANCE, SlotDisplay.Empty.INSTANCE, milk),
                new SlotDisplay.ItemStack(ItemStack.of(Material.STICK, 4)), CRAFTING_TABLE)), recipe.display());
        assertEquals(RecipeBookCategory.CRAFTING_EQUIPMENT, recipe.recipeBookCategory());
        assertEquals("sticks", recipe.group());
        assertEquals(List.of(0, -1, -1, 1), recipe.placementInfo().slotsToIngredientIndex());
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
        ), recipe.display());
        // 原版 TransmuteRecipe.createPlacementInfo：输入物品加上最大数量的材料
        assertEquals(3, recipe.placementInfo().ingredients().size());
        assertEquals("", recipe.group());
    }

    @Test
    void dyeAndImbue(Env env) {
        var dye = recipe(env, """
                {"type": "crafting_dye", "target": "minecraft:leather_helmet", "dye": "minecraft:red_dye", "result": "minecraft:leather_helmet"}
                """);
        var dyes = new SlotDisplay.OnlyWithComponent(new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.RED_DYE))), DataComponents.DYE);
        var helmet = new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.LEATHER_HELMET)));
        assertEquals(List.of(new RecipeDisplay.CraftingShapeless(List.of(helmet, dyes), new SlotDisplay.Dyed(dyes, helmet), CRAFTING_TABLE)),
                dye.display());

        var imbue = recipe(env, """
                {"type": "crafting_imbue", "source": "minecraft:lingering_potion", "material": "minecraft:arrow", "result": {"id": "minecraft:tipped_arrow", "count": 8}}
                """);
        var arrow = new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.ARROW)));
        var potion = new SlotDisplay.WithAnyPotion(new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.LINGERING_POTION))));
        assertEquals(List.of(new RecipeDisplay.CraftingShaped(3, 3, List.of(arrow, arrow, arrow, arrow, potion, arrow, arrow, arrow, arrow),
                        new SlotDisplay.WithAnyPotion(new SlotDisplay.ItemStack(ItemStack.of(Material.TIPPED_ARROW, 8))), CRAFTING_TABLE)),
                imbue.display());
        assertEquals(9, imbue.placementInfo().ingredients().size());
    }

    @Test
    void cookingStonecutterAndSmithing(Env env) {
        var blasting = recipe(env, """
                {"type": "blasting", "category": "food", "group": "iron", "ingredient": "minecraft:iron_ore", "result": "minecraft:iron_ingot", "experience": 0.7}
                """);
        assertEquals(List.of(new RecipeDisplay.Furnace(new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.IRON_ORE))),
                SlotDisplay.AnyFuel.INSTANCE, new SlotDisplay.ItemStack(ItemStack.of(Material.IRON_INGOT)),
                new SlotDisplay.Item(Material.BLAST_FURNACE), 100, 0.7F)), blasting.display());
        assertEquals(RecipeBookCategory.BLAST_FURNACE_MISC, blasting.recipeBookCategory());
        assertEquals(RecipeBookCategory.FURNACE_BLOCKS, recipe(env, """
                {"type": "smelting", "category": "blocks", "ingredient": "minecraft:sand", "result": "minecraft:glass"}
                """).recipeBookCategory());
        assertEquals(RecipeBookCategory.SMOKER_FOOD, recipe(env, """
                {"type": "smoking", "ingredient": "minecraft:beef", "result": "minecraft:cooked_beef"}
                """).recipeBookCategory());
        assertEquals(RecipeBookCategory.CAMPFIRE, recipe(env, """
                {"type": "campfire_cooking", "ingredient": "#planks", "result": "minecraft:charcoal"}
                """).recipeBookCategory());

        // 原版 Ingredient.display()：切石机的原料同样显示为 Composite
        var stonecutter = recipe(env, """
                {"type": "stonecutting", "ingredient": "minecraft:stone", "result": {"id": "minecraft:stone_slab", "count": 2}}
                """);
        assertEquals(List.of(new RecipeDisplay.Stonecutter(new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.STONE))),
                new SlotDisplay.ItemStack(ItemStack.of(Material.STONE_SLAB, 2)), new SlotDisplay.Item(Material.STONECUTTER))), stonecutter.display());
        assertEquals(RecipeBookCategory.STONECUTTER, stonecutter.recipeBookCategory());

        var transform = recipe(env, """
                {"type": "smithing_transform", "template": "minecraft:netherite_upgrade_smithing_template", "base": "minecraft:diamond_sword",
                 "result": "minecraft:netherite_sword"}
                """);
        assertEquals(List.of(new RecipeDisplay.Smithing(
                new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE))),
                new SlotDisplay.Composite(List.of(new SlotDisplay.Item(Material.DIAMOND_SWORD))),
                SlotDisplay.Empty.INSTANCE,
                new SlotDisplay.ItemStack(ItemStack.of(Material.NETHERITE_SWORD)),
                new SlotDisplay.Item(Material.SMITHING_TABLE))), transform.display());
        assertEquals(List.of(0, 1, -1), transform.placementInfo().slotsToIngredientIndex());

        var trim = recipe(env, """
                {"type": "smithing_trim", "addition": "minecraft:iron_ingot", "base": "minecraft:iron_chestplate",
                 "pattern": "minecraft:sentry", "template": "minecraft:sentry_armor_trim_smithing_template"}
                """);
        // Minestom 问题 M2 的绕过方法：没有显示数据
        assertEquals(List.of(), trim.display());
        assertEquals(RecipeBookCategory.SMITHING, trim.recipeBookCategory());
    }

    @Test
    void specialAndImpossibleRecipes(Env env) {
        var repair = recipe(env, """
                {"type": "crafting_special_repairitem"}
                """);
        assertTrue(repair.isSpecial());
        assertEquals(List.of(), repair.display());
        assertEquals(RecipeBookCategory.CRAFTING_MISC, repair.recipeBookCategory());
        assertTrue(repair.placementInfo().isImpossibleToPlace());

        var impossible = recipe(env, """
                {"type": "smelting", "ingredient": "#empty", "result": "minecraft:stone"}
                """);
        assertEquals(1, impossible.display().size());
        assertTrue(impossible.placementInfo().isImpossibleToPlace());
    }

    /**
     * 原版 {@code RecipeManager.finalizeRecipeLoading} 与 {@code unpackRecipeInfo}：显示 ID 按配方顺序编号，分组 ID 按非空分组第一次出现的顺序编号，
     * 特殊配方没有原料需求，不能放置的非特殊配方不进入物品属性集合和切石机列表。
     */
    @Test
    void replaceRecipesSendsDataToOnlinePlayers(Env env) {
        var process = env.process();
        var recipes = new Crafting(process, new InMemoryMaps()).recipes();
        var definitions = new TreeMap<Key, Recipe<?>>();
        definitions.put(Key.key("a_planks_stick"), recipe(env, """
                {"type": "crafting_shaped", "group": "sticks", "key": {"#": "#planks"}, "pattern": ["#", "#"], "result": {"id": "minecraft:stick", "count": 4}}
                """));
        definitions.put(Key.key("b_stone_stick"), recipe(env, """
                {"type": "crafting_shaped", "group": "sticks", "key": {"#": "minecraft:stone"}, "pattern": ["#", "#"], "result": "minecraft:stick"}
                """));
        definitions.put(Key.key("c_glass"), recipe(env, """
                {"type": "smelting", "group": "glass", "ingredient": "minecraft:sand", "result": "minecraft:glass"}
                """));
        definitions.put(Key.key("d_nothing"), recipe(env, """
                {"type": "smelting", "ingredient": "#empty", "result": "minecraft:stone"}
                """));
        definitions.put(Key.key("e_repair"), recipe(env, """
                {"type": "crafting_special_repairitem"}
                """));
        definitions.put(Key.key("f_stairs"), recipe(env, """
                {"type": "stonecutting", "ingredient": ["minecraft:stone", "minecraft:granite"], "result": "minecraft:stone_stairs"}
                """));

        var connection = env.createConnection();
        connection.connect(process.instance().createInstanceContainer(), new Pos(0, 40, 0));
        var declared = connection.trackIncoming(DeclareRecipesPacket.class);
        var book = connection.trackIncoming(RecipeBookAddPacket.class);
        recipes.replaceRecipes(definitions);

        assertEquals(definitions, recipes.recipes());
        var stick = recipes.displayInfo(0).orElseThrow();
        assertEquals(Key.key("a_planks_stick"), stick.parent().id());
        assertEquals(0, stick.group());
        assertEquals(List.of(new Ingredient(Material.staticRegistry().getTag(TagKey.of("minecraft:planks"))),
                new Ingredient(Material.staticRegistry().getTag(TagKey.of("minecraft:planks")))), stick.craftingRequirements());
        assertEquals(0, recipes.displayInfo(1).orElseThrow().group());
        assertEquals(1, recipes.displayInfo(2).orElseThrow().group());
        // 不能放置的配方仍然有显示，原料需求为空列表
        var nothing = recipes.displayInfo(3).orElseThrow();
        assertEquals(Key.key("d_nothing"), nothing.parent().id());
        assertNull(nothing.group());
        assertEquals(List.of(), nothing.craftingRequirements());
        var stairs = recipes.displayInfo(4).orElseThrow();
        assertEquals(Key.key("f_stairs"), stairs.parent().id());
        assertEquals(Optional.empty(), recipes.displayInfo(5));

        declared.assertSingle(packet -> {
            assertEquals(List.of(Material.SAND), packet.itemProperties().get(RecipeProperty.FURNACE_INPUT));
            assertEquals(List.of(new DeclareRecipesPacket.StonecutterRecipe(new Ingredient(Material.STONE, Material.GRANITE),
                    new SlotDisplay.ItemStack(ItemStack.of(Material.STONE_STAIRS)))), packet.stonecutterRecipes());
            NetworkBuffer.resizableBuffer(process).write(DeclareRecipesPacket.SERIALIZER, packet);
        });
        book.assertSingle(packet -> {
            assertTrue(packet.replace());
            assertEquals(5, packet.entries().size());
            assertTrue(packet.entries().stream().noneMatch(entry -> entry.notification() || entry.highlight()));
            NetworkBuffer.resizableBuffer(process).write(RecipeBookAddPacket.SERIALIZER, packet);
        });
    }

    /**
     * 原版 {@code RecipeManager.getRecipeFor}：返回配方 ID 最小的匹配配方；配方提示匹配时优先返回它；输入为空时没有结果。
     */
    @Test
    void getRecipeForUsesIdOrderAndHint(Env env) {
        var recipes = new Crafting(env.process(), new InMemoryMaps()).recipes();
        var definitions = new TreeMap<Key, Recipe<?>>();
        definitions.put(Key.key("example:z_first"), recipe(env, """
                {"type": "crafting_shapeless", "ingredients": ["minecraft:stone"], "result": "minecraft:cobblestone"}
                """));
        definitions.put(Key.key("example:a_second"), recipe(env, """
                {"type": "crafting_shapeless", "ingredients": ["minecraft:stone"], "result": "minecraft:gravel"}
                """));
        recipes.replaceRecipes(definitions);
        var input = CraftingInput.of(1, 1, List.of(ItemStack.of(Material.STONE)));
        // Key 先比较值再比较命名空间，与原版 Identifier 相同
        assertEquals(Key.key("example:a_second"), recipes.getRecipeFor(RecipeType.CRAFTING, input, null).orElseThrow().id());
        assertEquals(Key.key("example:z_first"), recipes.getRecipeFor(RecipeType.CRAFTING, input, Key.key("example:z_first")).orElseThrow().id());
        assertEquals(Optional.empty(), recipes.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 1, List.of(ItemStack.AIR)), null));
        assertEquals(Optional.empty(), recipes.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(ItemStack.of(Material.STONE)), null));
    }

    private static Recipe<?> recipe(Env env, String json) {
        return RecipeCodecs.CODEC.parse(RecipeCodecs.registryOps(env.process(), TAGS), JsonParser.parseString(json)).getOrThrow();
    }
}
