package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 记录每次准备和提交的订阅者。{@code log} 可以在多个订阅者之间共用，用来检查提交顺序。
 */
final class TestSubscriber<R> implements Subscriber<R> {
    private final String name;
    private final Prepare<R> prepare;
    private final List<String> log;
    final List<R> applied = new CopyOnWriteArrayList<>();
    final List<PrepareContext> contexts = new CopyOnWriteArrayList<>();

    TestSubscriber(String name, List<String> log, Prepare<R> prepare) {
        this.name = name;
        this.log = log;
        this.prepare = prepare;
    }

    TestSubscriber(Prepare<R> prepare) {
        this("subscriber", new CopyOnWriteArrayList<>(), prepare);
    }

    @Override
    public R prepare(PrepareContext context) throws Exception {
        contexts.add(context);
        log.add("prepare " + name);
        return prepare.prepare(context);
    }

    @Override
    public void apply(R prepared) {
        log.add("apply " + name);
        applied.add(prepared);
    }

    R last() {
        return applied.getLast();
    }

    @FunctionalInterface
    interface Prepare<R> {
        R prepare(PrepareContext context) throws Exception;
    }
}
