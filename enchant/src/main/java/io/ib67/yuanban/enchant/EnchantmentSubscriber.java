package io.ib67.yuanban.enchant;

import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Resource;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.enchant.internal.EnchantmentCodecs;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.itemtag.ItemTags;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.codec.Result;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.registry.TagKey;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 读取数据包中的附魔定义，按本次加载的物品标签和附魔标签解码，提交阶段注册到 Minestom 的附魔注册表。
 * <p>
 * 任何一个附魔文件解码失败的时候收集全部错误信息再抛出 {@link EnchantmentException}，
 * 和 {@code item-recipe} 的 {@code RecipeSubscriber} 相同。
 */
public final class EnchantmentSubscriber implements Subscriber<EnchantmentData> {
    public static final SubscriberKey<EnchantmentData> KEY = SubscriberKey.of("yuanban:enchantments");
    public static final Topic.Single TOPIC = Topic.json("enchantment");

    private final ServerProcess process;
    private volatile EnchantmentData current = EnchantmentData.EMPTY;

    public EnchantmentSubscriber(ServerProcess process) {
        this.process = Objects.requireNonNull(process, "process");
    }

    /**
     * 生命周期为 {@link Lifecycle#RELOADABLE}、读取 {@link #TOPIC}、依赖 {@link ItemTagSubscriber#KEY}
     * 和 {@link EnchantmentTagSubscriber#KEY} 的订阅。
     * 宿主需要同时注册物品标签订阅和附魔标签订阅。
     */
    public Subscription<EnchantmentData> subscription() {
        return Subscription.builder(KEY, this)
                .lifecycle(Lifecycle.RELOADABLE)
                .topic(TOPIC)
                .after(ItemTagSubscriber.KEY)
                .after(EnchantmentTagSubscriber.KEY)
                .build();
    }

    /**
     * 最近一次提交的附魔定义，提交之前是 {@link EnchantmentData#EMPTY}。
     */
    public EnchantmentData current() {
        return current;
    }

    @Override
    public EnchantmentData prepare(PrepareContext context) throws IOException {
        var registries = process.registries();
        var itemTags = context.dependency(ItemTagSubscriber.KEY);
        var enchantmentTags = context.dependency(EnchantmentTagSubscriber.KEY);

        var decoded = new TreeMap<Key, EnchantmentMut>();
        var failures = new ArrayList<String>();
        for (var entry : context.contents(TOPIC).files().entrySet()) {
            var resource = entry.getValue();
            switch (decode(registries, resource, itemTags, enchantmentTags)) {
                case Result.Ok<EnchantmentMut> ok -> decoded.put(entry.getKey(), ok.value());
                case Result.Error<EnchantmentMut> error -> failures.add("Couldn't parse data file '" + entry.getKey().asString()
                        + "' from " + resource + ": " + error.message());
            }
        }
        if (!failures.isEmpty()) {
            throw new EnchantmentException(String.join("\n", failures));
        }
        return new EnchantmentData(decoded);
    }

    private Result<EnchantmentMut> decode(Registries registries, Resource resource,
                                          ItemTags itemTags, EnchantmentTags enchantmentTags) throws IOException {
        var json = resource.readJson();
        if (!(json instanceof com.google.gson.JsonObject object)) {
            return new Result.Error<>("Not an object: " + json);
        }
        var decoded = EnchantmentCodecs.decodeEnchantment(registries, object);
        if (decoded instanceof Result.Error<EnchantmentMut> error) {
            return new Result.Error<>(error.message());
        }
        var enchantment = ((Result.Ok<EnchantmentMut>) decoded).value();

        if (itemTags.find(enchantment.supportedItems()).isEmpty()) {
            return new Result.Error<>("Missing item tag: '" + enchantment.supportedItems().asString() + "'");
        }
        if (enchantment.primaryItems() != null && itemTags.find(enchantment.primaryItems()).isEmpty()) {
            return new Result.Error<>("Missing item tag: '" + enchantment.primaryItems().asString() + "'");
        }
        if (enchantment.exclusiveSet() != null && !enchantmentTags.tags().containsKey(enchantment.exclusiveSet())) {
            return new Result.Error<>("Missing enchantment tag: '" + enchantment.exclusiveSet().asString() + "'");
        }
        return new Result.Ok<>(enchantment);
    }

    @Override
    public void apply(EnchantmentData prepared) {
        var registry = process.enchantment();
        for (var entry : prepared.enchantments().entrySet()) {
            registry.register(entry.getKey(), build(entry.getValue()));
        }
        current = prepared;
    }

    private Enchantment build(EnchantmentMut mut) {
        var supportedItems = process.material().getOrCreateTag(TagKey.of(mut.supportedItems()));
        RegistryTag<Enchantment> exclusiveSet = mut.exclusiveSet() == null
                ? RegistryTag.empty()
                : process.enchantment().getOrCreateTag(TagKey.of(mut.exclusiveSet()));
        var primaryItems = mut.primaryItems() == null
                ? null
                : process.material().getOrCreateTag(TagKey.of(mut.primaryItems()));
        return mut.build(supportedItems, primaryItems, exclusiveSet);
    }
}
