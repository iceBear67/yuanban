package io.ib67.yuanban.recipe.internal;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Keyable;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.crafting.recipe.BannerDuplicateRecipe;
import io.ib67.yuanban.crafting.recipe.BookCloningRecipe;
import io.ib67.yuanban.crafting.recipe.CookingBookInfo;
import io.ib67.yuanban.crafting.recipe.CookingCategory;
import io.ib67.yuanban.crafting.recipe.CookingKind;
import io.ib67.yuanban.crafting.recipe.CookingRecipe;
import io.ib67.yuanban.crafting.recipe.CraftingBookInfo;
import io.ib67.yuanban.crafting.recipe.CraftingCategory;
import io.ib67.yuanban.crafting.recipe.DecoratedPotRecipe;
import io.ib67.yuanban.crafting.recipe.DyeRecipe;
import io.ib67.yuanban.crafting.recipe.FireworkRocketRecipe;
import io.ib67.yuanban.crafting.recipe.FireworkStarFadeRecipe;
import io.ib67.yuanban.crafting.recipe.FireworkStarRecipe;
import io.ib67.yuanban.crafting.recipe.ImbueRecipe;
import io.ib67.yuanban.crafting.recipe.IntBounds;
import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.ItemStackTemplate;
import io.ib67.yuanban.crafting.recipe.MapExtendingRecipe;
import io.ib67.yuanban.crafting.recipe.Recipe;
import io.ib67.yuanban.crafting.recipe.RepairItemRecipe;
import io.ib67.yuanban.crafting.recipe.ShapedRecipe;
import io.ib67.yuanban.crafting.recipe.ShapelessRecipe;
import io.ib67.yuanban.crafting.recipe.ShieldDecorationRecipe;
import io.ib67.yuanban.crafting.recipe.SmithingTransformRecipe;
import io.ib67.yuanban.crafting.recipe.SmithingTrimRecipe;
import io.ib67.yuanban.crafting.recipe.StonecutterRecipe;
import io.ib67.yuanban.crafting.recipe.TransmuteRecipe;
import io.ib67.yuanban.itemtag.ItemTags;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.item.Material;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.registry.RegistryTranscoder;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 配方文件的编解码器，结构和原版 26.2 各配方类的 {@code MAP_CODEC} 相同。只用于解码。
 */
@ApiStatus.Internal
public final class RecipeCodecs {
    private static final Key RECIPE_SERIALIZER_REGISTRY = Key.key("recipe_serializer");
    private static final Key ITEM_REGISTRY = Key.key("item");

    private final ItemTags tags;
    private final RegistryTranscoder<JsonElement> transcoder;

    private final Codec<Material> item;
    private final Codec<ItemIngredient> ingredient;
    private final Codec<ItemStackTemplate> result;
    private final MapCodec<CraftingBookInfo> craftingBook;
    private final MapCodec<CookingBookInfo> cookingBook;
    private final Map<Key, MapCodec<? extends Recipe<?>>> serializers = new LinkedHashMap<>();
    private final Codec<Recipe<?>> recipe;

    /**
     * @param tags    本次加载展开的物品标签，标签原料在这里查找
     * @param process 解码物品组件和纹饰样式时使用它的注册表
     */
    public RecipeCodecs(ItemTags tags, ServerProcess process) {
        this.tags = tags;
        this.transcoder = new RegistryTranscoder<>(Transcoder.JSON, process);

        this.item = ID.comapFlatMap(RecipeCodecs::lookupItem, Material::key);
        this.ingredient = Codec.either(TAG_REFERENCE, compactList(item)).comapFlatMap(this::toIngredient, RecipeCodecs::fromIngredient);
        // 结果模板原样保存组件补丁，与物品原型相同的条目也保留
        MapCodec<ItemStackTemplate> fullResult = RecordCodecBuilder.mapCodec(i -> i.group(
                item.fieldOf("id").forGetter(ItemStackTemplate::material),
                intRange(1, 99).optionalFieldOf("count", 1).forGetter(ItemStackTemplate::count),
                minestom(DataComponent.PATCH_CODEC).optionalFieldOf("components", DataComponentMap.EMPTY).forGetter(ItemStackTemplate::components)
        ).apply(i, ItemStackTemplate::new));
        this.result = Codec.withAlternative(fullResult.codec(), item, ItemStackTemplate::of);
        this.craftingBook = RecordCodecBuilder.mapCodec(i -> i.group(
                enumCodec(CraftingCategory.values()).optionalFieldOf("category", CraftingCategory.MISC).forGetter(CraftingBookInfo::category),
                Codec.STRING.optionalFieldOf("group", "").forGetter(CraftingBookInfo::group)
        ).apply(i, CraftingBookInfo::new));
        this.cookingBook = RecordCodecBuilder.mapCodec(i -> i.group(
                enumCodec(CookingCategory.values()).optionalFieldOf("category", CookingCategory.MISC).forGetter(CookingBookInfo::category),
                Codec.STRING.optionalFieldOf("group", "").forGetter(CookingBookInfo::group)
        ).apply(i, CookingBookInfo::new));

        registerSerializers();
        Codec<Key> type = ID.comapFlatMap(id -> serializers.containsKey(id)
                ? DataResult.success(id)
                : DataResult.error(() -> unknownKey(RECIPE_SERIALIZER_REGISTRY, id)), Function.identity());
        this.recipe = type.dispatch("type", RecipeCodecs::typeOf, serializers::get);
    }

    public DataResult<Recipe<?>> decode(JsonElement json) {
        return recipe.parse(JsonOps.INSTANCE, json);
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

    private void registerSerializers() {
        serializers.put(Key.key("crafting_shaped"), withCommonInfo(RecordCodecBuilder.<ShapedRecipe>mapCodec(i -> i.group(
                craftingBook.forGetter(ShapedRecipe::book),
                shapedPattern().forGetter(r -> new ShapedPattern(r.width(), r.height(), r.ingredients())),
                result.fieldOf("result").forGetter(ShapedRecipe::result)
        ).apply(i, (b, p, r) -> new ShapedRecipe(b, p.width(), p.height(), p.ingredients(), r)))));
        serializers.put(Key.key("crafting_shapeless"), withCommonInfo(RecordCodecBuilder.<ShapelessRecipe>mapCodec(i -> i.group(
                craftingBook.forGetter(ShapelessRecipe::book),
                result.fieldOf("result").forGetter(ShapelessRecipe::result),
                ingredient.listOf(1, 9).fieldOf("ingredients").forGetter(ShapelessRecipe::ingredients)
        ).apply(i, ShapelessRecipe::new))));
        serializers.put(Key.key("crafting_dye"), withCommonInfo(RecordCodecBuilder.<DyeRecipe>mapCodec(i -> i.group(
                craftingBook.forGetter(DyeRecipe::book),
                ingredient.fieldOf("target").forGetter(DyeRecipe::target),
                ingredient.fieldOf("dye").forGetter(DyeRecipe::dye),
                result.fieldOf("result").forGetter(DyeRecipe::result)
        ).apply(i, DyeRecipe::new))));
        serializers.put(Key.key("crafting_imbue"), withCommonInfo(RecordCodecBuilder.<ImbueRecipe>mapCodec(i -> i.group(
                craftingBook.forGetter(ImbueRecipe::book),
                ingredient.fieldOf("source").forGetter(ImbueRecipe::source),
                ingredient.fieldOf("material").forGetter(ImbueRecipe::material),
                result.fieldOf("result").forGetter(ImbueRecipe::result)
        ).apply(i, ImbueRecipe::new))));
        serializers.put(Key.key("crafting_transmute"), withCommonInfo(RecordCodecBuilder.<TransmuteRecipe>mapCodec(i -> i.group(
                craftingBook.forGetter(TransmuteRecipe::book),
                ingredient.fieldOf("input").forGetter(TransmuteRecipe::input),
                ingredient.fieldOf("material").forGetter(TransmuteRecipe::material),
                bounds(1, 8).optionalFieldOf("material_count", IntBounds.exactly(1)).forGetter(TransmuteRecipe::materialCount),
                result.fieldOf("result").forGetter(TransmuteRecipe::result),
                Codec.BOOL.optionalFieldOf("add_material_count_to_result", false).forGetter(TransmuteRecipe::addMaterialCountToResult)
        ).apply(i, TransmuteRecipe::new))));
        serializers.put(Key.key("crafting_decorated_pot"), RecordCodecBuilder.<DecoratedPotRecipe>mapCodec(i -> i.group(
                ingredient.fieldOf("back").forGetter(DecoratedPotRecipe::back),
                ingredient.fieldOf("left").forGetter(DecoratedPotRecipe::left),
                ingredient.fieldOf("right").forGetter(DecoratedPotRecipe::right),
                ingredient.fieldOf("front").forGetter(DecoratedPotRecipe::front),
                result.fieldOf("result").forGetter(DecoratedPotRecipe::result)
        ).apply(i, DecoratedPotRecipe::new)));
        serializers.put(Key.key("crafting_special_bookcloning"), RecordCodecBuilder.<BookCloningRecipe>mapCodec(i -> i.group(
                ingredient.fieldOf("source").forGetter(BookCloningRecipe::source),
                ingredient.fieldOf("material").forGetter(BookCloningRecipe::material),
                bounds(0, 2).optionalFieldOf("allowed_generations", IntBounds.between(0, 1)).forGetter(BookCloningRecipe::allowedGenerations),
                result.fieldOf("result").forGetter(BookCloningRecipe::result)
        ).apply(i, BookCloningRecipe::new)));
        serializers.put(Key.key("crafting_special_mapextending"), RecordCodecBuilder.<MapExtendingRecipe>mapCodec(i -> i.group(
                ingredient.fieldOf("map").forGetter(MapExtendingRecipe::map),
                ingredient.fieldOf("material").forGetter(MapExtendingRecipe::material),
                result.fieldOf("result").forGetter(MapExtendingRecipe::result)
        ).apply(i, MapExtendingRecipe::new)));
        serializers.put(Key.key("crafting_special_firework_rocket"), RecordCodecBuilder.<FireworkRocketRecipe>mapCodec(i -> i.group(
                ingredient.fieldOf("shell").forGetter(FireworkRocketRecipe::shell),
                ingredient.fieldOf("fuel").forGetter(FireworkRocketRecipe::fuel),
                ingredient.fieldOf("star").forGetter(FireworkRocketRecipe::star),
                result.fieldOf("result").forGetter(FireworkRocketRecipe::result)
        ).apply(i, FireworkRocketRecipe::new)));
        Codec<FireworkExplosion.Shape> shape = enumCodec(FireworkExplosion.Shape.values());
        Keyable shapeNames = Keyable.forStrings(() -> Arrays.stream(FireworkExplosion.Shape.values()).map(RecipeCodecs::serializedName));
        serializers.put(Key.key("crafting_special_firework_star"), RecordCodecBuilder.<FireworkStarRecipe>mapCodec(i -> i.group(
                Codec.simpleMap(shape, ingredient, shapeNames).fieldOf("shapes").forGetter(FireworkStarRecipe::shapes),
                ingredient.fieldOf("trail").forGetter(FireworkStarRecipe::trail),
                ingredient.fieldOf("twinkle").forGetter(FireworkStarRecipe::twinkle),
                ingredient.fieldOf("fuel").forGetter(FireworkStarRecipe::fuel),
                ingredient.fieldOf("dye").forGetter(FireworkStarRecipe::dye),
                result.fieldOf("result").forGetter(FireworkStarRecipe::result)
        ).apply(i, FireworkStarRecipe::new)));
        serializers.put(Key.key("crafting_special_firework_star_fade"), RecordCodecBuilder.<FireworkStarFadeRecipe>mapCodec(i -> i.group(
                ingredient.fieldOf("target").forGetter(FireworkStarFadeRecipe::target),
                ingredient.fieldOf("dye").forGetter(FireworkStarFadeRecipe::dye),
                result.fieldOf("result").forGetter(FireworkStarFadeRecipe::result)
        ).apply(i, FireworkStarFadeRecipe::new)));
        serializers.put(Key.key("crafting_special_bannerduplicate"), RecordCodecBuilder.<BannerDuplicateRecipe>mapCodec(i -> i.group(
                ingredient.fieldOf("banner").forGetter(BannerDuplicateRecipe::banner),
                result.fieldOf("result").forGetter(BannerDuplicateRecipe::result)
        ).apply(i, BannerDuplicateRecipe::new)));
        serializers.put(Key.key("crafting_special_shielddecoration"), RecordCodecBuilder.<ShieldDecorationRecipe>mapCodec(i -> i.group(
                ingredient.fieldOf("banner").forGetter(ShieldDecorationRecipe::banner),
                ingredient.fieldOf("target").forGetter(ShieldDecorationRecipe::target),
                result.fieldOf("result").forGetter(ShieldDecorationRecipe::result)
        ).apply(i, ShieldDecorationRecipe::new)));
        serializers.put(Key.key("crafting_special_repairitem"), MapCodec.unit(new RepairItemRecipe()));
        for (var kind : CookingKind.values()) {
            serializers.put(Key.key(kind.type()), withCommonInfo(RecordCodecBuilder.<CookingRecipe>mapCodec(i -> i.group(
                    cookingBook.forGetter(CookingRecipe::book),
                    ingredient.fieldOf("ingredient").forGetter(CookingRecipe::ingredient),
                    result.fieldOf("result").forGetter(CookingRecipe::result),
                    Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(CookingRecipe::experience),
                    Codec.INT.optionalFieldOf("cookingtime", kind.defaultCookingTime()).forGetter(CookingRecipe::cookingTime)
            ).apply(i, (b, in, r, experience, time) -> new CookingRecipe(kind, b, in, r, experience, time)))));
        }
        serializers.put(Key.key("stonecutting"), withCommonInfo(RecordCodecBuilder.<StonecutterRecipe>mapCodec(i -> i.group(
                ingredient.fieldOf("ingredient").forGetter(StonecutterRecipe::ingredient),
                result.fieldOf("result").forGetter(StonecutterRecipe::result)
        ).apply(i, StonecutterRecipe::new))));
        serializers.put(Key.key("smithing_transform"), withCommonInfo(RecordCodecBuilder.<SmithingTransformRecipe>mapCodec(i -> i.group(
                ingredient.optionalFieldOf("template").forGetter(SmithingTransformRecipe::template),
                ingredient.fieldOf("base").forGetter(SmithingTransformRecipe::base),
                ingredient.optionalFieldOf("addition").forGetter(SmithingTransformRecipe::addition),
                result.fieldOf("result").forGetter(SmithingTransformRecipe::result)
        ).apply(i, SmithingTransformRecipe::new))));
        serializers.put(Key.key("smithing_trim"), withCommonInfo(RecordCodecBuilder.<SmithingTrimRecipe>mapCodec(i -> i.group(
                ingredient.fieldOf("template").forGetter(SmithingTrimRecipe::template),
                ingredient.fieldOf("base").forGetter(SmithingTrimRecipe::base),
                ingredient.fieldOf("addition").forGetter(SmithingTrimRecipe::addition),
                minestom(TrimPattern.CODEC).fieldOf("pattern").forGetter(SmithingTrimRecipe::pattern)
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
        };
    }

    // 原料：原版 Ingredient.CODEC = nonEmptyHolderSet(HolderSetCodec.create(ITEM, Item.CODEC, false))

    private static final Codec<Key> ID = Codec.STRING.comapFlatMap(RecipeCodecs::readId, Key::asString);

    private static final Codec<Key> TAG_REFERENCE = Codec.STRING.comapFlatMap(
            name -> name.startsWith("#") ? readId(name.substring(1)) : DataResult.error(() -> "Not a tag id"),
            id -> "#" + id.asString());

    private DataResult<ItemIngredient> toIngredient(Either<Key, List<Material>> value) {
        return value.map(
                tag -> tags.find(tag)
                        .<DataResult<ItemIngredient>>map(items -> DataResult.success(new ItemIngredient.OfTag(tag, items)))
                        .orElseGet(() -> DataResult.error(() -> "Missing tag: '" + tag.asString() + "' in '" + ITEM_REGISTRY.asString() + "'")),
                items -> items.isEmpty()
                        ? DataResult.error(() -> "List must have contents")
                        : DataResult.success(new ItemIngredient.OfItems(items)));
    }

    private static Either<Key, List<Material>> fromIngredient(ItemIngredient ingredient) {
        return switch (ingredient) {
            case ItemIngredient.OfTag tag -> Either.left(tag.tag());
            case ItemIngredient.OfItems items -> Either.right(items.items());
        };
    }

    private static DataResult<Material> lookupItem(Key id) {
        var material = Material.fromKey(id);
        if (material == null) {
            return DataResult.error(() -> unknownKey(ITEM_REGISTRY, id));
        }
        if (material == Material.AIR) {
            return DataResult.error(() -> "Item must not be minecraft:air");
        }
        return DataResult.success(material);
    }

    /**
     * 原版 {@code ExtraCodecs.compactListCodec}：先尝试列表，再尝试单个元素。
     */
    private static <E> Codec<List<E>> compactList(Codec<E> element) {
        return Codec.either(element.listOf(), element)
                .xmap(either -> either.map(Function.identity(), List::of),
                        list -> list.size() == 1 ? Either.right(list.getFirst()) : Either.left(list));
    }

    // 有序配方的图案：原版 ShapedRecipePattern

    private record ShapedPattern(int width, int height, List<Optional<ItemIngredient>> ingredients) {
    }

    private record PatternData(Map<Character, ItemIngredient> key, List<String> pattern) {
    }

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

    private static final Codec<Character> SYMBOL = Codec.STRING.comapFlatMap(symbol -> {
        if (symbol.length() != 1) {
            return DataResult.error(() -> "Invalid key entry: '" + symbol + "' is an invalid symbol (must be 1 character only).");
        }
        return " ".equals(symbol)
                ? DataResult.error(() -> "Invalid key entry: ' ' is a reserved symbol.")
                : DataResult.success(symbol.charAt(0));
    }, String::valueOf);

    private MapCodec<ShapedPattern> shapedPattern() {
        MapCodec<PatternData> data = RecordCodecBuilder.mapCodec(i -> i.group(
                strictMap(SYMBOL, ingredient).fieldOf("key").forGetter(PatternData::key),
                PATTERN.fieldOf("pattern").forGetter(PatternData::pattern)
        ).apply(i, PatternData::new));
        return data.flatXmap(RecipeCodecs::unpack, pattern -> DataResult.error(() -> "Cannot encode unpacked recipe"));
    }

    private static DataResult<ShapedPattern> unpack(PatternData data) {
        var rows = shrink(data.pattern());
        if (rows.isEmpty()) {
            // 原版在这里访问空数组的第一个元素，抛出未捕获的 ArrayIndexOutOfBoundsException
            return DataResult.error(() -> "Invalid pattern: pattern contains only spaces");
        }
        int width = rows.getFirst().length();
        int height = rows.size();
        var ingredients = new ArrayList<Optional<ItemIngredient>>(width * height);
        var unused = new HashSet<>(data.key().keySet());
        for (var row : rows) {
            for (int x = 0; x < row.length(); x++) {
                char symbol = row.charAt(x);
                if (symbol == ' ') {
                    ingredients.add(Optional.empty());
                } else {
                    var ingredient = data.key().get(symbol);
                    if (ingredient == null) {
                        return DataResult.error(() -> "Pattern references symbol '" + symbol + "' but it's not defined in the key");
                    }
                    ingredients.add(Optional.of(ingredient));
                }
                unused.remove(symbol);
            }
        }
        if (!unused.isEmpty()) {
            return DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + unused);
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

    /**
     * 原版 {@code ExtraCodecs.StrictUnboundedMapCodec}：遇到第一个无法解码的条目就失败。
     */
    private static <K, V> Codec<Map<K, V>> strictMap(Codec<K> keyCodec, Codec<V> valueCodec) {
        return Codec.of(Encoder.error("Cannot encode strict map"), new Decoder<>() {
            @Override
            public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getMap(input).flatMap(map -> {
                    var read = new HashMap<K, V>();
                    for (var entry : map.entries().toList()) {
                        var key = keyCodec.parse(ops, entry.getFirst());
                        var value = valueCodec.parse(ops, entry.getSecond());
                        var pair = key.apply2stable(Pair::of, value);
                        var error = pair.error();
                        if (error.isPresent()) {
                            var message = error.get().message();
                            return DataResult.error(() -> key.result().isPresent()
                                    ? "Map entry '" + key.result().get() + "' : " + message
                                    : message);
                        }
                        var kv = pair.result().orElseThrow();
                        if (read.put(kv.getFirst(), kv.getSecond()) != null) {
                            return DataResult.error(() -> "Duplicate entry for key: '" + kv.getFirst() + "'");
                        }
                    }
                    return DataResult.success(Pair.of(Map.copyOf(read), input));
                });
            }
        });
    }

    // 其他基础类型

    private static Codec<Integer> intRange(int min, int max) {
        return Codec.INT.validate(value -> value >= min && value <= max
                ? DataResult.success(value)
                : DataResult.error(() -> "Value must be within range [" + min + ";" + max + "]: " + value));
    }

    /**
     * 原版 {@code MinMaxBounds.Ints.CODEC}，再检查范围在 {@code [lowest, highest]} 之内。
     */
    private static Codec<IntBounds> bounds(int lowest, int highest) {
        Codec<IntBounds> range = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("min").forGetter(IntBounds::min),
                Codec.INT.optionalFieldOf("max").forGetter(IntBounds::max)
        ).apply(i, IntBounds::new));
        return Codec.either(range, Codec.INT)
                .xmap(either -> either.map(Function.identity(), IntBounds::exactly), Either::left)
                .validate(bounds -> bounds.min().isPresent() && bounds.max().isPresent() && bounds.min().get() > bounds.max().get()
                        ? DataResult.error(() -> "Swapped bounds in range: " + bounds.min() + " is higher than " + bounds.max())
                        : DataResult.success(bounds))
                .validate(bounds -> bounds.within(lowest, highest)
                        ? DataResult.success(bounds)
                        : DataResult.error(() -> "Range must be within [" + lowest + ".." + highest + "], but was " + describe(bounds)));
    }

    private static String describe(IntBounds bounds) {
        return bounds.min().map(String::valueOf).orElse("-∞") + ".." + bounds.max().map(String::valueOf).orElse("+∞");
    }

    /**
     * 原版 {@code StringRepresentable.fromEnum}：按小写名字解码。
     */
    private static <E extends Enum<E>> Codec<E> enumCodec(E[] values) {
        var byName = new HashMap<String, E>();
        for (var value : values) {
            byName.put(serializedName(value), value);
        }
        return Codec.STRING.comapFlatMap(name -> {
            var value = byName.get(name);
            return value != null ? DataResult.success(value) : DataResult.error(() -> "Unknown element name:" + name);
        }, RecipeCodecs::serializedName);
    }

    private static String serializedName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 把 Minestom 的编解码器包装成 DataFixerUpper 的解码器，用 {@link RegistryTranscoder} 提供注册表。
     */
    private <A> Codec<A> minestom(net.minestom.server.codec.Codec<A> codec) {
        return Codec.of(Encoder.error("Cannot encode " + codec), new Decoder<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                var json = ops.convertTo(JsonOps.INSTANCE, input);
                return switch (codec.decode(transcoder, json)) {
                    case Result.Ok<A>(var value) -> DataResult.success(Pair.of(value, input));
                    case Result.Error<A>(var message) -> DataResult.error(() -> message);
                };
            }
        });
    }

    /**
     * 原版 {@code Identifier.read}：省略命名空间的时候为 {@code minecraft}。
     */
    private static DataResult<Key> readId(String input) {
        int separator = input.indexOf(':');
        var namespace = separator > 0 ? input.substring(0, separator) : Key.MINECRAFT_NAMESPACE;
        var path = separator >= 0 ? input.substring(separator + 1) : input;
        try {
            return DataResult.success(Key.key(namespace, path));
        } catch (InvalidKeyException e) {
            return DataResult.error(() -> "Not a valid resource location: " + input + " " + e.getMessage());
        }
    }

    private static String unknownKey(Key registry, Key id) {
        return "Unknown registry key in ResourceKey[minecraft:root / " + registry.asString() + "]: " + id.asString();
    }
}
