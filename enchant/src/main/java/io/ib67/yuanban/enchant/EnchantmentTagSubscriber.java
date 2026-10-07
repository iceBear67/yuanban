package io.ib67.yuanban.enchant;

import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.enchant.internal.EnchantmentTagExpander;
import net.minestom.server.ServerProcess;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.EnchantmentTagContentWriter;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.registry.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 读取数据包中的附魔标签，按原版规则展开，提交阶段写入 Minestom 的附魔注册表。
 * <p>
 * 提交阶段替换全部标签的内容：数据包中没有声明的标签被清空，Minestom 启动时自带的标签保留
 * 但内容被完全替换。附魔注册表是 {@code DynamicRegistry}，标签对象通过
 * {@link net.minestom.server.registry.EnchantmentTagContentWriter} 保持同一个实例，
 * 之前解码出的引用（例如 {@code Enchantment.getFullname} 读到的 {@code curse} 标签）会看到新内容。
 */
public final class EnchantmentTagSubscriber implements Subscriber<EnchantmentTags> {
    public static final SubscriberKey<EnchantmentTags> KEY = SubscriberKey.of("yuanban:enchantment_tags");
    public static final Topic.Tags TOPIC = Topic.tags("enchantment");

    private final ServerProcess process;
    private volatile EnchantmentTags current = EnchantmentTags.EMPTY;

    public EnchantmentTagSubscriber(ServerProcess process) {
        this.process = Objects.requireNonNull(process, "process");
    }

    /**
     * 最近一次提交的附魔标签，提交之前是 {@link EnchantmentTags#EMPTY}。
     */
    public EnchantmentTags current() {
        return current;
    }

    /**
     * 生命周期为 {@link Lifecycle#RELOADABLE}、读取 {@link #TOPIC} 的订阅。
     */
    public Subscription<EnchantmentTags> subscription() {
        return Subscription.builder(KEY, this)
                .lifecycle(Lifecycle.RELOADABLE)
                .topic(TOPIC)
                .build();
    }

    @Override
    public EnchantmentTags prepare(PrepareContext context) {
        return EnchantmentTagExpander.expand(context.contents(TOPIC));
    }

    @Override
    public void apply(EnchantmentTags prepared) {
        var registry = process.enchantment();
        for (var tag : List.copyOf(registry.tags())) {
            var key = Objects.requireNonNull(tag.key(), "registry tag without key");
            if (!prepared.tags().containsKey(key.key())) {
                EnchantmentTagContentWriter.replace(tag, List.of());
                registry.removeTag(key);
            }
        }
        prepared.tags().forEach((id, values) -> {
            RegistryTag<Enchantment> tag = registry.getOrCreateTag(TagKey.of(id));
            var entries = new ArrayList<RegistryKey<Enchantment>>(values.size());
            for (var value : values) {
                entries.add(RegistryKey.of(value));
            }
            EnchantmentTagContentWriter.replace(tag, entries);
        });
        var connections = process.connection();
        // FIXME(Minestom M9): Registry.removeTag 和 getOrCreateTag 不会让缓存的标签数据包失效，见 MINESTOM-BUG-REPORT.md
        connections.invalidateTags();
        for (var player : connections.getOnlinePlayers()) {
            connections.sendRegistryTags(player);
        }
        current = prepared;
    }
}
