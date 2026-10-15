package io.ib67.yuanban.bot.observe.dispatch;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.ib67.yuanban.bot.observe.Observer;

/**
 * 已经交给 {@code observe} 或 {@code observeDuring} 注册的 observer。每个 observer 实例只能注册一次（按对象身份，弱引用）。
 */
public final class RegisteredObservers {
    private static final Cache<Observer, Boolean> REGISTERED = Caffeine.newBuilder().weakKeys().build();

    private RegisteredObservers() {
    }

    /**
     * 记为已注册
     *
     * @throws IllegalStateException observer 已经注册过
     */
    public static void mark(Observer observer) {
        if (REGISTERED.asMap().putIfAbsent(observer, Boolean.TRUE) != null) {
            throw new IllegalStateException("observer " + observer + " has already been registered");
        }
    }

    /**
     * bot 在注册生效之前已经结束：observer 不算注册过
     */
    public static void unmark(Observer observer) {
        REGISTERED.invalidate(observer);
    }

    /**
     * observer 是否已经交给 {@code observe} 或 {@code observeDuring} 注册（按对象身份）。
     * 内置 observer 只能在注册之前设置的方法（{@code limit}）据此拒绝注册之后的调用
     */
    public static boolean registered(Observer observer) {
        return REGISTERED.getIfPresent(observer) != null;
    }
}
