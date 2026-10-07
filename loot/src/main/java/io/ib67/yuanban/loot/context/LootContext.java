package io.ib67.yuanban.loot.context;

import net.minestom.server.ServerProcess;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.Set;

/**
 * 一次取用的运行环境，对应原版 {@code net.minecraft.world.level.storage.loot.LootContext}。
 * <p>
 * 原版 26.2 的 {@code net.minecraft.util.RandomSource} 在 Minestom 26.2 没有对应类型，本接口的
 * 随机数源统一使用 {@link Random}（{@code java.util.Random}）；宿主需要更具体的算法时传入它的子类
 * （例如 {@link java.util.random.RandomGeneratorFactory#of(String)} 的 {@code Xoroshiro128PlusPlus}）。
 */
public sealed interface LootContext permits LootContextImpl {

    /**
     * 上下文中已经设置的参数键集合，对应原版 {@code ContextMap} 里已有条目的键。
     * 上下文参数覆盖检查（DESIGN 8.4）按这个集合判定。
     */
    Set<ContextKey<?>> params();

    /**
     * 读取参数，缺失时按原版行为就地抛出 {@link java.util.NoSuchElementException}（原版
     * {@code ContextMap#getOrThrow}，消息是参数的命名空间 ID）。
     */
    <T> T require(ContextKey<T> key);

    /**
     * 读取参数，缺失时返回 {@code null}，对应原版 {@code ContextMap#getOptional}。
     */
    <T> @Nullable T find(ContextKey<T> key);

    /**
     * 运气值，对应原版 {@code LootContext#getLuck}（转发 {@code LootParams#getLuck}）。
     * 由 {@link LootContextImpl} 在构造时按 {@code LootEnvironment#luckOf} 计算。
     */
    float luck();

    /**
     * 取用的表所属的上下文类型（对应原版表的 {@code paramSet}），供运气计算使用。
     */
    net.kyori.adventure.key.Key contextType();

    ServerProcess process();

    /**
     * 本次取用的默认随机数源；表声明了 {@code random_sequence} 时求值器会改用按
     * {@link LootEnvironment#worldSeed()} 派生的稳定序列替换它（见 DESIGN 8.3 第 1 条）。
     */
    Random random();
}
