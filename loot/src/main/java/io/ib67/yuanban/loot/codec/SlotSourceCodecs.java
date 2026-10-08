package io.ib67.yuanban.loot.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.ExtraCodecs;
import io.ib67.yuanban.codec.RegistryCodecs;
import io.ib67.yuanban.codec.RegistryId;
import io.ib67.yuanban.loot.slot.SlotSource;
import net.kyori.adventure.key.Key;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 原版 {@code SlotSources} 注册的 6 种槽位来源的编解码器。
 */
public final class SlotSourceCodecs {
    /**
     * 原版 {@code SlotSources.CODEC}：按 {@code type}（原版 {@code slot_source_type} 注册表）分派的对象，
     * 或者槽位来源的列表（{@code group}）。
     */
    public static final Codec<SlotSource> CODEC = Codec.recursive("SlotSource", SlotSourceCodecs::slotSource);

    private SlotSourceCodecs() {
    }

    private static Codec<SlotSource> slotSource(Codec<SlotSource> self) {
        var types = new LinkedHashMap<Key, MapCodec<? extends SlotSource>>();
        types.put(Key.key("group"), RecordCodecBuilder.<SlotSource.Group>mapCodec(i -> i.group(
                self.listOf().fieldOf("terms").forGetter(SlotSource.Group::terms)
        ).apply(i, SlotSource.Group::new)));
        types.put(Key.key("filtered"), RecordCodecBuilder.<SlotSource.Filtered>mapCodec(i -> i.group(
                self.fieldOf("slot_source").forGetter(SlotSource.Filtered::slotSource),
                PredicateCodecs.ITEM.fieldOf("item_filter").forGetter(SlotSource.Filtered::itemFilter)
        ).apply(i, SlotSource.Filtered::new)));
        types.put(Key.key("limit_slots"), RecordCodecBuilder.<SlotSource.LimitSlots>mapCodec(i -> i.group(
                self.fieldOf("slot_source").forGetter(SlotSource.LimitSlots::slotSource),
                ExtraCodecs.POSITIVE_INT.fieldOf("limit").forGetter(SlotSource.LimitSlots::limit)
        ).apply(i, SlotSource.LimitSlots::new)));
        types.put(Key.key("slot_range"), RecordCodecBuilder.<SlotSource.SlotRangeSource>mapCodec(i -> i.group(
                CommonCodecs.ENTITY_OR_BLOCK.fieldOf("source").forGetter(SlotSource.SlotRangeSource::source),
                PredicateCodecs.slotRange().fieldOf("slots").forGetter(SlotSource.SlotRangeSource::slots)
        ).apply(i, SlotSource.SlotRangeSource::new)));
        types.put(Key.key("contents"), RecordCodecBuilder.<SlotSource.Contents>mapCodec(i -> i.group(
                self.fieldOf("slot_source").forGetter(SlotSource.Contents::slotSource),
                CommonCodecs.CONTAINER_COMPONENT.fieldOf("component").forGetter(SlotSource.Contents::component)
        ).apply(i, SlotSource.Contents::new)));
        types.put(Key.key("empty"), MapCodec.unit(SlotSource.Empty.INSTANCE));
        Map<Key, MapCodec<? extends SlotSource>> table = Map.copyOf(types);

        Codec<Key> typeCodec = RegistryCodecs.byName(RegistryId.of("slot_source_type"),
                id -> table.containsKey(id) ? id : null, id -> id);
        Codec<SlotSource> typed = typeCodec.dispatch(SlotSourceCodecs::typeId, table::get);
        Codec<SlotSource.Group> inline = self.listOf().xmap(SlotSource.Group::new, SlotSource.Group::terms);
        return Codec.withAlternative(typed, inline);
    }

    private static Key typeId(SlotSource source) {
        return Key.key(switch (source) {
            case SlotSource.Group ignored -> "group";
            case SlotSource.Filtered ignored -> "filtered";
            case SlotSource.LimitSlots ignored -> "limit_slots";
            case SlotSource.SlotRangeSource ignored -> "slot_range";
            case SlotSource.Contents ignored -> "contents";
            case SlotSource.Empty ignored -> "empty";
        });
    }
}
