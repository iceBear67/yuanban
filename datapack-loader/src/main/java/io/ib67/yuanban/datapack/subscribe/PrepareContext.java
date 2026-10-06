package io.ib67.yuanban.datapack.subscribe;

import io.ib67.yuanban.datapack.topic.Topic;
import net.kyori.adventure.key.Key;

import java.util.Set;
import java.util.concurrent.ForkJoinPool;

/**
 * 准备阶段可以使用的内容。
 */
public interface PrepareContext {
    /**
     * 读取订阅中声明过的 topic，没有声明的 topic 抛出 {@link IllegalStateException}。
     */
    <C> C contents(Topic<C> topic);

    /**
     * 读取 {@code after} 中声明过的订阅者的准备结果，没有声明的订阅者抛出 {@link IllegalStateException}。
     */
    <D> D dependency(SubscriberKey<D> key);

    Set<Key> enabledFeatures();

    LoadKind kind();

    /**
     * 订阅者内部需要拆分并行任务的时候使用。
     */
    ForkJoinPool executor();
}
