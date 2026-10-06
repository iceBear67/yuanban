package io.ib67.yuanban.itemtag;

import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.itemtag.internal.TagExpander;
import net.minestom.server.ServerProcess;
import net.minestom.server.item.Material;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.registry.TagContentWriter;
import net.minestom.server.registry.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 读取数据包中的物品标签，按原版规则展开，提交阶段写入 Minestom 的物品注册表并发给在线玩家。
 * <p>
 * 提交阶段会删除数据包中没有的标签，包括 Minestom 启动时自带的标签。附魔等在创建 {@code ServerProcess} 时解码的数据
 * 持有标签对象，标签被删除之后它们看到空标签，同一个 JVM 中也无法再新建 {@code ServerProcess}，见 {@code DESIGN.md} 5.1。
 */
public final class ItemTagSubscriber implements Subscriber<ItemTags> {
    public static final SubscriberKey<ItemTags> KEY = SubscriberKey.of("yuanban:item_tags");
    public static final Topic.Tags TOPIC = Topic.tags("item");

    private final ServerProcess process;

    public ItemTagSubscriber(ServerProcess process) {
        this.process = Objects.requireNonNull(process, "process");
    }

    /**
     * 生命周期为 {@link Lifecycle#RELOADABLE}、读取 {@link #TOPIC} 的订阅。
     */
    public static Subscription<ItemTags> subscription(ServerProcess process) {
        return Subscription.builder(KEY, new ItemTagSubscriber(process))
                .lifecycle(Lifecycle.RELOADABLE)
                .topic(TOPIC)
                .build();
    }

    @Override
    public ItemTags prepare(PrepareContext context) {
        return TagExpander.expand(context.contents(TOPIC), Material::fromKey);
    }

    @Override
    public void apply(ItemTags prepared) {
        var registry = process.material();
        for (var tag : List.copyOf(registry.tags())) {
            var key = Objects.requireNonNull(tag.key(), "registry tag without key");
            if (!prepared.tags().containsKey(key.key())) {
                TagContentWriter.replace(tag, List.of());
                registry.removeTag(key);
            }
        }
        prepared.tags().forEach((id, items) -> {
            RegistryTag<Material> tag = registry.getOrCreateTag(TagKey.of(id));
            var entries = new ArrayList<RegistryKey<Material>>(items.size());
            for (var item : items) {
                entries.add(RegistryKey.of(item.key()));
            }
            TagContentWriter.replace(tag, entries);
        });
        var connections = process.connection();
        // FIXME(Minestom M9): Registry.removeTag 和 getOrCreateTag 不会让缓存的标签数据包失效，只删除或新建空标签的时候
        //  没有条目增删来顺带触发失效，这里调用标注为 @ApiStatus.Internal 的 invalidateTags()，见 MINESTOM-BUG-REPORT.md
        connections.invalidateTags();
        for (var player : connections.getOnlinePlayers()) {
            connections.sendRegistryTags(player);
        }
    }
}
