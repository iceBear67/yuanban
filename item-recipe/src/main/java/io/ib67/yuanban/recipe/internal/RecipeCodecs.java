package io.ib67.yuanban.recipe.internal;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.ComponentSerialization;
import io.ib67.yuanban.codec.ExtraCodecs;
import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.Identifiers;
import io.ib67.yuanban.codec.ItemCodecs;
import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.codec.RegistryCodecs;
import io.ib67.yuanban.codec.RegistryGetter;
import io.ib67.yuanban.codec.RegistryId;
import io.ib67.yuanban.codec.RegistryIds;
import io.ib67.yuanban.codec.RegistryLookup;
import io.ib67.yuanban.codec.RegistryOps;
import io.ib67.yuanban.codec.StringRepresentables;
import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.Recipe;
import io.ib67.yuanban.crafting.recipe.kind.cooking.CookingBookInfo;
import io.ib67.yuanban.crafting.recipe.kind.cooking.CookingCategory;
import io.ib67.yuanban.crafting.recipe.kind.cooking.CookingKind;
import io.ib67.yuanban.crafting.recipe.kind.cooking.CookingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.CraftingBookInfo;
import io.ib67.yuanban.crafting.recipe.kind.crafting.CraftingCategory;
import io.ib67.yuanban.crafting.recipe.kind.crafting.DyeRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ImbueRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ShapedRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ShapelessRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.TransmuteRecipe;
import io.ib67.yuanban.crafting.recipe.kind.misc.IntBounds;
import io.ib67.yuanban.crafting.recipe.kind.misc.ItemStackTemplate;
import io.ib67.yuanban.crafting.recipe.kind.smithing.SmithingTransformRecipe;
import io.ib67.yuanban.crafting.recipe.kind.smithing.SmithingTrimRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.BannerDuplicateRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.BookCloningRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.DecoratedPotRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.FireworkRocketRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.FireworkStarFadeRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.FireworkStarRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.MapExtendingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.RepairItemRecipe;
import io.ib67.yuanban.crafting.recipe.kind.special.ShieldDecorationRecipe;
import io.ib67.yuanban.crafting.recipe.kind.stonecutter.StonecutterRecipe;
import io.ib67.yuanban.itemtag.ItemTags;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import it.unimi.dsi.fastutil.chars.CharSet;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.registry.Holder;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.utils.Range;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 配方文件的编解码器，移植原版 26.2 的 {@code Recipe.CODEC} 和各配方类的 {@code MAP_CODEC}：字段顺序、缺省值、校验和错误文本都与原版相同。
 * 解码时的注册表和物品标签由 {@link RegistryOps} 提供，见 {@link #registryOps(Registries, ItemTags)}。只用于解码。
 */
@ApiStatus.Internal
public final class RecipeCodecs {
    private static final RegistryId<RecipeSerializer> RECIPE_SERIALIZER = RegistryId.of("recipe_serializer");

    /**
     * 原版 {@code Ingredient.NON_AIR_HOLDER_SET_CODEC}：{@code HolderSetCodec.create(ITEM, Item.CODEC, false)}。
     */
    private static final Codec<HolderSet<Material>> NON_AIR_HOLDER_SET = RegistryCodecs.homogeneousList(RegistryIds.ITEM, ItemCodecs.ITEM, false);
    private static final Codec<HolderSet<Material>> INGREDIENT_VALUES = ExtraCodecs.nonEmptyHolderSet(NON_AIR_HOLDER_SET);

    /**
     * 原版 {@code Ingredient.CODEC}：{@code nonEmptyHolderSet(NON_AIR_HOLDER_SET_CODEC)}。
     * 标签原料在解码时取 ops 中这个标签的内容，与原版重载时把标签原料绑定到待提交的标签相同。
     */
    private static final Codec<ItemIngredient> INGREDIENT = new Codec<>() {
        @Override
        public <T> DataResult<Pair<ItemIngredient, T>> decode(DynamicOps<T> ops, T input) {
            return INGREDIENT_VALUES.decode(ops, input).map(pair -> pair.mapFirst(values -> toIngredient(ops, values)));
        }

        @Override
        public <T> DataResult<T> encode(ItemIngredient input, DynamicOps<T> ops, T prefix) {
            return INGREDIENT_VALUES.encode(toHolderSet(input), ops, prefix);
        }

        @Override
        public String toString() {
            return "Ingredient[" + INGREDIENT_VALUES + "]";
        }
    };

    /**
     * 原版 {@code ItemStackTemplate.CODEC}，即 {@link ItemCodecs#ITEM_STACK_TEMPLATE}，结果换成 {@code crafting} 的
     * {@link ItemStackTemplate}。组件补丁原样保存，与物品原型相同的条目也保留。
     */
    private static final Codec<ItemStackTemplate> ITEM_STACK_TEMPLATE = ItemCodecs.ITEM_STACK_TEMPLATE.xmap(
            template -> new ItemStackTemplate(template.item(), template.count(), template.components()),
            template -> new io.ib67.yuanban.codec.ItemStackTemplate(template.material(), template.count(), template.components()));

    /**
     * 原版 {@code CraftingRecipe.CraftingBookInfo.MAP_CODEC}。
     */
    private static final MapCodec<CraftingBookInfo> CRAFTING_BOOK = RecordCodecBuilder.mapCodec(i -> i.group(
            StringRepresentables.fromEnum(CraftingCategory.values(), RecipeCodecs::serializedName)
                    .optionalFieldOf("category", CraftingCategory.MISC).forGetter(CraftingBookInfo::category),
            Codec.STRING.optionalFieldOf("group", "").forGetter(CraftingBookInfo::group)
    ).apply(i, CraftingBookInfo::new));

    /**
     * 原版 {@code AbstractCookingRecipe.CookingBookInfo.MAP_CODEC}。
     */
    private static final MapCodec<CookingBookInfo> COOKING_BOOK = RecordCodecBuilder.mapCodec(i -> i.group(
            StringRepresentables.fromEnum(CookingCategory.values(), RecipeCodecs::serializedName)
                    .optionalFieldOf("category", CookingCategory.MISC).forGetter(CookingBookInfo::category),
            Codec.STRING.optionalFieldOf("group", "").forGetter(CookingBookInfo::group)
    ).apply(i, CookingBookInfo::new));

    /**
     * 原版 {@code TransmuteRecipe.MATERIAL_COUNT_BOUNDS}。
     */
    private static final Codec<IntBounds> MATERIAL_COUNT_BOUNDS = containedIn(new Range.Int(1, 8));

    /**
     * 原版 {@code BookCloningRecipe.ALLOWED_GENERATION_CODEC}。
     */
    private static final Codec<IntBounds> ALLOWED_GENERATION = containedIn(new Range.Int(0, 2));

    /**
     * 原版 {@code FireworkExplosion.Shape.CODEC}。
     */
    private static final Codec<FireworkExplosion.Shape> FIREWORK_SHAPE = StringRepresentables.fromEnum(FireworkExplosion.Shape.values(), RecipeCodecs::serializedName);

    /**
     * 原版 {@code TrimPattern.DIRECT_CODEC}：{@code decal} 缺省为 {@code false}。
     * Minestom 的 {@link TrimPattern#REGISTRY_CODEC} 要求写出 {@code decal}，所以这里按原版移植。
     */
    private static final Codec<TrimPattern> TRIM_PATTERN_DIRECT = RecordCodecBuilder.create(i -> i.group(
            Identifiers.CODEC.fieldOf("asset_id").forGetter(TrimPattern::assetId),
            ComponentSerialization.CODEC.fieldOf("description").forGetter(TrimPattern::description),
            ExtraCodecs.optionalAlwaysPresentFieldOf(Codec.BOOL, "decal", false).forGetter(TrimPattern::isDecal)
    ).apply(i, TrimPattern::create));

    /**
     * 原版 {@code TrimPattern.CODEC}：{@code RegistryFileCodec.create(TRIM_PATTERN, DIRECT_CODEC)}，
     * ID 在 {@code trim_pattern} 注册表中查找，内联对象按 {@link #TRIM_PATTERN_DIRECT} 解码。
     */
    private static final Codec<Holder<TrimPattern>> TRIM_PATTERN = RegistryCodecs.file(RegistryIds.TRIM_PATTERN,
            TRIM_PATTERN_DIRECT, true);

    /**
     * 原版 {@code ShapedRecipePattern.Data.PATTERN_CODEC}。
     */
    private static final Codec<List<String>> PATTERN = Codec.STRING.listOf().comapFlatMap(rows -> {
        if (rows.size() > 3) {
            return DataResult.error(() -> "Invalid pattern: too many rows, 3 is maximum");
        }
        if (rows.isEmpty()) {
            return DataResult.error(() -> "Invalid pattern: empty pattern not allowed");
        }
        int firstLength = rows.getFirst().length();
        for (var row : rows) {
            if (row.length() > 3) {
                return DataResult.error(() -> "Invalid pattern: too many columns, 3 is maximum");
            }
            if (firstLength != row.length()) {
                return DataResult.error(() -> "Invalid pattern: each row must be the same width");
            }
        }
        return DataResult.success(rows);
    }, Function.identity());

    /**
     * 原版 {@code ShapedRecipePattern.Data.SYMBOL_CODEC}。
     */
    private static final Codec<Character> SYMBOL = Codec.STRING.comapFlatMap(symbol -> {
        if (symbol.length() != 1) {
            return DataResult.error(() -> "Invalid key entry: '" + symbol + "' is an invalid symbol (must be 1 character only).");
        }
        return " ".equals(symbol)
                ? DataResult.error(() -> "Invalid key entry: ' ' is a reserved symbol.")
                : DataResult.success(symbol.charAt(0));
    }, String::valueOf);

    /**
     * 原版 {@code ShapedRecipePattern.MAP_CODEC}：{@code key} 用 {@code ExtraCodecs.strictUnboundedMap} 解码。
     */
    private static final MapCodec<ShapedPattern> SHAPED_PATTERN = RecordCodecBuilder.<PatternData>mapCodec(i -> i.group(
            ExtraCodecs.strictUnboundedMap(SYMBOL, INGREDIENT).fieldOf("key").forGetter(PatternData::key),
            PATTERN.fieldOf("pattern").forGetter(PatternData::pattern)
    ).apply(i, PatternData::new)).flatXmap(RecipeCodecs::unpack, _ -> DataResult.error(() -> "Cannot encode unpacked recipe"));

    private static final Map<Key, RecipeSerializer> SERIALIZERS = new LinkedHashMap<>();

    static {
        registerSerializers();
    }

    /**
     * 原版 {@code Recipe.CODEC}：按 {@code type} 在 {@code recipe_serializer} 注册表中查找序列化器，未知类型报
     * {@code Unknown registry key in ResourceKey[minecraft:root / minecraft:recipe_serializer]: <id>}。
     */
    public static final Codec<Recipe<?>> CODEC = RegistryCodecs.byName(RECIPE_SERIALIZER, SERIALIZERS::get, RecipeSerializer::id)
            .dispatch("type", recipe -> SERIALIZERS.get(typeOf(recipe)), RecipeSerializer::codec);

    private RecipeCodecs() {
    }

    /**
     * 解码配方使用的 {@link RegistryOps}：注册表是 {@code registries} 中的注册表及其标签，物品标签换成 {@code tags}。
     * 原版重载时同样用待提交的标签解码配方。
     *
     * @param registries Minestom 进程的注册表
     * @param tags       本次加载展开的物品标签
     */
    public static RegistryOps<JsonElement> registryOps(Registries registries, ItemTags tags) {
        var materials = registries.material();
        RegistryGetter<Material> items = RegistryGetter.of(
                id -> materials.get(id) != null,
                tag -> tags.find(tag).map(contents -> contents.stream().map(Material::key).toList()));
        return RegistryOps.create(JsonOps.INSTANCE, RegistryLookup.builder(registries)
                .put(RegistryIds.ITEM, items)
                .build());
    }

    /**
     * 原版 {@code RecipeSerializer}：注册名和这种配方的编解码器。
     */
    private record RecipeSerializer(Key id, MapCodec<? extends Recipe<?>> codec) {
    }

    private static void register(String id, MapCodec<? extends Recipe<?>> codec) {
        var key = Key.key(id);
        SERIALIZERS.put(key, new RecipeSerializer(key, codec));
    }

    /**
     * 原版 {@code Recipe.CommonInfo.MAP_CODEC}：检查 {@code show_notification}（缺省为 {@code true}）。
     * 配方解锁时才使用这个字段，全部配方对全部玩家已知，所以只解码、不保存。
     */
    private static <R> MapCodec<R> withCommonInfo(MapCodec<R> codec) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(_ -> true),
                codec.forGetter(r -> r)
        ).apply(i, (_, r) -> r));
    }

    private static void registerSerializers() {
        register("crafting_shaped", withCommonInfo(RecordCodecBuilder.<ShapedRecipe>mapCodec(i -> i.group(
                CRAFTING_BOOK.forGetter(ShapedRecipe::book),
                SHAPED_PATTERN.forGetter(r -> new ShapedPattern(r.width(), r.height(), r.ingredients())),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(ShapedRecipe::result)
        ).apply(i, (b, p, r) -> new ShapedRecipe(b, p.width(), p.height(), p.ingredients(), r)))));
        register("crafting_shapeless", withCommonInfo(RecordCodecBuilder.<ShapelessRecipe>mapCodec(i -> i.group(
                CRAFTING_BOOK.forGetter(ShapelessRecipe::book),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(ShapelessRecipe::result),
                INGREDIENT.listOf(1, 9).fieldOf("ingredients").forGetter(ShapelessRecipe::ingredients)
        ).apply(i, ShapelessRecipe::new))));
        register("crafting_dye", withCommonInfo(RecordCodecBuilder.<DyeRecipe>mapCodec(i -> i.group(
                CRAFTING_BOOK.forGetter(DyeRecipe::book),
                INGREDIENT.fieldOf("target").forGetter(DyeRecipe::target),
                INGREDIENT.fieldOf("dye").forGetter(DyeRecipe::dye),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(DyeRecipe::result)
        ).apply(i, DyeRecipe::new))));
        register("crafting_imbue", withCommonInfo(RecordCodecBuilder.<ImbueRecipe>mapCodec(i -> i.group(
                CRAFTING_BOOK.forGetter(ImbueRecipe::book),
                INGREDIENT.fieldOf("source").forGetter(ImbueRecipe::source),
                INGREDIENT.fieldOf("material").forGetter(ImbueRecipe::material),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(ImbueRecipe::result)
        ).apply(i, ImbueRecipe::new))));
        register("crafting_transmute", withCommonInfo(RecordCodecBuilder.<TransmuteRecipe>mapCodec(i -> i.group(
                CRAFTING_BOOK.forGetter(TransmuteRecipe::book),
                INGREDIENT.fieldOf("input").forGetter(TransmuteRecipe::input),
                INGREDIENT.fieldOf("material").forGetter(TransmuteRecipe::material),
                MATERIAL_COUNT_BOUNDS.optionalFieldOf("material_count", IntBounds.exactly(1)).forGetter(TransmuteRecipe::materialCount),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(TransmuteRecipe::result),
                Codec.BOOL.optionalFieldOf("add_material_count_to_result", false).forGetter(TransmuteRecipe::addMaterialCountToResult)
        ).apply(i, TransmuteRecipe::new))));
        register("crafting_decorated_pot", RecordCodecBuilder.<DecoratedPotRecipe>mapCodec(i -> i.group(
                INGREDIENT.fieldOf("back").forGetter(DecoratedPotRecipe::back),
                INGREDIENT.fieldOf("left").forGetter(DecoratedPotRecipe::left),
                INGREDIENT.fieldOf("right").forGetter(DecoratedPotRecipe::right),
                INGREDIENT.fieldOf("front").forGetter(DecoratedPotRecipe::front),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(DecoratedPotRecipe::result)
        ).apply(i, DecoratedPotRecipe::new)));
        register("crafting_special_bookcloning", RecordCodecBuilder.<BookCloningRecipe>mapCodec(i -> i.group(
                INGREDIENT.fieldOf("source").forGetter(BookCloningRecipe::source),
                INGREDIENT.fieldOf("material").forGetter(BookCloningRecipe::material),
                ALLOWED_GENERATION.optionalFieldOf("allowed_generations", IntBounds.between(0, 1)).forGetter(BookCloningRecipe::allowedGenerations),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(BookCloningRecipe::result)
        ).apply(i, BookCloningRecipe::new)));
        register("crafting_special_mapextending", RecordCodecBuilder.<MapExtendingRecipe>mapCodec(i -> i.group(
                INGREDIENT.fieldOf("map").forGetter(MapExtendingRecipe::map),
                INGREDIENT.fieldOf("material").forGetter(MapExtendingRecipe::material),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(MapExtendingRecipe::result)
        ).apply(i, MapExtendingRecipe::new)));
        register("crafting_special_firework_rocket", RecordCodecBuilder.<FireworkRocketRecipe>mapCodec(i -> i.group(
                INGREDIENT.fieldOf("shell").forGetter(FireworkRocketRecipe::shell),
                INGREDIENT.fieldOf("fuel").forGetter(FireworkRocketRecipe::fuel),
                INGREDIENT.fieldOf("star").forGetter(FireworkRocketRecipe::star),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(FireworkRocketRecipe::result)
        ).apply(i, FireworkRocketRecipe::new)));
        register("crafting_special_firework_star", RecordCodecBuilder.<FireworkStarRecipe>mapCodec(i -> i.group(
                Codec.simpleMap(FIREWORK_SHAPE, INGREDIENT, StringRepresentables.keys(FireworkExplosion.Shape.values(), RecipeCodecs::serializedName))
                        .fieldOf("shapes").forGetter(FireworkStarRecipe::shapes),
                INGREDIENT.fieldOf("trail").forGetter(FireworkStarRecipe::trail),
                INGREDIENT.fieldOf("twinkle").forGetter(FireworkStarRecipe::twinkle),
                INGREDIENT.fieldOf("fuel").forGetter(FireworkStarRecipe::fuel),
                INGREDIENT.fieldOf("dye").forGetter(FireworkStarRecipe::dye),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(FireworkStarRecipe::result)
        ).apply(i, FireworkStarRecipe::new)));
        register("crafting_special_firework_star_fade", RecordCodecBuilder.<FireworkStarFadeRecipe>mapCodec(i -> i.group(
                INGREDIENT.fieldOf("target").forGetter(FireworkStarFadeRecipe::target),
                INGREDIENT.fieldOf("dye").forGetter(FireworkStarFadeRecipe::dye),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(FireworkStarFadeRecipe::result)
        ).apply(i, FireworkStarFadeRecipe::new)));
        register("crafting_special_bannerduplicate", RecordCodecBuilder.<BannerDuplicateRecipe>mapCodec(i -> i.group(
                INGREDIENT.fieldOf("banner").forGetter(BannerDuplicateRecipe::banner),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(BannerDuplicateRecipe::result)
        ).apply(i, BannerDuplicateRecipe::new)));
        register("crafting_special_shielddecoration", RecordCodecBuilder.<ShieldDecorationRecipe>mapCodec(i -> i.group(
                INGREDIENT.fieldOf("banner").forGetter(ShieldDecorationRecipe::banner),
                INGREDIENT.fieldOf("target").forGetter(ShieldDecorationRecipe::target),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(ShieldDecorationRecipe::result)
        ).apply(i, ShieldDecorationRecipe::new)));
        register("crafting_special_repairitem", MapCodec.unit(new RepairItemRecipe()));
        for (var kind : CookingKind.values()) {
            register(kind.type(), withCommonInfo(RecordCodecBuilder.<CookingRecipe>mapCodec(i -> i.group(
                    COOKING_BOOK.forGetter(CookingRecipe::book),
                    INGREDIENT.fieldOf("ingredient").forGetter(CookingRecipe::ingredient),
                    ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(CookingRecipe::result),
                    Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(CookingRecipe::experience),
                    Codec.INT.optionalFieldOf("cookingtime", kind.defaultCookingTime()).forGetter(CookingRecipe::cookingTime)
            ).apply(i, (b, in, r, experience, time) -> new CookingRecipe(kind, b, in, r, experience, time)))));
        }
        register("stonecutting", withCommonInfo(RecordCodecBuilder.<StonecutterRecipe>mapCodec(i -> i.group(
                INGREDIENT.fieldOf("ingredient").forGetter(StonecutterRecipe::ingredient),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(StonecutterRecipe::result)
        ).apply(i, StonecutterRecipe::new))));
        register("smithing_transform", withCommonInfo(RecordCodecBuilder.<SmithingTransformRecipe>mapCodec(i -> i.group(
                INGREDIENT.optionalFieldOf("template").forGetter(SmithingTransformRecipe::template),
                INGREDIENT.fieldOf("base").forGetter(SmithingTransformRecipe::base),
                INGREDIENT.optionalFieldOf("addition").forGetter(SmithingTransformRecipe::addition),
                ITEM_STACK_TEMPLATE.fieldOf("result").forGetter(SmithingTransformRecipe::result)
        ).apply(i, SmithingTransformRecipe::new))));
        register("smithing_trim", withCommonInfo(RecordCodecBuilder.<SmithingTrimRecipe>mapCodec(i -> i.group(
                INGREDIENT.fieldOf("template").forGetter(SmithingTrimRecipe::template),
                INGREDIENT.fieldOf("base").forGetter(SmithingTrimRecipe::base),
                INGREDIENT.fieldOf("addition").forGetter(SmithingTrimRecipe::addition),
                TRIM_PATTERN.fieldOf("pattern").forGetter(SmithingTrimRecipe::pattern)
        ).apply(i, SmithingTrimRecipe::new))));
    }

    private static Key typeOf(Recipe<?> definition) {
        return switch (definition) {
            case ShapedRecipe _ -> Key.key("crafting_shaped");
            case ShapelessRecipe _ -> Key.key("crafting_shapeless");
            case DyeRecipe _ -> Key.key("crafting_dye");
            case ImbueRecipe _ -> Key.key("crafting_imbue");
            case TransmuteRecipe _ -> Key.key("crafting_transmute");
            case DecoratedPotRecipe _ -> Key.key("crafting_decorated_pot");
            case BookCloningRecipe _ -> Key.key("crafting_special_bookcloning");
            case MapExtendingRecipe _ -> Key.key("crafting_special_mapextending");
            case FireworkRocketRecipe _ -> Key.key("crafting_special_firework_rocket");
            case FireworkStarRecipe _ -> Key.key("crafting_special_firework_star");
            case FireworkStarFadeRecipe _ -> Key.key("crafting_special_firework_star_fade");
            case BannerDuplicateRecipe _ -> Key.key("crafting_special_bannerduplicate");
            case ShieldDecorationRecipe _ -> Key.key("crafting_special_shielddecoration");
            case RepairItemRecipe _ -> Key.key("crafting_special_repairitem");
            case CookingRecipe cooking -> Key.key(cooking.kind().type());
            case StonecutterRecipe _ -> Key.key("stonecutting");
            case SmithingTransformRecipe _ -> Key.key("smithing_transform");
            case SmithingTrimRecipe _ -> Key.key("smithing_trim");
            default -> throw new IllegalStateException("Unknown recipe type: " + definition.getClass());
        };
    }

    // 原料

    /**
     * 直接列出的物品成为 {@link ItemIngredient.OfItems}；标签成为 {@link ItemIngredient.OfTag}，物品取 ops 中这个标签的内容。
     * {@link RegistryCodecs#homogeneousList} 只在 ops 带有物品注册表、并且标签存在时才解码出标签。
     */
    private static <T> ItemIngredient toIngredient(DynamicOps<T> ops, HolderSet<Material> values) {
        return switch (values) {
            case HolderSet.Direct<Material> direct -> new ItemIngredient.OfItems(materials(direct.keys().stream().map(RegistryKey::key).toList()));
            case HolderSet.Named<Material> named -> {
                if (!(ops instanceof RegistryOps<T> registryOps)) {
                    throw new IllegalStateException("Item tag " + named.tag().key().asString() + " decoded without registry access");
                }
                var tag = named.tag().key();
                var contents = registryOps.getter(RegistryIds.ITEM).orElseThrow().tag(tag).orElseThrow();
                yield new ItemIngredient.OfTag(tag, materials(contents));
            }
        };
    }

    private static List<Material> materials(List<Key> ids) {
        return ids.stream().map(id -> Objects.requireNonNull(Material.fromKey(id), () -> "Unknown item " + id.asString())).toList();
    }

    private static HolderSet<Material> toHolderSet(ItemIngredient ingredient) {
        return switch (ingredient) {
            case ItemIngredient.OfTag tag -> HolderSet.named(tag.tag());
            case ItemIngredient.OfItems items -> HolderSet.direct(items.items());
        };
    }

    // 有序配方的图案：原版 ShapedRecipePattern

    private record ShapedPattern(int width, int height, List<Optional<ItemIngredient>> ingredients) {
    }

    /**
     * 原版 {@code ShapedRecipePattern.Data}。{@code key} 保持文件中键的顺序。
     */
    private record PatternData(Map<Character, ItemIngredient> key, List<String> pattern) {
    }

    /**
     * 原版 {@code ShapedRecipePattern.unpack}。未使用的符号按 {@code key} 中的顺序收集在 {@link CharArraySet} 中，
     * 错误文本按它的 {@code toString()} 打印，例如 {@code {a, b}}。
     */
    private static DataResult<ShapedPattern> unpack(PatternData data) {
        var rows = shrink(data.pattern());
        if (rows.isEmpty()) {
            // 原版在这里访问空数组的第一个元素，抛出未捕获的 ArrayIndexOutOfBoundsException
            return DataResult.error(() -> "Invalid pattern: pattern contains only spaces");
        }
        int width = rows.getFirst().length();
        int height = rows.size();
        var ingredients = new ArrayList<Optional<ItemIngredient>>(width * height);
        CharSet unusedSymbols = new CharArraySet(data.key().keySet());
        for (var row : rows) {
            for (int x = 0; x < row.length(); x++) {
                char symbol = row.charAt(x);
                Optional<ItemIngredient> ingredient;
                if (symbol == ' ') {
                    ingredient = Optional.empty();
                } else {
                    var ingredientForSymbol = data.key().get(symbol);
                    if (ingredientForSymbol == null) {
                        return DataResult.error(() -> "Pattern references symbol '" + symbol + "' but it's not defined in the key");
                    }
                    ingredient = Optional.of(ingredientForSymbol);
                }
                unusedSymbols.remove(symbol);
                ingredients.add(ingredient);
            }
        }
        if (!unusedSymbols.isEmpty()) {
            return DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + unusedSymbols);
        }
        return DataResult.success(new ShapedPattern(width, height, ingredients));
    }

    /**
     * 原版 {@code ShapedRecipePattern.shrink}：去掉四周全是空格的行和列。
     */
    private static List<String> shrink(List<String> pattern) {
        int left = Integer.MAX_VALUE;
        int right = 0;
        int top = 0;
        int bottom = 0;
        for (int i = 0; i < pattern.size(); i++) {
            var line = pattern.get(i);
            left = Math.min(left, firstNonSpace(line));
            int lastNonSpace = lastNonSpace(line);
            right = Math.max(right, lastNonSpace);
            if (lastNonSpace < 0) {
                if (top == i) {
                    top++;
                }
                bottom++;
            } else {
                bottom = 0;
            }
        }
        if (pattern.size() == bottom) {
            return List.of();
        }
        var result = new ArrayList<String>(pattern.size() - bottom - top);
        for (int line = top; line < pattern.size() - bottom; line++) {
            result.add(pattern.get(line).substring(left, right + 1));
        }
        return result;
    }

    private static int firstNonSpace(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private static int lastNonSpace(String line) {
        int index = line.length() - 1;
        while (index >= 0 && line.charAt(index) == ' ') {
            index--;
        }
        return index;
    }

    // 其他基础类型

    /**
     * 原版 {@code MinMaxBounds.Ints.CODEC.validate(MinMaxBounds.validateContainedInRange(allowed))}，结果换成 {@link IntBounds}。
     */
    private static Codec<IntBounds> containedIn(Range.Int allowed) {
        return MinMaxBounds.INTS.validate(MinMaxBounds.validateContainedInRange(allowed)).xmap(
                range -> new IntBounds(Optional.ofNullable(range.min()), Optional.ofNullable(range.max())),
                bounds -> new Range.Int(bounds.min().orElse(null), bounds.max().orElse(null)));
    }

    /**
     * 原版 {@code StringRepresentable.getSerializedName()}：这里用到的枚举都是小写的常量名。
     */
    private static String serializedName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
