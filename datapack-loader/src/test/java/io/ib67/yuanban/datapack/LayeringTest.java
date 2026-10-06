package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.TestPack.Layout;
import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Resource;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.datapack.topic.TopicFiles;
import io.ib67.yuanban.datapack.topic.TopicLayers;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeringTest {
    private static final Topic.Single RECIPES = Topic.json("recipe");
    private static final Topic.Layered RECIPE_LAYERS = Topic.layered("recipe", ".json");

    @TempDir
    Path tempDir;

    /**
     * 新建世界，读取 {@code topic}，返回提交的内容。世界数据包按 ID 顺序自动追加在 vanilla 之上。
     */
    private <C> C load(TestWorld world, Topic<C> topic) {
        var subscriber = new TestSubscriber<C>(context -> context.contents(topic));
        try (var session = world.loader()
                .subscribe(Subscription.builder(SubscriberKey.<C>of("test:reader"), subscriber)
                        .lifecycle(Lifecycle.RELOADABLE)
                        .topic(topic)
                        .build())
                .build()
                .start(TestWorld.createWorld())) {
            return subscriber.last();
        }
    }

    /**
     * 每个资源 ID 对应的文件内容。在会话关闭之前读取。
     */
    private static Map<String, String> contents(TestWorld world, Topic.Single topic) {
        var subscriber = new TestSubscriber<Map<String, String>>(context -> context.contents(topic).files().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().asString(), entry -> read(entry.getValue()))));
        try (var session = world.loader()
                .subscribe(Subscription.builder(SubscriberKey.<Map<String, String>>of("test:contents"), subscriber)
                        .lifecycle(Lifecycle.RELOADABLE)
                        .topic(topic)
                        .build())
                .build()
                .start(TestWorld.createWorld())) {
            return subscriber.last();
        }
    }

    private static String read(Resource resource) {
        try {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String packOf(TopicFiles files, String id) {
        return files.find(Key.key(id)).orElseThrow().pack().id();
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void higherPriorityPackWins(Layout layout) {
        var world = new TestWorld(tempDir)
                .vanilla("minecraft", "recipe/a.json", "vanilla a")
                .vanilla("minecraft", "recipe/b.json", "vanilla b")
                .vanilla("minecraft", "recipe/nested/c.json", "vanilla c");
        var x = world.worldPack(TestPack.create()
                .data("minecraft", "recipe/a.json", "x a")
                .data("minecraft", "recipe/b.json", "x b"), layout, "x");
        var y = world.worldPack(TestPack.create().data("minecraft", "recipe/b.json", "y b"), layout, "y");

        var files = load(world, RECIPES);
        assertEquals(List.of("minecraft:a", "minecraft:b", "minecraft:nested/c"),
                files.files().keySet().stream().map(Key::asString).toList());
        assertEquals(x, packOf(files, "minecraft:a"));
        assertEquals(y, packOf(files, "minecraft:b"));
        assertEquals("vanilla", packOf(files, "minecraft:nested/c"));
        var resource = files.find(Key.key("minecraft:a")).orElseThrow();
        assertEquals(Key.key("minecraft:recipe/a.json"), resource.location());
        assertNull(resource.overlay());

        assertEquals(Map.of("minecraft:a", "x a", "minecraft:b", "y b", "minecraft:nested/c", "vanilla c"),
                contents(world, RECIPES));
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void overlaysApplyInOrder(Layout layout) {
        var world = new TestWorld(tempDir);
        world.worldPack(TestPack.create("""
                        "overlays": {"entries": [
                          {"directory": "first", "min_format": 107, "max_format": 107},
                          {"directory": "second", "min_format": 107, "max_format": 107},
                          {"directory": "listed_but_missing", "min_format": 107, "max_format": 107},
                          {"directory": "inactive", "min_format": 200, "max_format": 200}]}""")
                .data("minecraft", "recipe/a.json", "root a")
                .data("minecraft", "recipe/b.json", "root b")
                .data("minecraft", "recipe/c.json", "root c")
                .file("first/data/minecraft/recipe/a.json", "first a")
                .file("first/data/minecraft/recipe/b.json", "first b")
                .file("second/data/minecraft/recipe/a.json", "second a")
                .file("second/data/other/recipe/d.json", "second d")
                .file("inactive/data/minecraft/recipe/c.json", "inactive c")
                .file("missing_is_fine/data/minecraft/recipe/x.json", "never listed"), layout, "x");

        assertEquals(Map.of(
                "minecraft:a", "second a",
                "minecraft:b", "first b",
                "minecraft:c", "root c",
                "other:d", "second d"), contents(world, RECIPES));
        var files = load(world, RECIPES);
        assertEquals("second", files.find(Key.key("minecraft:a")).orElseThrow().overlay());
        try (var session = world.loader().build().start(TestWorld.createWorld())) {
            assertEquals(List.of("first", "second", "listed_but_missing"), session.selection().packs().getLast().activeOverlays());
        }
        assertTrue(files.find(Key.key("minecraft:x")).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void filterOnlyAffectsLowerPacks(Layout layout) {
        var world = new TestWorld(tempDir)
                .vanilla("minecraft", "recipe/a.json", "vanilla a")
                .vanilla("minecraft", "recipe/b.json", "vanilla b")
                .vanilla("minecraft", "loot_table/a.json", "vanilla loot");
        world.worldPack(TestPack.create("""
                        "filter": {"block": [{"namespace": "minecraft", "path": "^recipe/a\\\\.json$"}]}""")
                .data("minecraft", "recipe/c.json", "x c"), layout, "x");
        world.worldPack(TestPack.create("""
                        "filter": {"block": [{"namespace": "minecraft", "path": "^recipe/b\\\\.json$"}]}""")
                .data("minecraft", "recipe/b.json", "y b"), layout, "y");
        world.worldPack(TestPack.create().data("minecraft", "recipe/a.json", "z a"), layout, "z");

        assertEquals(Map.of("minecraft:a", "z a", "minecraft:b", "y b", "minecraft:c", "x c"), contents(world, RECIPES));
        assertEquals(1, load(world, Topic.json("loot_table")).files().size());
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void filterRulesFollowVanillaQuirks(Layout layout) {
        var world = new TestWorld(tempDir)
                .vanilla("minecraft", "recipe/a.json", "a")
                .vanilla("minecraft", "recipe/b.json", "b")
                .vanilla("minecraft", "loot_table/recipes.json", "loot");
        // 原版数据只读取 minecraft 命名空间，other 命名空间放在优先级低于 x 的世界数据包中
        world.worldPack(TestPack.create().data("other", "recipe/b.json", "other b"), layout, "a_other");
        // 命名空间由第一条规则满足，路径由第二条规则满足；第二条规则的 path 只是部分匹配
        world.worldPack(TestPack.create("""
                        "filter": {"block": [{"namespace": "minecraft", "path": "never"}, {"namespace": "nothing", "path": "b"}]}"""),
                layout, "x");

        var recipes = load(world, RECIPES).files().keySet().stream().map(Key::asString).toList();
        assertEquals(List.of("minecraft:a", "other:b"), recipes);

        world.worldPack(TestPack.create("""
                "filter": {"block": [{"path": "recipe"}]}"""), layout, "y");
        assertTrue(load(world, RECIPES).files().isEmpty());
        assertTrue(load(world, Topic.json("loot_table")).files().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void layeredViewKeepsEveryVersion(Layout layout) {
        var world = new TestWorld(tempDir).vanilla("minecraft", "recipe/a.json", "vanilla a");
        var x = world.worldPack(TestPack.create().data("minecraft", "recipe/a.json", "x a"), layout, "x");
        var y = world.worldPack(TestPack.create().data("minecraft", "recipe/a.json", "y a"), layout, "y");

        TopicLayers layers = load(world, RECIPE_LAYERS);
        assertEquals(List.of("vanilla", x, y), layers.layers().get(Key.key("minecraft:a")).stream()
                .map(resource -> resource.pack().id()).toList());

        world.worldPack(TestPack.create("""
                        "filter": {"block": [{"path": "recipe/a"}]}""").data("minecraft", "recipe/b.json", "z b"),
                layout, "z");
        layers = load(world, RECIPE_LAYERS);
        assertEquals(List.of(Key.key("minecraft:b")), List.copyOf(layers.layers().keySet()));
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void otherExtensionsAreIgnoredAndInvalidNamesFail(Layout layout) {
        var world = new TestWorld(tempDir)
                .vanilla("minecraft", "recipe/a.json", "a")
                .vanilla("minecraft", "recipe/README.md", "ignored")
                .vanilla("minecraft", "recipe_extra/b.json", "different directory");
        assertEquals(List.of(Key.key("minecraft:a")), List.copyOf(load(world, RECIPES).files().keySet()));

        world.worldPack(TestPack.create().data("minecraft", "recipe/Bad.json", "{}"), layout, "x");
        var exception = assertThrows(LoadFailedException.class, () -> load(world, RECIPES));
        assertInstanceOf(PackStructureException.class, exception.getCause());
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void invalidNamespaceDirectoryFails(Layout layout) {
        var world = new TestWorld(tempDir);
        world.worldPack(TestPack.create().data("Upper", "recipe/a.json", "{}"), layout, "x");
        var exception = assertThrows(LoadFailedException.class, () -> load(world, RECIPES));
        assertInstanceOf(PackStructureException.class, exception.getCause());
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void fileDirectlyUnderDataFails(Layout layout) {
        var world = new TestWorld(tempDir);
        world.worldPack(TestPack.create().file("data/stray.json", "{}"), layout, "x");
        var exception = assertThrows(LoadFailedException.class, () -> load(world, RECIPES));
        assertInstanceOf(PackStructureException.class, exception.getCause());
    }
}
