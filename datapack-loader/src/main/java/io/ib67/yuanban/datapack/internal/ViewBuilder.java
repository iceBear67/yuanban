package io.ib67.yuanban.datapack.internal;

import io.ib67.yuanban.datapack.PackStructureException;
import io.ib67.yuanban.datapack.tag.TagDeclaration;
import io.ib67.yuanban.datapack.tag.TagDeclarations;
import io.ib67.yuanban.datapack.tag.TagValue;
import io.ib67.yuanban.datapack.topic.Resource;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.datapack.topic.TopicFiles;
import io.ib67.yuanban.datapack.topic.TopicLayers;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * 从各数据包的索引构建 topic 视图。数据包按优先级从低到高处理：先用这个数据包的 {@code filter}
 * 删除已经收集的、被屏蔽的文件位置，再加入它自己在这个 topic 下的文件。
 */
@ApiStatus.Internal
public final class ViewBuilder {
    private ViewBuilder() {
    }

    /**
     * @param packs 各数据包的索引，优先级从低到高
     */
    public static Object build(List<PackIndex> packs, Topic<?> topic) {
        return switch (topic) {
            case Topic.Single single -> single(packs, single.directory(), single.extension());
            case Topic.Layered layered -> layered(packs, layered.directory(), layered.extension());
            case Topic.Tags tags -> tags(packs, tags);
        };
    }

    public static TopicFiles single(List<PackIndex> packs, String directory, String extension) {
        var result = new TreeMap<Key, Resource>();
        for (var pack : packs) {
            var filter = pack.pack().metadata().filter();
            if (!filter.isEmpty()) {
                result.values().removeIf(resource -> filter.blocks(resource.location().namespace(), resource.location().value()));
            }
            forEachFile(pack, directory, extension, resource -> result.put(resource.id(), resource));
        }
        return new FilesView(Collections.unmodifiableNavigableMap(result));
    }

    public static TopicLayers layered(List<PackIndex> packs, String directory, String extension) {
        var result = new TreeMap<Key, List<Resource>>();
        for (var pack : packs) {
            var filter = pack.pack().metadata().filter();
            if (!filter.isEmpty()) {
                result.values().removeIf(layers -> {
                    var location = layers.getFirst().location();
                    return filter.blocks(location.namespace(), location.value());
                });
            }
            forEachFile(pack, directory, extension,
                    resource -> result.computeIfAbsent(resource.id(), ignored -> new ArrayList<>()).add(resource));
        }
        result.replaceAll((_, layers) -> List.copyOf(layers));
        return new LayersView(Collections.unmodifiableNavigableMap(result));
    }

    public static TagDeclarations tags(List<PackIndex> packs, Topic.Tags topic) {
        var layers = layered(packs, topic.directory(), topic.extension());
        var result = new TreeMap<Key, TagDeclaration>();
        for (var entry : layers.layers().entrySet()) {
            var values = new ArrayList<TagValue>();
            for (var resource : entry.getValue()) {
                var file = TagFileParser.parse(resource);
                if (file.replace()) {
                    values.clear();
                }
                for (var tagEntry : file.entries()) {
                    values.add(new TagValue(tagEntry, resource.pack()));
                }
            }
            result.put(entry.getKey(), new TagDeclaration(entry.getKey(), values));
        }
        return new TagsView(Collections.unmodifiableNavigableMap(result));
    }

    private static void forEachFile(PackIndex pack, String directory, String extension, Consumer<Resource> output) {
        var info = pack.pack().info();
        for (var namespace : pack.namespaces().entrySet()) {
            for (var file : PackIndex.filesIn(namespace.getValue(), directory).entrySet()) {
                var path = file.getKey();
                if (!path.endsWith(extension)) {
                    continue;
                }
                var idPath = path.substring(directory.length() + 1, path.length() - extension.length());
                Key location;
                Key id;
                try {
                    location = Key.key(namespace.getKey(), path);
                    id = Key.key(namespace.getKey(), idPath);
                } catch (InvalidKeyException e) {
                    throw new PackStructureException("数据包 " + pack.pack().id() + " 中的文件 "
                            + (file.getValue().overlay() == null ? "" : file.getValue().overlay() + "/")
                            + "data/" + namespace.getKey() + "/" + path
                            + " 不能构成合法的资源 ID，路径只能包含 [a-z0-9_.-/]", e);
                }
                output.accept(new Resource(info, file.getValue().overlay(), location, id, file.getValue().path()));
            }
        }
    }

    private record FilesView(NavigableMap<Key, Resource> files) implements TopicFiles {
    }

    private record LayersView(NavigableMap<Key, List<Resource>> layers) implements TopicLayers {
    }

    private record TagsView(NavigableMap<Key, TagDeclaration> tags) implements TagDeclarations {
    }
}
