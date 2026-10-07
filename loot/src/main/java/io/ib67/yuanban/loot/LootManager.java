package io.ib67.yuanban.loot;

import io.ib67.yuanban.loot.entry.CompositeEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.entry.SingletonEntry;
import io.ib67.yuanban.loot.entry.TagEntry;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

/**
 * 当前生效的战利品表集合，按 {@link Key} 查询。
 * <p>
 * 快照整体替换：{@link #replaceAll(NavigableMap)} 先按 DESIGN 第 7 节校验新集合（引用存在、
 * 引用链不指向自身形成环、复合条目展开的总权重不越过 {@code Integer.MAX_VALUE}），校验失败抛出
 * {@link LootException}，当前集合不做任何修改。校验通过后快照整体切换，{@link #get(Key)}、
 * {@link #tables()} 读当前快照，可在任何线程调用；原先从 {@link #tables()} 拿到的快照对象
 * 在替换之后保持替换前的内容。
 * <p>
 * 本模块不向客户端发送任何数据包：战利品表是服务端数据，26.2 的注册表同步不包含它。
 */
public final class LootManager {
    private final LootTypeRegistry types;

    private volatile NavigableMap<Key, LootTable> tables = Collections.unmodifiableNavigableMap(new TreeMap<>());

    public LootManager(LootTypeRegistry types) {
        this.types = Objects.requireNonNull(types, "types");
    }

    /**
     * 整体替换当前生效的集合。校验失败抛出 {@link LootException}，当前集合保持不变。
     */
    public void replaceAll(NavigableMap<Key, LootTable> tables) {
        Objects.requireNonNull(tables, "tables");
        var snapshot = new TreeMap<Key, LootTable>();
        for (var entry : tables.entrySet()) {
            snapshot.put(Objects.requireNonNull(entry.getKey(), "table id"),
                    Objects.requireNonNull(entry.getValue(), "table"));
        }
        validate(snapshot);
        this.tables = Collections.unmodifiableNavigableMap(snapshot);
    }

    public Optional<LootTable> get(Key id) {
        return Optional.ofNullable(tables.get(id));
    }

    /**
     * 当前生效的表集合的快照，按 {@link Key} 的自然顺序排列；返回值不可修改，替换之后保持取到时的内容。
     */
    public NavigableMap<Key, LootTable> tables() {
        return tables;
    }

    public LootTypeRegistry types() {
        return types;
    }

    // 校验：DESIGN 第 7 节

    private static void validate(NavigableMap<Key, LootTable> tables) {
        // inspect 的结果在一次校验内复用
        var inspectCache = Collections.synchronizedMap(new WeakHashMap<LootTable, LootContents>());
        for (var entry : tables.entrySet()) {
            checkTable(entry.getKey(), entry.getValue(), tables, inspectCache);
        }
    }

    private static LootContents inspect(LootTable table, Map<LootTable, LootContents> cache) {
        return cache.computeIfAbsent(table, LootContents::inspect);
    }

    /**
     * 沿战利品表的引用链深搜：规则 2 的无环检查顺带完成规则 1 的存在性核对，
     * 每条引用链路径只走一遍；环、缺失引用、权重超限都在当前路径上报错。
     *
     * @param id         当前表报错时使用的 ID；内联表沿用宿主表的 ID
     * @param pathIds    从根到当前表的引用链上出现的表 ID，用来检出表引用成环
     * @param pathTables 引用链上出现的表对象（含内联表），用来检出内联表自引用
     */
    private static void checkTable(Key id, LootTable table, NavigableMap<Key, LootTable> tables,
                                   Map<LootTable, LootContents> inspectCache) {
        var pathIds = new ArrayDeque<Key>();
        var pathTables = Collections.newSetFromMap(new IdentityHashMap<LootTable, Boolean>());
        walk(id, table, tables, inspectCache, pathIds, pathTables);
    }

    private static void walk(Key id, LootTable table, NavigableMap<Key, LootTable> tables,
                             Map<LootTable, LootContents> inspectCache,
                             ArrayDeque<Key> pathIds, Set<LootTable> pathTables) {
        var contents = inspect(table, inspectCache);
        for (var ref : contents.tables()) {
            if (!tables.containsKey(ref)) {
                throw new LootException("Loot table " + id.asString()
                        + " references missing loot table: " + ref.asString());
            }
        }
        for (var pool : table.pools()) {
            for (var entry : pool.entries()) {
                checkExpandedWeight(id, entry);
            }
        }
        for (var ref : contents.tables()) {
            if (pathIds.contains(ref) || isDirectSelf(table, ref, id, tables)) {
                throw new LootException("Detected infinite loop in loot tables: "
                        + id.asString() + " -> " + ref.asString());
            }
            pathIds.addLast(ref);
            walk(ref, tables.get(ref), tables, inspectCache, pathIds, pathTables);
            pathIds.removeLast();
        }
        for (var nested : contents.nested()) {
            if (!pathTables.add(nested)) {
                throw new LootException("Detected infinite loop in loot tables: " + id.asString()
                        + " contains an inline loot table that resolves to itself");
            }
            walk(id, nested, tables, inspectCache, pathIds, pathTables);
            pathTables.remove(nested);
        }
    }

    /**
     * 表经 ID 直接指到自己：当前表以 {@code id} 的身份在集合里，且引用了 {@code id}。
     */
    private static boolean isDirectSelf(LootTable table, Key ref, Key id, NavigableMap<Key, LootTable> tables) {
        return ref.equals(id) && tables.get(id) == table;
    }

    /**
     * 校验规则 3：复合条目完全展开后总权重不越过 {@link Integer#MAX_VALUE}（原版 {@code WeightedRandom} 的上限）。
     * {@code tag(expand=true)} 展开成每个标签成员一个单例，成员数量在注册阶段未知，按本条目的
     * {@code weight} 计一次。
     */
    private static void checkExpandedWeight(Key id, LootEntry entry) {
        long total = 0;
        for (var weight : expand(entry)) {
            total = saturatedAdd(total, weight);
            if (total > Integer.MAX_VALUE) {
                throw new LootException("Loot pool of " + id.asString()
                        + " expands to a total weight above " + Integer.MAX_VALUE
                        + ", which WeightedRandom cannot draw from");
            }
        }
    }

    /**
     * 把复合条目展开成叶子条目的权重列表：{@code group} 全展开、{@code alternatives} 取全部候选
     * （注册阶段不知道条件，按全部满足计）、{@code sequence} 与 {@code group} 相同。
     */
    private static List<Long> expand(LootEntry entry) {
        // TagEntry 实现 SingletonEntry；expand=true 时展开在 LootItemFunction 求值阶段发生，
        // 在注册阶段的权重统计按单条记录处理。
        return switch (entry) {
            case SingletonEntry singleton -> List.of((long) singleton.weight());
            case CompositeEntry composite -> expandChildren(composite);
        };
    }

    private static List<Long> expandChildren(CompositeEntry composite) {
        var weights = new ArrayList<Long>();
        for (var child : composite.children()) {
            weights.addAll(expand(child));
        }
        return weights;
    }

    private static long saturatedAdd(long a, long b) {
        var sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }
}
