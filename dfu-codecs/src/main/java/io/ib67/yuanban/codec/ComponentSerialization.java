package io.ib67.yuanban.codec;

import com.google.gson.JsonElement;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.VanillaText.AtlasSprite;
import io.ib67.yuanban.codec.VanillaText.BlockDataSource;
import io.ib67.yuanban.codec.VanillaText.ChangePage;
import io.ib67.yuanban.codec.VanillaText.ClickEvent;
import io.ib67.yuanban.codec.VanillaText.Contents;
import io.ib67.yuanban.codec.VanillaText.CopyToClipboard;
import io.ib67.yuanban.codec.VanillaText.Custom;
import io.ib67.yuanban.codec.VanillaText.DataSource;
import io.ib67.yuanban.codec.VanillaText.EntityDataSource;
import io.ib67.yuanban.codec.VanillaText.HoverEvent;
import io.ib67.yuanban.codec.VanillaText.Keybind;
import io.ib67.yuanban.codec.VanillaText.Nbt;
import io.ib67.yuanban.codec.VanillaText.ObjectContents;
import io.ib67.yuanban.codec.VanillaText.ObjectInfo;
import io.ib67.yuanban.codec.VanillaText.OpenFile;
import io.ib67.yuanban.codec.VanillaText.OpenUrl;
import io.ib67.yuanban.codec.VanillaText.PlainText;
import io.ib67.yuanban.codec.VanillaText.PlayerSprite;
import io.ib67.yuanban.codec.VanillaText.RunCommand;
import io.ib67.yuanban.codec.VanillaText.Score;
import io.ib67.yuanban.codec.VanillaText.Selector;
import io.ib67.yuanban.codec.VanillaText.ShowDialog;
import io.ib67.yuanban.codec.VanillaText.ShowEntity;
import io.ib67.yuanban.codec.VanillaText.ShowItem;
import io.ib67.yuanban.codec.VanillaText.ShowText;
import io.ib67.yuanban.codec.VanillaText.StorageDataSource;
import io.ib67.yuanban.codec.VanillaText.SuggestCommand;
import io.ib67.yuanban.codec.VanillaText.TextColor;
import io.ib67.yuanban.codec.VanillaText.TextComponent;
import io.ib67.yuanban.codec.VanillaText.TextStyle;
import io.ib67.yuanban.codec.VanillaText.Translatable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.dialog.Dialog;
import net.minestom.server.entity.EntityType;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.ResolvableProfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 原版 {@code net.minecraft.network.chat.ComponentSerialization}：文本组件的编解码器，结果是 adventure 的 {@link Component}。
 * <p>
 * 编解码器按原版的结构工作（{@link VanillaText}），包括 {@code Style.Serializer.MAP_CODEC}、{@code TextColor.CODEC}、
 * {@code ClickEvent.CODEC}、{@code HoverEvent.CODEC} 和七种内容；接受与拒绝的输入、错误文本都与原版相同。
 * 选择器、NBT 路径和方块坐标按原版的命令解析器检查（{@link EntitySelectorParser}、{@link NbtPath}、
 * {@link BlockPosArgument}）。
 * <p>
 * 原版的值属于 Minestom 时使用 Minestom 的编解码器（{@link MinestomCodecs#adapt}）：内联的对话框、
 * 悬停物品的组件值、玩家资料。这些值内部只报告第一个错误，错误文本是 Minestom 的。
 * <p>
 * 解码结果转换成 adventure 的组件时，以下原版数据 adventure 无法表示：
 * <ul>
 *   <li>颜色用名字写出还是用十六进制写出：adventure 中与命名颜色数值相同的颜色总是 {@link net.kyori.adventure.text.format.NamedTextColor}，
 *   编码时写成名字。原版比较颜色时只比较数值，所以解码结果的含义相同。</li>
 *   <li>{@code nbt} 内容的 {@code block} 来源中带小数的相对坐标（例如 {@code ~0.5}）：adventure 的世界坐标只能是整数，
 *   解码失败并报告这一点。</li>
 *   <li>{@code object} 内容的玩家资料中的披风、鞘翅和模型（{@code cape}、{@code elytra}、{@code model}）：
 *   adventure 的玩家头像只有皮肤，解码失败并报告这一点。</li>
 * </ul>
 */
// FIXME(Minestom M22): Minestom 的 Codec.COMPONENT（ComponentCodecs.COMPONENT）接受空列表，原版报告错误 List must have contents；
//  它把列表构造成空组件加上全部元素作为子组件，原版以第一个元素为基础并把其余元素作为子组件附加，两者的样式继承不同。
//  它还缺少 nbt 内容、show_item 的组件和 translatable 的非组件参数。Minestom 修复之后改用 Codec.COMPONENT。
public final class ComponentSerialization {
    /**
     * 原版结构上的 {@code ComponentSerialization.CODEC}。
     */
    static final Codec<TextComponent> VANILLA_CODEC = Codec.recursive("Component", ComponentSerialization::createCodec);

    /**
     * 原版 {@code AtlasSprite.DEFAULT_ATLAS}（{@code AtlasIds.BLOCKS}）。
     */
    private static final Key DEFAULT_ATLAS = Key.key("blocks");

    // 原版 TextColor.CODEC 与 Style.Serializer.MAP_CODEC
    private static final Map<String, TextColor> NAMED_COLORS = new HashMap<>();

    static {
        named("black", 0);
        named("dark_blue", 170);
        named("dark_green", 43520);
        named("dark_aqua", 43690);
        named("dark_red", 11141120);
        named("dark_purple", 11141290);
        named("gold", 16755200);
        named("gray", 11184810);
        named("dark_gray", 5592405);
        named("blue", 5592575);
        named("green", 5635925);
        named("aqua", 5636095);
        named("red", 16733525);
        named("light_purple", 16733695);
        named("yellow", 16777045);
        named("white", 16777215);
    }

    /**
     * 原版 {@code TextColor.CODEC}：颜色名或者 {@code #RRGGBB}。
     */
    static final Codec<TextColor> TEXT_COLOR = Codec.STRING.comapFlatMap(ComponentSerialization::parseColor, TextColor::serialize);
    /**
     * 原版 {@code HoverEvent.EntityTooltipInfo} 中的 {@code BuiltInRegistries.ENTITY_TYPE.byNameCodec()}。
     */
    private static final Codec<EntityType> ENTITY_TYPE = RegistryCodecs.byName(RegistryIds.ENTITY_TYPE, EntityType::fromKey, EntityType::key);
    /**
     * 原版 {@code ClickEvent.CODEC}。
     */
    static final Codec<ClickEvent> CLICK_EVENT = ClickAction.CODEC.dispatch("action", ClickEvent::action, ClickAction::valueCodec);
    /**
     * 原版 {@code HoverEvent.CODEC}。
     */
    static final Codec<HoverEvent> HOVER_EVENT = HoverAction.CODEC.dispatch("action", HoverEvent::action, HoverAction::valueCodec);
    /**
     * 原版 {@code Style.Serializer.MAP_CODEC}。
     */
    static final MapCodec<TextStyle> STYLE = RecordCodecBuilder.mapCodec(i -> i.group(
            TEXT_COLOR.optionalFieldOf("color").forGetter(o -> Optional.ofNullable(o.color())),
            ExtraCodecs.ARGB_COLOR_CODEC.optionalFieldOf("shadow_color").forGetter(o -> Optional.ofNullable(o.shadowColor())),
            Codec.BOOL.optionalFieldOf("bold").forGetter(o -> Optional.ofNullable(o.bold())),
            Codec.BOOL.optionalFieldOf("italic").forGetter(o -> Optional.ofNullable(o.italic())),
            Codec.BOOL.optionalFieldOf("underlined").forGetter(o -> Optional.ofNullable(o.underlined())),
            Codec.BOOL.optionalFieldOf("strikethrough").forGetter(o -> Optional.ofNullable(o.strikethrough())),
            Codec.BOOL.optionalFieldOf("obfuscated").forGetter(o -> Optional.ofNullable(o.obfuscated())),
            CLICK_EVENT.optionalFieldOf("click_event").forGetter(o -> Optional.ofNullable(o.clickEvent())),
            HOVER_EVENT.optionalFieldOf("hover_event").forGetter(o -> Optional.ofNullable(o.hoverEvent())),
            Codec.STRING.optionalFieldOf("insertion").forGetter(o -> Optional.ofNullable(o.insertion())),
            Identifiers.CODEC.optionalFieldOf("font").forGetter(o -> Optional.ofNullable(o.font()))
    ).apply(i, (color, shadowColor, bold, italic, underlined, strikethrough, obfuscated, clickEvent, hoverEvent, insertion, font) ->
            new TextStyle(color.orElse(null), shadowColor.orElse(null), bold.orElse(null), italic.orElse(null),
                    underlined.orElse(null), strikethrough.orElse(null), obfuscated.orElse(null), clickEvent.orElse(null),
                    hoverEvent.orElse(null), insertion.orElse(null), font.orElse(null))));

    // 原版选择器、NBT 路径和方块坐标的 CompilableString 编解码器
    /**
     * 原版 {@code EntitySelector.COMPILABLE_CODEC}。
     */
    static final Codec<CompilableString<EntitySelectorParser.Selector>> SELECTOR_STRING = CompilableString.codec(
            ComponentSerialization::parseSelector, ComponentSerialization::selectorErrorMessage);
    /**
     * 原版 {@code NbtContents.NBT_PATH_CODEC}。
     */
    static final Codec<CompilableString<NbtPath>> NBT_PATH_STRING = CompilableString.codec(
            NbtPath::parse, ComponentSerialization::nbtPathErrorMessage);
    /**
     * 原版 {@code BlockDataSource.BLOCK_POS_CODEC}。
     */
    static final Codec<CompilableString<BlockPosArgument.Coordinates>> BLOCK_POS_STRING = CompilableString.codec(
            BlockPosArgument::parse, ComponentSerialization::blockPosErrorMessage);

    // 原版 TranslatableContents 的参数
    /**
     * 原版 {@code TranslatableContents.PRIMITIVE_ARG_CODEC}。
     */
    private static final Codec<Object> PRIMITIVE_ARG_CODEC = ExtraCodecs.JAVA.validate(ComponentSerialization::filterAllowedArguments);
    /**
     * 原版 {@code TranslatableContents.ARG_CODEC}：可以写成纯文本的组件按字符串保存。
     */
    private static final Codec<Object> ARG_CODEC = Codec.either(PRIMITIVE_ARG_CODEC, VANILLA_CODEC).xmap(
            either -> either.map(o -> o, component -> Objects.requireNonNullElse(component.tryCollapseToString(), component)),
            o -> o instanceof TextComponent component ? Either.right(component) : Either.left(o));

    // 七种内容
    /**
     * 原版 {@code PlainTextContents.MAP_CODEC}。
     */
    static final MapCodec<PlainText> PLAIN_TEXT = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("text").forGetter(PlainText::text)
    ).apply(i, PlainText::new));
    /**
     * 原版 {@code TranslatableContents.MAP_CODEC}。
     */
    static final MapCodec<Translatable> TRANSLATABLE = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("translate").forGetter(Translatable::key),
            Codec.STRING.lenientOptionalFieldOf("fallback").forGetter(o -> Optional.ofNullable(o.fallback())),
            ARG_CODEC.listOf().optionalFieldOf("with").forGetter(o -> o.args().isEmpty() ? Optional.empty() : Optional.of(o.args()))
    ).apply(i, (key, fallback, args) -> new Translatable(key, fallback.orElse(null), args.orElse(List.of()))));
    /**
     * 原版 {@code KeybindContents.MAP_CODEC}。
     */
    static final MapCodec<Keybind> KEYBIND = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("keybind").forGetter(Keybind::name)
    ).apply(i, Keybind::new));
    /**
     * 原版 {@code ScoreContents.INNER_CODEC}：{@code name} 先按选择器解析，失败时按普通字符串读取。
     */
    private static final MapCodec<Score> SCORE_INNER = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.either(SELECTOR_STRING, Codec.STRING).fieldOf("name")
                    .forGetter(o -> Either.right(o.name())),
            Codec.STRING.fieldOf("objective").forGetter(Score::objective)
    ).apply(i, (name, objective) -> new Score(name.map(CompilableString::source, s -> s), objective)));
    /**
     * 原版 {@code ScoreContents.MAP_CODEC}。
     */
    static final MapCodec<Score> SCORE = SCORE_INNER.fieldOf("score");
    /**
     * 原版 {@code SelectorContents.MAP_CODEC}。
     */
    static final MapCodec<Selector> SELECTOR = RecordCodecBuilder.mapCodec(i -> i.group(
            SELECTOR_STRING.fieldOf("selector").forGetter(Selector::selector),
            VANILLA_CODEC.optionalFieldOf("separator").forGetter(Selector::separator)
    ).apply(i, Selector::new));

    /**
     * 原版 {@code EntityDataSource.MAP_CODEC}。
     */
    static final MapCodec<EntityDataSource> ENTITY_DATA_SOURCE = RecordCodecBuilder.mapCodec(i -> i.group(
            SELECTOR_STRING.fieldOf("entity").forGetter(EntityDataSource::selector)
    ).apply(i, EntityDataSource::new));
    /**
     * 原版 {@code BlockDataSource.MAP_CODEC}。
     */
    static final MapCodec<BlockDataSource> BLOCK_DATA_SOURCE = RecordCodecBuilder.mapCodec(i -> i.group(
            BLOCK_POS_STRING.fieldOf("block").forGetter(BlockDataSource::coordinates)
    ).apply(i, BlockDataSource::new));
    /**
     * 原版 {@code StorageDataSource.MAP_CODEC}。
     */
    static final MapCodec<StorageDataSource> STORAGE_DATA_SOURCE = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifiers.CODEC.fieldOf("storage").forGetter(StorageDataSource::id)
    ).apply(i, StorageDataSource::new));
    /**
     * 原版 {@code DataSources.ID_MAPPER}，按原版的注册顺序。
     */
    private static final ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends DataSource>> DATA_SOURCE_TYPES =
            new ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends DataSource>>()
                    .put("entity", ENTITY_DATA_SOURCE)
                    .put("block", BLOCK_DATA_SOURCE)
                    .put("storage", STORAGE_DATA_SOURCE);
    /**
     * 原版 {@code DataSources.CODEC}。
     */
    private static final MapCodec<DataSource> DATA_SOURCE = createLegacyComponentMatcher(DATA_SOURCE_TYPES, DataSource::codec, "source");
    /**
     * 原版 {@code NbtContents.MAP_CODEC}。
     */
    static final MapCodec<Nbt> NBT = RecordCodecBuilder.<Nbt>mapCodec(i -> i.group(
            NBT_PATH_STRING.fieldOf("nbt").forGetter(Nbt::nbtPath),
            Codec.BOOL.lenientOptionalFieldOf("interpret", false).forGetter(Nbt::interpreting),
            Codec.BOOL.lenientOptionalFieldOf("plain", false).forGetter(Nbt::plain),
            VANILLA_CODEC.lenientOptionalFieldOf("separator").forGetter(Nbt::separator),
            DATA_SOURCE.forGetter(Nbt::dataSource)
    ).apply(i, Nbt::new)).validate(nbt -> nbt.interpreting() && nbt.plain()
            ? DataResult.error(() -> "'interpret' and 'plain' flags can't be both on")
            : DataResult.success(nbt));

    /**
     * 原版 {@code ResolvableProfile.CODEC}：完整的玩家资料对象（使用 Minestom 的 {@link ResolvableProfile#CODEC}），
     * 或者只有玩家名的字符串。玩家名按原版 {@code ExtraCodecs.PLAYER_NAME} 检查。
     */
    private static final Codec<ResolvableProfile> RESOLVABLE_PROFILE = Codec.withAlternative(
            MinestomCodecs.adapt(ResolvableProfile.CODEC).validate(ComponentSerialization::validatePlayerName),
            ExtraCodecs.PLAYER_NAME,
            name -> new ResolvableProfile(new ResolvableProfile.Partial(name, null, List.of())));
    /**
     * 原版 {@code AtlasSprite.MAP_CODEC}。
     */
    static final MapCodec<AtlasSprite> ATLAS_SPRITE = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifiers.CODEC.optionalFieldOf("atlas", DEFAULT_ATLAS).forGetter(AtlasSprite::atlas),
            Identifiers.CODEC.fieldOf("sprite").forGetter(AtlasSprite::sprite)
    ).apply(i, AtlasSprite::new));
    /**
     * 原版 {@code PlayerSprite.MAP_CODEC}。
     */
    static final MapCodec<PlayerSprite> PLAYER_SPRITE = RecordCodecBuilder.mapCodec(i -> i.group(
            RESOLVABLE_PROFILE.fieldOf("player").forGetter(PlayerSprite::player),
            Codec.BOOL.optionalFieldOf("hat", true).forGetter(PlayerSprite::hat)
    ).apply(i, PlayerSprite::new));
    /**
     * 原版 {@code ObjectInfos.ID_MAPPER}，按原版的注册顺序。
     */
    private static final ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends ObjectInfo>> OBJECT_INFO_TYPES =
            new ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends ObjectInfo>>()
                    .put("atlas", ATLAS_SPRITE)
                    .put("player", PLAYER_SPRITE);
    /**
     * 原版 {@code ObjectInfos.CODEC}。
     */
    private static final MapCodec<ObjectInfo> OBJECT_INFO = createLegacyComponentMatcher(OBJECT_INFO_TYPES, ObjectInfo::codec, "object");
    /**
     * 原版 {@code ObjectContents.MAP_CODEC}。
     */
    static final MapCodec<ObjectContents> OBJECT = RecordCodecBuilder.mapCodec(i -> i.group(
            OBJECT_INFO.forGetter(ObjectContents::contents),
            VANILLA_CODEC.optionalFieldOf("fallback").forGetter(ObjectContents::fallback)
    ).apply(i, ObjectContents::new));
    /**
     * 原版 {@code ComponentSerialization.bootstrap} 登记的七种内容，按原版的注册顺序：没有 {@code type} 字段时按这个顺序尝试。
     */
    private static final ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends Contents>> CONTENT_TYPES =
            new ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends Contents>>()
                    .put("text", PLAIN_TEXT)
                    .put("translatable", TRANSLATABLE)
                    .put("keybind", KEYBIND)
                    .put("score", SCORE)
                    .put("selector", SELECTOR)
                    .put("nbt", NBT)
                    .put("object", OBJECT);

    /**
     * 原版 {@code ComponentSerialization.CODEC}：字符串、非空的组件列表，或者带内容、{@code extra}（非空列表）和样式字段的对象。
     * 列表的结果是第一个元素附加其余元素，与原版 {@code createFromList} 相同。
     */
    public static final Codec<Component> CODEC = VANILLA_CODEC.flatXmap(AdventureText::toAdventure, AdventureText::fromAdventure);

    private ComponentSerialization() {
    }

    /**
     * 原版 {@code ComponentSerialization.flatRestrictedCodec}：解码后按原版的方式重新编码为 JSON，
     * 长度超过 {@code maxFlatSize} 个字符时为 {@code Component was too large: greater than max size <maxFlatSize>}。
     * 长度按原版的数据结构计算；其中由 Minestom 编码的部分（内联对话框、悬停物品的组件值、玩家资料）按 Minestom 的编码计算。
     */
    public static Codec<Component> flatRestrictedCodec(int maxFlatSize) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
                return VANILLA_CODEC.decode(ops, input)
                        .flatMap(pair -> isTooLarge(ops, pair.getFirst())
                                ? DataResult.error(() -> "Component was too large: greater than max size " + maxFlatSize)
                                : DataResult.success(pair))
                        .flatMap(pair -> AdventureText.toAdventure(pair.getFirst()).map(component -> Pair.of(component, pair.getSecond())));
            }

            @Override
            public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
                return CODEC.encodeStart(ops, input);
            }

            private <T> boolean isTooLarge(DynamicOps<T> ops, TextComponent input) {
                DataResult<JsonElement> json = VANILLA_CODEC.encodeStart(asJsonOps(ops), input);
                return json.isSuccess() && encodesLongerThan(json.getOrThrow(), maxFlatSize);
            }
        };
    }

    private static <T> DynamicOps<JsonElement> asJsonOps(DynamicOps<T> ops) {
        if (ops instanceof RegistryOps<T> registryOps) {
            return registryOps.withParent(JsonOps.INSTANCE);
        }
        return JsonOps.INSTANCE;
    }

    /**
     * 原版 {@code GsonHelper.encodesLongerThan}：按 Gson 的 {@link JsonWriter} 写出时字符数是否超过 {@code limit}。
     */
    private static boolean encodesLongerThan(JsonElement element, int limit) {
        try {
            Streams.write(element, new JsonWriter(Streams.writerForAppendable(new CountedAppendable(limit))));
            return false;
        } catch (IllegalStateException e) {
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 原版 {@code ComponentSerialization.createFromList}：第一个元素加上其余元素作为子组件。
     */
    private static TextComponent createFromList(List<TextComponent> list) {
        TextComponent first = list.getFirst();
        List<TextComponent> siblings = new ArrayList<>(first.siblings());
        siblings.addAll(list.subList(1, list.size()));
        return new TextComponent(first.contents(), siblings, first.style());
    }

    /**
     * 原版 {@code ComponentSerialization.createCodec}。
     */
    private static Codec<TextComponent> createCodec(Codec<TextComponent> topSerializer) {
        MapCodec<Contents> contentsCodec = createLegacyComponentMatcher(CONTENT_TYPES, Contents::codec, "type");
        Codec<TextComponent> fullCodec = RecordCodecBuilder.create(i -> i.group(
                contentsCodec.forGetter(TextComponent::contents),
                ExtraCodecs.nonEmptyList(topSerializer.listOf()).optionalFieldOf("extra", List.of()).forGetter(TextComponent::siblings),
                STYLE.forGetter(TextComponent::style)
        ).apply(i, TextComponent::new));
        return Codec.either(Codec.either(Codec.STRING, ExtraCodecs.nonEmptyList(topSerializer.listOf())), fullCodec).xmap(
                specialOrComponent -> specialOrComponent.map(
                        special -> special.map(TextComponent::literal, ComponentSerialization::createFromList),
                        component -> component),
                component -> {
                    String text = component.tryCollapseToString();
                    return text != null ? Either.left(Either.left(text)) : Either.right(component);
                });
    }

    /**
     * 原版 {@code ComponentSerialization.createLegacyComponentMatcher}：有 {@code typeFieldName} 字段时按它分派，
     * 否则按登记顺序尝试每种类型，第一个完全成功的为结果，都失败时为 {@code No matching codec found}。
     */
    static <T> MapCodec<T> createLegacyComponentMatcher(ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends T>> types,
                                                       Function<T, MapCodec<? extends T>> codecGetter, String typeFieldName) {
        MapCodec<T> compactCodec = new FuzzyCodec<>(types.values(), codecGetter);
        MapCodec<T> discriminatorCodec = types.codec(Codec.STRING).dispatchMap(typeFieldName, codecGetter, c -> c);
        MapCodec<T> contentsCodec = new StrictEither<>(typeFieldName, discriminatorCodec, compactCodec);
        return ExtraCodecs.orCompressed(contentsCodec, discriminatorCodec);
    }

    private static void named(String name, int rgb) {
        NAMED_COLORS.put(name, new TextColor(rgb & 16777215, name));
    }

    /**
     * 原版 {@code TextColor.parseColor}。
     */
    private static DataResult<TextColor> parseColor(String color) {
        if (color.startsWith("#")) {
            try {
                int value = Integer.parseInt(color.substring(1), 16);
                return value >= 0 && value <= 16777215
                        ? DataResult.success(new TextColor(value, null), Lifecycle.stable())
                        : DataResult.error(() -> "Color value out of range: " + color);
            } catch (NumberFormatException e) {
                return DataResult.error(() -> "Invalid color value: " + color);
            }
        }
        TextColor predefinedColor = NAMED_COLORS.get(color);
        return predefinedColor == null
                ? DataResult.error(() -> "Invalid color name: " + color)
                : DataResult.success(predefinedColor, Lifecycle.stable());
    }

    /**
     * 原版 {@code TranslatableContents.filterAllowedArguments}。
     */
    private static DataResult<Object> filterAllowedArguments(Object result) {
        return !(result instanceof Number || result instanceof Boolean || result instanceof String)
                ? DataResult.error(() -> "This value needs to be parsed as component")
                : DataResult.success(result);
    }

    static EntitySelectorParser.Selector parseSelector(com.mojang.brigadier.StringReader reader)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return new EntitySelectorParser(reader, true).parse();
    }

    static String selectorErrorMessage(String original, com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
        return "Invalid selector component: " + original + ": " + exception.getMessage();
    }

    static String nbtPathErrorMessage(String original, com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
        return "Invalid NBT path: " + original + ": " + exception.getMessage();
    }

    static String blockPosErrorMessage(String original, com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
        return "Invalid coordinates path: " + original + ": " + exception.getMessage();
    }

    /**
     * 原版 {@code ExtraCodecs.PLAYER_NAME} 对玩家资料中玩家名的检查。
     */
    private static DataResult<ResolvableProfile> validatePlayerName(ResolvableProfile profile) {
        String name = switch (profile.profile()) {
            case net.minestom.server.utils.Either.Left(GameProfile gameProfile) -> gameProfile.name();
            case net.minestom.server.utils.Either.Right(ResolvableProfile.Partial partial) -> partial.name();
        };
        if (name == null) {
            return DataResult.success(profile);
        }
        return ExtraCodecs.PLAYER_NAME.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createString(name)).map(ignored -> profile);
    }

    /**
     * 原版 {@code ClickEvent.Action}。
     */
    enum ClickAction {
        OPEN_URL("open_url", true, RecordCodecBuilder.<OpenUrl>mapCodec(i -> i.group(
                ExtraCodecs.UNTRUSTED_URI.fieldOf("url").forGetter(OpenUrl::uri)
        ).apply(i, OpenUrl::new))),
        OPEN_FILE("open_file", false, RecordCodecBuilder.<OpenFile>mapCodec(i -> i.group(
                Codec.STRING.fieldOf("path").forGetter(OpenFile::path)
        ).apply(i, OpenFile::new))),
        RUN_COMMAND("run_command", true, RecordCodecBuilder.<RunCommand>mapCodec(i -> i.group(
                ExtraCodecs.CHAT_STRING.fieldOf("command").forGetter(RunCommand::command)
        ).apply(i, RunCommand::new))),
        SUGGEST_COMMAND("suggest_command", true, RecordCodecBuilder.<SuggestCommand>mapCodec(i -> i.group(
                ExtraCodecs.CHAT_STRING.fieldOf("command").forGetter(SuggestCommand::command)
        ).apply(i, SuggestCommand::new))),
        SHOW_DIALOG("show_dialog", true, RecordCodecBuilder.<ShowDialog>mapCodec(i -> i.group(
                RegistryCodecs.file(RegistryIds.DIALOG, MinestomCodecs.adapt(Dialog.REGISTRY_CODEC), true).fieldOf("dialog")
                        .forGetter(ShowDialog::dialog)
        ).apply(i, ShowDialog::new))),
        CHANGE_PAGE("change_page", true, RecordCodecBuilder.<ChangePage>mapCodec(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("page").forGetter(ChangePage::page)
        ).apply(i, ChangePage::new))),
        COPY_TO_CLIPBOARD("copy_to_clipboard", true, RecordCodecBuilder.<CopyToClipboard>mapCodec(i -> i.group(
                Codec.STRING.fieldOf("value").forGetter(CopyToClipboard::value)
        ).apply(i, CopyToClipboard::new))),
        CUSTOM("custom", true, RecordCodecBuilder.<Custom>mapCodec(i -> i.group(
                Identifiers.CODEC.fieldOf("id").forGetter(Custom::id),
                NbtCodecs.NBT.optionalFieldOf("payload").forGetter(Custom::payload)
        ).apply(i, Custom::new)));

        /**
         * 原版 {@code ClickEvent.Action.UNSAFE_CODEC}。
         */
        static final Codec<ClickAction> UNSAFE_CODEC = StringRepresentables.fromEnum(values(), ClickAction::serializedName);
        /**
         * 原版 {@code ClickEvent.Action.CODEC}：{@code open_file} 不允许由服务端发出，为
         * {@code Click event type not allowed: OPEN_FILE}。
         */
        static final Codec<ClickAction> CODEC = UNSAFE_CODEC.validate(ClickAction::filterForSerialization);

        private final String name;
        private final boolean allowFromServer;
        private final MapCodec<? extends ClickEvent> codec;

        ClickAction(String name, boolean allowFromServer, MapCodec<? extends ClickEvent> codec) {
            this.name = name;
            this.allowFromServer = allowFromServer;
            this.codec = codec;
        }

        String serializedName() {
            return name;
        }

        MapCodec<? extends ClickEvent> valueCodec() {
            return codec;
        }

        private static DataResult<ClickAction> filterForSerialization(ClickAction action) {
            return !action.allowFromServer
                    ? DataResult.error(() -> "Click event type not allowed: " + action)
                    : DataResult.success(action, Lifecycle.stable());
        }
    }

    /**
     * 原版 {@code HoverEvent.Action}。
     */
    enum HoverAction {
        SHOW_TEXT("show_text", RecordCodecBuilder.<ShowText>mapCodec(i -> i.group(
                VANILLA_CODEC.fieldOf("value").forGetter(ShowText::value)
        ).apply(i, ShowText::new))),
        SHOW_ITEM("show_item", ItemCodecs.ITEM_STACK_TEMPLATE_MAP.xmap(ShowItem::new, ShowItem::item)),
        SHOW_ENTITY("show_entity", RecordCodecBuilder.<ShowEntity>mapCodec(i -> i.group(
                ENTITY_TYPE.fieldOf("id").forGetter(ShowEntity::type),
                UUIDUtil.LENIENT_CODEC.fieldOf("uuid").forGetter(ShowEntity::uuid),
                VANILLA_CODEC.optionalFieldOf("name").forGetter(ShowEntity::name)
        ).apply(i, ShowEntity::new)));

        /**
         * 原版 {@code HoverEvent.Action.UNSAFE_CODEC}。
         */
        static final Codec<HoverAction> UNSAFE_CODEC = StringRepresentables.fromValues(values(), HoverAction::serializedName);
        /**
         * 原版 {@code HoverEvent.Action.CODEC}：三种动作都允许由服务端发出。
         */
        static final Codec<HoverAction> CODEC = UNSAFE_CODEC.validate(action -> DataResult.success(action, Lifecycle.stable()));

        private final String name;
        private final MapCodec<? extends HoverEvent> codec;

        HoverAction(String name, MapCodec<? extends HoverEvent> codec) {
            this.name = name;
            this.codec = codec;
        }

        String serializedName() {
            return name;
        }

        MapCodec<? extends HoverEvent> valueCodec() {
            return codec;
        }

        @Override
        public String toString() {
            return "<action " + name + ">";
        }
    }

    /**
     * 原版 {@code ComponentSerialization.StrictEither}：有类型字段时按类型分派，否则逐个尝试。
     */
    private static final class StrictEither<T> extends MapCodec<T> {
        private final String typeFieldName;
        private final MapCodec<T> typed;
        private final MapCodec<T> fuzzy;

        StrictEither(String typeFieldName, MapCodec<T> typed, MapCodec<T> fuzzy) {
            this.typeFieldName = typeFieldName;
            this.typed = typed;
            this.fuzzy = fuzzy;
        }

        @Override
        public <O> DataResult<T> decode(DynamicOps<O> ops, MapLike<O> input) {
            return input.get(typeFieldName) != null ? typed.decode(ops, input) : fuzzy.decode(ops, input);
        }

        @Override
        public <O> RecordBuilder<O> encode(T input, DynamicOps<O> ops, RecordBuilder<O> prefix) {
            return fuzzy.encode(input, ops, prefix);
        }

        @Override
        public <O> Stream<O> keys(DynamicOps<O> ops) {
            return Stream.concat(typed.keys(ops), fuzzy.keys(ops)).distinct();
        }
    }

    /**
     * 原版 {@code ComponentSerialization.FuzzyCodec}：按顺序尝试，返回第一个完全成功的结果。
     */
    private static final class FuzzyCodec<T> extends MapCodec<T> {
        private final Collection<MapCodec<? extends T>> codecs;
        private final Function<T, ? extends MapEncoder<? extends T>> encoderGetter;

        FuzzyCodec(Collection<MapCodec<? extends T>> codecs, Function<T, ? extends MapEncoder<? extends T>> encoderGetter) {
            this.codecs = codecs;
            this.encoderGetter = encoderGetter;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> DataResult<T> decode(DynamicOps<S> ops, MapLike<S> input) {
            for (MapDecoder<? extends T> codec : codecs) {
                DataResult<? extends T> result = codec.decode(ops, input);
                if (result.result().isPresent()) {
                    return (DataResult<T>) result;
                }
            }
            return DataResult.error(() -> "No matching codec found");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> RecordBuilder<S> encode(T input, DynamicOps<S> ops, RecordBuilder<S> prefix) {
            MapEncoder<T> encoder = (MapEncoder<T>) encoderGetter.apply(input);
            return encoder.encode(input, ops, prefix);
        }

        @Override
        public <S> Stream<S> keys(DynamicOps<S> ops) {
            return codecs.stream().flatMap(c -> c.keys(ops)).distinct();
        }

        @Override
        public String toString() {
            return "FuzzyCodec[" + codecs + "]";
        }
    }

    /**
     * 原版 {@code GsonHelper.CountedAppendable}。
     */
    private static final class CountedAppendable implements Appendable {
        private int totalCount;
        private final int limit;

        CountedAppendable(int limit) {
            this.limit = limit;
        }

        private Appendable accountChars(int count) {
            totalCount += count;
            if (totalCount > limit) {
                throw new IllegalStateException("Character count over limit: " + totalCount + " > " + limit);
            }
            return this;
        }

        @Override
        public Appendable append(CharSequence csq) {
            return accountChars(csq.length());
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) {
            return accountChars(end - start);
        }

        @Override
        public Appendable append(char c) {
            return accountChars(1);
        }
    }
}
