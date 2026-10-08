package io.ib67.yuanban.enchant;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.RegistryGetter;
import io.ib67.yuanban.codec.RegistryIds;
import io.ib67.yuanban.codec.RegistryLookup;
import io.ib67.yuanban.codec.RegistryOps;
import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Resource;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.enchant.internal.EnchantmentCodecs;
import io.ib67.yuanban.enchant.internal.UnboundReferences;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.itemtag.ItemTags;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.Registry;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;

import java.io.IOException;
import java.util.ArrayList;
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

    /**
     * 按原版 {@code Enchantment.DIRECT_CODEC} 解码全部附魔文件。
     * <p>
     * 物品注册表的元素取自 Minestom，标签取自本次加载的物品标签。原版在同一批注册表中加载附魔和附魔标签，
     * 解码时附魔注册表用注册查找回答，任何附魔和附魔标签都可以引用；全部文件解码之后，再按本次加载解码成功的附魔
     * 和附魔标签检查 {@code exclusive_set} 的引用，错误文本与原版冻结注册表时相同，放在各文件的错误之后。
     */
    @Override
    public EnchantmentData prepare(PrepareContext context) throws IOException {
        var itemTags = context.dependency(ItemTagSubscriber.KEY);
        var enchantmentTags = context.dependency(EnchantmentTagSubscriber.KEY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryLookup.builder(process.registries())
                .put(RegistryIds.ITEM, items(process.registries(), itemTags))
                .put(RegistryIds.ENCHANTMENT, RegistryGetter.registration())
                .build());

        var decoded = new TreeMap<Key, EnchantmentMut>();
        var failures = new ArrayList<String>();
        for (var entry : context.contents(TOPIC).files().entrySet()) {
            var id = entry.getKey();
            var resource = entry.getValue();
            JsonElement json;
            try {
                json = resource.readJson();
            } catch (JsonParseException e) {
                failures.add(failure(id, resource, e.getMessage()));
                continue;
            }
            var result = EnchantmentCodecs.ENCHANTMENT.parse(ops, json);
            result.error().ifPresentOrElse(
                    error -> failures.add(failure(id, resource, error.message())),
                    () -> decoded.put(id, result.getOrThrow()));
        }

        var unboundValues = new ArrayList<Key>();
        var unboundTags = new ArrayList<Key>();
        for (var enchantment : decoded.values()) {
            switch (enchantment.exclusiveSet()) {
                case HolderSet.Direct<Enchantment> direct -> direct.keys().stream()
                        .map(RegistryKey::key)
                        .filter(reference -> !decoded.containsKey(reference))
                        .forEach(unboundValues::add);
                case HolderSet.Named<Enchantment> named -> {
                    if (!enchantmentTags.tags().containsKey(named.tag().key())) {
                        unboundTags.add(named.tag().key());
                    }
                }
            }
        }
        UnboundReferences.message(RegistryIds.ENCHANTMENT, unboundValues, unboundTags).ifPresent(failures::add);

        if (!failures.isEmpty()) {
            throw new EnchantmentException(String.join("\n", failures));
        }
        return new EnchantmentData(decoded);
    }

    /**
     * 物品注册表：元素是 Minestom 的全部物品，标签是本次加载展开的物品标签。
     */
    private static RegistryGetter<Material> items(Registries registries, ItemTags itemTags) {
        var materials = registries.material();
        return RegistryGetter.of(id -> materials.get(id) != null,
                tag -> itemTags.find(tag).map(items -> items.stream().map(Material::key).toList()));
    }

    static String failure(Key id, Resource resource, String message) {
        return "Couldn't parse data file '" + id.asString() + "' from " + resource + ": " + message;
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
        var supportedItems = registryTag(process.material(), mut.supportedItems());
        var primaryItems = mut.primaryItems() == null ? null : registryTag(process.material(), mut.primaryItems());
        var exclusiveSet = registryTag(process.enchantment(), mut.exclusiveSet());
        return mut.build(supportedItems, primaryItems, exclusiveSet);
    }

    /**
     * 标签用 {@link Registry#getOrCreateTag} 取得注册表中的标签对象，元素列表按原顺序转换为直接标签。
     */
    private static <T> RegistryTag<T> registryTag(Registry<T> registry, HolderSet<T> set) {
        return switch (set) {
            case HolderSet.Named<T> named -> registry.getOrCreateTag(named.tag());
            case HolderSet.Direct<T> direct -> direct.toRegistryTag();
        };
    }
}
