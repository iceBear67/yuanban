package io.ib67.yuanban.lootparser;

import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.itemtag.ItemTags;
import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.entry.SingletonEntry;
import io.ib67.yuanban.loot.entry.TagEntry;
import io.ib67.yuanban.loot.condition.LocationCheckCondition;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.condition.MatchToolCondition;
import io.ib67.yuanban.lootparser.internal.CodecWiring;
import io.ib67.yuanban.lootparser.internal.LootCodecs;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 读取数据包里的战利品表，提交阶段交给 {@link LootManager}。
 * <p>
 * 生命周期为 {@link Lifecycle#RELOADABLE}：启动和每次数据包重载都会重新读取。
 * 读取要在 {@link ItemTagSubscriber} 之后完成，满足 {@code tag} 条目、{@code match_tool} 的
 * {@code #id} 引用对照同一次加载的物品标签。
 */
public final class LootTableSubscriber implements Subscriber<LootTableSet> {
    public static final SubscriberKey<LootTableSet> KEY = SubscriberKey.of("yuanban:loot_tables");
    public static final Topic.Single TOPIC = Topic.json("loot_table");

    private final LootManager manager;
    private final LootCodecs codecs;
    private volatile LootTableSet current = LootTableSet.EMPTY;

    public LootTableSubscriber(ServerProcess process, LootManager manager) {
        Objects.requireNonNull(process, "process");
        this.manager = Objects.requireNonNull(manager, "manager");
        LootTypeRegistry types = manager.types();
        CodecWiring.wire(types, process);
        this.codecs = LootCodecs.of(process, types);
    }

    /**
     * 生命周期为 {@link Lifecycle#RELOADABLE}、读取 {@link #TOPIC}、依赖 {@link ItemTagSubscriber#KEY} 的订阅。
     */
    public Subscription<LootTableSet> subscription() {
        return Subscription.builder(KEY, this)
                .lifecycle(Lifecycle.RELOADABLE)
                .topic(TOPIC)
                .after(ItemTagSubscriber.KEY)
                .build();
    }

    /**
     * 最近一次提交的战利品表集合；还没提交过的话返回 {@link LootTableSet#EMPTY}。
     */
    public LootTableSet current() {
        return current;
    }

    @Override
    public LootTableSet prepare(PrepareContext context) throws Exception {
        var files = context.contents(TOPIC).files();
        var itemTags = context.dependency(ItemTagSubscriber.KEY);

        var decoded = new TreeMap<Key, io.ib67.yuanban.loot.LootTable>();
        var failures = new ArrayList<String>();
        var resources = new TreeMap<Key, io.ib67.yuanban.datapack.topic.Resource>();

        for (Map.Entry<Key, io.ib67.yuanban.datapack.topic.Resource> file : files.entrySet()) {
            var id = file.getKey();
            var resource = file.getValue();
            resources.put(id, resource);
            try {
                var json = resource.readJson();
                var result = codecs.decodeTable(json);
                if (result.error().isPresent()) {
                    failures.add("Couldn't parse data file '" + id + "' from " + resource + ": "
                            + result.error().get().message());
                } else {
                    decoded.put(id, result.result().orElseThrow());
                }
            } catch (IOException | RuntimeException e) {
                failures.add("Couldn't parse data file '" + id + "' from " + resource + ": " + e.getMessage());
            }
        }

        // 标签核对：tag 条目的 name 与 match_tool 里的 #id 必须在本次加载的 ItemTags 里找得到。
        for (Map.Entry<Key, io.ib67.yuanban.loot.LootTable> entry : decoded.entrySet()) {
            validateTagReferences(entry.getKey(), entry.getValue(), resources.get(entry.getKey()), itemTags, failures);
        }

        if (!failures.isEmpty()) {
            // 全部行按资源 ID 字典序排序，与主错误通道的顺序对齐
            failures.sort(String::compareTo);
            throw new LootException(String.join("\n", failures));
        }
        return new LootTableSet(decoded);
    }

    private void validateTagReferences(Key id, io.ib67.yuanban.loot.LootTable table,
                                       io.ib67.yuanban.datapack.topic.Resource resource,
                                       ItemTags tags, List<String> failures) {
        for (var pool : table.pools()) {
            for (var entry : pool.entries()) {
                collectTagReferences(id, resource, entry, tags, failures);
            }
        }
    }

    private void collectTagReferences(Key id, io.ib67.yuanban.datapack.topic.Resource resource,
                                      io.ib67.yuanban.loot.entry.LootEntry entry, ItemTags tags,
                                      List<String> failures) {
        checkTag(id, resource, entry instanceof TagEntry tag ? tag.name() : null, tags, failures);
        for (var condition : entry.conditions()) {
            checkConditionTags(id, resource, condition, tags, failures);
        }
        if (entry instanceof io.ib67.yuanban.loot.entry.CompositeEntry composite) {
            for (var child : composite.children()) {
                collectTagReferences(id, resource, child, tags, failures);
            }
        }
    }

    private void checkConditionTags(Key id, io.ib67.yuanban.datapack.topic.Resource resource,
                                    LootCondition condition, ItemTags tags, List<String> failures) {
        if (condition instanceof MatchToolCondition matchTool) {
            var predicate = matchTool.predicate().orElse(null);
            if (predicate != null && predicate.itemTag() != null) {
                checkTag(id, resource, predicate.itemTag(), tags, failures);
            }
        }
        var terms = switch (condition) {
            case io.ib67.yuanban.loot.condition.AllOfCondition all -> all.terms();
            case io.ib67.yuanban.loot.condition.AnyOfCondition any -> any.terms();
            default -> List.<LootCondition>of();
        };
        for (var term : terms) {
            checkConditionTags(id, resource, term, tags, failures);
        }
    }

    private void checkTag(Key id, io.ib67.yuanban.datapack.topic.Resource resource,
                          Key tagName, ItemTags tags, List<String> failures) {
        if (tagName != null && tags.find(tagName).isEmpty()) {
            failures.add("Couldn't parse data file '" + id + "' from " + resource
                    + ": Missing tag: '" + tagName + "' in 'minecraft:item'");
        }
    }

    @Override
    public void apply(LootTableSet prepared) {
        manager.replaceAll(prepared.tables());
        this.current = prepared;
    }

    private static String resourceName(io.ib67.yuanban.datapack.topic.Resource resource) {
        // Resource 类封装了所在 pack 的路径；toString 已经包含资源位置信息。
        return resource.toString();
    }
}
