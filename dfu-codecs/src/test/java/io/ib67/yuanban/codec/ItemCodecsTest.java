package io.ib67.yuanban.codec;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.ib67.yuanban.codec.CodecTestSupport.decode;
import static io.ib67.yuanban.codec.CodecTestSupport.encode;
import static io.ib67.yuanban.codec.CodecTestSupport.error;
import static io.ib67.yuanban.codec.CodecTestSupport.json;
import static io.ib67.yuanban.codec.CodecTestSupport.registryOps;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ItemCodecs}：键的错误文本取自原版 26.2 服务端；组件值内部的错误文本是 Minestom 的。
 */
@EnvTest
class ItemCodecsTest {
    @Test
    void item() {
        assertEquals(Material.APPLE, decode(ItemCodecs.ITEM, "\"minecraft:apple\""));
        assertEquals("\"minecraft:apple\"", encode(ItemCodecs.ITEM, Material.APPLE));
        assertEquals("Item must not be minecraft:air", error(ItemCodecs.ITEM, "\"minecraft:air\""));
        assertEquals("Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: minecraft:nope", error(ItemCodecs.ITEM, "\"minecraft:nope\""));
        assertEquals("Not a valid resource location: Bad Non [a-z0-9/._-] character in path of location: minecraft:Bad", error(ItemCodecs.ITEM, "\"Bad\""));
    }

    @Test
    void dataComponentType() {
        assertEquals(DataComponents.MAX_STACK_SIZE, decode(ItemCodecs.DATA_COMPONENT_TYPE, "\"minecraft:max_stack_size\""));
        assertEquals(DataComponents.CREATIVE_SLOT_LOCK, decode(ItemCodecs.DATA_COMPONENT_TYPE, "\"minecraft:creative_slot_lock\""));
        assertEquals("Unknown registry key in ResourceKey[minecraft:root / minecraft:data_component_type]: minecraft:nope",
                error(ItemCodecs.DATA_COMPONENT_TYPE, "\"minecraft:nope\""));
        assertEquals("Encountered transient component minecraft:creative_slot_lock",
                error(ItemCodecs.PERSISTENT_DATA_COMPONENT_TYPE, "\"minecraft:creative_slot_lock\""));
    }

    @Test
    void patch(Env env) {
        DynamicOps<JsonElement> ops = registryOps(env.process());
        DataComponentMap patch = decode(ItemCodecs.DATA_COMPONENT_PATCH, ops, "{\"minecraft:max_stack_size\": 5, \"!minecraft:food\": {}}");
        // 移除标记在补丁中保留为空值
        assertEquals(List.of(new DataComponent.Value(DataComponents.MAX_STACK_SIZE, 5), new DataComponent.Value(DataComponents.FOOD, null)),
                patch.entrySet());
        assertEquals("{\"minecraft:max_stack_size\":5,\"!minecraft:food\":{}}", encode(ItemCodecs.DATA_COMPONENT_PATCH, ops, patch));
        assertEquals(DataComponentMap.EMPTY, decode(ItemCodecs.DATA_COMPONENT_PATCH, ops, "{}"));
        // 全部条目都会尝试解码；值的错误文本来自 Minestom（原版为 Not a number: "x"）
        assertEquals("No component with type: 'minecraft:nope'; No component with type: 'minecraft:nope'; Not an int: \"x\" missed input: "
                        + "{\"minecraft:max_stack_size\":\"x\",\"minecraft:nope\":1}",
                error(ItemCodecs.DATA_COMPONENT_PATCH, ops, "{\"minecraft:max_stack_size\": \"x\", \"minecraft:nope\": 1}"));
        assertEquals("Not a JSON object: [] missed input: {\"!minecraft:food\":[]}",
                error(ItemCodecs.DATA_COMPONENT_PATCH, ops, "{\"!minecraft:food\": []}"));
        assertEquals("'minecraft:creative_slot_lock' is not a persistent component; 'minecraft:creative_slot_lock' is not a persistent "
                        + "component missed input: {\"!minecraft:creative_slot_lock\":{}}",
                error(ItemCodecs.DATA_COMPONENT_PATCH, ops, "{\"!minecraft:creative_slot_lock\": {}}"));
        assertEquals("No component with type: 'null'; No component with type: 'null' missed input: {\"Bad:id\":{}}",
                error(ItemCodecs.DATA_COMPONENT_PATCH, ops, "{\"Bad:id\": {}}"));
    }

    @Test
    void valueMap(Env env) {
        DynamicOps<JsonElement> ops = registryOps(env.process());
        DataComponentMap map = decode(ItemCodecs.DATA_COMPONENT_MAP, ops, "{\"minecraft:max_stack_size\": 5}");
        assertEquals(5, map.get(DataComponents.MAX_STACK_SIZE));
        assertEquals("{\"minecraft:max_stack_size\":5}", encode(ItemCodecs.DATA_COMPONENT_MAP, ops, map));
        assertEquals("Encountered transient component minecraft:creative_slot_lock; Encountered transient component "
                        + "minecraft:creative_slot_lock missed input: {\"minecraft:creative_slot_lock\":{}}",
                error(ItemCodecs.DATA_COMPONENT_MAP, ops, "{\"minecraft:creative_slot_lock\": {}}"));
        assertEquals("Unknown registry key in ResourceKey[minecraft:root / minecraft:data_component_type]: minecraft:nope; Unknown registry key "
                        + "in ResourceKey[minecraft:root / minecraft:data_component_type]: minecraft:nope missed input: {\"minecraft:nope\":1}",
                error(ItemCodecs.DATA_COMPONENT_MAP, ops, "{\"minecraft:nope\": 1}"));
        assertEquals("Not a valid resource location: !minecraft:food Non [a-z0-9_.-] character in namespace of identifier: !minecraft:food; "
                        + "Not a valid resource location: !minecraft:food Non [a-z0-9_.-] character in namespace of identifier: !minecraft:food "
                        + "missed input: {\"!minecraft:food\":{}}",
                error(ItemCodecs.DATA_COMPONENT_MAP, ops, "{\"!minecraft:food\": {}}"));
    }

    @Test
    void itemStack(Env env) {
        DynamicOps<JsonElement> ops = registryOps(env.process());
        assertEquals(ItemStack.of(Material.APPLE), decode(ItemCodecs.ITEM_STACK, ops, "{\"id\": \"minecraft:apple\"}"));
        assertEquals("{\"id\":\"minecraft:apple\",\"count\":1}", encode(ItemCodecs.ITEM_STACK, ops, ItemStack.of(Material.APPLE)));
        ItemStack stack = decode(ItemCodecs.ITEM_STACK, ops, "{\"id\": \"minecraft:apple\", \"count\": 3, \"components\": {\"minecraft:max_stack_size\": 16}}");
        assertEquals(3, stack.amount());
        assertEquals(16, stack.get(DataComponents.MAX_STACK_SIZE));
        assertEquals("Value must be within range [1;99]: 0", error(ItemCodecs.ITEM_STACK, ops, "{\"id\": \"minecraft:apple\", \"count\": 0}"));
        assertEquals("Item must not be minecraft:air", error(ItemCodecs.ITEM_STACK, ops, "{\"id\": \"minecraft:air\"}"));
        assertEquals("No key id in MapLike[{\"count\":1}]", error(ItemCodecs.ITEM_STACK, ops, "{\"count\": 1}"));
    }

    @Test
    void itemStackTemplate(Env env) {
        DynamicOps<JsonElement> ops = registryOps(env.process());
        ItemStackTemplate apple = new ItemStackTemplate(Material.APPLE, 1, DataComponentMap.EMPTY);
        assertEquals(apple, decode(ItemCodecs.ITEM_STACK_TEMPLATE, ops, "\"minecraft:apple\""));
        assertEquals(apple, decode(ItemCodecs.ITEM_STACK_TEMPLATE, ops, "{\"id\": \"minecraft:apple\", \"count\": 1}"));
        assertEquals("{\"id\":\"minecraft:apple\"}", encode(ItemCodecs.ITEM_STACK_TEMPLATE, ops, apple));
        assertEquals("Failed to parse either. First: Not a JSON object: \"minecraft:air\"; Second: Item must not be minecraft:air",
                error(ItemCodecs.ITEM_STACK_TEMPLATE, ops, "\"minecraft:air\""));
        // 补丁原样保存，转换成物品堆时去掉与原型相同的部分
        ItemStackTemplate template = decode(ItemCodecs.ITEM_STACK_TEMPLATE, ops,
                "{\"id\": \"minecraft:apple\", \"components\": {\"minecraft:max_stack_size\": 64}}");
        assertEquals(List.of(new DataComponent.Value(DataComponents.MAX_STACK_SIZE, 64)), template.components().entrySet());
        assertEquals(DataComponentMap.EMPTY, template.asItemStack().componentPatch());
        assertEquals(json("{\"id\":\"minecraft:apple\",\"components\":{\"minecraft:max_stack_size\":64}}"),
                json(encode(ItemCodecs.ITEM_STACK_TEMPLATE, ops, template)));
    }
}
