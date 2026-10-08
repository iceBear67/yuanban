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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @EnumSource(Layout.class)
    void nullFieldsCountAsAbsent(Layout layout) {
        var world = new TestWorld(tempDir).vanilla("minecraft", "tags/block/logs.json", "{\"values\": [\"oak_log\"]}");
        var a = world.worldPack(TestPack.create()
                .data("minecraft", "tags/block/logs.json",
                        "{\"replace\": null, \"values\": [{\"id\": \"#example:logs\", \"required\": null}]}"), layout, "a");

        assertEquals(List.of("vanilla minecraft:oak_log", a + " #example:logs"), values(load(world, BLOCK_TAGS), "minecraft:logs"));
    }

    static Stream<Arguments> invalidTagFilesFail() {
        return Stream.of(
                Arguments.of("[]", "Not a JSON object: []"),
                Arguments.of("{}", "No key values in MapLike[{}]"),
                Arguments.of("{\"values\": null}", "No key values in MapLike[{\"values\":null}]"),
                Arguments.of("{\"values\": {}}", "Not a json array: {}"),
                Arguments.of("{\"values\": [1]}", "Failed to parse either. First: Not a string: 1; Second: Not a JSON object: 1"),
                Arguments.of("{\"values\": [\"Bad\"]}", "Failed to parse either."
                        + " First: Not a valid resource location: Bad Non [a-z0-9/._-] character in path of location: minecraft:Bad;"
                        + " Second: Not a JSON object: \"Bad\""),
                Arguments.of("{\"values\": [{\"required\": false}]}", "Failed to parse either."
                        + " First: Not a string: {\"required\":false}; Second: No key id in MapLike[{\"required\":false}]"),
                // 对象写法的错误带有部分结果（required 取缺省值），Codec.either 直接返回它的错误
                Arguments.of("{\"values\": [{\"id\": \"a\", \"required\": \"no\"}]}", "Not a boolean: \"no\""),
                Arguments.of("{\"values\": [], \"replace\": 1}", "Not a boolean: 1")
        );
    }

    @ParameterizedTest
    @MethodSource
    void invalidTagFilesFail(String content, String message) {
        var world = new TestWorld(tempDir).vanilla("minecraft", "tags/block/broken.json", content);
        var exception = assertThrows(LoadFailedException.class, () -> load(world, BLOCK_TAGS));
        var cause = assertInstanceOf(TagFileException.class, exception.getCause());
        assertEquals("标签文件 [vanilla] data/minecraft/tags/block/broken.json 不合法：" + message, cause.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"values\": []} trailing",
            "{values: []}"
    })
    void tagFileSyntaxErrorsFail(String content) {
        var world = new TestWorld(tempDir).vanilla("minecraft", "tags/block/broken.json", content);
        var exception = assertThrows(LoadFailedException.class, () -> load(world, BLOCK_TAGS));
        var cause = assertInstanceOf(TagFileException.class, exception.getCause());
        assertTrue(cause.getMessage().startsWith("标签文件 [vanilla] data/minecraft/tags/block/broken.json 的 JSON 语法错误："),
                cause.getMessage());
    }
}
