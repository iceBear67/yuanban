package io.ib67.yuanban.enchant;

import io.ib67.yuanban.datapack.ReloadRequest;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnvTest
@ExtendWith(ResetItemTags.class)
class ReloadTest {
    @TempDir
    Path tempDir;

    @Test
    void reloadReplacesEnchantments(Env env) {
        var world = new EnchantWorld(tempDir)
                .vanilla("tags/item/enchantable/sword.json", """
                        {"values": ["minecraft:iron_sword"]}
                        """)
                .vanilla("enchantment/sharpness.json", """
                        {"description": "S", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 10, "max_level": 5,
                         "min_cost": {"base": 1, "per_level_above_first": 11},
                         "max_cost": {"base": 21, "per_level_above_first": 11},
                         "anvil_cost": 1, "slots": ["mainhand"]}
                        """);

        var process = env.process();
        var enchantments = new EnchantmentSubscriber(process);
        var enchantmentTags = new EnchantmentTagSubscriber(process);
        var providers = new EnchantmentProviderSubscriber(process);
        var session = world.loader(process, enchantments, enchantmentTags, providers).start(EnchantWorld.createWorld());
        assertEquals(Set.of(Key.key("minecraft:sharpness")), enchantments.current().enchantments().keySet());

        // 玩家在线之后修改数据包再重载
        var connection = env.createConnection();
        connection.connect(process.instance().createInstanceContainer(), new Pos(0, 40, 0));

        world.pack("changed", "minecraft", "enchantment/sharpness.json", """
                        {"description": "S+", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 20, "max_level": 10,
                         "min_cost": {"base": 1, "per_level_above_first": 11},
                         "max_cost": {"base": 21, "per_level_above_first": 11},
                         "anvil_cost": 2, "slots": ["mainhand"]}
                        """)
                .pack("changed", "minecraft", "enchantment/smite.json", """
                        {"description": "Smite", "supported_items": "#minecraft:enchantable/sword",
                         "weight": 5, "max_level": 5,
                         "min_cost": {"base": 5, "per_level_above_first": 8},
                         "max_cost": {"base": 25, "per_level_above_first": 8},
                         "anvil_cost": 1, "slots": ["mainhand"]}
                        """);
        session.reload(new ReloadRequest.Rescan());

        assertEquals(Set.of(Key.key("minecraft:sharpness"), Key.key("minecraft:smite")),
                enchantments.current().enchantments().keySet());
        var sharpness = enchantments.current().enchantments().get(Key.key("minecraft:sharpness"));
        assertEquals(20, sharpness.weight());
        assertEquals(10, sharpness.maxLevel());
        assertEquals(2, sharpness.anvilCost());

        // 注册表反映新数据
        var registered = process.enchantment().get(Key.key("minecraft:sharpness"));
        assertEquals(20, registered.weight());
        assertEquals(10, registered.maxLevel());
        assertEquals(2, registered.anvilCost());
        assertNotNull(process.enchantment().get(Key.key("minecraft:smite")));

        session.close();
    }
}
