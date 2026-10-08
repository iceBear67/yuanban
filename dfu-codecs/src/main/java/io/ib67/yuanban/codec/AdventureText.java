package io.ib67.yuanban.codec;

import com.mojang.serialization.DataResult;
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
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.text.BlockNBTComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.EntityNBTComponent;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.NBTComponent;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.ScoreComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.StorageNBTComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.DataComponentValue;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.adventure.MinestomDataComponentValue;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.dialog.Dialog;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.item.Material;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.registry.Holder;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link VanillaText} 与 adventure 组件之间的转换，供 {@link ComponentSerialization} 使用。
 * adventure 无法表示的原版数据在转换时得到错误结果，见 {@link ComponentSerialization} 的说明。
 */
final class AdventureText {
    private AdventureText() {
    }

    /**
     * 原版结构转换成 adventure 的组件。
     */
    static DataResult<Component> toAdventure(TextComponent component) {
        return contents(component.contents()).flatMap(base -> style(component.style()).flatMap(style -> {
            List<Component> children = new ArrayList<>(component.siblings().size());
            for (TextComponent sibling : component.siblings()) {
                var child = toAdventure(sibling);
                if (child.isError()) {
                    return child;
                }
                children.add(child.getOrThrow());
            }
            return DataResult.success(base.style(style).children(children));
        }));
    }

    private static DataResult<Component> contents(Contents contents) {
        return switch (contents) {
            case PlainText(String text) -> DataResult.success(Component.text(text));
            case Translatable(String key, String fallback, List<Object> args) -> {
                List<ComponentLike> arguments = new ArrayList<>(args.size());
                for (Object arg : args) {
                    switch (arg) {
                        case String string -> arguments.add(TranslationArgument.component(Component.text(string)));
                        case Number number -> arguments.add(TranslationArgument.numeric(number));
                        case Boolean bool -> arguments.add(TranslationArgument.bool(bool));
                        case TextComponent component -> {
                            var converted = toAdventure(component);
                            if (converted.isError()) {
                                yield converted;
                            }
                            arguments.add(TranslationArgument.component(converted.getOrThrow()));
                        }
                        default -> throw new IllegalStateException("Unexpected translation argument: " + arg);
                    }
                }
                yield DataResult.success(Component.translatable().key(key).fallback(fallback).arguments(arguments).build());
            }
            case Keybind(String name) -> DataResult.success(Component.keybind(name));
            case Score(String name, String objective) -> DataResult.success(Component.score(name, objective));
            case Selector(CompilableString<EntitySelectorParser.Selector> selector, Optional<TextComponent> separator) ->
                    optional(separator).map(converted -> Component.selector(selector.source(), converted));
            case Nbt nbt -> optional(nbt.separator()).flatMap(separator -> nbtComponent(nbt, separator));
            case ObjectContents(ObjectInfo info, Optional<TextComponent> fallback) -> objectContents(info)
                    .flatMap(objectContents -> optional(fallback)
                            .map(converted -> Component.object().contents(objectContents).fallback(converted).build()));
        };
    }

    private static DataResult<Component> nbtComponent(Nbt nbt, @Nullable Component separator) {
        String path = nbt.nbtPath().source();
        return switch (nbt.dataSource()) {
            case EntityDataSource(CompilableString<EntitySelectorParser.Selector> selector) -> DataResult.success(Component.entityNBT()
                    .nbtPath(path).interpret(nbt.interpreting()).plain(nbt.plain()).separator(separator)
                    .selector(selector.source()).build());
            case BlockDataSource(CompilableString<BlockPosArgument.Coordinates> coordinates) -> blockPos(coordinates)
                    .map(pos -> Component.blockNBT()
                            .nbtPath(path).interpret(nbt.interpreting()).plain(nbt.plain()).separator(separator)
                            .pos(pos).build());
            case StorageDataSource(Key id) -> DataResult.success(Component.storageNBT()
                    .nbtPath(path).interpret(nbt.interpreting()).plain(nbt.plain()).separator(separator)
                    .storage(id).build());
        };
    }

    /**
     * 原版的方块坐标转换成 adventure 的坐标。adventure 的世界坐标只能是整数，原版带小数的相对坐标无法表示。
     */
    private static DataResult<BlockNBTComponent.Pos> blockPos(CompilableString<BlockPosArgument.Coordinates> coordinates) {
        return switch (coordinates.compiled()) {
            case BlockPosArgument.LocalCoordinates(double left, double up, double forwards) ->
                    DataResult.success(BlockNBTComponent.LocalPos.localPos(left, up, forwards));
            case BlockPosArgument.WorldCoordinates(BlockPosArgument.WorldCoordinate x, BlockPosArgument.WorldCoordinate y,
                                                   BlockPosArgument.WorldCoordinate z) -> {
                for (BlockPosArgument.WorldCoordinate coordinate : List.of(x, y, z)) {
                    if (coordinate.value() != Math.floor(coordinate.value())
                            || coordinate.value() < Integer.MIN_VALUE || coordinate.value() > Integer.MAX_VALUE) {
                        yield DataResult.error(() -> "Block position " + coordinates.source()
                                + " has a relative coordinate that is not a whole number, which adventure cannot represent");
                    }
                }
                yield DataResult.success(BlockNBTComponent.WorldPos.worldPos(worldCoordinate(x), worldCoordinate(y), worldCoordinate(z)));
            }
        };
    }

    private static BlockNBTComponent.WorldPos.Coordinate worldCoordinate(BlockPosArgument.WorldCoordinate coordinate) {
        return BlockNBTComponent.WorldPos.Coordinate.coordinate((int) coordinate.value(), coordinate.relative()
                ? BlockNBTComponent.WorldPos.Coordinate.Type.RELATIVE
                : BlockNBTComponent.WorldPos.Coordinate.Type.ABSOLUTE);
    }

    /**
     * 原版的玩家资料转换成 adventure 的玩家头像。adventure 只保存皮肤，披风、鞘翅和模型无法表示。
     */
    private static DataResult<net.kyori.adventure.text.object.ObjectContents> objectContents(ObjectInfo info) {
        return switch (info) {
            case AtlasSprite(Key atlas, Key sprite) ->
                    DataResult.success(net.kyori.adventure.text.object.ObjectContents.sprite(atlas, sprite));
            case PlayerSprite(ResolvableProfile player, boolean hat) -> {
                PlayerSkin.Patch patch = player.patch();
                if (patch.cape() != null || patch.elytra() != null || patch.model() != null) {
                    yield DataResult.error(() -> "Player profile skin patch with cape, elytra or model cannot be represented by adventure: " + player);
                }
                var builder = net.kyori.adventure.text.object.ObjectContents.playerHead();
                player.applySkinToPlayerHeadContents(builder);
                yield DataResult.success(builder.hat(hat).build());
            }
        };
    }

    private static DataResult<@Nullable Component> optional(Optional<TextComponent> component) {
        if (component.isEmpty()) {
            return DataResult.success(null);
        }
        return toAdventure(component.get()).map(converted -> converted);
    }

    private static DataResult<Style> style(TextStyle style) {
        var builder = Style.style();
        if (style.color() != null) {
            builder.color(net.kyori.adventure.text.format.TextColor.color(style.color().value()));
        }
        if (style.shadowColor() != null) {
            builder.shadowColor(ShadowColor.shadowColor(style.shadowColor()));
        }
        builder.decoration(TextDecoration.BOLD, TextDecoration.State.byBoolean(style.bold()));
        builder.decoration(TextDecoration.ITALIC, TextDecoration.State.byBoolean(style.italic()));
        builder.decoration(TextDecoration.UNDERLINED, TextDecoration.State.byBoolean(style.underlined()));
        builder.decoration(TextDecoration.STRIKETHROUGH, TextDecoration.State.byBoolean(style.strikethrough()));
        builder.decoration(TextDecoration.OBFUSCATED, TextDecoration.State.byBoolean(style.obfuscated()));
        if (style.clickEvent() != null) {
            builder.clickEvent(clickEvent(style.clickEvent()));
        }
        builder.insertion(style.insertion());
        builder.font(style.font());
        if (style.hoverEvent() == null) {
            return DataResult.success(builder.build());
        }
        return hoverEvent(style.hoverEvent()).map(hoverEvent -> builder.hoverEvent(hoverEvent).build());
    }

    private static net.kyori.adventure.text.event.ClickEvent<?> clickEvent(ClickEvent event) {
        return switch (event) {
            case OpenUrl(URI uri) -> net.kyori.adventure.text.event.ClickEvent.openUrl(uri.toString());
            case OpenFile(String path) -> net.kyori.adventure.text.event.ClickEvent.openFile(path);
            case RunCommand(String command) -> net.kyori.adventure.text.event.ClickEvent.runCommand(command);
            case SuggestCommand(String command) -> net.kyori.adventure.text.event.ClickEvent.suggestCommand(command);
            case ShowDialog(Holder<Dialog> dialog) -> net.kyori.adventure.text.event.ClickEvent.showDialog(dialogLike(dialog));
            case ChangePage(int page) -> net.kyori.adventure.text.event.ClickEvent.changePage(page);
            case CopyToClipboard(String value) -> net.kyori.adventure.text.event.ClickEvent.copyToClipboard(value);
            case Custom(Key id, Optional<BinaryTag> payload) ->
                    net.kyori.adventure.text.event.ClickEvent.custom(id, payload.map(MinestomAdventure::wrapNbt).orElse(null));
        };
    }

    /**
     * 注册表中的对话框转换成 {@link Dialog#forKey}，直接定义的对话框本身就是 adventure 的对话框。
     */
    private static net.kyori.adventure.dialog.DialogLike dialogLike(Holder<Dialog> dialog) {
        return switch (dialog) {
            case Dialog direct -> direct;
            case RegistryKey<Dialog> reference -> Dialog.forKey(reference);
            default -> throw new IllegalArgumentException("Unsupported dialog holder: " + dialog.getClass().getName());
        };
    }

    private static DataResult<net.kyori.adventure.text.event.HoverEvent<?>> hoverEvent(HoverEvent event) {
        return switch (event) {
            case ShowText(TextComponent value) -> toAdventure(value).map(net.kyori.adventure.text.event.HoverEvent::showText);
            case ShowItem(ItemStackTemplate item) -> {
                Map<Key, DataComponentValue> dataComponents = new LinkedHashMap<>();
                for (DataComponent.Value entry : item.components().entrySet()) {
                    dataComponents.put(entry.component().key(), entry.value() == null
                            ? DataComponentValue.removed()
                            : MinestomDataComponentValue.dataComponentValue(entry.value()));
                }
                yield DataResult.success(net.kyori.adventure.text.event.HoverEvent.showItem(item.item(), item.count(), dataComponents));
            }
            case ShowEntity(EntityType type, java.util.UUID uuid, Optional<TextComponent> name) -> optional(name)
                    .map(converted -> net.kyori.adventure.text.event.HoverEvent.showEntity(type, uuid, converted));
        };
    }

    /**
     * adventure 的组件转换成原版结构，用于编码。
     */
    static DataResult<TextComponent> fromAdventure(Component component) {
        return contentsFrom(component).flatMap(contents -> styleFrom(component.style()).flatMap(style -> {
            List<TextComponent> siblings = new ArrayList<>(component.children().size());
            for (Component child : component.children()) {
                var converted = fromAdventure(child);
                if (converted.isError()) {
                    return converted;
                }
                siblings.add(converted.getOrThrow());
            }
            return DataResult.success(new TextComponent(contents, siblings, style));
        }));
    }

    private static DataResult<Contents> contentsFrom(Component component) {
        return switch (component) {
            case net.kyori.adventure.text.TextComponent text -> DataResult.success(new PlainText(text.content()));
            case TranslatableComponent translatable -> {
                List<Object> args = new ArrayList<>(translatable.arguments().size());
                for (TranslationArgument argument : translatable.arguments()) {
                    switch (argument.value()) {
                        case Component argComponent -> {
                            var converted = fromAdventure(argComponent);
                            if (converted.isError()) {
                                yield converted.map(ignored -> null);
                            }
                            args.add(converted.getOrThrow());
                        }
                        case Number number -> args.add(number);
                        case Boolean bool -> args.add(bool);
                        default -> throw new IllegalStateException("Unexpected translation argument: " + argument.value());
                    }
                }
                yield DataResult.success(new Translatable(translatable.key(), translatable.fallback(), args));
            }
            case KeybindComponent keybind -> DataResult.success(new Keybind(keybind.keybind()));
            case ScoreComponent score -> DataResult.success(new Score(score.name(), score.objective()));
            case SelectorComponent selector -> optionalFrom(selector.separator()).flatMap(separator -> CompilableString.compile(
                    selector.pattern(), ComponentSerialization::parseSelector, ComponentSerialization::selectorErrorMessage
            ).map(compiled -> new Selector(compiled, separator)));
            case NBTComponent<?> nbt -> nbtFrom(nbt);
            case ObjectComponent object -> optionalFrom(object.fallback())
                    .map(fallback -> new ObjectContents(objectInfoFrom(object.contents()), fallback));
        };
    }

    private static DataResult<Contents> nbtFrom(NBTComponent<?> nbt) {
        var path = CompilableString.compile(nbt.nbtPath(), NbtPath::parse, ComponentSerialization::nbtPathErrorMessage);
        DataResult<DataSource> source = switch (nbt) {
            case EntityNBTComponent entity -> CompilableString.compile(entity.selector(), ComponentSerialization::parseSelector,
                    ComponentSerialization::selectorErrorMessage).map(EntityDataSource::new);
            case BlockNBTComponent block -> CompilableString.compile(block.pos().asString(), BlockPosArgument::parse,
                    ComponentSerialization::blockPosErrorMessage).map(BlockDataSource::new);
            case StorageNBTComponent storage -> DataResult.success(new StorageDataSource(storage.storage()));
        };
        return path.flatMap(compiledPath -> source.flatMap(dataSource -> optionalFrom(nbt.separator())
                .map(separator -> new Nbt(compiledPath, nbt.interpret(), nbt.plain(), separator, dataSource))));
    }

    private static ObjectInfo objectInfoFrom(net.kyori.adventure.text.object.ObjectContents contents) {
        return switch (contents) {
            case SpriteObjectContents sprite -> new AtlasSprite(sprite.atlas(), sprite.sprite());
            case PlayerHeadObjectContents playerHead ->
                    new PlayerSprite(ResolvableProfile.fromPlayerHeadContents(playerHead), playerHead.hat());
        };
    }

    private static DataResult<Optional<TextComponent>> optionalFrom(@Nullable Component component) {
        if (component == null) {
            return DataResult.success(Optional.empty());
        }
        return fromAdventure(component).map(Optional::of);
    }

    private static DataResult<TextStyle> styleFrom(Style style) {
        TextColor color = null;
        var styleColor = style.color();
        if (styleColor != null) {
            String name = styleColor instanceof NamedTextColor named ? NamedTextColor.NAMES.key(named) : null;
            color = new TextColor(styleColor.value(), name);
        }
        var styleShadowColor = style.shadowColor();
        Integer shadowColor = styleShadowColor != null ? styleShadowColor.value() : null;
        var styleClickEvent = style.clickEvent();
        DataResult<@Nullable ClickEvent> clickEvent = styleClickEvent != null
                ? clickEventFrom(styleClickEvent).map(event -> event)
                : DataResult.success(null);
        var styleHoverEvent = style.hoverEvent();
        DataResult<@Nullable HoverEvent> hoverEvent = styleHoverEvent != null
                ? hoverEventFrom(styleHoverEvent).map(event -> event)
                : DataResult.success(null);
        TextColor finalColor = color;
        return clickEvent.flatMap(click -> hoverEvent.map(hover -> new TextStyle(finalColor, shadowColor,
                bool(style.decoration(TextDecoration.BOLD)), bool(style.decoration(TextDecoration.ITALIC)),
                bool(style.decoration(TextDecoration.UNDERLINED)), bool(style.decoration(TextDecoration.STRIKETHROUGH)),
                bool(style.decoration(TextDecoration.OBFUSCATED)), click, hover, style.insertion(),
                style.font())));
    }

    private static @Nullable Boolean bool(TextDecoration.State state) {
        return switch (state) {
            case NOT_SET -> null;
            case FALSE -> false;
            case TRUE -> true;
        };
    }

    private static DataResult<ClickEvent> clickEventFrom(net.kyori.adventure.text.event.ClickEvent<?> event) {
        var payload = event.payload();
        return switch (event.action()) {
            case net.kyori.adventure.text.event.ClickEvent.Action.OpenUrl _ -> {
                String url = ((net.kyori.adventure.text.event.ClickEvent.Payload.Text) payload).value();
                try {
                    yield DataResult.success(new OpenUrl(new URI(url)));
                } catch (URISyntaxException e) {
                    yield DataResult.error(e::getMessage);
                }
            }
            case net.kyori.adventure.text.event.ClickEvent.Action.OpenFile _ ->
                    DataResult.success(new OpenFile(((net.kyori.adventure.text.event.ClickEvent.Payload.Text) payload).value()));
            case net.kyori.adventure.text.event.ClickEvent.Action.RunCommand _ ->
                    DataResult.success(new RunCommand(((net.kyori.adventure.text.event.ClickEvent.Payload.Text) payload).value()));
            case net.kyori.adventure.text.event.ClickEvent.Action.SuggestCommand _ ->
                    DataResult.success(new SuggestCommand(((net.kyori.adventure.text.event.ClickEvent.Payload.Text) payload).value()));
            case net.kyori.adventure.text.event.ClickEvent.Action.CopyToClipboard _ ->
                    DataResult.success(new CopyToClipboard(((net.kyori.adventure.text.event.ClickEvent.Payload.Text) payload).value()));
            case net.kyori.adventure.text.event.ClickEvent.Action.ChangePage _ ->
                    DataResult.success(new ChangePage(((net.kyori.adventure.text.event.ClickEvent.Payload.Int) payload).integer()));
            // Dialog.unwrap 是 Minestom 标注为内部的方法，与 Minestom 自己的组件编解码器相同；只在编码时使用
            case net.kyori.adventure.text.event.ClickEvent.Action.ShowDialog _ -> DataResult.success(
                    new ShowDialog(Dialog.unwrap(((net.kyori.adventure.text.event.ClickEvent.Payload.Dialog) payload).dialog())));
            case net.kyori.adventure.text.event.ClickEvent.Action.Custom _ -> {
                var custom = (net.kyori.adventure.text.event.ClickEvent.Payload.Custom) payload;
                yield DataResult.success(new Custom(custom.key(), Optional.ofNullable(custom.nbt()).map(MinestomAdventure::unwrapNbt)));
            }
        };
    }

    private static DataResult<HoverEvent> hoverEventFrom(net.kyori.adventure.text.event.HoverEvent<?> event) {
        Object value = event.value();
        return switch (value) {
            case Component text -> fromAdventure(text).map(ShowText::new);
            case net.kyori.adventure.text.event.HoverEvent.ShowItem item -> showItemFrom(item);
            case net.kyori.adventure.text.event.HoverEvent.ShowEntity entity -> {
                EntityType type = EntityType.fromKey(entity.type());
                if (type == null) {
                    yield DataResult.error(() -> "Unknown entity type: " + entity.type().asString());
                }
                yield optionalFrom(entity.name()).map(name -> new ShowEntity(type, entity.id(), name));
            }
            default -> DataResult.error(() -> "Unsupported hover event action: " + event.action());
        };
    }

    private static DataResult<HoverEvent> showItemFrom(net.kyori.adventure.text.event.HoverEvent.ShowItem item) {
        Material material = Material.fromKey(item.item());
        if (material == null) {
            return DataResult.error(() -> "Unknown item: " + item.item().asString());
        }
        var patch = DataComponentMap.patchBuilder();
        for (var entry : item.dataComponents().entrySet()) {
            @SuppressWarnings("unchecked")
            DataComponent<Object> type = (DataComponent<Object>) DataComponent.fromKey(entry.getKey());
            if (type == null) {
                return DataResult.error(() -> "Unknown data component: " + entry.getKey().asString());
            }
            switch (entry.getValue()) {
                case DataComponentValue.Removed _ -> patch.remove(type);
                case MinestomDataComponentValue minestomValue when minestomValue.value() == null -> patch.remove(type);
                case MinestomDataComponentValue minestomValue -> patch.set(type, minestomValue.value());
                default -> {
                    return DataResult.error(() -> "Unsupported data component value: " + entry.getValue().getClass().getName());
                }
            }
        }
        return DataResult.success(new ShowItem(new ItemStackTemplate(material, item.count(), patch.build())));
    }
}
