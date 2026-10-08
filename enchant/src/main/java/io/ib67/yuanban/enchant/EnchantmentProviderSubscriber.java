package io.ib67.yuanban.enchant;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import io.ib67.yuanban.codec.RegistryGetter;
import io.ib67.yuanban.codec.RegistryIds;
import io.ib67.yuanban.codec.RegistryLookup;
import io.ib67.yuanban.codec.RegistryOps;
import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.enchant.internal.EnchantmentCodecs;
import io.ib67.yuanban.enchant.internal.UnboundReferences;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;

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

    /**
     * 按原版 {@code EnchantmentProvider.DIRECT_CODEC} 解码全部提供器文件。
     * <p>
     * 原版的附魔提供器和附魔在同一批注册表中加载，解码时附魔注册表用注册查找回答；全部文件解码之后，
     * 再按本次加载的附魔和附魔标签检查引用，错误文本与原版冻结注册表时相同，放在各文件的错误之后。
     */
    @Override
    public EnchantmentProviderData prepare(PrepareContext context) throws IOException {
        var enchantments = context.dependency(EnchantmentSubscriber.KEY);
        var tags = context.dependency(EnchantmentTagSubscriber.KEY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryLookup.builder(process.registries())
                .put(RegistryIds.ENCHANTMENT, RegistryGetter.registration())
                .build());

        var decoded = new TreeMap<Key, ProviderEntry>();
        var failures = new ArrayList<String>();
        for (var entry : context.contents(TOPIC).files().entrySet()) {
            var id = entry.getKey();
            var resource = entry.getValue();
            JsonElement json;
            try {
                json = resource.readJson();
            } catch (JsonParseException e) {
                failures.add(EnchantmentSubscriber.failure(id, resource, e.getMessage()));
                continue;
            }
            var result = EnchantmentCodecs.PROVIDER.parse(ops, json);
            result.error().ifPresentOrElse(
                    error -> failures.add(EnchantmentSubscriber.failure(id, resource, error.message())),
                    () -> decoded.put(id, result.getOrThrow()));
        }

        var unboundValues = decoded.values().stream()
                .flatMap(provider -> provider.referencedEnchantments().stream())
                .filter(reference -> !enchantments.enchantments().containsKey(reference))
                .toList();
        var unboundTags = decoded.values().stream()
                .flatMap(provider -> provider.referencedEnchantmentTags().stream())
                .filter(reference -> !tags.tags().containsKey(reference))
                .toList();
        UnboundReferences.message(RegistryIds.ENCHANTMENT, unboundValues, unboundTags).ifPresent(failures::add);

        if (!failures.isEmpty()) {
            throw new EnchantmentException(String.join("\n", failures));
        }
        return new EnchantmentProviderData(decoded);
    }

    @Override
    public void apply(EnchantmentProviderData prepared) {
        current = prepared;
    }
}
