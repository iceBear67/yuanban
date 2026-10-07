package io.ib67.yuanban.loot.context;

import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * {@link LootContext} 的实现；对应原版 {@code LootContext.Builder(LootParams).create(...)} 的产物。
 * <p>
 * 运气在构造时按原版 {@code LootParams$Builder#getLuck} 的规则经
 * {@link LootEnvironment#luckOf(Player, ItemStack, Key)} 计算：玩家运气属性和钓竿
 * {@code fishing_luck_bonus} 的求和规则由宿主实现，求值模块只转发玩家与工具。
 */
public final class LootContextImpl implements LootContext {
    private final ServerProcess process;
    private final Key contextType;
    private final Map<ContextKey<?>, Object> params;
    private final float luck;
    private final Random random;

    /**
     * 经环境构造：{@code process()} 取 {@code environment.process()}，运气按
     * {@link LootEnvironment#luckOf} 计算。{@code tool} 缺省时读取
     * {@link ContextKey#TOOL} 参数的值（与原版工具栏位一致）。
     */
    public LootContextImpl(LootEnvironment environment, Key contextType,
                           Map<ContextKey<?>, Object> params,
                           @Nullable Player luckPlayer, @Nullable ItemStack tool, Random random) {
        Objects.requireNonNull(environment, "environment");
        this.process = environment.process();
        this.contextType = Objects.requireNonNull(contextType, "contextType");
        this.params = Map.copyOf(Objects.requireNonNull(params, "params"));
        this.random = Objects.requireNonNull(random, "random");
        var effectiveTool = tool != null ? tool : (ItemStack) this.params.get(ContextKey.TOOL);
        this.luck = environment.luckOf(luckPlayer, effectiveTool, contextType);
    }

    /**
     * 显式给出进程与运气的构造，供求值器派生稳定序列上下文和宿主自己组装的场景使用。
     */
    public LootContextImpl(ServerProcess process, Key contextType, Map<ContextKey<?>, Object> params,
                           float luck, Random random) {
        this.process = Objects.requireNonNull(process, "process");
        this.contextType = Objects.requireNonNull(contextType, "contextType");
        this.params = Map.copyOf(Objects.requireNonNull(params, "params"));
        this.random = Objects.requireNonNull(random, "random");
        this.luck = luck;
    }

    @Override
    public Set<ContextKey<?>> params() {
        return Collections.unmodifiableSet(params.keySet());
    }

    @Override
    public <T> T require(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        var value = params.get(key);
        if (value == null) {
            throw new NoSuchElementException(key.id().asString());
        }
        @SuppressWarnings("unchecked")
        var typed = (T) value;
        return typed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T find(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        return (T) params.get(key);
    }

    @Override
    public float luck() {
        return luck;
    }

    @Override
    public Key contextType() {
        return contextType;
    }

    @Override
    public ServerProcess process() {
        return process;
    }

    @Override
    public Random random() {
        return random;
    }

    /**
     * 求值器内部使用的派生副本：替换随机数源（稳定序列），其余内容不变。
     */
    public LootContextImpl withRandom(Random derived) {
        return new LootContextImpl(process, contextType, params, luck, derived);
    }
}
