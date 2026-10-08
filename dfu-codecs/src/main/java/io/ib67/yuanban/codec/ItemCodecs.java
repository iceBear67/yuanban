package io.ib67.yuanban.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * 原版物品与数据组件的编解码器，结果是 Minestom 的 {@link Material}、{@link DataComponentMap}、{@link ItemStack}。
 * <p>
 * 组件的键按原版解码（未知组件、不保存的组件、移除标记 {@code !}），错误文本与原版相同；
 * 每个组件的值通过 {@link MinestomCodecs#adapt} 使用 Minestom 为该组件提供的编解码器，
 * 值内部的错误只报告第一个，错误文本是 Minestom 的。
 */
public final class ItemCodecs {
    static {
        // DataComponent.fromKey 查询的表在 DataComponents 初始化时才填充，按 ID 查找组件之前先初始化它
        Objects.requireNonNull(DataComponents.CUSTOM_DATA);
    }

    /**
     * 原版 {@code Item.CODEC}：内置物品注册表的 ID，错误为
     * {@code Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: <id>}；
     * 空气为 {@code Item must not be minecraft:air}。
     */
    public static final Codec<Material> ITEM = RegistryCodecs.byName(RegistryIds.ITEM, Material::fromKey, Material::key)
            .validate(material -> material == Material.AIR
                    ? DataResult.error(() -> "Item must not be minecraft:air")
                    : DataResult.success(material));
    /**
     * 原版 {@code DataComponentType.CODEC}：内置数据组件类型注册表的 ID，包括不保存的组件。
     */
    public static final Codec<DataComponent<?>> DATA_COMPONENT_TYPE = RegistryCodecs.byName(
            RegistryIds.DATA_COMPONENT_TYPE, DataComponent::fromKey, DataComponent::key);
    /**
     * 原版 {@code DataComponentType.PERSISTENT_CODEC}：不保存的组件为 {@code Encountered transient component <id>}。
     * 原版中没有编解码器的组件不写入存档（{@code DataComponentType.isTransient()}），对应 Minestom 中
     * {@link DataComponent#isSerialized()} 为 {@code false}。
     */
    public static final Codec<DataComponent<?>> PERSISTENT_DATA_COMPONENT_TYPE = DATA_COMPONENT_TYPE.validate(type -> type.isSerialized()
            ? DataResult.success(type)
            : DataResult.error(() -> "Encountered transient component " + type.key().asString()));
    /**
     * 原版 {@code DataComponentMap.CODEC}（即 {@code DataComponentType.VALUE_MAP_CODEC}）：组件 ID 到值的映射。
     * 键按 {@link #PERSISTENT_DATA_COMPONENT_TYPE} 解码；全部条目都会尝试解码，错误按原版
     * {@code Codec.dispatchedMap} 合并，并附上 {@code missed input: ..}。
     */
    public static final Codec<DataComponentMap> DATA_COMPONENT_MAP = Codec.<DataComponent<?>, Object>dispatchedMap(
            PERSISTENT_DATA_COMPONENT_TYPE, ItemCodecs::valueCodec
    ).xmap(ItemCodecs::buildMap, ItemCodecs::unpackMap);
    /**
     * 原版 {@code DataComponentPatch.CODEC}：键为组件 ID，以 {@code !} 开头时表示移除该组件，值必须是对象（内容不读取）。
     * 未知组件为 {@code No component with type: '<id>'}（ID 不合法时 {@code <id>} 为 {@code null}），
     * 不保存的组件为 {@code '<id>' is not a persistent component}。结果是 Minestom 的组件补丁，移除的组件在补丁中保留移除标记。
     */
    public static final Codec<DataComponentMap> DATA_COMPONENT_PATCH = Codec.<PatchKey, Object>dispatchedMap(
            PatchKey.CODEC, PatchKey::valueCodec
    ).xmap(ItemCodecs::buildPatch, ItemCodecs::unpackPatch);
    /**
     * 原版 {@code ItemStack.MAP_CODEC}：{@code id}（{@link #ITEM}）、{@code count}（[1, 99]，缺省为 1，编码时总是写出）、
     * {@code components}（{@link #DATA_COMPONENT_PATCH}，缺省为空）。
     */
    public static final MapCodec<ItemStack> ITEM_STACK_MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            ITEM.fieldOf("id").forGetter(ItemStack::material),
            ExtraCodecs.optionalAlwaysPresentFieldOf(ExtraCodecs.intRange(1, 99), "count", 1).forGetter(ItemStack::amount),
            DATA_COMPONENT_PATCH.optionalFieldOf("components", DataComponentMap.EMPTY).forGetter(ItemStack::componentPatch)
    ).apply(i, ItemStack::of));
    /**
     * 原版 {@code ItemStack.CODEC}。
     */
    public static final Codec<ItemStack> ITEM_STACK = ITEM_STACK_MAP.codec();
    /**
     * 原版 {@code ItemStackTemplate.MAP_CODEC}：字段与 {@link #ITEM_STACK_MAP} 相同，{@code count} 为缺省值 1 时编码不写出。
     */
    public static final MapCodec<ItemStackTemplate> ITEM_STACK_TEMPLATE_MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            ITEM.fieldOf("id").forGetter(ItemStackTemplate::item),
            ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(ItemStackTemplate::count),
            DATA_COMPONENT_PATCH.optionalFieldOf("components", DataComponentMap.EMPTY).forGetter(ItemStackTemplate::components)
    ).apply(i, ItemStackTemplate::new));
    /**
     * 原版 {@code ItemStackTemplate.CODEC}：{@link #ITEM_STACK_TEMPLATE_MAP} 的对象，或者只写物品 ID（数量为 1）。
     */
    public static final Codec<ItemStackTemplate> ITEM_STACK_TEMPLATE = Codec.withAlternative(ITEM_STACK_TEMPLATE_MAP.codec(), ITEM,
            item -> new ItemStackTemplate(item, 1, DataComponentMap.EMPTY));

    private ItemCodecs() {
    }

    private static Codec<?> valueCodec(DataComponent<?> type) {
        return MinestomCodecs.adapt(Objects.requireNonNull(type.codec(), () -> type.key().asString() + " is not a persistent component"));
    }

    @SuppressWarnings("unchecked")
    private static DataComponentMap buildMap(Map<DataComponent<?>, ?> values) {
        var builder = DataComponentMap.builder();
        values.forEach((type, value) -> builder.set((DataComponent<Object>) type, value));
        return builder.build();
    }

    private static Map<DataComponent<?>, Object> unpackMap(DataComponentMap map) {
        var values = new java.util.LinkedHashMap<DataComponent<?>, Object>();
        for (var entry : map.entrySet()) {
            values.put(entry.component(), Objects.requireNonNull(entry.value(), "value"));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static DataComponentMap buildPatch(Map<PatchKey, ?> values) {
        var builder = DataComponentMap.patchBuilder();
        values.forEach((key, value) -> {
            if (key.removed()) {
                builder.remove(key.type());
            } else {
                builder.set((DataComponent<Object>) key.type(), value);
            }
        });
        return builder.build();
    }

    private static Map<PatchKey, Object> unpackPatch(DataComponentMap patch) {
        var values = new java.util.LinkedHashMap<PatchKey, Object>();
        for (var entry : patch.entrySet()) {
            Object value = entry.value();
            values.put(new PatchKey(entry.component(), value == null), value == null ? com.mojang.datafixers.util.Unit.INSTANCE : value);
        }
        return values;
    }

    /**
     * 原版 {@code DataComponentPatch.PatchKey}。
     */
    private record PatchKey(DataComponent<?> type, boolean removed) {
        private static final String REMOVED_PREFIX = "!";
        static final Codec<PatchKey> CODEC = Codec.STRING.flatXmap(string -> {
            boolean removed = string.startsWith(REMOVED_PREFIX);
            if (removed) {
                string = string.substring(REMOVED_PREFIX.length());
            }
            @Nullable Key id = tryParse(string);
            DataComponent<?> type = id != null ? DataComponent.fromKey(id) : null;
            if (type == null) {
                return DataResult.error(() -> "No component with type: '" + (id == null ? "null" : id.asString()) + "'");
            }
            if (!type.isSerialized()) {
                return DataResult.error(() -> "'" + id.asString() + "' is not a persistent component");
            }
            return DataResult.success(new PatchKey(type, removed));
        }, key -> DataResult.success(key.removed() ? REMOVED_PREFIX + key.type().key().asString() : key.type().key().asString()));

        Codec<?> valueCodec() {
            return removed ? Codec.EMPTY.codec() : ItemCodecs.valueCodec(type);
        }

        /**
         * 原版 {@code Identifier.tryParse}：不合法时为 {@code null}。
         */
        private static @Nullable Key tryParse(String string) {
            try {
                return Identifiers.parse(string);
            } catch (Identifiers.IdentifierException e) {
                return null;
            }
        }
    }
}
