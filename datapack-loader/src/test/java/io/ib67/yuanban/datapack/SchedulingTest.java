package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.TestPack.Layout;
import io.ib67.yuanban.datapack.selection.Placement;
import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.LoadKind;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Resource;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.datapack.topic.TopicFiles;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulingTest {
    private static final Topic.Single RECIPES = Topic.json("recipe");
    private static final Topic.Single FUNCTIONS = Topic.single("function", ".mcfunction");

    @TempDir
    Path tempDir;

    private static String read(Resource resource) throws IOException {
        return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void appliesInDependencyOrderAndSharesResults() {
        var world = new TestWorld(tempDir).vanilla("minecraft", "recipe/a.json", "{}");
        var log = new CopyOnWriteArrayList<String>();
        SubscriberKey<String> first = SubscriberKey.of("test:first");
        SubscriberKey<String> second = SubscriberKey.of("test:second");
        SubscriberKey<String> third = SubscriberKey.of("test:third");
        SubscriberKey<String> independent = SubscriberKey.of("test:independent");
        var thirdSubscriber = new TestSubscriber<String>("third", log,
                context -> context.dependency(second) + "+third");
        var secondSubscriber = new TestSubscriber<String>("second", log,
                context -> context.dependency(first) + "+second");
        var firstSubscriber = new TestSubscriber<String>("first", log, context -> "first");
        var independentSubscriber = new TestSubscriber<String>("independent", log, context -> "independent");

        try (var ignored = world.loader()
                .subscribe(Subscription.builder(third, thirdSubscriber).lifecycle(Lifecycle.RELOADABLE).after(second).build())
                .subscribe(Subscription.builder(independent, independentSubscriber).lifecycle(Lifecycle.RELOADABLE).build())
                .subscribe(Subscription.builder(second, secondSubscriber).lifecycle(Lifecycle.STARTUP).after(first).build())
                .subscribe(Subscription.builder(first, firstSubscriber).lifecycle(Lifecycle.RELOADABLE).build())
                .build()
                .start(TestWorld.createWorld())) {
            assertEquals(List.of("apply independent", "apply first", "apply second", "apply third"),
                    log.stream().filter(line -> line.startsWith("apply")).toList());
            assertEquals("first+second+third", thirdSubscriber.last());
            assertEquals(LoadKind.INITIAL, thirdSubscriber.contexts.getFirst().kind());
        }
    }

    @Test
    void subscribersShareTopicViews() {
        var world = new TestWorld(tempDir).vanilla("minecraft", "recipe/a.json", "{}");
        var a = new TestSubscriber<TopicFiles>(context -> context.contents(RECIPES));
        var b = new TestSubscriber<TopicFiles>(context -> context.contents(Topic.json("recipe")));
        try (var ignored = world.loader()
                .subscribe(Subscription.builder(SubscriberKey.<TopicFiles>of("test:a"), a).lifecycle(Lifecycle.RELOADABLE).topic(RECIPES).build())
                .subscribe(Subscription.builder(SubscriberKey.<TopicFiles>of("test:b"), b).lifecycle(Lifecycle.RELOADABLE).topic(RECIPES).build())
                .build()
                .start(TestWorld.createWorld())) {
            assertSame(a.last(), b.last());
        }
    }

    @Test
    void undeclaredTopicsAndDependenciesAreRejected() {
        var world = new TestWorld(tempDir);
        var reader = new TestSubscriber<TopicFiles>(context -> context.contents(RECIPES));
        var loader = world.loader()
                .subscribe(Subscription.builder(SubscriberKey.<TopicFiles>of("test:reader"), reader).lifecycle(Lifecycle.RELOADABLE).build())
                .build();
        assertInstanceOf(IllegalStateException.class, assertThrows(LoadFailedException.class,
                () -> loader.start(TestWorld.createWorld())).getCause());

        SubscriberKey<String> other = SubscriberKey.of("test:other");
        var dependent = new TestSubscriber<String>(context -> context.dependency(other));
        var loader2 = world.loader()
                .subscribe(Subscription.builder(other, new TestSubscriber<String>(context -> "x")).lifecycle(Lifecycle.RELOADABLE).build())
                .subscribe(Subscription.builder(SubscriberKey.<String>of("test:dependent"), dependent).lifecycle(Lifecycle.RELOADABLE).build())
                .build();
        assertInstanceOf(IllegalStateException.class, assertThrows(LoadFailedException.class,
                () -> loader2.start(TestWorld.createWorld())).getCause());
    }

    @Test
    void invalidSubscriptionsFailToBuild() {
        var world = new TestWorld(tempDir);
        SubscriberKey<String> a = SubscriberKey.of("test:a");
        SubscriberKey<String> b = SubscriberKey.of("test:b");
        SubscriberKey<String> c = SubscriberKey.of("test:c");
        var subscriber = new TestSubscriber<String>(context -> "x");

        var cycle = assertThrows(SubscriptionException.class, () -> world.loader()
                .subscribe(Subscription.builder(a, subscriber).lifecycle(Lifecycle.RELOADABLE).after(b).build())
                .subscribe(Subscription.builder(b, subscriber).lifecycle(Lifecycle.RELOADABLE).after(c).build())
                .subscribe(Subscription.builder(c, subscriber).lifecycle(Lifecycle.RELOADABLE).after(a).build())
                .build());
        assertTrue(cycle.getMessage().contains("test:a -> test:b -> test:c -> test:a"), cycle.getMessage());

        assertThrows(SubscriptionException.class, () -> world.loader()
                .subscribe(Subscription.builder(a, subscriber).lifecycle(Lifecycle.RELOADABLE).build())
                .subscribe(Subscription.builder(a, subscriber).lifecycle(Lifecycle.RELOADABLE).build())
                .build());
        assertThrows(SubscriptionException.class, () -> world.loader()
                .subscribe(Subscription.builder(a, subscriber).lifecycle(Lifecycle.RELOADABLE).after(b).build())
                .build());
        for (var topic : List.<Topic<?>>of(Topic.json(""), Topic.json("/recipe"), Topic.json("recipe/"), Topic.json("a//b"),
                Topic.json("a/../b"), Topic.json("Recipe"), Topic.single("recipe", "json"), Topic.single("recipe", "."))) {
            assertThrows(SubscriptionException.class, () -> world.loader()
                    .subscribe(Subscription.builder(a, subscriber).lifecycle(Lifecycle.RELOADABLE).topic(topic).build())
                    .build(), topic.toString());
        }
        assertThrows(IllegalStateException.class, () -> Subscription.builder(a, subscriber).build());
    }

    @Test
    void prepareFailureAppliesNothingAndClosesTheNewBatch() {
        var world = new TestWorld(tempDir);
        world.worldPack(TestPack.create().data("minecraft", "recipe/a.json", "zip content"), Layout.ZIP, "x");
        SubscriberKey<TopicFiles> capturedKey = SubscriberKey.of("test:captured");
        var captured = new TestSubscriber<TopicFiles>(context -> context.contents(RECIPES));
        // 依赖 captured，保证 captured 的准备阶段一定先完成
        var failing = new TestSubscriber<String>(context -> {
            assertEquals("zip content", read(context.dependency(capturedKey).find(Key.key("minecraft:a")).orElseThrow()));
            throw new IllegalArgumentException("broken");
        });
        var loader = world.loader()
                .subscribe(Subscription.builder(capturedKey, captured).lifecycle(Lifecycle.RELOADABLE).topic(RECIPES).build())
                .subscribe(Subscription.builder(SubscriberKey.<String>of("test:failing"), failing).lifecycle(Lifecycle.RELOADABLE).after(capturedKey).build())
                .build();
        var exception = assertThrows(LoadFailedException.class, () -> loader.start(TestWorld.createWorld()));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("test:failing"), exception.getMessage());
        assertTrue(captured.applied.isEmpty());
        assertTrue(failing.applied.isEmpty());
        assertEquals(1, captured.contexts.size());
        assertEquals(1, failing.contexts.size());
        var resource = captured.contexts.getFirst().contents(RECIPES).find(Key.key("minecraft:a")).orElseThrow();
        assertThrows(ClosedFileSystemException.class, resource::readAllBytes);
    }

    @Test
    void reloadRunsReloadableSubscribersWithRetainedStartupResults() throws IOException {
        var world = new TestWorld(tempDir)
                .vanilla("minecraft", "recipe/a.json", "vanilla a")
                .vanilla("minecraft", "function/load.mcfunction", "say hi");
        var w = world.worldPack(TestPack.create().data("minecraft", "function/w.mcfunction", "say w"), Layout.ZIP, "w");
        SubscriberKey<String> registry = SubscriberKey.of("test:registry");
        SubscriberKey<TopicFiles> recipes = SubscriberKey.of("test:recipes");
        var registryCalls = new CopyOnWriteArrayList<String>();
        var registrySubscriber = new TestSubscriber<String>(context -> {
            registryCalls.add("prepared");
            return "registry v" + registryCalls.size();
        });
        var recipeSubscriber = new TestSubscriber<TopicFiles>(context -> {
            assertEquals("registry v1", context.dependency(registry));
            return context.contents(RECIPES);
        });
        var functionSubscriber = new TestSubscriber<TopicFiles>(context -> context.contents(FUNCTIONS));
        try (var session = world.loader()
                .subscribe(Subscription.builder(registry, registrySubscriber).lifecycle(Lifecycle.STARTUP).build())
                .subscribe(Subscription.builder(recipes, recipeSubscriber).lifecycle(Lifecycle.RELOADABLE).topic(RECIPES).after(registry).build())
                .subscribe(Subscription.builder(SubscriberKey.<TopicFiles>of("test:functions"), functionSubscriber).lifecycle(Lifecycle.STARTUP).topic(FUNCTIONS).build())
                .build()
                .start(TestWorld.createWorld())) {
            assertEquals(List.of("vanilla", w), session.selection().ids());
            assertEquals("say hi", read(functionSubscriber.last().find(Key.key("minecraft:load")).orElseThrow()));

            var x = world.worldPack(TestPack.create().data("minecraft", "recipe/a.json", "x a"), Layout.ZIP, "x");
            var result = session.reload(new ReloadRequest.Rescan());
            assertEquals(List.of("vanilla", w, x), result.selection().ids());
            assertTrue(result.closeFailure().isEmpty());
            assertEquals(List.of("prepared"), registryCalls);
            assertEquals(1, functionSubscriber.applied.size());
            assertEquals(2, recipeSubscriber.applied.size());
            assertEquals(LoadKind.RELOAD, recipeSubscriber.contexts.getLast().kind());
            assertEquals("x a", read(recipeSubscriber.last().find(Key.key("minecraft:a")).orElseThrow()));

            var disabled = session.editor().disable(x);
            assertTrue(session.reload(new ReloadRequest.Select(disabled)).closeFailure().isEmpty());
            assertEquals(List.of("vanilla", w), session.selection().ids());
            assertEquals(List.of(x), session.selection().config().disabled());
            assertEquals("vanilla a", read(recipeSubscriber.last().find(Key.key("minecraft:a")).orElseThrow()));
            // 第一次重载产生的批次已经关闭
            var old = recipeSubscriber.applied.get(1).find(Key.key("minecraft:a")).orElseThrow();
            assertThrows(ClosedFileSystemException.class, old::readAllBytes);

            session.reload(new ReloadRequest.Select(session.editor().enable(x, new Placement.First())));
            assertEquals(List.of(x, "vanilla", w), session.selection().ids());
            assertEquals("vanilla a", read(recipeSubscriber.last().find(Key.key("minecraft:a")).orElseThrow()));
            // STARTUP 订阅者提交的内容来自初始批次，初始批次在会话关闭之前保持打开
            assertEquals("say w", read(functionSubscriber.last().find(Key.key("minecraft:w")).orElseThrow()));
        }
        assertThrows(ClosedFileSystemException.class,
                () -> functionSubscriber.last().find(Key.key("minecraft:w")).orElseThrow().readAllBytes());
    }

    @Test
    void initialBatchIsClosedWithoutStartupSubscribers() throws IOException {
        var world = new TestWorld(tempDir);
        world.worldPack(TestPack.create().data("minecraft", "recipe/a.json", "x a"), Layout.ZIP, "x");
        var subscriber = new TestSubscriber<TopicFiles>(context -> context.contents(RECIPES));
        try (var session = world.loader()
                .subscribe(Subscription.builder(SubscriberKey.<TopicFiles>of("test:recipes"), subscriber).lifecycle(Lifecycle.RELOADABLE).topic(RECIPES).build())
                .build()
                .start(TestWorld.createWorld())) {
            var initial = subscriber.last().find(Key.key("minecraft:a")).orElseThrow();
            assertEquals("x a", read(initial));
            assertTrue(session.reload(new ReloadRequest.Rescan()).closeFailure().isEmpty());
            assertThrows(ClosedFileSystemException.class, initial::readAllBytes);
            assertEquals("x a", read(subscriber.last().find(Key.key("minecraft:a")).orElseThrow()));
        }
    }

    @Test
    void failedReloadKeepsTheCurrentBatch() throws IOException {
        var world = new TestWorld(tempDir);
        var x = world.worldPack(TestPack.create().data("minecraft", "recipe/a.json", "x a"), Layout.ZIP, "x");
        var fail = new AtomicBoolean();
        var subscriber = new TestSubscriber<TopicFiles>(context -> {
            if (fail.get()) {
                throw new IllegalStateException("reload broken");
            }
            return context.contents(RECIPES);
        });
        try (var session = world.loader()
                .subscribe(Subscription.builder(SubscriberKey.<TopicFiles>of("test:recipes"), subscriber).lifecycle(Lifecycle.RELOADABLE).topic(RECIPES).build())
                .build()
                .start(TestWorld.createWorld())) {
            var before = session.selection();
            fail.set(true);
            world.worldPack(TestPack.create(), Layout.DIRECTORY, "y");
            assertThrows(LoadFailedException.class, () -> session.reload(new ReloadRequest.Rescan()));
            assertSame(before, session.selection());
            assertEquals(1, subscriber.applied.size());
            assertEquals("x a", read(subscriber.last().find(Key.key("minecraft:a")).orElseThrow()));
            assertEquals(List.of("vanilla", x), session.selection().ids());

            assertThrows(SelectionException.class, () -> session.reload(new ReloadRequest.Select(List.of("vanilla", "nope"))));
            assertSame(before, session.selection());
        }
    }
}
