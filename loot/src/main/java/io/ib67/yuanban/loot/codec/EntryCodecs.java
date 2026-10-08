package io.ib67.yuanban.loot.codec;

import com.mojang.datafixers.Products;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.Identifiers;
import io.ib67.yuanban.codec.ItemCodecs;
import io.ib67.yuanban.loot.EitherId;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.entry.AlternativesEntry;
import io.ib67.yuanban.loot.entry.CompositeEntry;
import io.ib67.yuanban.loot.entry.DynamicEntry;
import io.ib67.yuanban.loot.entry.EmptyEntry;
import io.ib67.yuanban.loot.entry.GroupEntry;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.entry.NestedTableEntry;
import io.ib67.yuanban.loot.entry.SequenceEntry;
import io.ib67.yuanban.loot.entry.SingletonEntry;
import io.ib67.yuanban.loot.entry.SlotsEntry;
import io.ib67.yuanban.loot.entry.TagEntry;
import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.function.BiFunction;

/**
 * 原版 {@code LootPoolEntries} 注册的 9 种条目的编解码器。单例条目的字段顺序是条目自己的字段、
 * {@code weight}（默认 1）、{@code quality}（默认 0）、{@code conditions}、{@code functions}；
 * 复合条目是 {@code children}（默认空列表）、{@code conditions}。
 */
public final class EntryCodecs {
    private EntryCodecs() {
    }

    /**
     * 原版 {@code EmptyLootItem.MAP_CODEC}：只有单例条目的公共字段。
     */
    public static MapCodec<EmptyEntry> empty(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> singletonFields(i, codecs).apply(i,
                (weight, quality, conditions, functions) -> new EmptyEntry(conditions, functions, weight, quality)));
    }

    /**
     * 原版 {@code LootItem.MAP_CODEC}：{@code name}（物品 ID，不能是 {@code minecraft:air}）。
     */
    public static MapCodec<ItemEntry> item(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                ItemCodecs.ITEM.fieldOf("name").forGetter(ItemEntry::name)
        ).and(singletonFields(i, codecs)).apply(i,
                (name, weight, quality, conditions, functions) -> new ItemEntry(conditions, functions, weight, quality, name)));
    }

    /**
     * 原版 {@code NestedLootTable.MAP_CODEC}：{@code value} 是战利品表 ID（不检查是否存在）或内联的战利品表。
     */
    public static MapCodec<NestedTableEntry> lootTable(LootCodecs codecs) {
        Codec<EitherId<LootTable>> value = Codec.either(Identifiers.CODEC, codecs.table()).xmap(
                either -> either.map(EitherId::<LootTable>ofLeft, EitherId::ofRight),
                id -> id.left().<Either<Key, LootTable>>map(Either::left)
                        .orElseGet(() -> Either.right(id.right().orElseThrow())));
        return RecordCodecBuilder.mapCodec(i -> i.group(
                value.fieldOf("value").forGetter(NestedTableEntry::value)
        ).and(singletonFields(i, codecs)).apply(i,
                (table, weight, quality, conditions, functions) -> new NestedTableEntry(conditions, functions, weight, quality, table)));
    }

    /**
     * 原版 {@code DynamicLoot.MAP_CODEC}：{@code name}（任意命名空间 ID）。
     */
    public static MapCodec<DynamicEntry> dynamic(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Identifiers.CODEC.fieldOf("name").forGetter(DynamicEntry::name)
        ).and(singletonFields(i, codecs)).apply(i,
                (name, weight, quality, conditions, functions) -> new DynamicEntry(conditions, functions, weight, quality, name)));
    }

    /**
     * 原版 {@code TagEntry.MAP_CODEC}：{@code name}（原版 {@code TagKey.codec}，不带 {@code #} 的物品标签 ID，
     * 解码时不检查是否存在）、{@code expand}（必填）。
     */
    public static MapCodec<TagEntry> tag(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Identifiers.CODEC.fieldOf("name").forGetter(TagEntry::name),
                Codec.BOOL.fieldOf("expand").forGetter(TagEntry::expand)
        ).and(singletonFields(i, codecs)).apply(i,
                (name, expand, weight, quality, conditions, functions) -> new TagEntry(conditions, functions, weight, quality, name, expand)));
    }

    /**
     * 原版 {@code SlotLoot.MAP_CODEC}：{@code slot_source}。
     */
    public static MapCodec<SlotsEntry> slots(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                SlotSourceCodecs.CODEC.fieldOf("slot_source").forGetter(SlotsEntry::slotSource)
        ).and(singletonFields(i, codecs)).apply(i,
                (source, weight, quality, conditions, functions) -> new SlotsEntry(conditions, functions, weight, quality, source)));
    }

    /**
     * 原版 {@code AlternativesEntry.MAP_CODEC}。
     */
    public static MapCodec<AlternativesEntry> alternatives(LootCodecs codecs) {
        return composite(codecs, AlternativesEntry::new);
    }

    /**
     * 原版 {@code SequentialEntry.MAP_CODEC}。
     */
    public static MapCodec<SequenceEntry> sequence(LootCodecs codecs) {
        return composite(codecs, SequenceEntry::new);
    }

    /**
     * 原版 {@code EntryGroup.MAP_CODEC}。
     */
    public static MapCodec<GroupEntry> group(LootCodecs codecs) {
        return composite(codecs, GroupEntry::new);
    }

    /**
     * 原版 {@code LootPoolSingletonContainer#singletonFields}。
     */
    private static <T extends SingletonEntry> Products.P4<RecordCodecBuilder.Mu<T>, Integer, Integer, List<LootCondition>, List<LootFunction>>
    singletonFields(RecordCodecBuilder.Instance<T> i, LootCodecs codecs) {
        return i.group(
                Codec.INT.optionalFieldOf("weight", 1).forGetter(SingletonEntry::weight),
                Codec.INT.optionalFieldOf("quality", 0).forGetter(SingletonEntry::quality),
                codecs.condition().listOf().optionalFieldOf("conditions", List.of()).forGetter(SingletonEntry::conditions),
                codecs.function().listOf().optionalFieldOf("functions", List.of()).forGetter(SingletonEntry::functions));
    }

    /**
     * 原版 {@code CompositeEntryBase.createCodec}：{@code children}（默认空列表）、{@code conditions}。
     */
    private static <T extends CompositeEntry> MapCodec<T> composite(LootCodecs codecs,
                                                                    BiFunction<List<LootCondition>, List<LootEntry>, T> constructor) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                codecs.entry().listOf().optionalFieldOf("children", List.of()).forGetter(CompositeEntry::children),
                codecs.condition().listOf().optionalFieldOf("conditions", List.of()).forGetter(CompositeEntry::conditions)
        ).apply(i, (children, conditions) -> constructor.apply(conditions, children)));
    }
}
