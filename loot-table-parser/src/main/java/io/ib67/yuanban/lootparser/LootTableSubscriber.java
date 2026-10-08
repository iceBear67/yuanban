package io.ib67.yuanban.lootparser;

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
import io.ib67.yuanban.datapack.tag.TagDeclarations;
import io.ib67.yuanban.datapack.tag.TagValue;
import io.ib67.yuanban.datapack.topic.Resource;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.enchant.EnchantmentSubscriber;
import io.ib67.yuanban.enchant.EnchantmentTagSubscriber;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.itemtag.ItemTags;
import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.codec.LootCodecs;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.item.Material;
import net.minestom.server.registry.Registries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 读取数据包里的战利品表，提交阶段交给 {@link LootManager}。
 * <p>
 * 生命周期为 {@link Lifecycle#RELOADABLE}：启动和每次数据包重载都会重新读取。
 * 战利品表按 {@link LootCodecs#table()} 解码，类型 ID 在 {@link LootManager#types()} 中查找，宿主注册的类型同样可以解码。
 * 解码中的注册表引用按本次加载的内容检查：物品标签来自 {@link ItemTagSubscriber}，附魔与附魔标签来自
 * {@link EnchantmentSubscriber} 与 {@link EnchantmentTagSubscriber}，结构与结构标签来自本次加载的
 * {@code worldgen/structure} 文件与标签；其余注册表取 Minestom 的注册表。
 */
public final class LootTableSubscriber implements Subscriber<LootTableSet> {
    public static final SubscriberKey<LootTableSet> KEY = SubscriberKey.of("yuanban:loot_tables");
    public static final Topic.Single TOPIC = Topic.json("loot_table");
    /**
     * 结构文件：只用于检查 {@code exploration_map} 等处的结构引用是否存在。
     */
    public static final Topic.Single STRUCTURE_TOPIC = Topic.json("worldgen/structure");
    /**
     * 结构标签：只用于检查结构标签是否存在。
     */
    public static final Topic.Tags STRUCTURE_TAG_TOPIC = Topic.tags("worldgen/structure");

    private final ServerProcess process;
    private final LootManager manager;
    private final LootCodecs codecs;
    private volatile LootTableSet current = LootTableSet.EMPTY;

    public LootTableSubscriber(ServerProcess process, LootManager manager) {
        this.process = Objects.requireNonNull(process, "process");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.codecs = new LootCodecs(manager.types());
    }

    /**
     * 生命周期为 {@link Lifecycle#RELOADABLE}、读取 {@link #TOPIC}、{@link #STRUCTURE_TOPIC} 与 {@link #STRUCTURE_TAG_TOPIC}，
     * 依赖 {@link ItemTagSubscriber#KEY}、{@link EnchantmentSubscriber#KEY} 与 {@link EnchantmentTagSubscriber#KEY} 的订阅。
     * 宿主需要同时注册这三个订阅。
     */
    public Subscription<LootTableSet> subscription() {
        return Subscription.builder(KEY, this)
                .lifecycle(Lifecycle.RELOADABLE)
                .topic(TOPIC)
                .topic(STRUCTURE_TOPIC)
                .topic(STRUCTURE_TAG_TOPIC)
                .after(ItemTagSubscriber.KEY)
                .after(EnchantmentSubscriber.KEY)
                .after(EnchantmentTagSubscriber.KEY)
                .build();
    }

    /**
     * 最近一次提交的战利品表集合；还没提交过的话返回 {@link LootTableSet#EMPTY}。
     */
    public LootTableSet current() {
        return current;
    }

    /**
     * 解码全部战利品表。任何文件读取或解码失败时收集全部错误，按文本排序之后抛出 {@link LootException}。
     */
    @Override
    public LootTableSet prepare(PrepareContext context) {
        var itemTags = context.dependency(ItemTagSubscriber.KEY);
        var enchantments = context.dependency(EnchantmentSubscriber.KEY);
        var enchantmentTags = context.dependency(EnchantmentTagSubscriber.KEY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryLookup.builder(process.registries())
                .put(RegistryIds.ITEM, items(process.registries(), itemTags))
                .put(RegistryIds.ENCHANTMENT, RegistryGetter.of(enchantments.enchantments().keySet(), enchantmentTags.tags()))
                .put(RegistryIds.STRUCTURE, RegistryGetter.of(context.contents(STRUCTURE_TOPIC).files().keySet(),
                        tags(context.contents(STRUCTURE_TAG_TOPIC))))
                .build());

        var decoded = new TreeMap<Key, LootTable>();
        var failures = new ArrayList<String>();
        for (var file : context.contents(TOPIC).files().entrySet()) {
            var id = file.getKey();
            var resource = file.getValue();
            JsonElement json;
            try {
                json = resource.readJson();
            } catch (IOException | JsonParseException e) {
                failures.add(failure(id, resource, e.getMessage()));
                continue;
            }
            var result = codecs.table().parse(ops, json);
            result.error().ifPresentOrElse(
                    error -> failures.add(failure(id, resource, error.message())),
                    () -> decoded.put(id, result.getOrThrow()));
        }

        if (!failures.isEmpty()) {
            failures.sort(String::compareTo);
            throw new LootException(String.join("\n", failures));
        }
        return new LootTableSet(decoded);
    }

    /**
     * 物品注册表：元素是 Minestom 的全部物品，标签是本次加载展开的物品标签。
     */
    private static RegistryGetter<Material> items(Registries registries, ItemTags itemTags) {
        var materials = registries.material();
        return RegistryGetter.of(id -> materials.get(id) != null,
                tag -> itemTags.find(tag).map(items -> items.stream().map(Material::key).toList()));
    }

    /**
     * 标签声明：解码只检查标签是否存在，内容取声明中直接写出的元素。
     */
    private static Map<Key, List<Key>> tags(TagDeclarations declarations) {
        var result = new LinkedHashMap<Key, List<Key>>();
        declarations.tags().forEach((id, declaration) -> result.put(id, declaration.values().stream()
                .map(TagValue::entry)
                .filter(entry -> !entry.tag())
                .map(io.ib67.yuanban.datapack.tag.TagEntry::id)
                .toList()));
        return result;
    }

    private static String failure(Key id, Resource resource, String message) {
        return "Couldn't parse data file '" + id.asString() + "' from " + resource + ": " + message;
    }

    @Override
    public void apply(LootTableSet prepared) {
        manager.replaceAll(prepared.tables());
        this.current = prepared;
    }
}
