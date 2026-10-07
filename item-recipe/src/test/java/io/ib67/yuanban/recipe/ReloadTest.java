package io.ib67.yuanban.recipe;

import io.ib67.yuanban.crafting.Crafting;
import io.ib67.yuanban.crafting.testing.InMemoryMaps;
import io.ib67.yuanban.datapack.ReloadRequest;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.play.DeclareRecipesPacket;
import net.minestom.server.network.packet.server.play.RecipeBookAddPacket;
import net.minestom.server.recipe.Ingredient;
import net.minestom.server.recipe.RecipeProperty;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
@ExtendWith(ResetItemTags.class)
class ReloadTest {
    @TempDir
    Path tempDir;

    @Test
    void reloadReplacesRecipesForOnlinePlayers(Env env) {
        var world = new RecipeWorld(tempDir)
                .vanilla("tags/item/ores.json", """
                        {"values": ["minecraft:iron_ore"]}
                        """)
                .vanilla("recipe/iron.json", """
                        {"type": "smelting", "ingredient": "#minecraft:ores", "result": "minecraft:iron_ingot"}
                        """)
                .vanilla("recipe/stone_slab.json", """
                        {"type": "stonecutting", "ingredient": "minecraft:stone", "result": {"id": "minecraft:stone_slab", "count": 2}}
                        """)
                .vanilla("recipe/stone_bricks.json", """
                        {"type": "stonecutting", "ingredient": ["minecraft:stone", "minecraft:granite"], "result": "minecraft:stone_bricks"}
                        """)
                .vanilla("recipe/trim.json", """
                        {"type": "smithing_trim", "addition": "minecraft:iron_ingot", "base": "minecraft:iron_chestplate",
                         "pattern": "minecraft:sentry", "template": "minecraft:sentry_armor_trim_smithing_template"}
                        """);
        var process = env.process();
        var crafting = new Crafting(process, new InMemoryMaps());
        var subscriber = new RecipeSubscriber(process, crafting.recipes());
        var session = world.loader(process, subscriber).start(RecipeWorld.createWorld());
        assertEquals(Set.of(Key.key("iron"), Key.key("stone_slab"), Key.key("stone_bricks"), Key.key("trim")),
                subscriber.current().recipes().keySet());

        var connection = env.createConnection();
        connection.connect(process.instance().createInstanceContainer(), new Pos(0, 40, 0));
        var declared = connection.trackIncoming(DeclareRecipesPacket.class);
        var book = connection.trackIncoming(RecipeBookAddPacket.class);

        world.pack("gold", "minecraft", "tags/item/ores.json", """
                        {"values": ["minecraft:gold_ore"]}
                        """)
                .pack("gold", "example", "recipe/copper.json", """
                        {"type": "smelting", "group": "ingots", "ingredient": "minecraft:copper_ore", "result": "minecraft:copper_ingot"}
                        """);
        session.reload(new ReloadRequest.Rescan());

        assertEquals(Set.of(Key.key("iron"), Key.key("stone_slab"), Key.key("stone_bricks"), Key.key("trim"), Key.key("example:copper")),
                subscriber.current().recipes().keySet());
        assertEquals(subscriber.current().recipes(), crafting.recipes().recipes());

        declared.assertSingle(packet -> {
            // 原版即使属性集合为空也发送全部七种
            assertEquals(EnumSet.allOf(RecipeProperty.class), EnumSet.copyOf(packet.itemProperties().keySet()));
            // 标签 ores 已经在同一次重载中加入 gold_ore
            assertEquals(Set.of(Material.IRON_ORE, Material.GOLD_ORE, Material.COPPER_ORE),
                    new HashSet<>(packet.itemProperties().get(RecipeProperty.FURNACE_INPUT)));
            assertEquals(List.of(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE), packet.itemProperties().get(RecipeProperty.SMITHING_TEMPLATE));
            assertEquals(List.of(), packet.itemProperties().get(RecipeProperty.SMOKER_INPUT));
            // 切石机列表直接由配方的原料生成，按配方 ID 排列，原料列出多个物品的配方也在其中
            assertEquals(List.of(
                    new DeclareRecipesPacket.StonecutterRecipe(new Ingredient(Material.STONE, Material.GRANITE),
                            new SlotDisplay.ItemStack(ItemStack.of(Material.STONE_BRICKS))),
                    new DeclareRecipesPacket.StonecutterRecipe(new Ingredient(Material.STONE),
                            new SlotDisplay.ItemStack(ItemStack.of(Material.STONE_SLAB, 2)))
            ), packet.stonecutterRecipes());
            NetworkBuffer.resizableBuffer(process).write(DeclareRecipesPacket.SERIALIZER, packet);
        });
        book.assertSingle(packet -> {
            assertTrue(packet.replace());
            // 按配方 ID 排列：example:copper、iron、stone_bricks、stone_slab；锻造纹饰配方没有配方书条目（Minestom 问题 M2）
            assertEquals(List.of(0, 1, 2, 3), packet.entries().stream().map(RecipeBookAddPacket.Entry::displayId).toList());
            assertEquals(2, packet.entries().stream().filter(entry -> entry.display() instanceof RecipeDisplay.Furnace).count());
            assertEquals(0, packet.entries().getFirst().group());
            NetworkBuffer.resizableBuffer(process).write(RecipeBookAddPacket.SERIALIZER, packet);
        });
        session.close();
    }
}
