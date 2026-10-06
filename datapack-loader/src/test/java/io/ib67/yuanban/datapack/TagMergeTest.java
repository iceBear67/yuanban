package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.TestPack.Layout;
import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.tag.TagDeclarations;
import io.ib67.yuanban.datapack.tag.TagEntry;
import io.ib67.yuanban.datapack.topic.Topic;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagMergeTest {
    private static final Topic.Tags BLOCK_TAGS = Topic.tags("block");

    @TempDir
    Path tempDir;

    private static TagDeclarations load(TestWorld world, Topic.Tags topic) {
        var subscriber = new TestSubscriber<TagDeclarations>(context -> context.contents(topic));
        try (var session = world.loader()
                .subscribe(Subscription.builder(SubscriberKey.<TagDeclarations>of("test:tags"), subscriber)
                        .lifecycle(Lifecycle.RELOADABLE)
                        .topic(topic)
                        .build())
                .build()
                .start(TestWorld.createWorld())) {
            return subscriber.last();
        }
    }

    private static List<String> values(TagDeclarations tags, String id) {
        return tags.tags().get(Key.key(id)).values().stream()
                .map(value -> value.pack().id() + " " + value.entry())
                .toList();
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void mergesAcrossPacksAndHonoursReplace(Layout layout) {
        var world = new TestWorld(tempDir)
                .vanilla("minecraft", "tags/block/logs.json", "{\"values\": [\"oak_log\", \"minecraft:birch_log\"]}")
                .vanilla("minecraft", "tags/block/stones.json", "{\"values\": [\"stone\"]}");
        var a = world.worldPack(TestPack.create()
                .data("minecraft", "tags/block/logs.json", "{\"values\": [\"example:ruby_log\"]}"), layout, "a");
        var b = world.worldPack(TestPack.create()
                .data("minecraft", "tags/block/stones.json", "{\"replace\": true, \"values\": [\"#minecraft:base_stone\"]}"), layout, "b");
        var c = world.worldPack(TestPack.create()
                .data("minecraft", "tags/block/stones.json", "{\"values\": [{\"id\": \"example:marble\", \"required\": false}]}"), layout, "c");

        var tags = load(world, BLOCK_TAGS);
        assertEquals(List.of("vanilla minecraft:oak_log", "vanilla minecraft:birch_log", a + " example:ruby_log"),
                values(tags, "minecraft:logs"));
        assertEquals(List.of(b + " #minecraft:base_stone", c + " example:marble?"), values(tags, "minecraft:stones"));
        var entry = tags.tags().get(Key.key("minecraft:stones")).values().getLast().entry();
        assertEquals(new TagEntry(Key.key("example:marble"), false, false), entry);
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void overlayTagReplacesRootTagInSamePack(Layout layout) {
        var world = new TestWorld(tempDir).vanilla("minecraft", "tags/block/logs.json", "{\"values\": [\"oak_log\"]}");
        var a = world.worldPack(TestPack.create("""
                        "overlays": {"entries": [{"directory": "ov", "min_format": 107, "max_format": 107}]}""")
                .data("minecraft", "tags/block/logs.json", "{\"values\": [\"root_log\"]}")
                .file("ov/data/minecraft/tags/block/logs.json", "{\"values\": [\"overlay_log\"]}"), layout, "a");

        assertEquals(List.of("vanilla minecraft:oak_log", a + " minecraft:overlay_log"), values(load(world, BLOCK_TAGS), "minecraft:logs"));
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void tagsOfNestedRegistries(Layout layout) {
        var world = new TestWorld(tempDir)
                .vanilla("minecraft", "tags/worldgen/biome/is_ocean.json", "{\"values\": [\"ocean\"], \"unknown\": 1}")
                .vanilla("minecraft", "tags/function/load.json", "{\"values\": []}");
        assertEquals(List.of("vanilla minecraft:ocean"), values(load(world, Topic.tags("worldgen/biome")), "minecraft:is_ocean"));
        assertEquals(List.of(), values(load(world, Topic.tags("function")), "minecraft:load"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "{}",
            "{\"values\": {}}",
            "{\"values\": [1]}",
            "{\"values\": [\"Bad\"]}",
            "{\"values\": [{\"required\": false}]}",
            "{\"values\": [{\"id\": \"a\", \"required\": \"no\"}]}",
            "{\"values\": [], \"replace\": 1}",
            "{\"values\": []} trailing",
            "{values: []}"
    })
    void invalidTagFilesFail(String content) {
        var world = new TestWorld(tempDir).vanilla("minecraft", "tags/block/broken.json", content);
        var exception = assertThrows(LoadFailedException.class, () -> load(world, BLOCK_TAGS));
        assertInstanceOf(TagFileException.class, exception.getCause());
    }
}
