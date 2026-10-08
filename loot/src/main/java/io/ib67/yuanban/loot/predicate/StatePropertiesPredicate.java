package io.ib67.yuanban.loot.predicate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 方块或流体状态的属性谓词，对应原版 {@code net.minecraft.advancements.predicates.StatePropertiesPredicate}。
 * JSON 是属性名到取值的对象，取值是字符串（精确匹配）或 {@code {min?, max?}}（范围匹配）。
 *
 * @param properties 按 JSON 中的顺序
 */
public record StatePropertiesPredicate(List<PropertyMatcher> properties) {
    public StatePropertiesPredicate {
        properties = List.copyOf(properties);
    }

    /**
     * 原版 {@code StatePropertiesPredicate$PropertyMatcher}：状态中没有名为 {@code name} 的属性时不匹配。
     */
    public record PropertyMatcher(String name, ValueMatcher valueMatcher) {
        public PropertyMatcher {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(valueMatcher, "valueMatcher");
        }
    }

    /**
     * 原版 {@code StatePropertiesPredicate$ValueMatcher}。取值按属性的类型解析（原版 {@code Property#getValue}），
     * 解析失败时不匹配。
     */
    public sealed interface ValueMatcher {
    }

    /**
     * 原版 {@code ExactMatcher}：属性值等于 {@code value}。
     */
    public record Exact(String value) implements ValueMatcher {
        public Exact {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * 原版 {@code RangedMatcher}：属性值不小于 {@code min}、不大于 {@code max}，按属性值的自然顺序比较。
     */
    public record Ranged(Optional<String> min, Optional<String> max) implements ValueMatcher {
        public Ranged {
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
        }
    }
}
