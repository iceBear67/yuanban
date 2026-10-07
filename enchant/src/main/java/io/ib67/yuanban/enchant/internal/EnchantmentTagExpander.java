package io.ib67.yuanban.enchant.internal;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.ib67.yuanban.datapack.tag.TagDeclaration;
import io.ib67.yuanban.datapack.tag.TagDeclarations;
import io.ib67.yuanban.enchant.EnchantmentException;
import io.ib67.yuanban.enchant.EnchantmentTags;
import net.kyori.adventure.key.Key;
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
import java.util.stream.Collectors;

/**
 * 按原版 {@code TagLoader.build} 展开附魔标签，展开顺序复现原版 {@code DependencySorter}。
 * 附魔标签的条目不解析成注册表条目；每个标签保存它引用的附魔 ID 集合。
 */
@ApiStatus.Internal
public final class EnchantmentTagExpander {
    /**
     * 原版 {@code FileToIdConverter.listMatchingResourceStacks} 按文件位置排序：先比较路径
     * {@code tags/enchantment/<路径>.json}，再比较命名空间。各标签共用目录前缀，只比较 {@code <路径>.json}。
     */
    private static final Comparator<Key> LOCATION_ORDER = Comparator
            .comparing((Key id) -> id.value() + ".json")
            .thenComparing(Key::namespace);

    private EnchantmentTagExpander() {
    }

    /**
     * @throws EnchantmentException 附魔标签之间有循环引用
     */
    public static EnchantmentTags expand(TagDeclarations declarations) {
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

        var expansion = new Expansion(contents, dependencies);
        var visited = new HashSet<TagId>();
        for (var id : contents.keySet()) {
            expansion.visit(visited, id);
        }

        var result = new TreeMap<Key, List<Key>>();
        expansion.built.forEach(result::put);
        return new EnchantmentTags(result);
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
        private final Map<TagId, TagDeclaration> contents;
        private final Multimap<TagId, TagId> dependencies;
        private final Map<Key, List<Key>> built = new HashMap<>();

        private Expansion(Map<TagId, TagDeclaration> contents, Multimap<TagId, TagId> dependencies) {
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
            var values = new LinkedHashSet<Key>();
            for (var value : declaration.values()) {
                var entry = value.entry();
                if (entry.tag()) {
                    var tag = built.get(entry.id());
                    if (tag == null) {
                        // 循环引用被 addDependencyIfNotCyclic 过滤之后，引用方认不到这个条目；原版在这种情况下跳过
                        throw new EnchantmentException("Couldn't load tag " + declaration.id().asString()
                                + " as it references missing tag " + entry.id().asString());
                    }
                    values.addAll(tag);
                } else {
                    values.add(entry.id());
                }
            }
            built.put(declaration.id(), List.copyOf(values));
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
