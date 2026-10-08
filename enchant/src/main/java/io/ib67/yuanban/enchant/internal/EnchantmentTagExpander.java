package io.ib67.yuanban.enchant.internal;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.ib67.yuanban.codec.RegistryIds;
import io.ib67.yuanban.datapack.tag.TagDeclaration;
import io.ib67.yuanban.datapack.tag.TagDeclarations;
import io.ib67.yuanban.enchant.EnchantmentException;
import io.ib67.yuanban.enchant.EnchantmentTags;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;

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

/**
 * 按原版 {@code TagLoader.build} 展开附魔标签，展开顺序复现原版 {@code DependencySorter}。
 * 附魔标签的条目不解析成注册表条目；每个标签保存它引用的附魔 ID 集合。
 * <p>
 * 原版在附魔注册之后构建附魔标签（{@code ResourceManagerRegistryLoadTask}），元素条目按
 * {@code TagLoader.ElementLookup.fromGetters} 查找：必需的元素一律放入标签，没有注册的在冻结注册表时报
 * {@code Unbound values}；可选的元素只在已经注册时放入。找不到的可选标签引用跳过，与 {@code TagEntry.build} 相同。
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
     * @param enchantments 本次加载的全部附魔 ID
     * @throws EnchantmentException 必需的标签引用找不到（包括附魔标签之间的循环引用），
     *                              或者必需的元素不在 {@code enchantments} 中
     */
    public static EnchantmentTags expand(TagDeclarations declarations, Set<Key> enchantments) {
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

        var expansion = new Expansion(contents, dependencies, enchantments);
        var visited = new HashSet<TagId>();
        for (var id : contents.keySet()) {
            expansion.visit(visited, id);
        }
        UnboundReferences.message(RegistryIds.ENCHANTMENT, expansion.unbound, List.of()).ifPresent(message -> {
            throw new EnchantmentException(message);
        });

        var result = new TreeMap<>(expansion.built);
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
        private final Set<Key> enchantments;
        private final Map<Key, List<Key>> built = new HashMap<>();
        /**
         * 放入了标签但不在本次加载中的必需元素。
         */
        private final List<Key> unbound = new ArrayList<>();

        private Expansion(Map<TagId, TagDeclaration> contents, Multimap<TagId, TagId> dependencies, Set<Key> enchantments) {
            this.contents = contents;
            this.dependencies = dependencies;
            this.enchantments = enchantments;
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
                        if (!entry.required()) {
                            continue;
                        }
                        // 循环引用被 addDependencyIfNotCyclic 过滤之后，引用方认不到这个条目；原版在这种情况下跳过
                        throw new EnchantmentException("Couldn't load tag " + declaration.id().asString()
                                + " as it references missing tag " + entry.id().asString());
                    }
                    values.addAll(tag);
                } else if (enchantments.contains(entry.id())) {
                    values.add(entry.id());
                } else if (entry.required()) {
                    values.add(entry.id());
                    unbound.add(entry.id());
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
