package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.predicate.StatePropertiesPredicate;
import net.minestom.server.instance.block.Block;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原版 {@code StatePropertiesPredicate} 的求值。原版的属性对象带有类型（整数、布尔、枚举），Minestom 的方块状态只有
 * 字符串形式的属性，这里由该方块全部状态中出现的取值推出属性的类型：取值都是 {@code true}/{@code false} 时为布尔属性，
 * 都是整数时为整数属性，其余为枚举属性。
 */
final class StatePredicates {
    private static final Map<Integer, Map<String, Property>> BLOCK_PROPERTIES = new ConcurrentHashMap<>();

    private StatePredicates() {
    }

    /**
     * 属性的类型与取值。整数属性的取值是连续的整数，与原版 {@code IntegerProperty} 相同。
     */
    record Property(Kind kind, List<String> values) {
        /**
         * 原版 {@code Property#getValue(String)}：名字不是该属性的取值时为空。
         */
        Optional<Comparable<?>> parse(String name) {
            return switch (kind) {
                case BOOLEAN -> "true".equals(name) ? Optional.of(true) : "false".equals(name) ? Optional.of(false) : Optional.empty();
                case INTEGER -> {
                    int value;
                    try {
                        value = Integer.parseInt(name);
                    } catch (NumberFormatException notNumber) {
                        // 原版 IntegerProperty#getValue 捕获这个异常，返回空
                        yield Optional.empty();
                    }
                    yield values.contains(Integer.toString(value)) ? Optional.of(value) : Optional.empty();
                }
                case ENUM -> values.contains(name) ? Optional.of(name) : Optional.empty();
            };
        }
    }

    enum Kind {
        BOOLEAN, INTEGER, ENUM
    }

    /**
     * 方块的属性定义，按方块缓存。
     */
    static Map<String, Property> properties(Block block) {
        return BLOCK_PROPERTIES.computeIfAbsent(block.id(), _ -> definition(block));
    }

    private static Map<String, Property> definition(Block block) {
        var values = new LinkedHashMap<String, LinkedHashSet<String>>();
        for (var state : block.possibleStates()) {
            state.properties().forEach((name, value) -> values.computeIfAbsent(name, _ -> new LinkedHashSet<>()).add(value));
        }
        var result = new LinkedHashMap<String, Property>();
        values.forEach((name, set) -> result.put(name, new Property(kindOf(set), List.copyOf(set))));
        return Map.copyOf(result);
    }

    private static Kind kindOf(LinkedHashSet<String> values) {
        if (values.stream().allMatch(value -> value.equals("true") || value.equals("false"))) {
            return Kind.BOOLEAN;
        }
        if (values.stream().allMatch(value -> value.matches("-?[0-9]+"))) {
            return Kind.INTEGER;
        }
        return Kind.ENUM;
    }

    /**
     * 原版 {@code StatePropertiesPredicate#matches(BlockState)}。
     */
    static boolean matches(StatePropertiesPredicate predicate, Block state) {
        return matches(predicate, properties(state), state.properties());
    }

    /**
     * 原版 {@code StatePropertiesPredicate#matches(StateDefinition, StateHolder)}：每个属性都满足时通过；
     * 状态定义中没有该属性时不通过。
     */
    static boolean matches(StatePropertiesPredicate predicate, Map<String, Property> definition, Map<String, String> state) {
        for (var matcher : predicate.properties()) {
            var property = definition.get(matcher.name());
            if (property == null || !matches(matcher.valueMatcher(), property, state.get(matcher.name()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(StatePropertiesPredicate.ValueMatcher matcher, Property property, String actualName) {
        Comparable<?> actual = property.parse(actualName).orElseThrow();
        return switch (matcher) {
            case StatePropertiesPredicate.Exact exact -> {
                var expected = property.parse(exact.value());
                yield expected.isPresent() && actual.equals(expected.get());
            }
            case StatePropertiesPredicate.Ranged ranged -> {
                if (ranged.min().isPresent()) {
                    var min = property.parse(ranged.min().get());
                    if (min.isEmpty() || compare(property, actual, min.get()) < 0) {
                        yield false;
                    }
                }
                if (ranged.max().isPresent()) {
                    var max = property.parse(ranged.max().get());
                    if (max.isEmpty() || compare(property, actual, max.get()) > 0) {
                        yield false;
                    }
                }
                yield true;
            }
        };
    }

    private static int compare(Property property, Comparable<?> actual, Comparable<?> bound) {
        return switch (property.kind()) {
            case BOOLEAN -> Boolean.compare((Boolean) actual, (Boolean) bound);
            case INTEGER -> Integer.compare((Integer) actual, (Integer) bound);
            // TODO(minestom): 原版按枚举常量的声明顺序比较枚举属性的取值，Minestom 的方块状态只有取值的名字，没有声明顺序
            case ENUM -> throw LootEvaluation.unsupported("range matching on the enum block state property with values " + property.values());
        };
    }
}
