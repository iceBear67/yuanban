package io.ib67.yuanban.loot.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.ExtraCodecs;
import io.ib67.yuanban.codec.ItemCodecs;
import io.ib67.yuanban.codec.RegistryCodecs;
import io.ib67.yuanban.codec.RegistryIds;
import io.ib67.yuanban.codec.StringRepresentables;
import io.ib67.yuanban.loot.context.LootContextArg;
import io.ib67.yuanban.loot.function.AppendOperation;
import io.ib67.yuanban.loot.function.InsertOperation;
import io.ib67.yuanban.loot.function.ListOperation;
import io.ib67.yuanban.loot.function.ReplaceAllOperation;
import io.ib67.yuanban.loot.function.ReplaceSectionOperation;
import io.ib67.yuanban.loot.function.StandAloneOperation;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.slot.ContainerComponent;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponent;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockEntityType;
import net.minestom.server.item.book.FilteredText;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.potion.PotionType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 战利品编解码器共用的小型编解码器：枚举名字、上下文来源、列表操作、可过滤文本。
 */
final class CommonCodecs {
    /**
     * 原版 {@code LootContext$EntityTarget.CODEC}：{@code StringRepresentable.fromEnum}。
     */
    static final Codec<EntityTarget> ENTITY_TARGET = StringRepresentables.fromEnum(EntityTarget.values(), EntityTarget::serializedName);
    /**
     * 原版 {@code EquipmentSlotGroup.CODEC}，包括 {@code saddle}。
     */
    static final Codec<EquipmentSlotGroup> EQUIPMENT_SLOT_GROUP = StringRepresentables.fromEnum(
            EquipmentSlotGroup.values(), EquipmentSlotGroup::nbtName);
    /**
     * 原版 {@code AttributeModifier$Operation.CODEC}：{@code add_value}、{@code add_multiplied_base}、
     * {@code add_multiplied_total}。
     */
    static final Codec<AttributeOperation> ATTRIBUTE_OPERATION = StringRepresentables.fromEnum(
            AttributeOperation.values(), operation -> operation.name().toLowerCase(Locale.ROOT));
    /**
     * 原版 {@code FireworkExplosion$Shape.CODEC}：{@code StringRepresentable.fromValues}。
     */
    static final Codec<FireworkExplosion.Shape> FIREWORK_SHAPE = StringRepresentables.fromValues(
            FireworkExplosion.Shape.values(), shape -> shape.name().toLowerCase(Locale.ROOT));
    /**
     * 原版 {@code DyeColor.CODEC}。
     */
    static final Codec<DyeColor> DYE_COLOR = StringRepresentables.fromEnum(
            DyeColor.values(), color -> color.name().toLowerCase(Locale.ROOT));
    /**
     * 原版 {@code LootContextArg.ENTITY_OR_BLOCK}：实体目标名或 {@code block_entity}，未知名字为
     * {@code Unknown element id: <name>}。{@code copy_name} 与 {@code slot_range} 使用。
     */
    static final Codec<LootContextArg> ENTITY_OR_BLOCK = contextArgCodec(true, true, false);
    /**
     * 原版 {@code CopyComponentsFunction.GETTER_CODEC}：实体目标名、{@code block_entity} 或 {@code tool}。
     */
    static final Codec<LootContextArg> ENTITY_BLOCK_OR_TOOL = contextArgCodec(true, true, true);
    /**
     * 原版 {@code ContainerComponentManipulators.CODEC}：按组件类型 ID 解码，不是这三种组件时为
     * {@code No items in component}。
     */
    static final Codec<ContainerComponent> CONTAINER_COMPONENT = ItemCodecs.DATA_COMPONENT_TYPE.comapFlatMap(type -> {
        for (var component : ContainerComponent.values()) {
            if (component.component() == type) {
                return DataResult.success(component);
            }
        }
        return DataResult.error(() -> "No items in component");
    }, ContainerComponent::component);
    /**
     * 原版 {@code BuiltInRegistries.BLOCK.holderByNameCodec()}：方块注册表的 ID，解码为该方块的默认状态。
     */
    static final Codec<Block> BLOCK = RegistryCodecs.byName(RegistryIds.BLOCK, Block::fromKey, Block::key);
    /**
     * 原版 {@code BuiltInRegistries.ATTRIBUTE.holderByNameCodec()}。
     */
    static final Codec<Attribute> ATTRIBUTE = RegistryCodecs.byName(RegistryIds.ATTRIBUTE, Attribute::fromKey, Attribute::key);
    /**
     * 原版 {@code BuiltInRegistries.POTION.holderByNameCodec()}。
     */
    static final Codec<PotionType> POTION = RegistryCodecs.byName(RegistryIds.POTION, PotionType::fromKey, PotionType::key);
    /**
     * 原版 {@code BuiltInRegistries.BLOCK_ENTITY_TYPE.holderByNameCodec()}。
     */
    static final Codec<BlockEntityType> BLOCK_ENTITY_TYPE = RegistryCodecs.byName(RegistryIds.BLOCK_ENTITY_TYPE,
            BlockEntityType::fromKey, BlockEntityType::key);

    private CommonCodecs() {
    }

    /**
     * 原版 {@code LootContextArg.createArgCodec}：名字到来源的表，按 {@code ExtraCodecs.LateBoundIdMapper} 解码。
     */
    private static Codec<LootContextArg> contextArgCodec(boolean entities, boolean blockEntity, boolean tool) {
        var byName = new LinkedHashMap<String, LootContextArg>();
        if (entities) {
            for (var target : EntityTarget.values()) {
                byName.put(target.serializedName(), new LootContextArg.Entity(target));
            }
        }
        if (blockEntity) {
            byName.put(LootContextArg.BlockEntity.INSTANCE.serializedName(), LootContextArg.BlockEntity.INSTANCE);
        }
        if (tool) {
            byName.put(LootContextArg.Tool.INSTANCE.serializedName(), LootContextArg.Tool.INSTANCE);
        }
        Map<String, LootContextArg> table = Map.copyOf(byName);
        return ExtraCodecs.idResolverCodec(Codec.STRING, table::get,
                arg -> table.containsKey(arg.serializedName()) ? arg.serializedName() : null);
    }

    /**
     * 原版 {@code ListOperation$Type}。
     */
    private enum ListOperationType {
        REPLACE_ALL("replace_all", MapCodec.unit(ReplaceAllOperation.INSTANCE)),
        REPLACE_SECTION("replace_section", RecordCodecBuilder.<ReplaceSectionOperation>mapCodec(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("offset", 0).forGetter(ReplaceSectionOperation::offset),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("size").forGetter(ReplaceSectionOperation::size)
        ).apply(i, ReplaceSectionOperation::new))),
        INSERT("insert", RecordCodecBuilder.<InsertOperation>mapCodec(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("offset", 0).forGetter(InsertOperation::offset)
        ).apply(i, InsertOperation::new))),
        APPEND("append", MapCodec.unit(AppendOperation.INSTANCE));

        static final Codec<ListOperationType> CODEC = StringRepresentables.fromEnum(values(), type -> type.id);

        private final String id;
        private final MapCodec<? extends ListOperation> mapCodec;

        ListOperationType(String id, MapCodec<? extends ListOperation> mapCodec) {
            this.id = id;
            this.mapCodec = mapCodec;
        }

        static ListOperationType of(ListOperation operation) {
            return switch (operation) {
                case ReplaceAllOperation ignored -> REPLACE_ALL;
                case ReplaceSectionOperation ignored -> REPLACE_SECTION;
                case InsertOperation ignored -> INSERT;
                case AppendOperation ignored -> APPEND;
            };
        }
    }

    /**
     * 原版 {@code ListOperation.codec(maxSize)}：内联在所属对象中，必填的 {@code mode} 选择操作；
     * {@code replace_section} 的 {@code size} 大于 {@code maxSize} 时为 {@code Size value too large: <size>, max size is <maxSize>}。
     */
    static MapCodec<ListOperation> listOperation(int maxSize) {
        return ListOperationType.CODEC.<ListOperation>dispatchMap("mode", ListOperationType::of, type -> type.mapCodec)
                .validate(operation -> {
                    if (operation instanceof ReplaceSectionOperation section && section.size().isPresent()) {
                        int size = section.size().get();
                        if (size > maxSize) {
                            return DataResult.error(() -> "Size value too large: " + size + ", max size is " + maxSize);
                        }
                    }
                    return DataResult.success(operation);
                });
    }

    /**
     * 原版 {@code ListOperation$StandAlone.codec(valueCodec, maxSize)}：{@code values}（最多 {@code maxSize} 个）
     * 加上内联的列表操作。
     */
    static <T> Codec<StandAloneOperation<T>> standAlone(Codec<T> valueCodec, int maxSize) {
        return RecordCodecBuilder.create(i -> i.group(
                valueCodec.sizeLimitedListOf(maxSize).fieldOf("values").forGetter(StandAloneOperation::values),
                listOperation(maxSize).forGetter(StandAloneOperation::operation)
        ).apply(i, StandAloneOperation::new));
    }

    /**
     * 原版 {@code Filterable.codec(valueCodec)}：对象 {@code {raw, filtered?}}，或者只写值本身。
     */
    static <T> Codec<FilteredText<T>> filterable(Codec<T> valueCodec) {
        Codec<FilteredText<T>> full = RecordCodecBuilder.create(i -> i.group(
                valueCodec.fieldOf("raw").forGetter(FilteredText::text),
                valueCodec.optionalFieldOf("filtered").forGetter(text -> Optional.ofNullable(text.filtered()))
        ).apply(i, (raw, filtered) -> new FilteredText<>(raw, filtered.orElse(null))));
        Codec<FilteredText<T>> simple = valueCodec.xmap(value -> new FilteredText<>(value, null), FilteredText::text);
        return Codec.withAlternative(full, simple);
    }

    /**
     * 原版 {@code DataComponentType.CODEC} 的列表。
     */
    static Codec<List<DataComponent<?>>> dataComponentTypes() {
        return ItemCodecs.DATA_COMPONENT_TYPE.listOf();
    }
}
