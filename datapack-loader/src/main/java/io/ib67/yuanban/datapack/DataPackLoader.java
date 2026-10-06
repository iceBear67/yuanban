package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.internal.LoadRun;
import io.ib67.yuanban.datapack.internal.PackSelector;
import io.ib67.yuanban.datapack.internal.SubscriberGraph;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.provider.PackProvider;
import io.ib67.yuanban.datapack.selection.SelectionRequest;
import io.ib67.yuanban.datapack.subscribe.LoadKind;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;

/**
 * 框架的入口。构建之后订阅关系不再变化。
 */
public final class DataPackLoader {
    private final LoaderSettings settings;
    private final List<PackProvider> providers;
    private final SubscriberGraph graph;
    private final ForkJoinPool executor;

    private DataPackLoader(LoaderSettings settings, List<PackProvider> providers, SubscriberGraph graph, ForkJoinPool executor) {
        this.settings = settings;
        this.providers = providers;
        this.graph = graph;
        this.executor = executor;
    }

    public static Builder builder(LoaderSettings settings) {
        return new Builder(Objects.requireNonNull(settings, "settings"));
    }

    public LoaderSettings settings() {
        return settings;
    }

    /**
     * 发现数据包、选择启用列表、运行全部订阅者，返回会话。阻塞到全部 {@code apply} 执行完。
     *
     * @throws LoadFailedException 索引、视图或者准备任务失败；这时没有调用过任何 {@code apply}
     */
    public DataPackSession start(SelectionRequest request) {
        Objects.requireNonNull(request, "request");
        var available = discover();
        var selection = PackSelector.select(available, request, settings);
        var participants = graph.participants(LoadKind.INITIAL);
        var outcome = LoadRun.prepare(selection.packs(), participants, Map.of(), selection.enabledFeatures(),
                LoadKind.INITIAL, executor);
        // apply 抛出的异常原样传播：一部分订阅者已经提交，框架不尝试恢复
        LoadRun.apply(participants, outcome.results());
        return new DataPackSession(this, available, selection, outcome.batch(), graph.retainedResults(outcome.results()));
    }

    SortedMap<String, Pack> discover() {
        var available = new TreeMap<String, Pack>();
        for (var provider : providers) {
            provider.discover(settings, pack -> {
                var previous = available.putIfAbsent(pack.id(), pack);
                if (previous != null) {
                    throw new PackStructureException("数据包 ID " + pack.id() + " 重复");
                }
            });
        }
        return Collections.unmodifiableSortedMap(available);
    }

    SubscriberGraph graph() {
        return graph;
    }

    ForkJoinPool executor() {
        return executor;
    }

    public static final class Builder {
        private final LoaderSettings settings;
        private final List<PackProvider> providers = new ArrayList<>();
        private final List<Subscription<?>> subscriptions = new ArrayList<>();
        private @Nullable ForkJoinPool executor;

        private Builder(LoaderSettings settings) {
            this.settings = settings;
        }

        public Builder provider(PackProvider provider) {
            providers.add(Objects.requireNonNull(provider, "provider"));
            return this;
        }

        public Builder subscribe(Subscription<?> subscription) {
            subscriptions.add(Objects.requireNonNull(subscription, "subscription"));
            return this;
        }

        /**
         * 订阅者在准备阶段提交子任务并等待结果的时候，{@link ForkJoinPool} 会补充工作线程。
         */
        public Builder executor(ForkJoinPool executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        /**
         * @throws SubscriptionException key 重复、依赖未注册、依赖有环、topic 不合法
         */
        public DataPackLoader build() {
            if (executor == null) {
                throw new IllegalStateException("executor is not set");
            }
            return new DataPackLoader(settings, List.copyOf(providers), SubscriberGraph.build(List.copyOf(subscriptions)), executor);
        }
    }
}
