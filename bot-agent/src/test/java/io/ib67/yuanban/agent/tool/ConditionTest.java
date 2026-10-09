package io.ib67.yuanban.agent.tool;

import io.ib67.yuanban.agent.bot.FixtureWorld;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.SchemaViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionTest {
    private final FixtureWorld world = new FixtureWorld(Json.parseObject("""
            {"player": {"position": {"x": 0.5, "y": 64, "z": 0.5}, "health": 8},
             "ground": {"y": 63, "name": "minecraft:stone"},
             "loadedChunks": [[0, 0]],
             "blocks": [{"x": 2, "y": 64, "z": 2, "name": "minecraft:lava"}],
             "entities": [{"id": 5, "type": "minecraft:zombie", "position": {"x": 3.5, "y": 64, "z": 0.5}}],
             "inventory": {"items": [{"slot": 36, "item": "minecraft:dirt", "count": 5}], "mainHand": {"slot": 36, "item": "minecraft:dirt", "count": 5}},
             "chat": [{"text": "hello world"}]}
            """));

    private boolean test(String json) {
        return Condition.parse(Json.parse(json)).test(world);
    }

    @Test
    void playerConditions() {
        assertTrue(test("{\"near\": {\"x\": 1, \"y\": 64, \"z\": 1, \"distance\": 1}}"));
        assertTrue(test("{\"far\": {\"x\": 10, \"y\": 64, \"z\": 10, \"distance\": 5}}"));
        assertTrue(test("{\"health\": {\"lt\": 10, \"gte\": 8}}"));
        assertFalse(test("{\"health\": {\"gt\": 8}}"));
        assertTrue(test("{\"onGround\": true}"));
        assertTrue(test("{\"loaded\": true}"));
        assertTrue(test("{\"dimension\": \"minecraft:overworld\"}"));
        assertTrue(test("{\"gameMode\": \"SURVIVAL\"}"));
    }

    @Test
    void blockConditions() {
        assertTrue(test("{\"block\": {\"x\": 2, \"y\": 64, \"z\": 2, \"is\": \"minecraft:lava\", \"liquid\": true}}"));
        assertTrue(test("{\"block\": {\"x\": 0, \"y\": 63, \"z\": 0, \"blocksMotion\": true}}"));
        assertTrue(test("{\"block\": {\"x\": 0, \"y\": 65, \"z\": 0, \"air\": true}}"));
        assertTrue(test("{\"chunkLoaded\": {\"x\": 3, \"z\": 3}}"));
        assertFalse(test("{\"chunkLoaded\": {\"x\": 40, \"z\": 3}}"));
        // 未加载区块中的方块条件不成立，不抛出异常
        assertFalse(test("{\"block\": {\"x\": 40, \"y\": 64, \"z\": 0, \"air\": true}}"));
    }

    @Test
    void entityInventoryAndChatConditions() {
        assertTrue(test("{\"entityNear\": {\"type\": \"minecraft:zombie\", \"radius\": 4}}"));
        assertFalse(test("{\"entityNear\": {\"type\": \"minecraft:zombie\", \"radius\": 2}}"));
        assertTrue(test("{\"entityExists\": {\"id\": 5}}"));
        assertTrue(test("{\"holding\": {\"item\": \"minecraft:dirt\"}}"));
        assertTrue(test("{\"hasItem\": {\"item\": \"minecraft:dirt\", \"count\": 5}}"));
        assertFalse(test("{\"hasItem\": {\"item\": \"minecraft:dirt\", \"count\": 6}}"));
        assertTrue(test("{\"chatContains\": {\"text\": \"world\"}}"));
        assertTrue(test("{\"windowOpen\": false}"));
    }

    @Test
    void combinators() {
        assertTrue(test("{\"all\": [{\"onGround\": true}, {\"not\": {\"dead\": true}}]}"));
        assertTrue(test("{\"any\": [{\"dead\": true}, {\"loaded\": true}]}"));
        var failures = Condition.parse(Json.parse("{\"all\": [{\"dead\": true}, {\"onGround\": true}, {\"health\": {\"gt\": 19}}]}"))
                .failures(world);
        assertEquals(2, failures.size());
    }

    @Test
    void invalidConditionsAreRejected() {
        assertThrows(SchemaViolationException.class, () -> Condition.parse(Json.parse("{\"near\": {\"x\": 1}}")));
        assertThrows(SchemaViolationException.class, () -> Condition.parse(Json.parse("{\"onGround\": true, \"loaded\": true}")));
        assertThrows(SchemaViolationException.class, () -> Condition.parse(Json.parse("{\"teleport\": true}")));
        assertThrows(SchemaViolationException.class,
                () -> Condition.parse(Json.parse("{\"near\": {\"x\": 1e9, \"y\": 64, \"z\": 0, \"distance\": 1}}")));
    }
}
