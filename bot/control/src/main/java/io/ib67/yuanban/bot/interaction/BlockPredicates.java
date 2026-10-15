package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.registry.TagTable;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.AdventureModePredicate;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.HolderSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冒险模式的 {@code can_break}（原版 {@code AdventureModePredicate} 与 {@code BlockPredicate}）：任意一个谓词匹配即为匹配。
 * 每个谓词依次检查方块集合、方块状态属性，然后在有 NBT 或组件条件时要求该位置有方块实体。
 */
public final class BlockPredicates {
    private static final Key BLOCK_REGISTRY = Key.key("block");
    /**
     * 方块编号 → 属性名称 → 按原版顺序排列的全部取值
     */
    private static final Map<Integer, Map<String, List<String>>> PROPERTY_VALUES = new ConcurrentHashMap<>();

    private BlockPredicates() {
    }

    /**
     * @param blockEntity 该位置方块实体的 NBT；没有方块实体时为 null
     * @param position    方块的位置
     * @throws UnsupportedOperationException 检查到带有组件条件的谓词
     */
    public static boolean test(AdventureModePredicate predicate, Block block, @Nullable NbtMap blockEntity, Point position,
                               TagTable tags) {
        for (var each : predicate.getPredicates()) {
            if (matches(each, block, blockEntity, position, tags)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(AdventureModePredicate.BlockPredicate predicate, Block block, @Nullable NbtMap blockEntity,
                                   Point position, TagTable tags) {
        if (predicate.getBlocks() != null && !contains(predicate.getBlocks(), block, tags)) {
            return false;
        }
        if (predicate.getProperties() != null && !matchesProperties(predicate.getProperties(), block)) {
            return false;
        }
        var components = predicate.getComponents();
        var hasComponents = !components.exactMatchers().isEmpty() || !components.partialMatchers().isEmpty();
        if (predicate.getNbt() == null && !hasComponents) {
            return true;
        }
        if (blockEntity == null) {
            return false;
        }
        if (predicate.getNbt() != null && !NbtCompare.contains(predicate.getNbt(), fullMetadata(block, blockEntity, position))) {
            return false;
        }
        if (hasComponents) {
            // TODO(T4): 组件条件需要按持久化格式解码方块实体中的组件，再按原版 DataComponentMatchers 的规则比较
            throw new UnsupportedOperationException("block predicates with component conditions are not supported: " + predicate);
        }
        return true;
    }

    private static boolean contains(HolderSet blocks, Block block, TagTable tags) {
        var tag = blocks.getLocation();
        if (tag != null) {
            return tags.contains(BLOCK_REGISTRY, tag, block.id());
        }
        return Objects.requireNonNull(blocks.getHolders()).contains(block.id());
    }

    /**
     * 原版 {@code StatePropertiesPredicate}：方块没有这个属性、给出的值不是它的取值时不匹配；精确值要求相等，
     * 范围按取值的顺序比较（布尔值 false 小于 true，其余按原版取值的顺序：整数从小到大，枚举按声明顺序）
     */
    private static boolean matchesProperties(List<AdventureModePredicate.PropertyMatcher> matchers, Block block) {
        var values = PROPERTY_VALUES.computeIfAbsent(block.id(), _ -> propertyValues(block));
        for (var matcher : matchers) {
            var possible = values.get(matcher.getName());
            if (possible == null) {
                return false;
            }
            var current = Objects.requireNonNull(block.getProperty(matcher.getName()));
            if (matcher.getValue() != null) {
                if (!possible.contains(matcher.getValue()) || !matcher.getValue().equals(current)) {
                    return false;
                }
                continue;
            }
            if (matcher.getMinValue() != null
                    && (!possible.contains(matcher.getMinValue()) || compare(possible, current, matcher.getMinValue()) < 0)) {
                return false;
            }
            if (matcher.getMaxValue() != null
                    && (!possible.contains(matcher.getMaxValue()) || compare(possible, current, matcher.getMaxValue()) > 0)) {
                return false;
            }
        }
        return true;
    }

    private static int compare(List<String> possible, String left, String right) {
        if (possible.size() == 2 && possible.contains("true") && possible.contains("false")) {
            return Boolean.compare(Boolean.parseBoolean(left), Boolean.parseBoolean(right));
        }
        return Integer.compare(possible.indexOf(left), possible.indexOf(right));
    }

    /**
     * 按状态编号的顺序收集每个属性的取值：原版按属性取值的顺序生成方块状态，所以第一次出现的顺序就是取值的顺序
     */
    private static Map<String, List<String>> propertyValues(Block block) {
        var result = new LinkedHashMap<String, List<String>>();
        for (var state : block.possibleStates()) {
            state.properties().forEach((name, value) -> {
                var list = result.computeIfAbsent(name, _ -> new ArrayList<>());
                if (!list.contains(value)) {
                    list.add(value);
                }
            });
        }
        var frozen = new LinkedHashMap<String, List<String>>();
        result.forEach((name, list) -> frozen.put(name, List.copyOf(list)));
        return Map.copyOf(frozen);
    }

    /**
     * 原版与 {@code BlockEntity.saveWithFullMetadata} 的结果比较，它在方块实体的数据之外还带有类型与坐标
     */
    private static NbtMap fullMetadata(Block block, NbtMap blockEntity, Point position) {
        var type = Objects.requireNonNull(block.blockEntityType(), () -> block + " has no block entity");
        return blockEntity.toBuilder()
                .putString("id", type.key().asString())
                .putInt("x", position.blockX())
                .putInt("y", position.blockY())
                .putInt("z", position.blockZ())
                .build();
    }
}
