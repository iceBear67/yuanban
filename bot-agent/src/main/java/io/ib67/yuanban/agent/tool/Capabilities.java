package io.ib67.yuanban.agent.tool;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * 一组工具能力。模式：{@code *} 全部工具；{@code observe.*} 一层的全部工具；{@code locomotion.lookAt} 一个工具；
 * 不带点的名称（例如 {@code command}）匹配最后一段为这个名称的工具（{@code external.command}）。
 */
public final class Capabilities {
    public static final Capabilities ALL = of(List.of("*"));
    public static final Capabilities NONE = of(List.of());

    private final Predicate<String> predicate;
    private final String description;

    private Capabilities(Predicate<String> predicate, String description) {
        this.predicate = predicate;
        this.description = description;
    }

    public static Capabilities of(Collection<String> patterns) {
        var compiled = patterns.stream().map(Capabilities::compile).toList();
        return new Capabilities(tool -> compiled.stream().anyMatch(pattern -> pattern.test(tool)),
                String.join(",", patterns));
    }

    public boolean allows(String tool) {
        return predicate.test(tool);
    }

    /**
     * 两者都允许的工具
     */
    public Capabilities and(Capabilities other) {
        return new Capabilities(predicate.and(other.predicate), "(" + description + ")&(" + other.description + ")");
    }

    /**
     * 这一组允许、denied 不允许的工具
     */
    public Capabilities without(Capabilities denied) {
        return new Capabilities(predicate.and(denied.predicate.negate()), "(" + description + ")-(" + denied.description + ")");
    }

    @Override
    public String toString() {
        return description;
    }

    private static Predicate<String> compile(String pattern) {
        if (pattern.equals("*")) {
            return _ -> true;
        }
        if (pattern.endsWith(".*")) {
            var prefix = pattern.substring(0, pattern.length() - 1);
            return tool -> tool.startsWith(prefix);
        }
        if (!pattern.contains(".")) {
            var suffix = "." + pattern;
            return tool -> tool.endsWith(suffix);
        }
        return pattern::equals;
    }
}
