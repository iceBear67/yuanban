package io.ib67.yuanban.loot.testenv;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.LootEnvironment;
import io.ib67.yuanban.loot.context.ScoreboardView;
import io.ib67.yuanban.loot.context.SmeltingView;
import io.ib67.yuanban.loot.context.StorageView;
import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.ServerProcess;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 测试宿主：用内存中的记分板、命令存储、熔炼配方、动态掉落与静态运气表来满足
 * {@link LootEnvironment} 的最小接口。宿主侧接入（server runtime）应当用真实的服务端数据
 * 替换这里的 Map 视图，本实现仅给求值器测试与集成测试使用。
 */
public final class InMemoryLootEnvironment implements LootEnvironment {
    private final ServerProcess process;
    private final Map<Key, LootFunction> functions = new ConcurrentHashMap<>();
    private final Map<Key, LootCondition> conditions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> scores = new ConcurrentHashMap<>();
    private final Map<Key, CompoundBinaryTag> storage = new ConcurrentHashMap<>();
    private final Map<Material, ItemStack> smeltingResults = new ConcurrentHashMap<>();
    private final Map<Key, ItemStack[]> dynamicDrops = new ConcurrentHashMap<>();
    private final long worldSeed;
    private volatile float fixedLuck;

    public InMemoryLootEnvironment(ServerProcess process) {
        this(process, 0L);
    }

    public InMemoryLootEnvironment(ServerProcess process, long worldSeed) {
        this.process = Objects.requireNonNull(process, "process");
        this.worldSeed = worldSeed;
    }

    @Override
    public ServerProcess process() {
        return process;
    }

    @Override
    public ScoreboardView scoreboards() {
        return new ScoreboardView() {
            @Override
            public OptionalInt getScore(String name, String objective) {
                var byObjective = scores.get(name);
                if (byObjective == null) return OptionalInt.empty();
                var value = byObjective.get(objective);
                return value == null ? OptionalInt.empty() : OptionalInt.of(value);
            }

            @Override
            public Optional<String> getScoreboardName(@Nullable Entity entity) {
                if (entity == null) return Optional.empty();
                if (entity instanceof Player player) return Optional.of(player.getUsername());
                return Optional.of(entity.getUuid().toString());
            }
        };
    }

    @Override
    public StorageView storage() {
        return storageId -> storage.getOrDefault(storageId, CompoundBinaryTag.empty());
    }

    /**
     * 熔炼配方按输入的物品查表，每个物品最多一个配方，与原版熔炉配方以物品为输入的形式相同。
     */
    @Override
    public SmeltingView smelting() {
        return input -> Optional.ofNullable(smeltingResults.get(input.material()));
    }

    @Override
    public float luckOf(@Nullable Player player, @Nullable ItemStack tool, Key contextType) {
        return fixedLuck;
    }

    @Override
    public Stream<ItemStack> dynamicLoot(Key name) {
        var stacks = dynamicDrops.get(name);
        return stacks == null ? Stream.of() : Stream.of(stacks).map(s -> s.withAmount(s.amount()));
    }

    @Override
    public Optional<LootFunction> findFunction(Key id) {
        return Optional.ofNullable(functions.get(id));
    }

    @Override
    public Optional<LootCondition> findCondition(Key id) {
        return Optional.ofNullable(conditions.get(id));
    }

    @Override
    public long worldSeed() {
        return worldSeed;
    }

    // ------------------------------------------------------------------
    // 测试布置辅助
    // ------------------------------------------------------------------

    /**
     * 设置固定的运气值；宿主侧实际是 {@code player.getAttributeValue(Attribute.LUCK)}，
     * 测试只需要一个可控返回值。
     */
    public void setLuck(float luck) {
        this.fixedLuck = luck;
    }

    public void putFunction(Key id, LootFunction function) {
        functions.put(id, function);
    }

    public void putCondition(Key id, LootCondition condition) {
        conditions.put(id, condition);
    }

    public void putScore(String name, String objective, int value) {
        scores.computeIfAbsent(name, k -> new HashMap<>()).put(objective, value);
    }

    public void putStorage(Key id, CompoundBinaryTag tag) {
        storage.put(id, tag);
    }

    /**
     * 登记一个熔炼配方：输入 {@code input}，产出 {@code result}（含产出数量）。
     */
    public void putSmelting(Material input, ItemStack result) {
        smeltingResults.put(input, result);
    }

    public void putDynamicLoot(Key name, ItemStack... stacks) {
        dynamicDrops.put(name, stacks);
    }

    public void clearDynamicLoot(Key name) {
        dynamicDrops.remove(name);
    }

    public static ScoreboardView emptyScoreboards() {
        return new ScoreboardView() {
            @Override
            public OptionalInt getScore(String name, String objective) {
                return OptionalInt.empty();
            }

            @Override
            public Optional<String> getScoreboardName(@Nullable Entity entity) {
                return Optional.empty();
            }
        };
    }
}
