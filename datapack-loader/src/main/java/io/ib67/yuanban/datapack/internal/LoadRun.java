package io.ib67.yuanban.datapack.internal;

import io.ib67.yuanban.datapack.LoadFailedException;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.subscribe.LoadKind;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Topic;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 一次加载的索引、视图和准备阶段。这三步组成一张 {@link CompletableFuture} 依赖图，全部在执行器上运行，
 * 任务在前置任务全部完成之后才提交。任何一个任务失败，还没开始的任务直接放弃，
 * 全部任务结束之后关闭新批次并抛出 {@link LoadFailedException}。
 */
@ApiStatus.Internal
public final class LoadRun {
    private final ForkJoinPool pool;
    private final AtomicBoolean failed = new AtomicBoolean();
    private final ConcurrentLinkedQueue<TaskFailure> failures = new ConcurrentLinkedQueue<>();
    private final List<CompletableFuture<?>> tasks = new ArrayList<>();

    private LoadRun(ForkJoinPool pool) {
        this.pool = pool;
    }

    /**
     * @param packs        启用的数据包，优先级从低到高
     * @param participants 参与本次加载的订阅者，按提交顺序排列
     * @param retained     不参与本次加载、但被参与者依赖的订阅者的准备结果
     * @return 新批次和每个参与者的准备结果；调用者负责之后的提交和批次替换
     */
    public static Outcome prepare(List<Pack> packs,
                                  List<Subscription<?>> participants,
                                  Map<SubscriberKey<?>, Object> retained,
                                  Set<Key> enabledFeatures,
                                  LoadKind kind,
                                  ForkJoinPool pool) {
        return new LoadRun(pool).execute(packs, participants, retained, Set.copyOf(enabledFeatures), kind);
    }

    /**
     * 在调用线程上按顺序提交准备结果。
     */
    public static void apply(List<Subscription<?>> participants, Map<SubscriberKey<?>, Object> results) {
        for (var subscription : participants) {
            applyOne(subscription, results.get(subscription.key()));
        }
    }

    @SuppressWarnings("unchecked")
    private static <R> void applyOne(Subscription<R> subscription, Object result) {
        subscription.subscriber().apply((R) result);
    }

    private Outcome execute(List<Pack> packs,
                            List<Subscription<?>> participants,
                            Map<SubscriberKey<?>, Object> retained,
                            Set<Key> enabledFeatures,
                            LoadKind kind) {
        var batch = new Batch();

        var indexTasks = new ArrayList<CompletableFuture<PackIndex>>(packs.size());
        for (var pack : packs) {
            indexTasks.add(submit(List.of(), "数据包 " + pack.id(), () -> {
                var opened = OpenedPack.open(pack);
                batch.add(opened);
                return PackIndex.build(pack, opened.root());
            }));
        }
        var indexes = CompletableFuture.allOf(indexTasks.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> indexTasks.stream().map(CompletableFuture::join).toList());

        var views = new HashMap<Topic<?>, CompletableFuture<Object>>();
        for (var subscription : participants) {
            for (var topic : subscription.topics()) {
                views.computeIfAbsent(topic, key -> submit(List.of(indexes), "topic " + describe(key),
                        () -> ViewBuilder.build(indexes.join(), key)));
            }
        }

        var prepared = new LinkedHashMap<SubscriberKey<?>, CompletableFuture<Object>>();
        for (var subscription : participants) {
            var dependencies = new HashMap<SubscriberKey<?>, CompletableFuture<Object>>();
            for (var dependency : subscription.after()) {
                var future = prepared.get(dependency);
                if (future == null) {
                    future = CompletableFuture.completedFuture(Objects.requireNonNull(retained.get(dependency),
                            () -> "no retained result for " + dependency));
                }
                dependencies.put(dependency, future);
            }
            var topicViews = new HashMap<Topic<?>, CompletableFuture<Object>>();
            for (var topic : subscription.topics()) {
                topicViews.put(topic, views.get(topic));
            }
            var prerequisites = new ArrayList<CompletableFuture<?>>(dependencies.values());
            prerequisites.addAll(topicViews.values());
            var context = new Context(topicViews, dependencies, enabledFeatures, kind, pool);
            prepared.put(subscription.key(), submit(prerequisites, "订阅者 " + subscription.key(), () -> Objects.requireNonNull(
                    subscription.subscriber().prepare(context),
                    () -> "subscriber " + subscription.key() + " returned null from prepare")));
        }

        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).handle((ignored, error) -> null).join();

        if (!failures.isEmpty()) {
            var list = List.copyOf(failures);
            var exception = new LoadFailedException("加载失败：" + list.stream()
                    .map(failure -> failure.task() + "：" + failure.error())
                    .collect(Collectors.joining("；")), list.getFirst().error());
            list.stream().skip(1).forEach(failure -> exception.addSuppressed(failure.error()));
            try {
                batch.close();
            } catch (RuntimeException e) {
                exception.addSuppressed(e);
            }
            throw exception;
        }

        var results = new LinkedHashMap<SubscriberKey<?>, Object>();
        prepared.forEach((key, future) -> results.put(key, future.join()));
        return new Outcome(batch, results);
    }

    private <T> CompletableFuture<T> submit(List<? extends CompletableFuture<?>> prerequisites, String task, Callable<T> body) {
        var future = CompletableFuture.allOf(prerequisites.toArray(CompletableFuture[]::new))
                .thenApplyAsync(ignored -> {
                    if (failed.get()) {
                        throw new CancellationException(task + " skipped because another task failed");
                    }
                    try {
                        return body.call();
                    } catch (Throwable e) {
                        failed.set(true);
                        failures.add(new TaskFailure(task, e));
                        throw new CompletionException(e);
                    }
                }, pool);
        tasks.add(future);
        return future;
    }

    private static String describe(Topic<?> topic) {
        return topic.directory() + " (" + topic.extension() + ")";
    }

    public record Outcome(Batch batch, Map<SubscriberKey<?>, Object> results) {
    }

    private record TaskFailure(String task, Throwable error) {
    }

    private record Context(Map<Topic<?>, CompletableFuture<Object>> topics,
                           Map<SubscriberKey<?>, CompletableFuture<Object>> dependencies,
                           Set<Key> enabledFeatures,
                           LoadKind kind,
                           ForkJoinPool executor) implements PrepareContext {
        @Override
        @SuppressWarnings("unchecked")
        public <C> C contents(Topic<C> topic) {
            var view = topics.get(topic);
            if (view == null) {
                throw new IllegalStateException("topic " + describe(topic) + " is not declared by this subscription");
            }
            return (C) view.join();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <D> D dependency(SubscriberKey<D> key) {
            var result = dependencies.get(key);
            if (result == null) {
                throw new IllegalStateException("subscriber " + key + " is not declared in after()");
            }
            return (D) result.join();
        }
    }
}
