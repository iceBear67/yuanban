package io.ib67.yuanban.itemtag.internal;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.ib67.yuanban.datapack.tag.TagDeclaration;
import io.ib67.yuanban.datapack.tag.TagDeclarations;
import io.ib67.yuanban.datapack.tag.TagValue;
import io.ib67.yuanban.itemtag.ItemTagException;
import io.ib67.yuanban.itemtag.ItemTags;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按原版 {@code TagLoader.build} 展开标签，展开顺序复现原版 {@code DependencySorter}。
 */
@ApiStatus.Internal
public final class TagExpander {
    /**
     * 原版 {@code FileToIdConverter.listMatchingResourceStacks} 按文件位置排序：先比较路径
     * {@code tags/item/<路径>.json}，再比较命名空间。各标签共用目录前缀，只比较 {@code <路径>.json}。
     */
    private static final Comparator<Key> LOCATION_ORDER = Comparator
            .comparing((Key id) -> id.value() + ".json")
            .thenComparing(Key::namespace);

    private TagExpander() {
    }

    /**
     * @param elements 按物品 ID 查找物品，找不到返回 {@code null}
     * @throws ItemTagException 存在引用了不存在的物品或标签的必需条目
     */
    public static ItemTags expand(TagDeclarations declarations, Function<Key, @Nullable Material> elements) {
        // 原版 TagLoader.load 按文件位置的顺序放入 HashMap，build 再按这个 HashMap 的遍历顺序放入 DependencySorter
        var builders = new HashMap<TagId, TagDeclaration>();
        declarations.tags().values().stream()
                .sorted(Comparator.comparing(TagDeclaration::id, LOCATION_ORDER))
                .forEach(declaration -> builders.put(new TagId(declaration.id()), declaration));
        var contents = new HashMap<TagId, TagDeclaration>();
        builders.forEach(contents::put);

        Multimap<TagId, TagId> dependencies = HashMultimap.create();
        contents.forEach((id, declaration) -> forEachTagReference(declaration, true,
                dependency -> addDependencyIfNotCyclic(dependencies, id, dependency)));
        contents.forEach((id, declaration) -> forEachTagReference(declaration, false,
                dependency -> addDependencyIfNotCyclic(dependencies, id, dependency)));

        var expansion = new Expansion(elements, contents, dependencies);
        var visited = new HashSet<TagId>();
        for (var id : contents.keySet()) {
            expansion.visit(visited, id);
        }

        if (!expansion.failures.isEmpty()) {
            throw new ItemTagException(expansion.failures.entrySet().stream()
                    .map(failure -> "Couldn't load tag " + failure.getKey().asString()
                            + " as it is missing following references: "
                            + failure.getValue().stream()
                            .map(value -> value.entry() + " (from " + value.pack().id() + ")")
                            .collect(Collectors.joining(", ")))
                    .collect(Collectors.joining("\n")));
        }
        var result = new TreeMap<Key, List<Material>>();
        expansion.built.forEach(result::put);
        return new ItemTags(result);
    }

    private static void forEachTagReference(TagDeclaration declaration, boolean required, Consumer<TagId> output) {
        for (var value : declaration.values()) {
            var entry = value.entry();
            if (entry.tag() && entry.required() == required) {
                output.accept(new TagId(entry.id()));
            }
        }
    }

    private static boolean isCyclic(Multimap<TagId, TagId> dependencies, TagId from, TagId to) {
        var direct = dependencies.get(to);
        return direct.contains(from) || direct.stream().anyMatch(dependency -> isCyclic(dependencies, from, dependency));
    }

    private static void addDependencyIfNotCyclic(Multimap<TagId, TagId> dependencies, TagId from, TagId to) {
        if (!isCyclic(dependencies, from, to)) {
            dependencies.put(from, to);
        }
    }

    private static final class Expansion {
        private final Function<Key, @Nullable Material> elements;
        private final Map<TagId, TagDeclaration> contents;
        private final Multimap<TagId, TagId> dependencies;
        private final Map<Key, List<Material>> built = new HashMap<>();
        private final Map<Key, List<TagValue>> failures = new TreeMap<>();

        private Expansion(Function<Key, @Nullable Material> elements, Map<TagId, TagDeclaration> contents,
                          Multimap<TagId, TagId> dependencies) {
            this.elements = elements;
            this.contents = contents;
            this.dependencies = dependencies;
        }

        private void visit(Set<TagId> visited, TagId id) {
            if (!visited.add(id)) {
                return;
            }
            for (var dependency : dependencies.get(id)) {
                visit(visited, dependency);
            }
            var declaration = contents.get(id);
            if (declaration != null) {
                build(declaration);
            }
        }

        private void build(TagDeclaration declaration) {
            var values = new LinkedHashSet<Material>();
            var missing = new ArrayList<TagValue>();
            for (var value : declaration.values()) {
                var entry = value.entry();
                if (entry.tag()) {
                    var tag = built.get(entry.id());
                    if (tag != null) {
                        values.addAll(tag);
                    } else if (entry.required()) {
                        missing.add(value);
                    }
                } else {
                    var element = elements.apply(entry.id());
                    if (element != null) {
                        values.add(element);
                    } else if (entry.required()) {
                        missing.add(value);
                    }
                }
            }
            if (missing.isEmpty()) {
                built.put(declaration.id(), List.copyOf(values));
            } else {
                failures.put(declaration.id(), missing);
            }
        }
    }

    /**
     * 标签 ID。{@link #hashCode()} 和原版 {@code Identifier} 相同，{@link HashMap} 和 {@link HashMultimap}
     * 的遍历顺序因此和原版一致。
     */
    private record TagId(Key key) {
        @Override
        public int hashCode() {
            return 31 * key.namespace().hashCode() + key.value().hashCode();
        }
    }
}
