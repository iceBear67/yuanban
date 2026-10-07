package io.ib67.yuanban.enchant;

import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Resource;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.enchant.internal.EnchantmentCodecs;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.codec.Result;
import net.minestom.server.registry.Registries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 读取数据包中的附魔提供器文件，准备阶段按类型解码为不可变数据，提交阶段保存。
 * 提供器的原版行为（按经验等级给物品选附魔组合）由怪物生成、村民交易等系统代码承担，
 * 这些系统在本仓库中还没有对应模块，本模块只负责数据。
 */
public final class EnchantmentProviderSubscriber implements Subscriber<EnchantmentProviderData> {
    public static final SubscriberKey<EnchantmentProviderData> KEY = SubscriberKey.of("yuanban:enchantment_providers");
    public static final Topic.Single TOPIC = Topic.json("enchantment_provider");

    private final ServerProcess process;
    private volatile EnchantmentProviderData current = EnchantmentProviderData.EMPTY;

    public EnchantmentProviderSubscriber(ServerProcess process) {
        this.process = Objects.requireNonNull(process, "process");
    }

    /**
     * 生命周期为 {@link Lifecycle#RELOADABLE}、读取 {@link #TOPIC}、依赖 {@link EnchantmentSubscriber#KEY}
     * 和 {@link EnchantmentTagSubscriber#KEY} 的订阅。
     */
    public Subscription<EnchantmentProviderData> subscription() {
        return Subscription.builder(KEY, this)
                .lifecycle(Lifecycle.RELOADABLE)
                .topic(TOPIC)
                .after(EnchantmentSubscriber.KEY)
                .after(EnchantmentTagSubscriber.KEY)
                .build();
    }

    /**
     * 最近一次提交的提供器数据，提交之前是 {@link EnchantmentProviderData#EMPTY}。
     */
    public EnchantmentProviderData current() {
        return current;
    }

    @Override
    public EnchantmentProviderData prepare(PrepareContext context) throws IOException {
        var registries = process.registries();
        var enchantments = context.dependency(EnchantmentSubscriber.KEY);
        var tags = context.dependency(EnchantmentTagSubscriber.KEY);

        var decoded = new TreeMap<Key, ProviderEntry>();
        var failures = new ArrayList<String>();
        for (var entry : context.contents(TOPIC).files().entrySet()) {
            var resource = entry.getValue();
            switch (decode(registries, enchantments, tags, resource)) {
                case Result.Ok<ProviderEntry> ok -> decoded.put(entry.getKey(), ok.value());
                case Result.Error<ProviderEntry> error -> failures.add("Couldn't parse data file '" + entry.getKey().asString()
                        + "' from " + resource + ": " + error.message());
            }
        }
        if (!failures.isEmpty()) {
            throw new EnchantmentException(String.join("\n", failures));
        }
        return new EnchantmentProviderData(decoded);
    }

    private Result<ProviderEntry> decode(Registries registries, EnchantmentData enchantments, EnchantmentTags tags,
                                         Resource resource) throws IOException {
        var json = resource.readJson();
        if (!(json instanceof com.google.gson.JsonObject object)) {
            return error("Not an object: " + json);
        }

        var decoded = EnchantmentCodecs.decodeProvider(registries, object);
        if (decoded instanceof Result.Error<ProviderEntry> decodeError) {
            return error(decodeError.message());
        }
        var provider = ((Result.Ok<ProviderEntry>) decoded).value();

        var enchantment = provider.referencedEnchantment();
        if (enchantment != null && !enchantments.enchantments().containsKey(enchantment)) {
            return error("Missing enchantment: '" + enchantment.asString() + "'");
        }
        var enchantmentTag = provider.referencedEnchantmentTag();
        if (enchantmentTag != null && !tags.tags().containsKey(enchantmentTag)) {
            return error("Missing enchantment tag: '" + enchantmentTag.asString() + "'");
        }
        return new Result.Ok<>(provider);
    }

    private static Result<ProviderEntry> error(String message) {
        return new Result.Error<>(message);
    }

    @Override
    public void apply(EnchantmentProviderData prepared) {
        current = prepared;
    }
}
