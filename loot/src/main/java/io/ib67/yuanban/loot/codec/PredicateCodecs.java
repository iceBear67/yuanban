package io.ib67.yuanban.loot.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.ComponentSerialization;
import io.ib67.yuanban.codec.ExtraCodecs;
import io.ib67.yuanban.codec.Identifiers;
import io.ib67.yuanban.codec.ItemCodecs;
import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.codec.NbtCodecs;
import io.ib67.yuanban.codec.RegistryCodecs;
import io.ib67.yuanban.codec.RegistryId;
import io.ib67.yuanban.codec.RegistryIds;
import io.ib67.yuanban.codec.StringRepresentables;
import io.ib67.yuanban.loot.predicate.CollectionPredicate;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.DamageSourcePredicate;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.predicate.LocationPredicate;
import io.ib67.yuanban.loot.predicate.StatePropertiesPredicate;
import io.ib67.yuanban.loot.predicate.component.AttributeEntryPredicate;
import io.ib67.yuanban.loot.predicate.component.ComponentPredicate;
import io.ib67.yuanban.loot.predicate.component.EnchantmentPredicate;
import io.ib67.yuanban.loot.predicate.component.FireworkPredicate;
import io.ib67.yuanban.loot.predicate.entity.AdvancementPredicate;
import io.ib67.yuanban.loot.predicate.entity.DistancePredicate;
import io.ib67.yuanban.loot.predicate.entity.EffectInstancePredicate;
import io.ib67.yuanban.loot.predicate.entity.EntityPredicate;
import io.ib67.yuanban.loot.predicate.entity.EntitySubPredicate;
import io.ib67.yuanban.loot.predicate.entity.FoodPredicate;
import io.ib67.yuanban.loot.predicate.entity.InputPredicate;
import io.ib67.yuanban.loot.predicate.entity.StatMatcher;
import io.ib67.yuanban.loot.slot.SlotRange;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.statistic.StatisticCategory;
import net.minestom.server.statistic.StatisticType;
import net.minestom.server.utils.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 原版谓词的编解码器：物品、组件、实体、位置、方块、流体、伤害来源与方块状态属性。
 * 字段名、必填与可选、缺省值、注册表检查与原版相同；注册表引用经 {@link io.ib67.yuanban.codec.RegistryOps}
 * 在解码时检查。
 */
public final class PredicateCodecs {
    private static final Range.Int ANY_INT = MinMaxBounds.ANY_INT;
    private static final Range.Double ANY_DOUBLE = MinMaxBounds.ANY_DOUBLE;

    /**
     * 原版 {@code StatePropertiesPredicate.CODEC}：属性名到取值的对象，取值是字符串或 {@code {min?, max?}}。
     */
    public static final Codec<StatePropertiesPredicate> STATE_PROPERTIES = statePropertiesCodec();
    /**
     * 原版 {@code ItemPredicate.CODEC}。
     */
    public static final Codec<ItemPredicate> ITEM = Codec.recursive("ItemPredicate", PredicateCodecs::itemPredicate);
    /**
     * 原版 {@code DataComponentPredicate.CODEC}：组件谓词类型 ID 或组件类型 ID 到谓词的对象，保持 JSON 中的顺序。
     */
    public static final Codec<List<ComponentPredicate>> COMPONENT_PREDICATES = componentPredicates(ITEM);
    /**
     * 原版 {@code DataComponentMatchers.CODEC}：内联的 {@code components} 与 {@code predicates}。
     */
    public static final MapCodec<ComponentMatchers> COMPONENT_MATCHERS = componentMatchers(COMPONENT_PREDICATES);
    /**
     * 原版 {@code BlockPredicate.CODEC}。
     */
    public static final Codec<LocationPredicate.BlockPredicate> BLOCK = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(RegistryIds.BLOCK).optionalFieldOf("blocks").forGetter(LocationPredicate.BlockPredicate::blocks),
            STATE_PROPERTIES.optionalFieldOf("state").forGetter(LocationPredicate.BlockPredicate::state),
            NbtCodecs.LENIENT_COMPOUND_TAG.optionalFieldOf("nbt").forGetter(LocationPredicate.BlockPredicate::nbt),
            COMPONENT_MATCHERS.forGetter(LocationPredicate.BlockPredicate::components)
    ).apply(i, LocationPredicate.BlockPredicate::new));
    /**
     * 原版 {@code FluidPredicate.CODEC}。
     */
    public static final Codec<LocationPredicate.FluidPredicate> FLUID = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(RegistryIds.FLUID).optionalFieldOf("fluids").forGetter(LocationPredicate.FluidPredicate::fluids),
            STATE_PROPERTIES.optionalFieldOf("state").forGetter(LocationPredicate.FluidPredicate::state)
    ).apply(i, LocationPredicate.FluidPredicate::new));
    /**
     * 原版 {@code LocationPredicate.CODEC}。
     */
    public static final Codec<LocationPredicate> LOCATION = locationCodec();
    /**
     * 原版 {@code EntityPredicate.CODEC}：子谓词类型 ID（原版 {@code entity_sub_predicate_type} 注册表）到子谓词的对象。
     */
    public static final Codec<EntityPredicate> ENTITY = Codec.recursive("EntityPredicate", PredicateCodecs::entityPredicate);
    /**
     * 原版 {@code DamageSourcePredicate.CODEC}。
     */
    public static final Codec<DamageSourcePredicate> DAMAGE_SOURCE = damageSourceCodec();

    private PredicateCodecs() {
    }

    // ------------------------------------------------------------------
    // 方块状态属性
    // ------------------------------------------------------------------

    private static Codec<StatePropertiesPredicate> statePropertiesCodec() {
        Codec<StatePropertiesPredicate.Exact> exact = Codec.STRING.xmap(StatePropertiesPredicate.Exact::new,
                StatePropertiesPredicate.Exact::value);
        Codec<StatePropertiesPredicate.Ranged> ranged = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("min").forGetter(StatePropertiesPredicate.Ranged::min),
                Codec.STRING.optionalFieldOf("max").forGetter(StatePropertiesPredicate.Ranged::max)
        ).apply(i, StatePropertiesPredicate.Ranged::new));
        // 原版 ValueMatcher.CODEC：先按精确取值解码，再按范围解码
        Codec<StatePropertiesPredicate.ValueMatcher> matcher = Codec.either(exact, ranged).xmap(
                either -> either.<StatePropertiesPredicate.ValueMatcher>map(m -> m, m -> m),
                m -> switch (m) {
                    case StatePropertiesPredicate.Exact e -> Either.left(e);
                    case StatePropertiesPredicate.Ranged r -> Either.right(r);
                });
        return Codec.unboundedMap(Codec.STRING, matcher).xmap(
                map -> new StatePropertiesPredicate(map.entrySet().stream()
                        .map(entry -> new StatePropertiesPredicate.PropertyMatcher(entry.getKey(), entry.getValue()))
                        .toList()),
                predicate -> {
                    var map = new LinkedHashMap<String, StatePropertiesPredicate.ValueMatcher>();
                    predicate.properties().forEach(property -> map.put(property.name(), property.valueMatcher()));
                    return map;
                });
    }

    // ------------------------------------------------------------------
    // 物品与组件
    // ------------------------------------------------------------------

    private static Codec<ItemPredicate> itemPredicate(Codec<ItemPredicate> self) {
        MapCodec<ComponentMatchers> matchers = componentMatchers(componentPredicates(self));
        return RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.homogeneousList(RegistryIds.ITEM).optionalFieldOf("items").forGetter(ItemPredicate::items),
                MinMaxBounds.INTS.optionalFieldOf("count", ANY_INT).forGetter(ItemPredicate::count),
                matchers.forGetter(ItemPredicate::components)
        ).apply(i, ItemPredicate::new));
    }

    private static MapCodec<ComponentMatchers> componentMatchers(Codec<List<ComponentPredicate>> predicates) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                ItemCodecs.DATA_COMPONENT_MAP.optionalFieldOf("components", DataComponentMap.EMPTY).forGetter(ComponentMatchers::exact),
                predicates.optionalFieldOf("predicates", List.of()).forGetter(ComponentMatchers::partial)
        ).apply(i, ComponentMatchers::new));
    }

    /**
     * 原版 {@code DataComponentPredicate$Type}。具体的谓词类型是单例，按引用比较；任意组件类型的谓词与原版
     * {@code AnyValueType.create} 相同，每次解码新建，两个不同的键不会被当作重复的键。
     * {@code id} 是组件谓词类型的 ID，任意组件类型的谓词没有它；{@code anyComponent} 是任意组件类型的谓词检查的组件，
     * 具体的谓词类型没有它。
     */
    private static final class ComponentPredicateType {
        private final @Nullable Key id;
        private final @Nullable DataComponent<?> anyComponent;
        private final Codec<? extends ComponentPredicate> codec;

        private ComponentPredicateType(@Nullable Key id, @Nullable DataComponent<?> anyComponent,
                                       Codec<? extends ComponentPredicate> codec) {
            this.id = id;
            this.anyComponent = anyComponent;
            this.codec = codec;
        }

        @Override
        public String toString() {
            return id != null ? id.asString() : Objects.requireNonNull(anyComponent).key().asString();
        }
    }

    private static Codec<List<ComponentPredicate>> componentPredicates(Codec<ItemPredicate> item) {
        var types = new LinkedHashMap<Key, ComponentPredicateType>();
        var enchantment = enchantmentPredicate();
        var firework = fireworkPredicate();
        register(types, "damage", RecordCodecBuilder.<ComponentPredicate.Damage>create(i -> i.group(
                MinMaxBounds.INTS.optionalFieldOf("durability", ANY_INT).forGetter(ComponentPredicate.Damage::durability),
                MinMaxBounds.INTS.optionalFieldOf("damage", ANY_INT).forGetter(ComponentPredicate.Damage::damage)
        ).apply(i, ComponentPredicate.Damage::new)));
        register(types, "enchantments", enchantment.listOf().xmap(ComponentPredicate.Enchantments::new,
                ComponentPredicate.Enchantments::enchantments));
        register(types, "stored_enchantments", enchantment.listOf().xmap(ComponentPredicate.StoredEnchantments::new,
                ComponentPredicate.StoredEnchantments::enchantments));
        register(types, "potion_contents", RegistryCodecs.homogeneousList(RegistryIds.POTION)
                .xmap(ComponentPredicate.Potions::new, ComponentPredicate.Potions::potions));
        register(types, "custom_data", NbtCodecs.LENIENT_COMPOUND_TAG
                .xmap(ComponentPredicate.CustomData::new, ComponentPredicate.CustomData::tag));
        register(types, "container", RecordCodecBuilder.<ComponentPredicate.Container>create(i -> i.group(
                collection(item).optionalFieldOf("items").forGetter(ComponentPredicate.Container::items)
        ).apply(i, ComponentPredicate.Container::new)));
        register(types, "bundle_contents", RecordCodecBuilder.<ComponentPredicate.BundleContents>create(i -> i.group(
                collection(item).optionalFieldOf("items").forGetter(ComponentPredicate.BundleContents::items)
        ).apply(i, ComponentPredicate.BundleContents::new)));
        register(types, "firework_explosion", firework.xmap(ComponentPredicate.FireworkExplosion::new,
                ComponentPredicate.FireworkExplosion::predicate));
        register(types, "fireworks", RecordCodecBuilder.<ComponentPredicate.Fireworks>create(i -> i.group(
                collection(firework).optionalFieldOf("explosions").forGetter(ComponentPredicate.Fireworks::explosions),
                MinMaxBounds.INTS.optionalFieldOf("flight_duration", ANY_INT).forGetter(ComponentPredicate.Fireworks::flightDuration)
        ).apply(i, ComponentPredicate.Fireworks::new)));
        register(types, "writable_book_content", RecordCodecBuilder.<ComponentPredicate.WritableBook>create(i -> i.group(
                collection(Codec.STRING).optionalFieldOf("pages").forGetter(ComponentPredicate.WritableBook::pages)
        ).apply(i, ComponentPredicate.WritableBook::new)));
        register(types, "written_book_content", RecordCodecBuilder.<ComponentPredicate.WrittenBook>create(i -> i.group(
                collection(ComponentSerialization.CODEC).optionalFieldOf("pages").forGetter(ComponentPredicate.WrittenBook::pages),
                Codec.STRING.optionalFieldOf("author").forGetter(ComponentPredicate.WrittenBook::author),
                Codec.STRING.optionalFieldOf("title").forGetter(ComponentPredicate.WrittenBook::title),
                MinMaxBounds.INTS.optionalFieldOf("generation", ANY_INT).forGetter(ComponentPredicate.WrittenBook::generation),
                Codec.BOOL.optionalFieldOf("resolved").forGetter(ComponentPredicate.WrittenBook::resolved)
        ).apply(i, ComponentPredicate.WrittenBook::new)));
        register(types, "attribute_modifiers", RecordCodecBuilder.<ComponentPredicate.AttributeModifiers>create(i -> i.group(
                collection(attributeEntryPredicate()).optionalFieldOf("modifiers").forGetter(ComponentPredicate.AttributeModifiers::modifiers)
        ).apply(i, ComponentPredicate.AttributeModifiers::new)));
        register(types, "trim", RecordCodecBuilder.<ComponentPredicate.Trim>create(i -> i.group(
                RegistryCodecs.homogeneousList(RegistryIds.TRIM_MATERIAL).optionalFieldOf("material").forGetter(ComponentPredicate.Trim::material),
                RegistryCodecs.homogeneousList(RegistryIds.TRIM_PATTERN).optionalFieldOf("pattern").forGetter(ComponentPredicate.Trim::pattern)
        ).apply(i, ComponentPredicate.Trim::new)));
        register(types, "jukebox_playable", RecordCodecBuilder.<ComponentPredicate.JukeboxPlayable>create(i -> i.group(
                RegistryCodecs.homogeneousList(RegistryIds.JUKEBOX_SONG).optionalFieldOf("song").forGetter(ComponentPredicate.JukeboxPlayable::song)
        ).apply(i, ComponentPredicate.JukeboxPlayable::new)));
        register(types, "villager/variant", RegistryCodecs.homogeneousList(RegistryIds.VILLAGER_TYPE)
                .xmap(ComponentPredicate.VillagerVariant::new, ComponentPredicate.VillagerVariant::villagerTypes));

        Map<Key, ComponentPredicateType> table = Map.copyOf(types);
        Codec<ComponentPredicateType> predicateType = RegistryCodecs.byName(
                RegistryId.of("data_component_predicate_type"), table::get, type -> type.id);
        // 原版 DataComponentPredicate$Type.CODEC：先按组件谓词类型解码，再按组件类型解码成 AnyValue
        Codec<ComponentPredicateType> typeCodec = Codec.either(predicateType, ItemCodecs.DATA_COMPONENT_TYPE).xmap(
                either -> either.map(type -> type, PredicateCodecs::anyValueType),
                type -> type.anyComponent == null ? Either.left(type) : Either.right(type.anyComponent));
        return Codec.<ComponentPredicateType, ComponentPredicate>dispatchedMap(typeCodec, type -> type.codec)
                .xmap(map -> List.copyOf(map.values()), list -> {
                    var map = new LinkedHashMap<ComponentPredicateType, ComponentPredicate>();
                    for (var predicate : list) {
                        var type = predicate instanceof ComponentPredicate.AnyValue(var component)
                                ? anyValueType(component)
                                : table.get(componentPredicateId(predicate));
                        map.put(type, predicate);
                    }
                    return map;
                });
    }

    private static ComponentPredicateType anyValueType(DataComponent<?> component) {
        var predicate = new ComponentPredicate.AnyValue(component);
        // 原版 MapCodec.unitCodec(predicate)：值必须是对象，内容不读取
        return new ComponentPredicateType(null, component, MapCodec.unit(predicate).codec());
    }

    private static void register(Map<Key, ComponentPredicateType> types, String id, Codec<? extends ComponentPredicate> codec) {
        var key = Key.key(id);
        types.put(key, new ComponentPredicateType(key, null, codec));
    }

    /**
     * 组件谓词在原版 {@code data_component_predicate_type} 注册表中的 ID，编码时使用。
     */
    private static Key componentPredicateId(ComponentPredicate predicate) {
        return Key.key(switch (predicate) {
            case ComponentPredicate.AnyValue anyValue -> anyValue.component().key().asString();
            case ComponentPredicate.Damage ignored -> "damage";
            case ComponentPredicate.Enchantments ignored -> "enchantments";
            case ComponentPredicate.StoredEnchantments ignored -> "stored_enchantments";
            case ComponentPredicate.Potions ignored -> "potion_contents";
            case ComponentPredicate.CustomData ignored -> "custom_data";
            case ComponentPredicate.Container ignored -> "container";
            case ComponentPredicate.BundleContents ignored -> "bundle_contents";
            case ComponentPredicate.FireworkExplosion ignored -> "firework_explosion";
            case ComponentPredicate.Fireworks ignored -> "fireworks";
            case ComponentPredicate.WritableBook ignored -> "writable_book_content";
            case ComponentPredicate.WrittenBook ignored -> "written_book_content";
            case ComponentPredicate.AttributeModifiers ignored -> "attribute_modifiers";
            case ComponentPredicate.Trim ignored -> "trim";
            case ComponentPredicate.JukeboxPlayable ignored -> "jukebox_playable";
            case ComponentPredicate.VillagerVariant ignored -> "villager/variant";
        });
    }

    private static Codec<EnchantmentPredicate> enchantmentPredicate() {
        return RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.homogeneousList(RegistryIds.ENCHANTMENT).optionalFieldOf("enchantments").forGetter(EnchantmentPredicate::enchantments),
                MinMaxBounds.INTS.optionalFieldOf("levels", ANY_INT).forGetter(EnchantmentPredicate::levels)
        ).apply(i, EnchantmentPredicate::new));
    }

    private static Codec<FireworkPredicate> fireworkPredicate() {
        return RecordCodecBuilder.create(i -> i.group(
                CommonCodecs.FIREWORK_SHAPE.optionalFieldOf("shape").forGetter(FireworkPredicate::shape),
                Codec.BOOL.optionalFieldOf("has_twinkle").forGetter(FireworkPredicate::twinkle),
                Codec.BOOL.optionalFieldOf("has_trail").forGetter(FireworkPredicate::trail)
        ).apply(i, FireworkPredicate::new));
    }

    private static Codec<AttributeEntryPredicate> attributeEntryPredicate() {
        return RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.homogeneousList(RegistryIds.ATTRIBUTE).optionalFieldOf("attribute").forGetter(AttributeEntryPredicate::attribute),
                Identifiers.CODEC.optionalFieldOf("id").forGetter(AttributeEntryPredicate::id),
                MinMaxBounds.DOUBLES.optionalFieldOf("amount", ANY_DOUBLE).forGetter(AttributeEntryPredicate::amount),
                CommonCodecs.ATTRIBUTE_OPERATION.optionalFieldOf("operation").forGetter(AttributeEntryPredicate::operation),
                CommonCodecs.EQUIPMENT_SLOT_GROUP.optionalFieldOf("slot").forGetter(AttributeEntryPredicate::slot)
        ).apply(i, AttributeEntryPredicate::new));
    }

    /**
     * 原版 {@code CollectionPredicate.codec}：{@code contains}（元素谓词列表）、{@code count}
     * （{@code {test, count}} 列表）、{@code size}，都可选。
     */
    static <P> Codec<CollectionPredicate<P>> collection(Codec<P> element) {
        Codec<CollectionPredicate.CountEntry<P>> countEntry = RecordCodecBuilder.create(i -> i.group(
                element.fieldOf("test").forGetter(CollectionPredicate.CountEntry::test),
                MinMaxBounds.INTS.fieldOf("count").forGetter(CollectionPredicate.CountEntry::count)
        ).apply(i, CollectionPredicate.CountEntry::new));
        return RecordCodecBuilder.create(i -> i.group(
                element.listOf().optionalFieldOf("contains").forGetter(CollectionPredicate::contains),
                countEntry.listOf().optionalFieldOf("count").forGetter(CollectionPredicate::count),
                MinMaxBounds.INTS.optionalFieldOf("size").forGetter(CollectionPredicate::size)
        ).apply(i, CollectionPredicate::new));
    }

    // ------------------------------------------------------------------
    // 位置
    // ------------------------------------------------------------------

    private static Codec<LocationPredicate> locationCodec() {
        Codec<LocationPredicate.PositionPredicate> position = RecordCodecBuilder.create(i -> i.group(
                MinMaxBounds.DOUBLES.optionalFieldOf("x", ANY_DOUBLE).forGetter(LocationPredicate.PositionPredicate::x),
                MinMaxBounds.DOUBLES.optionalFieldOf("y", ANY_DOUBLE).forGetter(LocationPredicate.PositionPredicate::y),
                MinMaxBounds.DOUBLES.optionalFieldOf("z", ANY_DOUBLE).forGetter(LocationPredicate.PositionPredicate::z)
        ).apply(i, LocationPredicate.PositionPredicate::new));
        Codec<LocationPredicate.LightPredicate> light = RecordCodecBuilder.create(i -> i.group(
                MinMaxBounds.INTS.optionalFieldOf("light", ANY_INT).forGetter(LocationPredicate.LightPredicate::light)
        ).apply(i, LocationPredicate.LightPredicate::new));
        return RecordCodecBuilder.create(i -> i.group(
                position.optionalFieldOf("position").forGetter(LocationPredicate::position),
                RegistryCodecs.homogeneousList(RegistryIds.BIOME).optionalFieldOf("biomes").forGetter(LocationPredicate::biomes),
                RegistryCodecs.homogeneousList(RegistryIds.STRUCTURE).optionalFieldOf("structures").forGetter(LocationPredicate::structures),
                Identifiers.CODEC.optionalFieldOf("dimension").forGetter(LocationPredicate::dimension),
                Codec.BOOL.optionalFieldOf("smokey").forGetter(LocationPredicate::smokey),
                light.optionalFieldOf("light").forGetter(LocationPredicate::light),
                BLOCK.optionalFieldOf("block").forGetter(LocationPredicate::block),
                FLUID.optionalFieldOf("fluid").forGetter(LocationPredicate::fluid),
                Codec.BOOL.optionalFieldOf("can_see_sky").forGetter(LocationPredicate::canSeeSky)
        ).apply(i, LocationPredicate::new));
    }

    // ------------------------------------------------------------------
    // 伤害来源
    // ------------------------------------------------------------------

    private static Codec<DamageSourcePredicate> damageSourceCodec() {
        Codec<DamageSourcePredicate.TagPredicate<net.minestom.server.entity.damage.DamageType>> tag = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.tagKey(RegistryIds.DAMAGE_TYPE).fieldOf("id").forGetter(DamageSourcePredicate.TagPredicate::tag),
                Codec.BOOL.fieldOf("expected").forGetter(DamageSourcePredicate.TagPredicate::expected)
        ).apply(i, DamageSourcePredicate.TagPredicate::new));
        return RecordCodecBuilder.create(i -> i.group(
                tag.listOf().optionalFieldOf("tags", List.of()).forGetter(DamageSourcePredicate::tags),
                ENTITY.optionalFieldOf("direct_entity").forGetter(DamageSourcePredicate::directEntity),
                ENTITY.optionalFieldOf("source_entity").forGetter(DamageSourcePredicate::sourceEntity),
                Codec.BOOL.optionalFieldOf("is_direct").forGetter(DamageSourcePredicate::isDirect)
        ).apply(i, DamageSourcePredicate::new));
    }

    // ------------------------------------------------------------------
    // 实体
    // ------------------------------------------------------------------

    /**
     * 原版 {@code entity_sub_predicate_type} 注册表中的一项：注册表的值就是子谓词的编解码器，按 ID 比较。
     */
    private record SubPredicateType(Key id, Codec<? extends EntitySubPredicate> codec) {
        @Override
        public @NotNull String toString() {
            return id.asString();
        }
    }

    private static Codec<EntityPredicate> entityPredicate(Codec<EntityPredicate> self) {
        var types = new LinkedHashMap<Key, SubPredicateType>();
        sub(types, "entity_type", RegistryCodecs.homogeneousList(RegistryIds.ENTITY_TYPE)
                .xmap(EntitySubPredicate.EntityTypes::new, EntitySubPredicate.EntityTypes::types));
        sub(types, "location", LOCATION.xmap(EntitySubPredicate.Location::new, EntitySubPredicate.Location::predicate));
        sub(types, "stepping_on", LOCATION.xmap(EntitySubPredicate.SteppingOn::new, EntitySubPredicate.SteppingOn::predicate));
        sub(types, "movement_affected_by", LOCATION.xmap(EntitySubPredicate.MovementAffectedBy::new,
                EntitySubPredicate.MovementAffectedBy::predicate));
        sub(types, "distance", distancePredicate().xmap(EntitySubPredicate.Distance::new, EntitySubPredicate.Distance::distance));
        sub(types, "movement", RecordCodecBuilder.<EntitySubPredicate.Movement>create(i -> i.group(
                MinMaxBounds.DOUBLES.optionalFieldOf("x", ANY_DOUBLE).forGetter(EntitySubPredicate.Movement::x),
                MinMaxBounds.DOUBLES.optionalFieldOf("y", ANY_DOUBLE).forGetter(EntitySubPredicate.Movement::y),
                MinMaxBounds.DOUBLES.optionalFieldOf("z", ANY_DOUBLE).forGetter(EntitySubPredicate.Movement::z),
                MinMaxBounds.DOUBLES.optionalFieldOf("speed", ANY_DOUBLE).forGetter(EntitySubPredicate.Movement::speed),
                MinMaxBounds.DOUBLES.optionalFieldOf("horizontal_speed", ANY_DOUBLE).forGetter(EntitySubPredicate.Movement::horizontalSpeed),
                MinMaxBounds.DOUBLES.optionalFieldOf("vertical_speed", ANY_DOUBLE).forGetter(EntitySubPredicate.Movement::verticalSpeed),
                MinMaxBounds.DOUBLES.optionalFieldOf("fall_distance", ANY_DOUBLE).forGetter(EntitySubPredicate.Movement::fallDistance)
        ).apply(i, EntitySubPredicate.Movement::new)));
        sub(types, "effects", Codec.unboundedMap(mobEffect(), effectInstancePredicate())
                .xmap(EntitySubPredicate.Effects::new, EntitySubPredicate.Effects::effects));
        sub(types, "nbt", NbtCodecs.LENIENT_COMPOUND_TAG.xmap(EntitySubPredicate.Nbt::new, EntitySubPredicate.Nbt::nbt));
        sub(types, "flags", RecordCodecBuilder.<EntitySubPredicate.Flags>create(i -> i.group(
                Codec.BOOL.optionalFieldOf("is_on_ground").forGetter(EntitySubPredicate.Flags::isOnGround),
                Codec.BOOL.optionalFieldOf("is_on_fire").forGetter(EntitySubPredicate.Flags::isOnFire),
                Codec.BOOL.optionalFieldOf("is_sneaking").forGetter(EntitySubPredicate.Flags::isSneaking),
                Codec.BOOL.optionalFieldOf("is_sprinting").forGetter(EntitySubPredicate.Flags::isSprinting),
                Codec.BOOL.optionalFieldOf("is_swimming").forGetter(EntitySubPredicate.Flags::isSwimming),
                Codec.BOOL.optionalFieldOf("is_flying").forGetter(EntitySubPredicate.Flags::isFlying),
                Codec.BOOL.optionalFieldOf("is_baby").forGetter(EntitySubPredicate.Flags::isBaby),
                Codec.BOOL.optionalFieldOf("is_in_water").forGetter(EntitySubPredicate.Flags::isInWater),
                Codec.BOOL.optionalFieldOf("is_fall_flying").forGetter(EntitySubPredicate.Flags::isFallFlying)
        ).apply(i, EntitySubPredicate.Flags::new)));
        sub(types, "equipment", RecordCodecBuilder.<EntitySubPredicate.Equipment>create(i -> i.group(
                ITEM.optionalFieldOf("head").forGetter(EntitySubPredicate.Equipment::head),
                ITEM.optionalFieldOf("chest").forGetter(EntitySubPredicate.Equipment::chest),
                ITEM.optionalFieldOf("legs").forGetter(EntitySubPredicate.Equipment::legs),
                ITEM.optionalFieldOf("feet").forGetter(EntitySubPredicate.Equipment::feet),
                ITEM.optionalFieldOf("body").forGetter(EntitySubPredicate.Equipment::body),
                ITEM.optionalFieldOf("mainhand").forGetter(EntitySubPredicate.Equipment::mainhand),
                ITEM.optionalFieldOf("offhand").forGetter(EntitySubPredicate.Equipment::offhand)
        ).apply(i, EntitySubPredicate.Equipment::new)));
        sub(types, "periodic_tick", ExtraCodecs.POSITIVE_INT.xmap(EntitySubPredicate.PeriodicTick::new,
                EntitySubPredicate.PeriodicTick::period));
        sub(types, "vehicle", self.xmap(EntitySubPredicate.Vehicle::new, EntitySubPredicate.Vehicle::vehicle));
        sub(types, "passenger", self.xmap(EntitySubPredicate.Passenger::new, EntitySubPredicate.Passenger::passenger));
        sub(types, "targeted_entity", self.xmap(EntitySubPredicate.TargetedEntity::new,
                EntitySubPredicate.TargetedEntity::targetedEntity));
        sub(types, "team", Codec.STRING.xmap(EntitySubPredicate.Team::new, EntitySubPredicate.Team::team));
        sub(types, "slots", Codec.unboundedMap(slotRange(), ITEM).xmap(EntitySubPredicate.Slots::new,
                EntitySubPredicate.Slots::slots));
        sub(types, "components", ItemCodecs.DATA_COMPONENT_MAP.xmap(EntitySubPredicate.Components::new,
                EntitySubPredicate.Components::components));
        sub(types, "predicates", COMPONENT_PREDICATES.xmap(EntitySubPredicate.Predicates::new,
                EntitySubPredicate.Predicates::predicates));
        sub(types, "entity_tags", RecordCodecBuilder.<EntitySubPredicate.EntityTags>create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("any_of").forGetter(EntitySubPredicate.EntityTags::anyOf),
                Codec.STRING.listOf().optionalFieldOf("all_of").forGetter(EntitySubPredicate.EntityTags::allOf),
                Codec.STRING.listOf().optionalFieldOf("none_of").forGetter(EntitySubPredicate.EntityTags::noneOf)
        ).apply(i, EntitySubPredicate.EntityTags::new)));
        sub(types, "type_specific/lightning", RecordCodecBuilder.<EntitySubPredicate.Lightning>create(i -> i.group(
                MinMaxBounds.INTS.optionalFieldOf("blocks_set_on_fire", ANY_INT).forGetter(EntitySubPredicate.Lightning::blocksSetOnFire),
                self.optionalFieldOf("entity_struck").forGetter(EntitySubPredicate.Lightning::entityStruck)
        ).apply(i, EntitySubPredicate.Lightning::new)));
        sub(types, "type_specific/fishing_hook", RecordCodecBuilder.<EntitySubPredicate.FishingHook>create(i -> i.group(
                Codec.BOOL.optionalFieldOf("in_open_water").forGetter(EntitySubPredicate.FishingHook::inOpenWater)
        ).apply(i, EntitySubPredicate.FishingHook::new)));
        sub(types, "type_specific/player", playerPredicate(self));
        sub(types, "type_specific/cube_mob", RecordCodecBuilder.<EntitySubPredicate.CubeMob>create(i -> i.group(
                MinMaxBounds.INTS.optionalFieldOf("size", ANY_INT).forGetter(EntitySubPredicate.CubeMob::size)
        ).apply(i, EntitySubPredicate.CubeMob::new)));
        sub(types, "type_specific/raider", RecordCodecBuilder.<EntitySubPredicate.Raider>create(i -> i.group(
                Codec.BOOL.optionalFieldOf("has_raid", false).forGetter(EntitySubPredicate.Raider::hasRaid),
                Codec.BOOL.optionalFieldOf("is_captain", false).forGetter(EntitySubPredicate.Raider::isCaptain)
        ).apply(i, EntitySubPredicate.Raider::new)));
        sub(types, "type_specific/sheep", RecordCodecBuilder.<EntitySubPredicate.Sheep>create(i -> i.group(
                Codec.BOOL.optionalFieldOf("sheared").forGetter(EntitySubPredicate.Sheep::sheared)
        ).apply(i, EntitySubPredicate.Sheep::new)));

        Map<Key, SubPredicateType> table = Map.copyOf(types);
        Codec<SubPredicateType> typeCodec = RegistryCodecs.byName(RegistryId.of("entity_sub_predicate_type"),
                table::get, SubPredicateType::id);
        return Codec.<SubPredicateType, EntitySubPredicate>dispatchedMap(typeCodec, SubPredicateType::codec)
                .xmap(map -> new EntityPredicate(List.copyOf(map.values())), predicate -> {
                    var map = new LinkedHashMap<SubPredicateType, EntitySubPredicate>();
                    for (var part : predicate.parts()) {
                        map.put(table.get(subPredicateId(part)), part);
                    }
                    return map;
                });
    }

    /**
     * 实体子谓词在原版 {@code entity_sub_predicate_type} 注册表中的 ID，编码时使用。
     */
    static Key subPredicateId(EntitySubPredicate predicate) {
        return Key.key(switch (predicate) {
            case EntitySubPredicate.EntityTypes ignored -> "entity_type";
            case EntitySubPredicate.Location ignored -> "location";
            case EntitySubPredicate.SteppingOn ignored -> "stepping_on";
            case EntitySubPredicate.MovementAffectedBy ignored -> "movement_affected_by";
            case EntitySubPredicate.Distance ignored -> "distance";
            case EntitySubPredicate.Movement ignored -> "movement";
            case EntitySubPredicate.Effects ignored -> "effects";
            case EntitySubPredicate.Nbt ignored -> "nbt";
            case EntitySubPredicate.Flags ignored -> "flags";
            case EntitySubPredicate.Equipment ignored -> "equipment";
            case EntitySubPredicate.PeriodicTick ignored -> "periodic_tick";
            case EntitySubPredicate.Vehicle ignored -> "vehicle";
            case EntitySubPredicate.Passenger ignored -> "passenger";
            case EntitySubPredicate.TargetedEntity ignored -> "targeted_entity";
            case EntitySubPredicate.Team ignored -> "team";
            case EntitySubPredicate.Slots ignored -> "slots";
            case EntitySubPredicate.Components ignored -> "components";
            case EntitySubPredicate.Predicates ignored -> "predicates";
            case EntitySubPredicate.EntityTags ignored -> "entity_tags";
            case EntitySubPredicate.Lightning ignored -> "type_specific/lightning";
            case EntitySubPredicate.FishingHook ignored -> "type_specific/fishing_hook";
            case EntitySubPredicate.Player ignored -> "type_specific/player";
            case EntitySubPredicate.CubeMob ignored -> "type_specific/cube_mob";
            case EntitySubPredicate.Raider ignored -> "type_specific/raider";
            case EntitySubPredicate.Sheep ignored -> "type_specific/sheep";
        });
    }

    private static void sub(Map<Key, SubPredicateType> types, String id, Codec<? extends EntitySubPredicate> codec) {
        var key = Key.key(id);
        types.put(key, new SubPredicateType(key, codec));
    }

    /**
     * 原版 {@code MobEffect.CODEC}：状态效果注册表的 ID。
     */
    static Codec<PotionEffect> mobEffect() {
        return RegistryCodecs.byName(RegistryIds.MOB_EFFECT, PotionEffect::fromKey, PotionEffect::key);
    }

    /**
     * 原版 {@code SlotRanges.CODEC}：{@code StringRepresentable.fromValues}。
     */
    static Codec<SlotRange> slotRange() {
        return StringRepresentables.fromValues(SlotRange.VALUES.toArray(SlotRange[]::new), SlotRange::name);
    }

    private static Codec<DistancePredicate> distancePredicate() {
        return RecordCodecBuilder.create(i -> i.group(
                MinMaxBounds.DOUBLES.optionalFieldOf("x", ANY_DOUBLE).forGetter(DistancePredicate::x),
                MinMaxBounds.DOUBLES.optionalFieldOf("y", ANY_DOUBLE).forGetter(DistancePredicate::y),
                MinMaxBounds.DOUBLES.optionalFieldOf("z", ANY_DOUBLE).forGetter(DistancePredicate::z),
                MinMaxBounds.DOUBLES.optionalFieldOf("horizontal", ANY_DOUBLE).forGetter(DistancePredicate::horizontal),
                MinMaxBounds.DOUBLES.optionalFieldOf("absolute", ANY_DOUBLE).forGetter(DistancePredicate::absolute)
        ).apply(i, DistancePredicate::new));
    }

    private static Codec<EffectInstancePredicate> effectInstancePredicate() {
        return RecordCodecBuilder.create(i -> i.group(
                MinMaxBounds.INTS.optionalFieldOf("amplifier", ANY_INT).forGetter(EffectInstancePredicate::amplifier),
                MinMaxBounds.INTS.optionalFieldOf("duration", ANY_INT).forGetter(EffectInstancePredicate::duration),
                Codec.BOOL.optionalFieldOf("ambient").forGetter(EffectInstancePredicate::ambient),
                Codec.BOOL.optionalFieldOf("visible").forGetter(EffectInstancePredicate::visible)
        ).apply(i, EffectInstancePredicate::new));
    }

    private static Codec<EntitySubPredicate.Player> playerPredicate(Codec<EntityPredicate> entity) {
        Codec<FoodPredicate> food = RecordCodecBuilder.create(i -> i.group(
                MinMaxBounds.INTS.optionalFieldOf("level", ANY_INT).forGetter(FoodPredicate::level),
                MinMaxBounds.DOUBLES.optionalFieldOf("saturation", ANY_DOUBLE).forGetter(FoodPredicate::saturation)
        ).apply(i, FoodPredicate::new));
        // 原版 GameType.CODEC：survival、creative、adventure、spectator
        Codec<GameMode> gameMode = StringRepresentables.fromEnum(GameMode.values(), mode -> mode.name().toLowerCase(Locale.ROOT));
        Codec<InputPredicate> input = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("forward").forGetter(InputPredicate::forward),
                Codec.BOOL.optionalFieldOf("backward").forGetter(InputPredicate::backward),
                Codec.BOOL.optionalFieldOf("left").forGetter(InputPredicate::left),
                Codec.BOOL.optionalFieldOf("right").forGetter(InputPredicate::right),
                Codec.BOOL.optionalFieldOf("jump").forGetter(InputPredicate::jump),
                Codec.BOOL.optionalFieldOf("sneak").forGetter(InputPredicate::sneak),
                Codec.BOOL.optionalFieldOf("sprint").forGetter(InputPredicate::sprint)
        ).apply(i, InputPredicate::new));
        // 原版 AdvancementPredicate.CODEC：先按布尔值解码，再按准则对象解码
        Codec<AdvancementPredicate> advancement = Codec.either(
                Codec.BOOL.xmap(AdvancementPredicate.Done::new, AdvancementPredicate.Done::done),
                Codec.unboundedMap(Codec.STRING, Codec.BOOL).xmap(AdvancementPredicate.Criteria::new, AdvancementPredicate.Criteria::criteria)
        ).xmap(either -> either.map(done -> (AdvancementPredicate) done, criteria -> criteria), predicate -> switch (predicate) {
            case AdvancementPredicate.Done done -> Either.left(done);
            case AdvancementPredicate.Criteria criteria -> Either.right(criteria);
        });
        return RecordCodecBuilder.create(i -> i.group(
                MinMaxBounds.INTS.optionalFieldOf("level", ANY_INT).forGetter(EntitySubPredicate.Player::level),
                food.optionalFieldOf("food", FoodPredicate.ANY).forGetter(EntitySubPredicate.Player::food),
                gameMode.listOf().optionalFieldOf("gamemode", Arrays.asList(GameMode.values())).forGetter(EntitySubPredicate.Player::gameModes),
                statMatcher().listOf().optionalFieldOf("stats", List.of()).forGetter(EntitySubPredicate.Player::stats),
                Codec.unboundedMap(Identifiers.CODEC, Codec.BOOL).optionalFieldOf("recipes", Map.of()).forGetter(EntitySubPredicate.Player::recipes),
                Codec.unboundedMap(Identifiers.CODEC, advancement).optionalFieldOf("advancements", Map.of()).forGetter(EntitySubPredicate.Player::advancements),
                entity.optionalFieldOf("looking_at").forGetter(EntitySubPredicate.Player::lookingAt),
                input.optionalFieldOf("input").forGetter(EntitySubPredicate.Player::input)
        ).apply(i, EntitySubPredicate.Player::new));
    }

    /**
     * 原版统计类型注册表 {@code stat_type} 中的一项：统计项从哪个注册表中取。
     */
    private enum StatType {
        MINED("mined", StatisticCategory.MINED, statKeys(RegistryIds.BLOCK, Block::fromKey, Block::key)),
        CRAFTED("crafted", StatisticCategory.CRAFTED, statKeys(RegistryIds.ITEM, Material::fromKey, Material::key)),
        USED("used", StatisticCategory.USED, statKeys(RegistryIds.ITEM, Material::fromKey, Material::key)),
        BROKEN("broken", StatisticCategory.BROKEN, statKeys(RegistryIds.ITEM, Material::fromKey, Material::key)),
        PICKED_UP("picked_up", StatisticCategory.PICKED_UP, statKeys(RegistryIds.ITEM, Material::fromKey, Material::key)),
        DROPPED("dropped", StatisticCategory.DROPPED, statKeys(RegistryIds.ITEM, Material::fromKey, Material::key)),
        KILLED("killed", StatisticCategory.KILLED, statKeys(RegistryIds.ENTITY_TYPE, EntityType::fromKey, EntityType::key)),
        KILLED_BY("killed_by", StatisticCategory.KILLED_BY, statKeys(RegistryIds.ENTITY_TYPE, EntityType::fromKey, EntityType::key)),
        CUSTOM("custom", StatisticCategory.CUSTOM, statKeys(RegistryId.of("custom_stat"),
                StatisticType::fromKey, StatisticType::key));

        private final Key id;
        private final StatisticCategory category;
        private final Codec<Key> statCodec;

        StatType(String id, StatisticCategory category, Codec<Key> statCodec) {
            this.id = Key.key(id);
            this.category = category;
            this.statCodec = statCodec;
        }
    }

    /**
     * 原版 {@code StatType#getRegistry().holderByNameCodec()}：统计项在对应注册表中的 ID。
     */
    private static <T> Codec<Key> statKeys(RegistryId<T> registry, Function<Key, @Nullable T> lookup, Function<T, Key> toKey) {
        return RegistryCodecs.byName(registry, lookup, toKey).xmap(toKey, lookup);
    }

    /**
     * 原版 {@code PlayerPredicate$StatMatcher.CODEC}：按 {@code type}（原版 {@code stat_type} 注册表）分派，
     * 字段 {@code stat}、{@code value}（缺省不限）。
     */
    private static Codec<StatMatcher> statMatcher() {
        var byId = new LinkedHashMap<Key, StatType>();
        for (var type : StatType.values()) {
            byId.put(type.id, type);
        }
        Map<Key, StatType> table = Map.copyOf(byId);
        Map<StatisticCategory, StatType> byCategory = new LinkedHashMap<>();
        for (var type : StatType.values()) {
            byCategory.put(type.category, type);
        }
        Codec<StatType> typeCodec = RegistryCodecs.byName(RegistryId.of("stat_type"), table::get, type -> type.id);
        return typeCodec.dispatch(matcher -> byCategory.get(matcher.category()), type -> RecordCodecBuilder.mapCodec(i -> i.group(
                type.statCodec.fieldOf("stat").forGetter(StatMatcher::stat),
                MinMaxBounds.INTS.optionalFieldOf("value", ANY_INT).forGetter(StatMatcher::value)
        ).apply(i, (stat, value) -> new StatMatcher(type.category, stat, value))));
    }

    /**
     * 原版 {@code Holder.Reference#toString}，错误文本中打印注册表引用时使用，例如
     * {@code Reference{ResourceKey[minecraft:block / minecraft:stone]=Block{minecraft:stone}}}。
     */
    static String holderString(RegistryId<?> registry, Key id, String value) {
        return "Reference{" + registry.element(id) + "=" + value + "}";
    }
}
